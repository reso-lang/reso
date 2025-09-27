package com.reso.compiler.codegen.expressions;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.values.ResoValue;
import com.reso.compiler.values.expressions.UnaryExpressionValue;
import com.reso.grammar.ResoParser;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for unary expressions.
 * Supports the following unary operators:
 * - Unary plus (+): Identity operation for numeric types
 * - Unary minus (-): Negation for numeric types
 * - Logical NOT (not): boolean negation
 * - Bitwise NOT (~): Bitwise complement for integer types
 */
public class UnaryExpressionGenerator {

    // Operator constants
    private static final String OP_PLUS = "+";
    private static final String OP_MINUS = "-";
    private static final String OP_LOGICAL_NOT = "not";
    private static final String OP_BITWISE_NOT = "~";

    private final CodeGenerationContext context;

    /**
     * Creates a new unary expression generator.
     *
     * @param context The code generation context
     * @throws NullPointerException if context is null
     */
    public UnaryExpressionGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a unary expression.
     *
     * @param expr The unary expression context
     * @return The result value, or null if an error occurred
     */
    @Nullable
    public ResoValue generateUnaryExpr(@Nullable ResoParser.UnaryExprContext expr) {
        if (expr == null) {
            return null;
        }

        // Get position info for error reporting
        int line = expr.getStart().getLine();
        int column = expr.getStart().getCharPositionInLine();

        // Evaluate the operand first
        ResoValue operand = evaluateOperand(expr);
        if (operand == null) {
            return null; // Error already reported
        }

        // Determine the operator
        String operator = determineOperator(expr, line, column);
        if (operator == null) {
            return null; // Error already reported
        }

        // Validate the operation
        if (!validateOperation(operator, operand, line, column)) {
            return null; // Error already reported
        }

        return new UnaryExpressionValue(
            operand,
            operator,
            context.getIrBuilder(),
            line,
            column
        );
    }

    /**
     * Evaluates the operand expression.
     */
    @Nullable
    private ResoValue evaluateOperand(@Nonnull ResoParser.UnaryExprContext expr) {
        return context.getExpressionGenerator().visit(expr.expression());
    }

    /**
     * Determines which unary operator is being used.
     */
    @Nullable
    private String determineOperator(@Nonnull ResoParser.UnaryExprContext expr, int line,
                                     int column) {
        if (expr.PLUS() != null) {
            return OP_PLUS;
        } else if (expr.MINUS() != null) {
            return OP_MINUS;
        } else if (expr.NOT() != null) {
            return OP_LOGICAL_NOT;
        } else if (expr.NOT_OP() != null) {
            return OP_BITWISE_NOT;
        } else {
            context.getErrorReporter().error("Unknown unary operator", line, column);
            return null;
        }
    }

    /**
     * Validates that the operator can be applied to the given operand type.
     */
    private boolean validateOperation(@Nonnull String operator, @Nonnull ResoValue operand,
                                      int line, int column) {
        ErrorReporter errorReporter = context.getErrorReporter();

        return switch (operator) {
            case OP_LOGICAL_NOT -> validateLogicalNot(operand, errorReporter, line, column);
            case OP_BITWISE_NOT -> validateBitwiseNot(operand, errorReporter, line, column);
            case OP_PLUS, OP_MINUS ->
                validateArithmetic(operator, operand, errorReporter, line, column);
            default -> {
                errorReporter.error("Unsupported unary operator: " + operator, line, column);
                yield false;
            }
        };
    }

    /**
     * Validates logical NOT operation.
     */
    private boolean validateLogicalNot(@Nonnull ResoValue operand,
                                       @Nonnull ErrorReporter errorReporter,
                                       int line, int column) {
        if (!operand.isBool()) {
            errorReporter.error(
                "Logical NOT (not) requires a boolean operand, got " + operand.getTypeName(),
                line, column);
            return false;
        }
        return true;
    }

    /**
     * Validates bitwise NOT operation.
     */
    private boolean validateBitwiseNot(@Nonnull ResoValue operand,
                                       @Nonnull ErrorReporter errorReporter,
                                       int line, int column) {
        if (!operand.isInteger()) {
            errorReporter.error(
                "Bitwise NOT (~) requires an integer operand, got " + operand.getTypeName(),
                line, column);
            return false;
        }
        return true;
    }

    /**
     * Validates arithmetic unary operations (+ and -).
     */
    private boolean validateArithmetic(@Nonnull String operator, @Nonnull ResoValue operand,
                                       @Nonnull ErrorReporter errorReporter, int line, int column) {
        if (!operand.isNumeric()) {
            errorReporter.error(
                "Unary " + operator + " requires a numeric operand, got " + operand.getTypeName(),
                line, column);
            return false;
        }
        return true;
    }
}