package com.reso.compiler.codegen.functions;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.codegen.common.FunctionGenerationUtils;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.symbols.FunctionSymbol;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import com.reso.llvm.api.IrValue;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for functions.
 * This class handles the generation of LLVM IR code for function definitions,
 * including parameter processing, body generation, and return statement validation.
 */
public class FunctionGenerator {
    // Constants for special function and type names
    private static final String MAIN_FUNCTION_NAME = "main";
    private static final String I32_TYPE_NAME = "i32";
    private static final String ENTRY_BLOCK_NAME = "entry";

    private final CodeGenerationContext context;

    /**
     * Creates a new function generator.
     *
     * @param context The code generation context
     */
    public FunctionGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a function.
     *
     * @param funcDef The function definition context
     * @return The result of the function generation
     */
    @Nullable
    public ResoValue generateFunction(@Nonnull ResoParser.FunctionDefContext funcDef) {
        Objects.requireNonNull(funcDef, "Function definition cannot be null");

        FunctionContext functionContext = createFunctionContext(funcDef);
        if (functionContext == null) {
            return null;
        }

        // Validate main function requirements
        if (!validateMainFunction(functionContext)) {
            return null;
        }

        // Setup function environment
        setupFunctionEnvironment(functionContext);

        initGc(functionContext);

        // Generate function body
        FunctionBodyResult bodyResult = generateFunctionBody(funcDef);

        // Handle return statement requirements
        handleFunctionReturn(functionContext, bodyResult);

        // Cleanup function environment
        cleanupFunctionEnvironment(functionContext);

        return new ResoValue(functionContext.returnType, functionContext.functionValue,
            functionContext.line, functionContext.column);
    }

    /**
     * Creates a function context from the function definition.
     */
    @Nullable
    private FunctionContext createFunctionContext(@Nonnull ResoParser.FunctionDefContext funcDef) {
        String functionName = funcDef.Identifier().getText();
        int line = funcDef.getStart().getLine();
        int column = funcDef.getStart().getCharPositionInLine();

        FunctionSymbol functionSymbol = context.getSymbolTable().findFunction(functionName);
        if (functionSymbol == null) {
            context.getErrorReporter().error(
                "Failed to find function " + functionName, line, column);
            return null;
        }

        return new FunctionContext(
            functionName,
            functionSymbol.getLlvmValue(),
            functionSymbol.getReturnType(),
            line,
            column,
            MAIN_FUNCTION_NAME.equals(functionName)
        );
    }

    /**
     * Validates main function requirements.
     */
    private boolean validateMainFunction(@Nonnull FunctionContext functionContext) {
        if (!functionContext.isMain) {
            return true;
        }

        ErrorReporter errorReporter = context.getErrorReporter();

        // Main function should have no parameters (validation would need funcDef context)
        // This check should be done earlier in the compilation process

        // Check that main returns i32
        if (!I32_TYPE_NAME.equals(functionContext.returnType.getName())) {
            errorReporter.error(
                "Main function must return " + I32_TYPE_NAME + ", but got "
                    + functionContext.returnType.getName(),
                functionContext.line,
                functionContext.column
            );
            return false;
        }

        return true;
    }

    /**
     * Sets up the function environment for code generation.
     */
    private void setupFunctionEnvironment(@Nonnull FunctionContext functionContext) {
        // Save current function
        functionContext.previousFunction = context.getCurrentFunction();
        context.setCurrentFunction(functionContext.functionValue);

        // Create entry block
        IrBasicBlock entryBlock = IrFactory.createBasicBlock(
            functionContext.functionValue, ENTRY_BLOCK_NAME);

        // Enter function scope
        context.getSymbolTable().enterFunctionScope(functionContext.returnType);

        // Position builder at entry block
        IrFactory.positionAtEnd(context.getIrBuilder(), entryBlock);
    }

    private void initGc(@Nonnull FunctionContext functionContext) {
        if (functionContext.isMain) {
            IrValue gcInitFunction = IrFactory.declareGCInit(context.getIrModule());

            // Call GC_init()
            IrFactory.createCall(
                context.getIrBuilder(),
                gcInitFunction,
                new IrValue[0], // No arguments
                ""
            );
        }
    }

