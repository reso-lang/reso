package com.reso.compiler.types.primary;

import com.reso.compiler.types.AbstractResoType;
import com.reso.llvm.api.IrType;
import javax.annotation.Nonnull;

/**
 * Implementation of null type.
 */
public final class NullType extends AbstractResoType {
    /**
     * Creates a new null type.
     *
     * @param irType The IR type
     */
    public NullType(@Nonnull IrType irType) {
        super("Null", irType);
    }

    @Override
    public boolean isNull() {
        return true;
    }
}