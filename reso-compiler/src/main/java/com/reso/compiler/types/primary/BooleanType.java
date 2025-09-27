package com.reso.compiler.types.primary;

import com.reso.compiler.types.AbstractResoType;
import com.reso.llvm.api.IrType;
import javax.annotation.Nonnull;

/**
 * Implementation of boolean type.
 */
public final class BooleanType extends AbstractResoType {
    /**
     * Creates a new boolean type.
     *
     * @param irType The IR type
     */
    public BooleanType(@Nonnull IrType irType) {
        super("bool", irType);
    }

    @Override
    public boolean isBool() {
        return true;
    }

    @Override
    public int getBitWidth() {
        return 1; // boolean is represented as i1
    }
}