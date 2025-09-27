package com.reso.compiler.types;

import javax.annotation.Nonnull;

public class GenericType extends AbstractResoType {

    private final int index;

    /**
     * Creates a new generic type.
     *
     * @param name The type name
     */
    public GenericType(@Nonnull String name, int index) {
        super(name, null);
        this.index = index;
    }

    @Override
    public boolean isGeneric() {
        return true;
    }

    public int getIndex() {
        return index;
    }
}
