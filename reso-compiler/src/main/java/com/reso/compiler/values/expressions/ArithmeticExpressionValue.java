package com.reso.compiler.values.expressions;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import com.reso.llvm.enums.IrIntPredicate;
import com.reso.llvm.enums.IrRealPredicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Untyped arithmetic expression (e.g., a + b, x * y).
 */
public final class ArithmeticExpressionValue extends BinaryExpressionValue {
    private final IrBuilder irBuilder;

    public ArithmeticExpressionValue(@Nonnull ResoType type,
                                     @Nullable ResoType defaultType,
                                     @Nonnull ResoValue left,
                                     @Nonnull ResoValue right,
                                     @Nonnull String operator,
                                     @Nonnull IrBuilder irBuilder,
                                     int line,
                                     int column) {
        super(type, defaultType, left, right, operator, line, column);
        this.irBuilder = irBuilder;
    }

    @Override
    @Nullable
    public ConcreteResoValue concretize(@Nonnull ResoType targetType,
                                        @Nonnull ErrorReporter errorReporter) {
        if (!canConcretizeTo(targetType)) {
            errorReporter.error(
                "Cannot convert arithmetic expression to type " + targetType.getName(),
                line, column
            );
            return null;
        }

        // Concretize operands
        ConcreteResoValue concretLeft = left.concretize(targetType, errorReporter);
        ConcreteResoValue concretRight = right.concretize(targetType, errorReporter);

        if (concretLeft == null || concretRight == null) {
            return null;
        }

        // Perform the arithmetic operation
        IrValue result = performArithmeticOperation(
            concretLeft, concretRight, operator, irBuilder, errorReporter
        );

        if (result == null) {
            return null;
        }

        return new ConcreteResoValue(targetType, result, line, column);
    }

    @Override
    public boolean canConcretizeTo(@Nonnull ResoType targetType) {
        if (!targetType.isNumeric()) {
            return false;
        }

        return left.canConcretizeTo(targetType) && right.canConcretizeTo(targetType);
    }

    @Nullable
    private IrValue performArithmeticOperation(@Nonnull ConcreteResoValue left,
                                               @Nonnull ConcreteResoValue right,
                                               @Nonnull String operator,
                                               @Nonnull IrBuilder builder,
                                               @Nonnull ErrorReporter errorReporter) {

        IrValue leftValue = left.getValue();
        IrValue rightValue = right.getValue();

        return switch (operator) {
            case "+" -> left.isFloatingPoint()
                ? IrFactory.createFAdd(builder, leftValue, rightValue, "fadd_tmp") :
                IrFactory.createAdd(builder, leftValue, rightValue, "add_tmp");

            case "-" -> left.isFloatingPoint()
                ? IrFactory.createFSub(builder, leftValue, rightValue, "fsub_tmp") :
                IrFactory.createSub(builder, leftValue, rightValue, "sub_tmp");

            case "*" -> left.isFloatingPoint()
                ? IrFactory.createFMul(builder, leftValue, rightValue, "fmul_tmp") :
                IrFactory.createMul(builder, leftValue, rightValue, "mul_tmp");

            case "div" -> left.isFloatingPoint()
                ? IrFactory.createFDiv(builder, leftValue, rightValue, "fdiv_tmp") :
                left.isUnsignedInteger()
                    ? IrFactory.createUDiv(builder, leftValue, rightValue, "udiv_tmp") :
                    IrFactory.createSDiv(builder, leftValue, rightValue, "sdiv_tmp");

            case "rem" -> left.isFloatingPoint()
                ? IrFactory.createFRem(builder, leftValue, rightValue, "frem_tmp") :
                left.isUnsignedInteger()
                    ? IrFactory.createURem(builder, leftValue, rightValue, "urem_tmp") :
                    IrFactory.createSRem(builder, leftValue, rightValue, "srem_tmp");

            case "mod" -> left.isFloatingPoint()
                ? generateFloatingPointMod(left, right, builder) :
                left.isUnsignedInteger()
                    ? generateUnsignedMod(left, right, builder) :
                    generateSignedMod(left, right, builder);

            default -> {
                errorReporter.error(
                    "Unsupported arithmetic operator: " + operator,
                    line, column
                );
                yield null;
            }
        };
    }

