package com.reso.compiler.types.primary;

import com.reso.compiler.types.AbstractResoType;
import com.reso.llvm.api.IrType;
import javax.annotation.Nonnull;

/**
 * Implementation of character type.
 */
public final class CharType extends AbstractResoType {
    /**
     * Creates a new character type.
     *
     * @param irType The IR type
     */
    public CharType(@Nonnull IrType irType) {
        super("char", irType);
    }

    @Override
    public boolean isChar() {
        return true;
    }

    @Override
    public int getBitWidth() {
        return 32;
    }
}
