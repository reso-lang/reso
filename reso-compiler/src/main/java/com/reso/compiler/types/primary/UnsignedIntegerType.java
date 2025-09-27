package com.reso.compiler.types.primary;

import com.reso.compiler.types.AbstractResoType;
import com.reso.llvm.api.IrType;
import javax.annotation.Nonnull;

/**
 * Implementation of unsigned integer types (u8, u16, u32, u64).
 */
public final class UnsignedIntegerType extends AbstractResoType {
    private final int bitWidth;

    /**
     * Creates a new unsigned integer type.
     *
     * @param name     The type name (e.g., "u8", "u16")
     * @param irType   The IR type (same as signed, interpretation differs)
     * @param bitWidth The bit width (8, 16, 32, 64)
     */
    public UnsignedIntegerType(@Nonnull String name, @Nonnull IrType irType,
                               int bitWidth) {
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
    public boolean isUnsignedInteger() {
        return true;
    }

    @Override
    public int getBitWidth() {
        return bitWidth;
    }
}