    @Nonnull
    private IrValue generateSignedMod(@Nonnull ConcreteResoValue left,
                                      @Nonnull ConcreteResoValue right,
                                      @Nonnull IrBuilder builder) {
        IrValue leftValue = left.getValue();
        IrValue rightValue = right.getValue();

        // Compute remainder first: a rem b
        IrValue remainder = IrFactory.createSRem(builder, leftValue, rightValue, "srem_tmp");

        // For modulo, we need to adjust when signs differ and remainder is not zero
        // Algorithm: if (remainder != 0 && sign(a) != sign(b)) result = remainder + b

        // Create zero constant for comparison
        IrValue zero = IrFactory.createConstantInt(left.getType().getType(), 0, false);

        // Check if remainder is not zero
        IrValue remainderNotZero = IrFactory.createICmp(
            builder, IrIntPredicate.NE, remainder, zero, "rem_not_zero"
        );

        // Check if left operand is negative (< 0)
        IrValue leftNegative = IrFactory.createICmp(
            builder, IrIntPredicate.SLT, leftValue, zero, "left_negative"
        );

        // Check if right operand is negative (< 0)
        IrValue rightNegative = IrFactory.createICmp(
            builder, IrIntPredicate.SLT, rightValue, zero, "right_negative"
        );

        // Check if signs are different (left_negative XOR right_negative)
        IrValue signsDiffer =
            IrFactory.createXor(builder, leftNegative, rightNegative, "signs_differ");

        // Combine conditions: remainder != 0 AND signs differ
        IrValue needsAdjustment =
            IrFactory.createAnd(builder, remainderNotZero, signsDiffer, "needs_adjustment");

        // Calculate adjusted result: remainder + right
        IrValue adjustedResult =
            IrFactory.createAdd(builder, remainder, rightValue, "adjusted_result");

        // Select between remainder and adjusted result based on condition
        return IrFactory.createSelect(builder, needsAdjustment, adjustedResult, remainder,
            "mod_result");
    }

    @Nonnull
    private IrValue generateUnsignedMod(@Nonnull ConcreteResoValue left,
                                        @Nonnull ConcreteResoValue right,
                                        @Nonnull IrBuilder builder) {
        IrValue leftValue = left.getValue();
        IrValue rightValue = right.getValue();

        return IrFactory.createURem(builder, leftValue, rightValue, "umod_tmp");
    }

    @Nonnull
    private IrValue generateFloatingPointMod(@Nonnull ConcreteResoValue left,
                                             @Nonnull ConcreteResoValue right,
                                             @Nonnull IrBuilder builder) {
        IrValue leftValue = left.getValue();
        IrValue rightValue = right.getValue();

        // Compute floating-point remainder first: a rem b
        IrValue remainder = IrFactory.createFRem(builder, leftValue, rightValue, "frem_tmp");

        // For floating-point modulo, we need to adjust when signs differ and remainder is not zero
        // Algorithm: if (remainder != 0.0 && sign(a) != sign(b)) result = remainder + b

        // Create zero constant for comparison
        IrValue zero = IrFactory.createConstantFloat(left.getType().getType(), 0.0);

        // Check if remainder is not zero (using unordered not equal to handle NaN properly)
        IrValue remainderNotZero = IrFactory.createFCmp(
            builder, IrRealPredicate.UNE, remainder, zero, "frem_not_zero"
        );

        // Check if left operand is negative (< 0.0)
        IrValue leftNegative = IrFactory.createFCmp(
            builder, IrRealPredicate.OLT, leftValue, zero, "left_negative"
        );

        // Check if right operand is negative (< 0.0)
        IrValue rightNegative = IrFactory.createFCmp(
            builder, IrRealPredicate.OLT, rightValue, zero, "right_negative"
        );

        // Check if signs are different (left_negative XOR right_negative)
        IrValue signsDiffer =
            IrFactory.createXor(builder, leftNegative, rightNegative, "signs_differ");

        // Combine conditions: remainder != 0.0 AND signs differ
        IrValue needsAdjustment =
            IrFactory.createAnd(builder, remainderNotZero, signsDiffer, "needs_adjustment");

        // Calculate adjusted result: remainder + right
        IrValue adjustedResult =
            IrFactory.createFAdd(builder, remainder, rightValue, "adjusted_result");

        // Select between remainder and adjusted result based on condition
        return IrFactory.createSelect(builder, needsAdjustment, adjustedResult, remainder,
            "fmod_result");
    }
}
