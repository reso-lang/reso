package com.reso.compiler.codegen.statements;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for variable declarations.
 */
public class VariableGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new variable generator.
     *
     * @param context The code generation context
     */
    public VariableGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a variable declaration.
     *
     * @param varDecl The variable declaration context
     * @return The result of the variable declaration
     */
    @Nullable
    public ResoValue generateVariable(@Nullable ResoParser.VariableDeclarationContext varDecl) {
        if (varDecl == null) {
            return null;
        }

        String varName = varDecl.Identifier().getText();
        boolean hasInitializer = varDecl.expression() != null;
        int line = varDecl.getStart().getLine();
        int column = varDecl.getStart().getCharPositionInLine();
        ErrorReporter errorReporter = context.getErrorReporter();

        // Check scope permissions
        if (!context.getSymbolTable().isInFunctionScope()
            && !context.getSymbolTable().isInGlobalScope()) {
            errorReporter.error("Variable declaration not allowed in this scope", line, column);
            return null;
        }

        // Process initializer first to handle untyped literals
        ResoValue initValue;
        if (hasInitializer) {
            initValue = context.getExpressionGenerator().visit(varDecl.expression());
            if (initValue == null) {
                return null; // Error already reported
            }
        } else {
            // If no initializer, report error
            // Fow now we assume that all variables must be initialized
            errorReporter.error(varName + " is not initialized.", line, column);
            return null;
        }

        // Determine variable type
        ResoType varType = determineVariableType(varDecl, initValue, line, column);
        if (varType == null) {
            return null; // Error already reported
        }

        // Create allocation for the variable
        IrValue allocaInst = createAllocation(varType, varName);

        boolean isConstant = varDecl.CONST() != null;

        // Register in symbol table
        if (context.getSymbolTable().defineVariable(
            varName, allocaInst, varType, isConstant, hasInitializer,
            errorReporter, line, column) == null) {
            return null; // Error already reported
        }

        // Initialize the variable if it has an initializer
        if (!processInitializer(initValue, allocaInst, varType, line, column)) {
            return null; // Error already reported
        }

        return new ResoValue(varType, allocaInst, line, column);
    }

    /**
     * Determines the type of a variable.
     *
     * @param varDecl   The variable declaration context
     * @param initValue The initializer value
     * @param line      The line number
     * @param column    The column number
     * @return The variable type
     */
    @Nullable
    private ResoType determineVariableType(
        @Nonnull ResoParser.VariableDeclarationContext varDecl,
        @Nullable ResoValue initValue,
        int line,
        int column) {

        // Explicit type declaration takes precedence
        if (varDecl.type() != null) {
            return context.getTypeSystem().resolveType(varDecl.type(), context.getErrorReporter());
        }

        // Infer type from initializer
        if (initValue != null) {
            ResoType initType = initValue.getDefaultType();

            if (initType == null) {
                context.getErrorReporter().error(
                    "Cannot infer type for variable " + varDecl.Identifier().getText(),
                    line, column
                );
                return null;
            }

            return initType;
        }

        // Neither type nor initializer
        context.getErrorReporter().error(
            "Variable " + varDecl.Identifier().getText()
                + " must have either a type or an initializer",
            line, column
        );
        return null;
    }

    /**
     * Creates an allocation for a variable.
     *
     * @param type    The variable type
     * @param varName The variable name
     * @return The allocation instruction
     */
    @Nonnull
    private IrValue createAllocation(@Nonnull ResoType type, @Nonnull String varName) {
        IrValue currentFunction = context.getCurrentFunction();

        if (currentFunction == null) {
            // Not in a function, cannot allocate
            context.getErrorReporter().error(
                "Cannot allocate variable " + varName + " outside of a function",
                -1, -1
            );
        }

        // Create alloca at the start of the function for better optimization
        IrBasicBlock entryBlock = IrFactory.getEntryBasicBlock(currentFunction);

        try (IrBuilder tempBuilder = IrFactory.createBuilder(context.getIrContext())) {
            // Position at the start of the entry block for better optimization
            IrValue firstInst = entryBlock.getFirstInstruction();
            if (firstInst != null) {
                IrFactory.positionBefore(tempBuilder, firstInst);
            } else {
                IrFactory.positionAtEnd(tempBuilder, entryBlock);
            }

            return IrFactory.createAlloca(tempBuilder, type.getType(), varName);
        }
    }

    /**
     * Processes an initializer for a variable.
     *
     * @param initValue  The initializer value
     * @param allocaInst The allocation instruction
     * @param targetType The target type
     * @param line       The line number
     * @param column     The column number
     * @return true if initialization was successful
     */
    private boolean processInitializer(
        @Nonnull ResoValue initValue,
        @Nonnull IrValue allocaInst,
        @Nonnull ResoType targetType,
        int line,
        int column) {

        // Try to concretize the initializer value to the target type
        ConcreteResoValue convertedValue =
            initValue.concretize(targetType, context.getErrorReporter());

        if (convertedValue != null) {
            IrFactory.createStore(context.getIrBuilder(), convertedValue.getValue(), allocaInst);
            return true;
        }

        // Types do not match, report error
        context.getErrorReporter().error(
            "Cannot assign " + initValue.getType().getName() + " to " + targetType.getName(),
            line, column
        );
        return false;
    }
}