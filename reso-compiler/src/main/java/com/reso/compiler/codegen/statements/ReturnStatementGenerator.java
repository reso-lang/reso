package com.reso.compiler.codegen.statements;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrValue;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for return statements.
 * Handles the generation of LLVM IR code for return statements,
 * including type checking and value conversion. Supports both
 * unit-returning functions (no explicit value) and functions
 * that return specific types.
 */
public class ReturnStatementGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new return statement generator.
     *
     * @param context The code generation context
     */
    public ReturnStatementGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a return statement.
     *
     * @param returnStmt The return statement context
     * @return Always null (return statements don't return a value)
     */
    @Nullable
    public ResoValue generateReturn(@Nullable ResoParser.ReturnStatementContext returnStmt) {
        if (returnStmt == null) {
            return null;
        }

        int line = returnStmt.getStart().getLine();
        int column = returnStmt.getStart().getCharPositionInLine();
        ErrorReporter errorReporter = context.getErrorReporter();

        // Check if we're in a function
        ResoType returnType = context.getSymbolTable().getCurrentFunctionReturnType();
        if (returnType == null) {
            errorReporter.error("Return statement outside of function", line, column);
            return null;
        }

        boolean hasExpression = returnStmt.expression() != null;

        // Check return type compatibility
        if (returnType.isUnit() && !hasExpression) {
            // Generate return with unit value - create constant empty struct
            IrValue unitValue =
                IrFactory.createConstantNamedStruct(returnType.getType(), new IrValue[0]);
            IrFactory.createReturn(context.getIrBuilder(), unitValue);
            return null;
        }

        // Non-unit function must return a value of the correct type
        if (!returnType.isUnit() && !hasExpression) {
            errorReporter.error("Function with return type " + returnType.getName()
                + " must return a value", line, column);
            return null;
        }

        // Evaluate the return expression
        ResoValue returnValue = context.getExpressionGenerator().visit(returnStmt.expression());
        if (returnValue == null) {
            return null; // Error already reported
        }

        // Try to concretize the return value to the expected return type
        ConcreteResoValue convertedValue = returnValue.concretize(returnType, errorReporter);

        if (convertedValue != null) {
            // Generate return instruction with concretized value
            IrFactory.createReturn(context.getIrBuilder(), convertedValue.getValue());
            return null;
        }

        errorReporter.error(
            "Function return type mismatch: expected " + returnType.getName()
                + ", but got " + returnValue.getType().getName(), line, column);
        return null;
    }
}