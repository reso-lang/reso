package com.reso.compiler.codegen.expressions;

import static java.util.Objects.requireNonNull;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ResoValue;
import com.reso.compiler.values.expressions.ArithmeticExpressionValue;
import com.reso.grammar.ResoParser;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for arithmetic expressions with support for signed and unsigned integers.
 * This class handles addition, subtraction, multiplication, division, and modulo operations
 * for both integer and floating-point types.
 */
public class ArithmeticExpressionGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new arithmetic expression generator.
     *
     * @param context The code generation context
     */
    public ArithmeticExpressionGenerator(@Nonnull CodeGenerationContext context) {
        this.context = requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for an additive expression.
     *
     * @param expr The additive expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateAdditiveExpr(@Nullable ResoParser.AdditiveExprContext expr) {
        return generateBinaryArithmeticExpr(expr, this::extractOperandsFromAdditive);
    }

    /**
     * Generates code for a multiplicative expression.
     *
     * @param expr The multiplicative expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateMultiplicativeExpr(
        @Nullable ResoParser.MultiplicativeExprContext expr) {
        return generateBinaryArithmeticExpr(expr, this::extractOperandsFromMultiplicative);
    }

    /**
     * Generic method for generating binary arithmetic expressions.
     *
     * @param expr             The expression context
     * @param operandExtractor Function to extract operands and operator from the expression
     * @param <T>              The type of the expression context
     * @return The result of the expression
     */
    @Nullable
    private <T extends ResoParser.ExpressionContext> ResoValue generateBinaryArithmeticExpr(
        @Nullable T expr,
        @Nonnull OperandExtractor<T> operandExtractor) {

        if (expr == null) {
            return null;
        }

        BinaryOperands operands = operandExtractor.extract(expr);
        if (operands == null) {
            return null;
        }

        return generateArithmeticExpr(
            operands.left,
            operands.right,
            operands.operator,
            operands.line,
            operands.column
        );
    }

    /**
     * Generates code for an arithmetic expression.
     * Supports both signed and unsigned integer operations, with proper operation selection.
     *
     * @param left     The left operand
     * @param right    The right operand
     * @param operator The operator
     * @param line     The line number
     * @param column   The column number
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateArithmeticExpr(
        @Nonnull ResoValue left,
        @Nonnull ResoValue right,
        @Nonnull String operator,
        int line,
        int column) {
        validateInputs(left, right, operator);

        // Validate numeric types
        if (!areOperandsNumeric(left, right)) {
            reportNonNumericError(left, right, operator, line, column);
            return null;
        }

        ResoType resultType = determineResultType(left, right);

        if (resultType == null) {
            reportError(
                "Cannot determine result type for operands: "
                    + left.getTypeName()
                    + " and "
                    + right.getTypeName(), line, column);
            return null;
        }

        ResoType defaultType = determineDefaultType(left, right);

        return new ArithmeticExpressionValue(
            resultType,
            defaultType,
            left,
            right,
            operator,
            context.getIrBuilder(),
            line,
            column
        );
    }

    /**
     * Validates that all inputs are non-null.
     */
    private void validateInputs(@Nullable ResoValue left, @Nullable ResoValue right,
                                @Nullable String operator) {
        requireNonNull(left, "Left operand cannot be null");
        requireNonNull(right, "Right operand cannot be null");
        requireNonNull(operator, "Operator cannot be null");
    }

    /**
     * Checks if both operands are numeric types.
     */
    private boolean areOperandsNumeric(@Nonnull ResoValue left, @Nonnull ResoValue right) {
        return left.isNumeric() && right.isNumeric();
    }

    @Nullable
    private ResoType determineResultType(@Nonnull ResoValue left, @Nonnull ResoValue right) {
        if (left.canConcretizeTo(right.getType())) {
            return right.getType();
        } else if (right.canConcretizeTo(left.getType())) {
            return left.getType();
        }
        return null;
    }

    @Nullable
    private ResoType determineDefaultType(@Nonnull ResoValue left, @Nonnull ResoValue right) {
        if (left.canConcretizeTo(right.getType())) {
            return right.getDefaultType();
        } else if (right.canConcretizeTo(left.getType())) {
            return left.getDefaultType();
        }
        return null;
    }


    // Helper methods for extracting operands from specific expression types

    @Nullable
    private BinaryOperands extractOperandsFromAdditive(
        @Nonnull ResoParser.AdditiveExprContext expr) {
        return extractBinaryOperands(expr,
            expr.expression(0), expr.expression(1), expr.getChild(1).getText());
    }

    @Nullable
    private BinaryOperands extractOperandsFromMultiplicative(
        @Nonnull ResoParser.MultiplicativeExprContext expr) {
        return extractBinaryOperands(expr,
            expr.expression(0), expr.expression(1), expr.getChild(1).getText());
    }

    @Nullable
    private BinaryOperands extractBinaryOperands(@Nonnull ResoParser.ExpressionContext expr,
                                                 @Nonnull ResoParser.ExpressionContext leftExpr,
                                                 @Nonnull ResoParser.ExpressionContext rightExpr,
                                                 @Nonnull String operator) {

        int line = expr.getStart().getLine();
        int column = expr.getStart().getCharPositionInLine();

        ResoValue left = context.getExpressionGenerator().visit(leftExpr);
        ResoValue right = context.getExpressionGenerator().visit(rightExpr);

        if (left == null || right == null) {
            return null; // Error already reported
        }

        return new BinaryOperands(left, right, operator, line, column);
    }

    // Error reporting methods

    private void reportError(@Nonnull String message, int line, int column) {
        context.getErrorReporter().error(message, line, column);
    }

    private void reportNonNumericError(@Nonnull ResoValue left, @Nonnull ResoValue right,
                                       @Nonnull String operator, int line, int column) {
        reportError(
            "Cannot perform arithmetic operation '"
                + operator
                + "' on non-numeric types: "
                + left.getTypeName()
                + " and "
                + right.getTypeName(), line, column);
    }

    // Helper classes and interfaces

    @FunctionalInterface
    private interface OperandExtractor<T> {
        @Nullable
        BinaryOperands extract(@Nonnull T expr);
    }

    private record BinaryOperands(@Nonnull ResoValue left, @Nonnull ResoValue right,
                                  @Nonnull String operator, int line, int column) {
        public BinaryOperands {
            requireNonNull(left, "Left operand cannot be null");
            requireNonNull(right, "Right operand cannot be null");
            requireNonNull(operator, "Operator cannot be null");
        }
    }
}