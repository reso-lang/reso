package com.reso.compiler.codegen.expressions;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrValue;
import com.reso.llvm.enums.IrIntPredicate;
import com.reso.llvm.enums.IrRealPredicate;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for comparison expressions.
 */
public class ComparisonExpressionGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new comparison expression generator.
     *
     * @param context The code generation context
     */
    public ComparisonExpressionGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a relational expression.
     *
     * @param expr The relational expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateRelationalExpr(@Nullable ResoParser.RelationalExprContext expr) {
        if (expr == null) {
            return null;
        }

        return generateBinaryComparisonExpr(
            expr.expression(0),
            expr.expression(1),
            expr.getChild(1).getText(),
            expr.getStart().getLine(),
            expr.getStart().getCharPositionInLine()
        );
    }

    /**
     * Generates code for an equality expression.
     *
     * @param expr The equality expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateEqualityExpr(@Nullable ResoParser.EqualityExprContext expr) {
        if (expr == null) {
            return null;
        }

        return generateBinaryComparisonExpr(
            expr.expression(0),
            expr.expression(1),
            expr.getChild(1).getText(),
            expr.getStart().getLine(),
            expr.getStart().getCharPositionInLine()
        );
    }

    /**
     * Common method to generate code for binary comparison expressions.
     * This method handles both relational and equality expressions.
     *
     * @param leftExpr  The left expression context
     * @param rightExpr The right expression context
     * @param operator  The comparison operator
     * @param line      The line number
     * @param column    The column number
     * @return The result of the expression
     */
    @Nullable
    private ResoValue generateBinaryComparisonExpr(
        @Nullable ResoParser.ExpressionContext leftExpr,
        @Nullable ResoParser.ExpressionContext rightExpr,
        @Nonnull String operator,
        int line,
        int column) {

        // Evaluate the left and right expressions
        ResoValue left = context.getExpressionGenerator().visit(leftExpr);
        ResoValue right = context.getExpressionGenerator().visit(rightExpr);

        if (left == null || right == null) {
            // Error already reported
            return null;
        }

        // Perform the comparison
        return generateComparisonExpr(left, right, operator, line, column);
    }

    /**
     * Generates code for a comparison expression.
     *
     * @param left     The left operand
     * @param right    The right operand
     * @param operator The operator
     * @param line     The line number
     * @param column   The column number
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateComparisonExpr(
        @Nonnull ResoValue left,
        @Nonnull ResoValue right,
        @Nonnull String operator,
        int line,
        int column) {

        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(operator, "Operator cannot be null");

        ResoType exprType = evaluateExprType(left, right);

        if (exprType == null) {
            context.getErrorReporter().error(
                "Cannot compare " + left.getTypeName() + " and " + right.getTypeName()
                    + ". Consider using explicit type conversions.",
                line, column
            );
            return null;
        }

        // Handle unit type comparisons
        if (exprType.isUnit()) {
            return generateUnitComparison(operator, line, column);
        }

        ConcreteResoValue concretizedLeft = left.concretize(exprType, context.getErrorReporter());
        if (concretizedLeft == null) {
            // Error already reported
            return null;
        }

        ConcreteResoValue concretizedRight = right.concretize(exprType, context.getErrorReporter());
        if (concretizedRight == null) {
            // Error already reported
            return null;
        }

        // Generate the actual comparison
        return generateComparison(concretizedLeft, concretizedRight, operator, line, column);
    }

    @Nullable
    private ResoType evaluateExprType(
        @Nonnull ResoValue left,
        @Nonnull ResoValue right) {
        ResoType exprType = null;
        if (left.canConcretizeTo(right.getType())) {
            exprType = right.getType();
        }
        if (right.canConcretizeTo(left.getType())) {
            exprType = left.getType();
        }
        if (exprType != null && exprType.isUntyped()) {
            exprType = left.getDefaultType();
        }
        return exprType;
    }

    /**
     * Generates comparison for unit type values.
     * Unit values are always equal to each other since there's only one unit value.
     */
    @Nullable
    private ResoValue generateUnitComparison(@Nonnull String operator, int line, int column) {
        ResoType boolType = context.getTypeSystem().getBoolType();

        // Unit values are always equal to each other
        boolean result = switch (operator) {
            case "==" -> true;  // () == () is always true
            case "!=" -> false; // () != () is always false
            default -> {
                context.getErrorReporter().error(
                    "Comparison operator '" + operator + "' not supported for unit type",
                    line, column);
                yield false; // Fallback, but error is reported
            }
        };

        if (!operator.equals("==") && !operator.equals("!=")) {
            return null; // Error already reported
        }

        IrValue constResult = IrFactory.createConstantBool(context.getIrContext(), result);
        return new ResoValue(boolType, constResult, line, column);
    }

    /**
     * Generates the actual typed comparison.
     */
    @Nullable
    private ResoValue generateComparison(
        @Nonnull ConcreteResoValue left,
        @Nonnull ConcreteResoValue right,
        @Nonnull String operator,
        int line,
        int column) {

        ResoType boolType = context.getTypeSystem().getBoolType();
        IrValue result;

        if (left.isInteger()) {
            result = generateIntegerComparison(left, right, operator, line, column);
        } else if (left.isFloatingPoint()) {
            result = generateFloatingPointComparison(left, right, operator, line, column);
        } else {
            result = generateNonNumericComparison(left, right, operator, line, column);
        }

        return result != null ? new ResoValue(boolType, result, line, column) : null;
    }

    /**
     * Generates integer comparison.
     */
    @Nullable
    private IrValue generateIntegerComparison(
        @Nonnull ConcreteResoValue left,
        @Nonnull ConcreteResoValue right,
        @Nonnull String operator,
        int line,
        int column) {

        boolean isUnsigned = left.isUnsignedInteger();
        IrIntPredicate predicate = getIntegerPredicate(operator, isUnsigned);

        if (predicate == null) {
            context.getErrorReporter().error(
                "Unsupported integer comparison: " + operator, line, column);
            return null;
        }

        return IrFactory.createICmp(context.getIrBuilder(), predicate,
            left.getValue(), right.getValue(), "icmp_tmp");
    }

    /**
     * Generates floating-point comparison.
     */
    @Nullable
    private IrValue generateFloatingPointComparison(
        @Nonnull ConcreteResoValue left,
        @Nonnull ConcreteResoValue right,
        @Nonnull String operator,
        int line,
        int column) {

        IrRealPredicate predicate = getFloatingPointPredicate(operator);

        if (predicate == null) {
            context.getErrorReporter().error(
                "Unsupported floating-point comparison: " + operator, line, column);
            return null;
        }

        return IrFactory.createFCmp(context.getIrBuilder(), predicate,
            left.getValue(), right.getValue(), "fcmp_tmp");
    }

    /**
     * Generates comparison for non-numeric types.
     */
    @Nullable
    private IrValue generateNonNumericComparison(
        @Nonnull ConcreteResoValue left,
        @Nonnull ConcreteResoValue right,
        @Nonnull String operator,
        int line,
        int column) {

        if (!isEqualityOperator(operator)) {
            context.getErrorReporter().error(
                "Comparison operator '" + operator + "' not supported for type "
                    + left.getTypeName(), line, column);
            return null;
        }

        IrIntPredicate predicate = operator.equals("==") ? IrIntPredicate.EQ : IrIntPredicate.NE;
        return IrFactory.createICmp(context.getIrBuilder(), predicate,
            left.getValue(), right.getValue(), "cmp_tmp");
    }

    // Helper methods

    private boolean isEqualityOperator(@Nonnull String operator) {
        return "==".equals(operator) || "!=".equals(operator);
    }

    /**
     * Gets the appropriate LLVM predicate for an integer comparison.
     *
     * @param operator   The comparison operator
     * @param isUnsigned Whether the integers are unsigned
     * @return The LLVM predicate, or null if unsupported
     */
    @Nullable
    private IrIntPredicate getIntegerPredicate(@Nonnull String operator, boolean isUnsigned) {
        return switch (operator) {
            case ">" -> isUnsigned ? IrIntPredicate.UGT : IrIntPredicate.SGT;
            case ">=" -> isUnsigned ? IrIntPredicate.UGE : IrIntPredicate.SGE;
            case "<" -> isUnsigned ? IrIntPredicate.ULT : IrIntPredicate.SLT;
            case "<=" -> isUnsigned ? IrIntPredicate.ULE : IrIntPredicate.SLE;
            case "==" -> IrIntPredicate.EQ;
            case "!=" -> IrIntPredicate.NE;
            default -> null;
        };
    }

    /**
     * Gets the appropriate LLVM predicate for a floating-point comparison.
     *
     * @param operator The comparison operator
     * @return The LLVM predicate, or null if unsupported
     */
    @Nullable
    private IrRealPredicate getFloatingPointPredicate(@Nonnull String operator) {
        return switch (operator) {
            case ">" -> IrRealPredicate.OGT;
            case "<" -> IrRealPredicate.OLT;
            case ">=" -> IrRealPredicate.OGE;
            case "<=" -> IrRealPredicate.OLE;
            case "==" -> IrRealPredicate.OEQ;
            case "!=" -> IrRealPredicate.ONE;
            default -> null;
        };
    }
}