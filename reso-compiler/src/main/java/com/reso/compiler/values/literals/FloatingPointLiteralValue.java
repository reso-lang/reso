package com.reso.compiler.values.literals;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.types.literals.FloatingPointLiteralType;
import com.reso.compiler.types.primary.FloatingPointType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Untyped floating-point literal.
 */
public final class FloatingPointLiteralValue extends ResoValue {
    private final FloatingPointLiteral value;

    public FloatingPointLiteralValue(@Nonnull FloatingPointLiteralType type,
                                     @Nonnull FloatingPointType defaultType,
                                     @Nonnull FloatingPointLiteral value,
                                     int line,
                                     int column) {
        super(type, defaultType, null, line, column);
        this.value = value;
    }

    @Override
    @Nullable
    public ConcreteResoValue concretize(@Nonnull ResoType targetType,
                                        @Nonnull ErrorReporter errorReporter) {
        if (!targetType.isFloatingPoint()) {
            errorReporter.error(
                "Cannot convert floating-point literal to non-float type " + targetType.getName(),
                line, column
            );
            return null;
        }

        if (!value.isInRange(targetType.getName())) {
            errorReporter.error(
                "Float literal " + value + " is out of range for type " + targetType.getName(),
                line, column
            );
            return null;
        }

        IrValue irValue = IrFactory.createConstantFloat(targetType.getType(), value.getValue());
        return new ConcreteResoValue(targetType, irValue, line, column);
    }

    @Override
    public boolean canConcretizeTo(@Nonnull ResoType targetType) {
        return targetType.isFloatingPoint() && value.isInRange(targetType.getName());
    }
}
