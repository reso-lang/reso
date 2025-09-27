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
 * Untyped ternary expression (e.g., condition ? true_value : false_value).
 */
public final class TernaryExpressionValue extends ResoValue {
    private final ResoValue condition;
    private final ResoValue trueValue;
    private final ResoValue falseValue;
    private final IrBuilder irBuilder;
    private final TypeSystem typeSystem;

    public TernaryExpressionValue(@Nonnull ResoType type,
                                  @Nullable ResoType defaultType,
                                  @Nonnull ResoValue condition,
                                  @Nonnull ResoValue trueValue,
                                  @Nonnull ResoValue falseValue,
                                  @Nonnull IrBuilder irBuilder,
                                  @Nonnull TypeSystem typeSystem,
                                  int line,
                                  int column) {
        super(type, defaultType, null, line, column);
        this.condition = condition;
        this.trueValue = trueValue;
        this.falseValue = falseValue;
        this.irBuilder = irBuilder;
        this.typeSystem = typeSystem;
    }

    @Override
    @Nullable
    public ConcreteResoValue concretize(@Nonnull ResoType targetType,
                                        @Nonnull ErrorReporter errorReporter) {
        if (!canConcretizeTo(targetType)) {
            errorReporter.error(
                "Cannot convert ternary expression to type " + targetType.getName(),
                line, column
            );
            return null;
        }

        // Concretize condition to boolean
        ResoType boolType = typeSystem.getBoolType();
        ConcreteResoValue concreteCondition = condition.concretize(boolType, errorReporter);
        if (concreteCondition == null) {
            return null;
        }

        // Concretize both branches to target type
        ConcreteResoValue concreteTrueValue = trueValue.concretize(targetType, errorReporter);
        ConcreteResoValue concreteFalseValue = falseValue.concretize(targetType, errorReporter);

        if (concreteTrueValue == null || concreteFalseValue == null) {
            return null;
        }

        // Generate the select instruction
        IrValue result = IrFactory.createSelect(
            irBuilder,
            concreteCondition.getValue(),
            concreteTrueValue.getValue(),
            concreteFalseValue.getValue(),
            "ternary_tmp"
        );

        return new ConcreteResoValue(targetType, result, line, column);
    }

    @Override
    public boolean canConcretizeTo(@Nonnull ResoType targetType) {
        // Check if condition can be concretized to boolean
        ResoType boolType = typeSystem.getBoolType();
        if (!condition.canConcretizeTo(boolType)) {
            return false;
        }

        // Check if both branches can be concretized to target type
        return trueValue.canConcretizeTo(targetType) && falseValue.canConcretizeTo(targetType);
    }
}