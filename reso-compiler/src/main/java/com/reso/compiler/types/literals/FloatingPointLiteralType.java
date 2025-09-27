package com.reso.compiler.types.literals;

import com.reso.compiler.types.AbstractResoType;
import javax.annotation.Nonnull;

public final class FloatingPointLiteralType extends AbstractResoType {

    /**
     * Creates a floating point literal type.
     *
     * @param name The type name
     */
    public FloatingPointLiteralType(@Nonnull String name) {
        super(name, null);
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    @Override
    public boolean isFloatingPoint() {
        return true;
    }

    @Override
    public boolean isUntyped() {
        return true;
    }
}
