package com.reso.compiler.values;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.llvm.api.IrValue;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a concrete value in the Reso language.
 */
public class ConcreteResoValue extends ResoValue {

    public ConcreteResoValue(@Nonnull ResoType type, @Nonnull IrValue value, int line, int column) {
        super(type, Objects.requireNonNull(value), line, column);
    }

    public ConcreteResoValue(@Nonnull ResoType type, @Nonnull ResoType defaultType,
                             @Nonnull IrValue value, int line, int column) {
        super(type, Objects.requireNonNull(defaultType), Objects.requireNonNull(value), line,
            column);
    }

    @Override
    @Nullable
    public ConcreteResoValue concretize(@Nonnull ResoType targetType,
                                        @Nonnull ErrorReporter errorReporter) {
        if (canConcretizeTo(targetType)) {
            return this;
        } else {
            errorReporter.error(
                "Cannot convert value of type " + type.getName() + " to type "
                    + targetType.getName(),
                line, column
            );
            return null;
        }
    }

    /**
     * Gets the LLVM IR value.
     *
     * @return The LLVM IR value
     */
    @Nonnull
    public IrValue getValue() {
        return value;
    }
}
