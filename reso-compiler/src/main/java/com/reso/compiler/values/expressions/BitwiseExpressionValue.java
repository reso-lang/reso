package com.reso.compiler.values.expressions;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.types.TypeSystem;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Untyped bitwise expression (e.g., a & b, x | y, z ^ w, a << b, c >> d).
 */
public final class BitwiseExpressionValue extends BinaryExpressionValue {
    private final IrBuilder irBuilder;
    private final TypeSystem typeSystem;

    public BitwiseExpressionValue(@Nonnull ResoType type,
                                  @Nullable ResoType defaultType,
                                  @Nonnull ResoValue left,
                                  @Nonnull ResoValue right,
                                  @Nonnull String operator,
                                  @Nonnull IrBuilder irBuilder,
                                  @Nonnull TypeSystem typeSystem,
                                  int line,
                                  int column) {
        super(type, defaultType, left, right, operator, line, column);
        this.irBuilder = irBuilder;
        this.typeSystem = typeSystem;
    }

    @Override
    @Nullable
    public ConcreteResoValue concretize(@Nonnull ResoType targetType,
                                        @Nonnull ErrorReporter errorReporter) {
        if (!canConcretizeTo(targetType)) {
            errorReporter.error(
                "Cannot convert bitwise expression to type " + targetType.getName(),
                line, column
            );
            return null;
        }

        // Concretize operands
        ConcreteResoValue concretLeft = left.concretize(targetType, errorReporter);
        ConcreteResoValue concretRight;

        if (isShiftOperation(operator)) {
            ConcreteResoValue valueToConvert;
            if (right.isUntyped()) {
                valueToConvert = right.concretize(typeSystem.getDefaultIntType(), errorReporter);
                if (valueToConvert == null) {
                    errorReporter.error(
                        "Cannot convert right operand of shift operation to integer type",
                        line, column
                    );
                    return null;
                }
            } else {
                valueToConvert = right.concretizeToDefault(errorReporter);
            }
            concretRight =
                typeSystem.createConversion(valueToConvert, targetType, errorReporter, line,
                    column);
        } else {
            concretRight = right.concretize(targetType, errorReporter);
        }

        if (concretLeft == null || concretRight == null) {
            return null;
        }

        // Perform the bitwise operation
        IrValue result = performBitwiseOperation(
            concretLeft, concretRight, operator, irBuilder, errorReporter
        );

        if (result == null) {
            return null;
        }

        return new ConcreteResoValue(targetType, result, line, column);
    }

    @Override
    public boolean canConcretizeTo(@Nonnull ResoType targetType) {
        if (!targetType.isInteger()) {
            return false;
        }

        return left.canConcretizeTo(targetType)
            && (right.canConcretizeTo(targetType) || isShiftOperation(operator));
    }

    @Nullable
    private IrValue performBitwiseOperation(@Nonnull ConcreteResoValue left,
                                            @Nonnull ConcreteResoValue right,
                                            @Nonnull String operator,
                                            @Nonnull IrBuilder builder,
                                            @Nonnull ErrorReporter errorReporter) {

        IrValue leftValue = left.getValue();
        IrValue rightValue = right.getValue();

        return switch (operator) {
            case "&" -> IrFactory.createAnd(builder, leftValue, rightValue, "bitand_tmp");

            case "|" -> IrFactory.createOr(builder, leftValue, rightValue, "bitor_tmp");

            case "^" -> IrFactory.createXor(builder, leftValue, rightValue, "bitxor_tmp");

            case "<<" -> IrFactory.createShl(builder, leftValue, rightValue, "shl_tmp");

            case ">>" -> left.isUnsignedInteger()
                ? IrFactory.createLShr(builder, leftValue, rightValue, "lshr_tmp") :
                IrFactory.createAShr(builder, leftValue, rightValue, "ashr_tmp");

            default -> {
                errorReporter.error(
                    "Unsupported bitwise operator: " + operator,
                    line, column
                );
                yield null;
            }
        };
    }

    /**
     * Checks if the operator is a shift operation.
     */
    private boolean isShiftOperation(@Nonnull String operator) {
        return "<<".equals(operator) || ">>".equals(operator);
    }
}