package com.reso.compiler.types.literals;

import com.reso.compiler.types.AbstractResoType;
import javax.annotation.Nonnull;

public final class IntegerLiteralType extends AbstractResoType {

    /**
     * Creates an integer literal type.
     *
     * @param name The type name
     */
    public IntegerLiteralType(@Nonnull String name) {
        super(name, null);
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    @Override
    public boolean isInteger() {
        return true;
    }

    @Override
    public boolean isUntyped() {
        return true;
    }
}
