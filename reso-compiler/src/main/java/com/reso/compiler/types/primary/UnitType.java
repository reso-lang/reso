package com.reso.compiler.types.primary;

import com.reso.compiler.types.AbstractResoType;
import com.reso.llvm.api.IrType;
import javax.annotation.Nonnull;

/**
 * Implementation of unit type.
 */
public final class UnitType extends AbstractResoType {
    /**
     * Creates a new unit type.
     *
     * @param irType The IR type
     */
    public UnitType(@Nonnull IrType irType) {
        super("()", irType);
    }

    @Override
    public boolean isUnit() {
        return true;
    }
}