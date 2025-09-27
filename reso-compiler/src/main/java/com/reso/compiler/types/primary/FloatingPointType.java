package com.reso.compiler.types.primary;

import com.reso.compiler.types.AbstractResoType;
import com.reso.llvm.api.IrType;
import javax.annotation.Nonnull;

/**
 * Implementation of floating-point types (f32, f64).
 */
public final class FloatingPointType extends AbstractResoType {
    private final int bitWidth;

    /**
     * Creates a new floating-point type.
     *
     * @param name     The type name
     * @param irType   The IR type
     * @param bitWidth The bit width
     */
    public FloatingPointType(@Nonnull String name, @Nonnull IrType irType, int bitWidth) {
        super(name, irType);
        this.bitWidth = bitWidth;
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
    public int getBitWidth() {
        return bitWidth;
    }
}