package com.reso.compiler.types.primary;

import com.reso.compiler.types.AbstractResoType;
import com.reso.llvm.api.IrType;
import javax.annotation.Nonnull;

/**
 * Implementation of integer types (i8, i16, i32, i64).
 */
public final class IntegerType extends AbstractResoType {
    private final int bitWidth;

    /**
     * Creates a new integer type.
     *
     * @param name     The type name
     * @param irType   The IR type
     * @param bitWidth The bit width
     */
    public IntegerType(@Nonnull String name, @Nonnull IrType irType, int bitWidth) {
        super(name, irType);
        this.bitWidth = bitWidth;
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
    public int getBitWidth() {
        return bitWidth;
    }
}
