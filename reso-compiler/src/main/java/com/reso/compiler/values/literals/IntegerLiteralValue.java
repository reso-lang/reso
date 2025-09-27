package com.reso.compiler.values.literals;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.types.literals.IntegerLiteralType;
import com.reso.compiler.types.primary.IntegerType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Untyped integer literal that can be converted to any integer type that can represent its value.
 */
public final class IntegerLiteralValue extends ResoValue {
    private final IntegerLiteral value;

    public IntegerLiteralValue(@Nonnull IntegerLiteralType type,
                               @Nonnull IntegerType defaultType,
                               @Nonnull IntegerLiteral value,
                               int line,
                               int column) {
        super(type, defaultType, null, line, column);
        this.value = value;
    }

    @Override
    @Nullable
    public ConcreteResoValue concretize(@Nonnull ResoType targetType,
                                        @Nonnull ErrorReporter errorReporter) {
        if (!targetType.isInteger()) {
            errorReporter.error(
                "Cannot convert integer literal to non-integer type " + targetType.getName(),
                line, column
            );
            return null;
        }

        if (!value.isInRange(targetType.getName())) {
            errorReporter.error(
                "Integer literal " + value + " is out of range for type " + targetType.getName(),
                line, column
            );
            return null;
        }

        IrValue irValue =
            IrFactory.createConstantInt(targetType.getType(), value.getBigInteger().longValue(),
                targetType.isSignedInteger());
        return new ConcreteResoValue(targetType, irValue, line, column);
    }

    @Override
    public boolean canConcretizeTo(@Nonnull ResoType targetType) {
        return targetType.isInteger() && value.isInRange(targetType.getName());
    }
}
