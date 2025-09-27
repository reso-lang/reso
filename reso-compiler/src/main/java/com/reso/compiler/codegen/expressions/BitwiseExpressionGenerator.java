package com.reso.compiler.codegen.expressions;

import static java.util.Objects.requireNonNull;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ResoValue;
import com.reso.compiler.values.expressions.BitwiseExpressionValue;
import com.reso.grammar.ResoParser;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for bitwise expressions with support for signed and unsigned integers.
 * This class handles bitwise AND, OR, XOR, and shift operations (left shift and right shift)
 * for integer types. Shift operations use logical right shift for unsigned integers
 * and arithmetic right shift for signed integers.
 */
public class BitwiseExpressionGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new bitwise expression generator.
     *
     * @param context The code generation context
     */
    public BitwiseExpressionGenerator(@Nonnull CodeGenerationContext context) {
        this.context = requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a bitwise AND expression.
     *
     * @param expr The bitwise AND expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateBitwiseAndExpr(@Nullable ResoParser.BitwiseAndExprContext expr) {
        if (expr == null) {
            return null;
        }
        return generateBinaryBitwiseExpr(
            context.getExpressionGenerator().visit(expr.expression(0)),
            context.getExpressionGenerator().visit(expr.expression(1)),
            "&",
            expr.getStart().getLine(),
            expr.getStart().getCharPositionInLine()
        );
    }

    /**
     * Generates code for a bitwise XOR expression.
     *
     * @param expr The bitwise XOR expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateBitwiseXorExpr(@Nullable ResoParser.BitwiseXorExprContext expr) {
        if (expr == null) {
            return null;
        }
        return generateBinaryBitwiseExpr(
            context.getExpressionGenerator().visit(expr.expression(0)),
            context.getExpressionGenerator().visit(expr.expression(1)),
            "^",
            expr.getStart().getLine(),
            expr.getStart().getCharPositionInLine()
        );
    }

    /**
     * Generates code for a bitwise OR expression.
     *
     * @param expr The bitwise OR expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateBitwiseOrExpr(@Nullable ResoParser.BitwiseOrExprContext expr) {
        if (expr == null) {
            return null;
        }
        return generateBinaryBitwiseExpr(
            context.getExpressionGenerator().visit(expr.expression(0)),
            context.getExpressionGenerator().visit(expr.expression(1)),
            "|",
            expr.getStart().getLine(),
            expr.getStart().getCharPositionInLine()
        );
    }

    /**
     * Generates code for a shift expression.
     *
     * @param expr The shift expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateShiftExpr(@Nullable ResoParser.ShiftExprContext expr) {
        if (expr == null) {
            return null;
        }

        int line = expr.getStart().getLine();
        int column = expr.getStart().getCharPositionInLine();

        String operatorSymbol = expr.getChild(1).getText();

        return generateBinaryBitwiseExpr(
            context.getExpressionGenerator().visit(expr.expression(0)),
            context.getExpressionGenerator().visit(expr.expression(1)),
            operatorSymbol,
            line,
            column
        );
    }

    /**
     * Common method for generating binary bitwise expressions.
     *
     * @param line     The line number
     * @param column   The column number
     * @param left     The left operand
     * @param right    The right operand
     * @param operator The operator symbol
     * @return The result of the expression
     */
    @Nullable
    private ResoValue generateBinaryBitwiseExpr(
        @Nullable ResoValue left,
        @Nullable ResoValue right,
        @Nonnull String operator,
        int line,
        int column) {

        if (left == null || right == null) {
            // Error already reported by expression generator
            return null;
        }

        return generateBitwiseExpr(left, right, operator, line, column);
    }

    /**
     * Generates code for a bitwise expression.
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
    public ResoValue generateBitwiseExpr(
        @Nonnull ResoValue left,
        @Nonnull ResoValue right,
        @Nonnull String operator,
        int line,
        int column) {
        validateInputs(left, right, operator);

        // Validate integer types
        if (!areOperandsInteger(left, right)) {
            reportNonIntegerError(left, right, operator, line, column);
            return null;
        }

        ResoType resultType = determineResultType(operator, left, right);

        if (resultType == null) {
            reportTypeCompatibilityError(left, right, operator, line, column);
            return null;
        }

        ResoType defaultType = determineDefaultType(left, right);

        return new BitwiseExpressionValue(
            resultType,
            defaultType,
            left,
            right,
            operator,
            context.getIrBuilder(),
            context.getTypeSystem(),
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
     * Checks if both operands are integer types.
     */
    private boolean areOperandsInteger(@Nonnull ResoValue left, @Nonnull ResoValue right) {
        return left.isInteger() && right.isInteger();
    }

    private boolean isShiftOperation(@Nonnull String operator) {
        return "<<".equals(operator) || ">>".equals(operator);
    }

    @Nullable
    private ResoType determineResultType(String operator, @Nonnull ResoValue left,
                                         @Nonnull ResoValue right) {
        if (isShiftOperation(operator)) {
            return left.getType();
        }
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

    // Error reporting methods

    private void reportError(@Nonnull String message, int line, int column) {
        context.getErrorReporter().error(message, line, column);
    }

    private void reportTypeCompatibilityError(@Nonnull ResoValue left, @Nonnull ResoValue right,
                                              @Nonnull String operator, int line, int column) {
        reportError("Cannot perform bitwise operation '" + operator + "' on different types: "
            + left.getTypeName() + " and " + right.getTypeName()
            + ". Consider using explicit type conversions.", line, column);
    }

    private void reportNonIntegerError(@Nonnull ResoValue left, @Nonnull ResoValue right,
                                       @Nonnull String operator, int line, int column) {
        String leftTypeName = left.getTypeName();
        String rightTypeName = right.getTypeName();

        if (!left.isInteger() && !right.isInteger()) {
            reportError(
                "Cannot perform bitwise operation '" + operator + "' on non-integer types: "
                    + leftTypeName + " and " + rightTypeName, line, column);
        } else if (!left.isInteger()) {
            reportError("Cannot perform bitwise operation '" + operator
                + "' with non-integer left operand: "
                + leftTypeName, line, column);
        } else {
            reportError("Cannot perform bitwise operation '" + operator
                + "' with non-integer right operand: "
                + rightTypeName, line, column);
        }
    }
}