package com.reso.compiler.codegen.resources;

import static com.reso.compiler.codegen.resources.ResourceGenerator.parsePathSegments;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.codegen.common.FunctionGenerationUtils;
import com.reso.compiler.symbols.Parameter;
import com.reso.compiler.symbols.resources.MethodSymbol;
import com.reso.compiler.symbols.resources.PathSegment;
import com.reso.compiler.symbols.resources.ResourceSymbol;
import com.reso.compiler.types.ResoType;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import com.reso.llvm.api.IrValue;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for resource methods.
 * This class handles the generation of LLVM IR code for resource method
 * implementations, including parameter processing, body generation, and return
 * statement validation. Each resource method has an implicit 'this' parameter.
 */
public class ResourceMethodGenerator {
    private static final String ENTRY_BLOCK_NAME = "entry";
    private static final String THIS_PARAMETER_NAME = "this";

    private final CodeGenerationContext context;

    /**
     * Creates a new resource method generator.
     *
     * @param context The code generation context
     */
    public ResourceMethodGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a method within a resource path.
     *
     * @param methodCtx      The method context
     * @param pathCtx        The path context containing this method
     * @param resourceSymbol The resource symbol
     */
    public void generateMethod(
        @Nonnull ResoParser.ResourceMethodContext methodCtx,
        @Nonnull ResoParser.ResourcePathContext pathCtx,
        @Nonnull ResourceSymbol resourceSymbol) {

        String methodName = methodCtx.Identifier().getText();
        String pathString = buildPathString(pathCtx);

        if (pathString == null) {
            return;
        }

        // Find the method symbol in the specific path
        MethodSymbol methodSymbol = resourceSymbol.findMethod(pathString, methodName);
        if (methodSymbol == null) {
            context.getErrorReporter().error(
                "Method symbol not found in path '" + pathString + "': " + methodName,
                methodCtx.getStart().getLine(),
                methodCtx.getStart().getCharPositionInLine());
            return;
        }

        generateMethodImplementation(methodCtx, methodSymbol, resourceSymbol);
    }

    /**
     * Generates the actual method implementation.
     *
     * @param methodCtx      The method context
     * @param methodSymbol   The method symbol
     * @param resourceSymbol The resource symbol
     */
    private void generateMethodImplementation(
        @Nonnull ResoParser.ResourceMethodContext methodCtx,
        @Nonnull MethodSymbol methodSymbol,
        @Nonnull ResourceSymbol resourceSymbol) {

        MethodContext methodContext = createMethodContext(methodCtx, methodSymbol);

        try {
            // Setup method environment
            setupMethodEnvironment(methodContext);

            // Add 'this' parameter to the symbol table
            addThisParameter(methodContext, resourceSymbol);

            // Add regular parameters to the symbol table
            addMethodParameters(methodContext);

            // Generate method body
            MethodBodyResult bodyResult = generateMethodBody(methodCtx);

            // Handle return statement requirements
            handleMethodReturn(methodContext, bodyResult);
        } finally {
            // Cleanup method environment
            cleanupMethodEnvironment(methodContext);
        }
    }

    /**
     * Creates a method context from the method definition.
     */
    @Nonnull
    private MethodContext createMethodContext(
        @Nonnull ResoParser.ResourceMethodContext methodCtx,
        @Nonnull MethodSymbol methodSymbol) {

        String methodName = methodCtx.Identifier().getText();
        int line = methodCtx.getStart().getLine();
        int column = methodCtx.getStart().getCharPositionInLine();

        return new MethodContext(
            methodName,
            methodSymbol.getLlvmValue(),
            methodSymbol.getReturnType(),
            methodSymbol.getParameters(),
            line,
            column
        );
    }

    /**
     * Sets up the method environment for code generation.
     */
    private void setupMethodEnvironment(
        @Nonnull MethodContext methodContext) {

        // Save current function
        methodContext.previousFunction = context.getCurrentFunction();
        context.setCurrentFunction(methodContext.methodValue);

        // Create entry block
        IrBasicBlock entryBlock = IrFactory.createBasicBlock(
            methodContext.methodValue, ENTRY_BLOCK_NAME);

        // Enter method scope (similar to function scope)
        context.getSymbolTable().enterFunctionScope(methodContext.returnType);

        // Position builder at entry block
        IrFactory.positionAtEnd(context.getIrBuilder(), entryBlock);
    }

