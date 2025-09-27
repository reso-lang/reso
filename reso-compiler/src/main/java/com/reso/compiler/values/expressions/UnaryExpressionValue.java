package com.reso.compiler.values.expressions;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Untyped unary expression (e.g., -x, !y).
 */
public final class UnaryExpressionValue extends ResoValue {
    private final ResoValue operand;
    private final String operator;
    private final IrBuilder irBuilder;

    public UnaryExpressionValue(@Nonnull ResoValue operand,
                                @Nonnull String operator,
                                @Nonnull IrBuilder irBuilder,
                                int line,
                                int column) {
        super(operand.getType(), operand.getDefaultType(), null, line, column);
        this.operand = operand;
        this.operator = operator;
        this.irBuilder = irBuilder;
    }

    @Override
    @Nullable
    public ConcreteResoValue concretize(@Nonnull ResoType targetType,
                                        @Nonnull ErrorReporter errorReporter) {
        ConcreteResoValue concreteOperand = operand.concretize(targetType, errorReporter);
        if (concreteOperand == null) {
            return null;
        }

        IrValue result = performUnaryOperation(
            concreteOperand, operator, irBuilder, errorReporter
        );

        if (result == null) {
            return null;
        }

        return new ConcreteResoValue(concreteOperand.getType(), result, line, column);
    }

    @Override
    public boolean canConcretizeTo(@Nonnull ResoType targetType) {
        return switch (operator) {
            case "+", "-" -> targetType.isNumeric() && operand.canConcretizeTo(targetType);
            case "!" -> targetType.isBool() && operand.canConcretizeTo(targetType);
            case "~" -> targetType.isInteger() && operand.canConcretizeTo(targetType);
            default -> false;
        };
    }

    @Nullable
    private IrValue performUnaryOperation(@Nonnull ConcreteResoValue operand,
                                          @Nonnull String operator,
                                          @Nonnull IrBuilder builder,
                                          @Nonnull ErrorReporter errorReporter) {

        IrValue operandValue = operand.getValue();

        return switch (operator) {
            case "+" -> operandValue; // Unary plus is no-op
            case "-" -> operand.isFloatingPoint()
                ? IrFactory.createFNeg(builder, operandValue, "fneg_tmp") :
                IrFactory.createNeg(builder, operandValue, "neg_tmp");
            case "not" -> {
                if (operand.isBool()) {
                    IrValue one =
                        IrFactory.createConstantInt(operand.getType().getType(), 1, false);
                    yield IrFactory.createXor(builder, operand.getValue(), one, "not_tmp");
                } else {
                    errorReporter.error(
                        "Logical NOT can only be applied to boolean values",
                        line, column
                    );
                    yield null;
                }
            }
            case "~" -> {
                if (operand.isInteger()) {
                    IrValue allOnes = IrFactory.createAllOnes(operand.getType().getType());
                    yield IrFactory.createXor(builder, operand.getValue(), allOnes, "bitnot_tmp");
                } else {
                    errorReporter.error(
                        "Bitwise NOT can only be applied to integer values",
                        line, column
                    );
                    yield null;
                }
            }
            default -> {
                errorReporter.error(
                    "Unsupported unary operator: " + operator,
                    line, column
                );
                yield null;
            }
        };
    }
}
