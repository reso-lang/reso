package com.reso.compiler.codegen.expressions;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ResoValue;
import com.reso.compiler.values.expressions.TernaryExpressionValue;
import com.reso.grammar.ResoParser;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generates untyped ternary expressions (conditional expressions).
 * Creates TernaryExpressionValue instances that can be concretized later.
 */
public class TernaryExpressionGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new ternary expression generator.
     *
     * @param context The code generation context
     */
    public TernaryExpressionGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a ternary expression.
     *
     * @param expr The ternary expression context
     * @return The untyped ternary expression, or null if an error occurred
     */
    @Nullable
    public ResoValue generateTernaryExpr(@Nullable ResoParser.TernaryExprContext expr) {
        if (expr == null) {
            return null;
        }

        int line = expr.getStart().getLine();
        int column = expr.getStart().getCharPositionInLine();

        // Extract the three expressions: trueExpr if condition else falseExpr
        // Grammar: expression IF expression ELSE expression
        ResoParser.ExpressionContext trueExpr = expr.expression(0);   // value_if_true
        ResoParser.ExpressionContext conditionExpr = expr.expression(1); // condition
        ResoParser.ExpressionContext falseExpr = expr.expression(2);  // value_if_false

        // Evaluate all three expressions (may be untyped)
        ResoValue condition = context.getExpressionGenerator().visit(conditionExpr);
        if (condition == null) {
            return null; // Error already reported
        }

        if (!condition.canConcretizeTo(context.getTypeSystem().getBoolType())) {
            context.getErrorReporter().error(
                "Condition of ternary expression must be a boolean, found: "
                    + condition.getTypeName(),
                line, column
            );
            return null;
        }

        ResoValue trueValue = context.getExpressionGenerator().visit(trueExpr);
        if (trueValue == null) {
            return null; // Error already reported
        }

        ResoValue falseValue = context.getExpressionGenerator().visit(falseExpr);
        if (falseValue == null) {
            return null; // Error already reported
        }

        // Determine the result type for the ternary expression
        ResoType resultType = determineResultType(trueValue, falseValue);

        if (resultType == null) {
            context.getErrorReporter().error(
                "Cannot determine result type for ternary expression with operands: "
                    + trueValue.getTypeName() + " and " + falseValue.getTypeName(),
                line, column
            );
            return null;
        }

        ResoType defaultType = determineDefaultType(trueValue, falseValue);

        // Create untyped ternary expression
        return new TernaryExpressionValue(
            resultType,
            defaultType,
            condition,
            trueValue,
            falseValue,
            context.getIrBuilder(),
            context.getTypeSystem(),
            line,
            column
        );
    }

    /**
     * Determines the result type of a ternary expression based on the operand types.
     * Ensures type compatibility when untyped values are involved.
     */
    @Nullable
    private ResoType determineResultType(@Nonnull ResoValue trueValue,
                                         @Nonnull ResoValue falseValue) {

        if (trueValue.canConcretizeTo(falseValue.getType())) {
            return falseValue.getType();
        }

        if (falseValue.canConcretizeTo(trueValue.getType())) {
            return trueValue.getType();
        }

        return null;
    }

    @Nullable
    private ResoType determineDefaultType(@Nonnull ResoValue trueValue,
                                          @Nonnull ResoValue falseValue) {
        if (trueValue.canConcretizeTo(falseValue.getType())) {
            return falseValue.getDefaultType();
        }

        if (falseValue.canConcretizeTo(trueValue.getType())) {
            return trueValue.getDefaultType();
        }

        return null;
    }
}