    /**
     * Generates the function body and returns information about the body structure.
     */
    @Nonnull
    private FunctionBodyResult generateFunctionBody(
        @Nonnull ResoParser.FunctionDefContext funcDef) {
        // Process parameters first
        processParameters(funcDef);

        // Process function body
        boolean endsWithReturningIf = FunctionGenerationUtils.processBody(context, funcDef.block());

        return new FunctionBodyResult(endsWithReturningIf);
    }

    /**
     * Processes parameters and adds them to the symbol table.
     */
    private void processParameters(@Nonnull ResoParser.FunctionDefContext funcDef) {
        if (funcDef.parameterList() == null) {
            return;
        }

        IrValue functionValue = context.getCurrentFunction();
        int paramIndex = 0;

        for (ResoParser.ParameterContext paramCtx : funcDef.parameterList().parameter()) {
            if (!processParameter(paramCtx, functionValue, paramIndex++)) {
                return; // Error occurred, stop processing
            }
        }
    }

    /**
     * Processes a single parameter.
     */
    private boolean processParameter(
        @Nonnull ResoParser.ParameterContext paramCtx,
        @Nonnull IrValue functionValue,
        int paramIndex) {

        String paramName = paramCtx.Identifier().getText();
        int line = paramCtx.getStart().getLine();
        int column = paramCtx.getStart().getCharPositionInLine();

        // Resolve parameter type
        ResoType paramType = context.getTypeSystem().resolveType(
            paramCtx.type(), context.getErrorReporter());

        if (paramType == null) {
            return false; // Error already reported
        }

        // Get parameter value
        IrValue paramValue = functionValue.getParam(paramIndex);

        // Create alloca and store
        IrValue paramAlloca = FunctionGenerationUtils.createParameterAlloca(
            context, paramName, paramType, paramValue);

        // Add to symbol table
        context.getSymbolTable().defineVariable(
            paramName, paramAlloca, paramType, false, true,
            context.getErrorReporter(), line, column);

        return true;
    }

    /**
     * Handles function return requirements and adds implicit returns if needed.
     */
    private void handleFunctionReturn(
        @Nonnull FunctionContext functionContext,
        @Nonnull FunctionBodyResult bodyResult) {

        if (!FunctionGenerationUtils.hasTerminator(context.getIrBuilder())) {
            addImplicitReturn(functionContext, bodyResult.endsWithReturningIf);
        }
    }

    /**
     * Adds an implicit return if needed.
     */
    private void addImplicitReturn(@Nonnull FunctionContext functionContext,
                                   boolean endsWithReturningIf) {
        if (endsWithReturningIf) {
            // All branches return, no additional return needed
            return;
        }

        if (functionContext.isMain) {
            addMainFunctionReturn(functionContext.returnType);
        } else {
            // Regular method return handling
            FunctionGenerationUtils.handleReturn(
                context,
                functionContext.functionName,
                functionContext.returnType,
                false, // endsWithReturningIf is already false here
                functionContext.line,
                functionContext.column
            );
        }
    }

    /**
     * Adds return 0 for main function.
     */
    private void addMainFunctionReturn(@Nonnull ResoType returnType) {
        IrValue zero = IrFactory.createConstantInt(returnType.getType(), 0, false);
        IrFactory.createReturn(context.getIrBuilder(), zero);
    }

    /**
     * Cleans up the function environment.
     */
    private void cleanupFunctionEnvironment(@Nonnull FunctionContext functionContext) {
        // Exit function scope
        context.getSymbolTable().exitFunctionScope(
            context.getErrorReporter(),
            functionContext.line,
            functionContext.column);

        // Restore previous function
        context.setCurrentFunction(functionContext.previousFunction);
    }

    /**
     * Context information for function generation.
     */
    private static class FunctionContext {
        final String functionName;
        final IrValue functionValue;
        final ResoType returnType;
        final int line;
        final int column;
        final boolean isMain;
        IrValue previousFunction;

        FunctionContext(String functionName, IrValue functionValue, ResoType returnType,
                        int line, int column, boolean isMain) {
            this.functionName = functionName;
            this.functionValue = functionValue;
            this.returnType = returnType;
            this.line = line;
            this.column = column;
            this.isMain = isMain;
        }
    }

    /**
     * Result of function body processing.
     */
    private record FunctionBodyResult(boolean endsWithReturningIf) {
    }
}