    /**
     * Adds the 'this' parameter to the symbol table.
     */
    private void addThisParameter(
        @Nonnull MethodContext methodContext,
        @Nonnull ResourceSymbol resourceSymbol) {

        // Create alloca and store for 'this' parameter
        IrValue thisParamValue = methodContext.methodValue.getParam(0); // First parameter is 'this'
        IrValue thisAlloca = FunctionGenerationUtils.createParameterAlloca(
            context, THIS_PARAMETER_NAME, resourceSymbol.getType(), thisParamValue);

        context.getSymbolTable().defineVariable(
            THIS_PARAMETER_NAME,
            thisAlloca,
            resourceSymbol.getType(),
            true, // const
            true, // initialized
            context.getErrorReporter(),
            methodContext.line,
            methodContext.column
        );
    }

    /**
     * Adds method parameters to the symbol table.
     */
    private void addMethodParameters(@Nonnull MethodContext methodContext) {
        for (int i = 0; i < methodContext.parameters.size(); i++) {
            Parameter param = methodContext.parameters.get(i);
            ResoType paramType = param.type();
            IrValue paramValue = methodContext.methodValue.getParam(i + 1);
            String paramName = param.name();

            // Create alloca and store for parameter
            IrValue paramAlloca = FunctionGenerationUtils.createParameterAlloca(
                context, paramName, paramType, paramValue);

            // Add to symbol table
            context.getSymbolTable().defineVariable(
                paramName,
                paramAlloca,
                paramType,
                false, // parameters are not const by default
                true,  // initialized
                context.getErrorReporter(),
                methodContext.line,
                methodContext.column
            );
        }
    }

    /**
     * Generates the method body and returns information about the body structure.
     */
    @Nonnull
    private MethodBodyResult generateMethodBody(
        @Nonnull ResoParser.ResourceMethodContext methodCtx) {
        boolean endsWithReturningIf = false;

        if (methodCtx.block() != null) {
            endsWithReturningIf = FunctionGenerationUtils.processBody(
                context, methodCtx.block());
        }

        return new MethodBodyResult(endsWithReturningIf);
    }

    /**
     * Handles return statement requirements for the method.
     */
    private void handleMethodReturn(
        @Nonnull MethodContext methodContext,
        @Nonnull MethodBodyResult bodyResult) {

        FunctionGenerationUtils.handleReturn(
            context,
            methodContext.methodName,
            methodContext.returnType,
            bodyResult.endsWithReturningIf,
            methodContext.line,
            methodContext.column
        );
    }

    /**
     * Cleans up the method environment.
     */
    private void cleanupMethodEnvironment(@Nonnull MethodContext methodContext) {
        // Exit method scope
        context.getSymbolTable().exitFunctionScope(
            context.getErrorReporter(),
            methodContext.line,
            methodContext.column
        );

        // Restore previous function
        context.setCurrentFunction(methodContext.previousFunction);
    }

    /**
     * Builds a path string from a resource path context.
     */
    @Nullable
    private String buildPathString(@Nonnull ResoParser.ResourcePathContext pathCtx) {
        List<PathSegment> segments = parsePathSegments(context, pathCtx);
        if (segments == null) {
            context.getErrorReporter().error(
                "Invalid path segments in resource definition",
                pathCtx.getStart().getLine(),
                pathCtx.getStart().getCharPositionInLine());
            return null;
        }

        return segments.stream()
            .map(PathSegment::getDisplayName)
            .collect(Collectors.joining("."));
    }

    /**
     * Context for method generation.
     */
    private static class MethodContext {
        final String methodName;
        final IrValue methodValue;
        final ResoType returnType;
        final List<Parameter> parameters;
        final int line;
        final int column;
        IrValue previousFunction;

        MethodContext(
            String methodName,
            IrValue methodValue,
            ResoType returnType,
            List<Parameter> parameters,
            int line,
            int column) {
            this.methodName = methodName;
            this.methodValue = methodValue;
            this.returnType = returnType;
            this.parameters = parameters;
            this.line = line;
            this.column = column;
        }
    }

    /**
     * Result of method body generation.
     */
    private record MethodBodyResult(boolean endsWithReturningIf) {
    }
}