package com.reso.llvm.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public interface for LLVM basic block operations.
 */
public interface IrBasicBlock {

    /**
     * Gets the context this basic block belongs to.
     *
     * @return The context
     */
    @Nonnull
    IrContext getContext();

    /**
     * Gets the parent function of this basic block.
     *
     * @return The parent function, or null if not found
     * @throws IllegalStateException if the context has been disposed
     */
    @Nullable
    IrValue getParent();

    /**
     * Gets the first instruction in this basic block.
     *
     * @return The first instruction, or null if the block is empty
     * @throws IllegalStateException if the context has been disposed
     */
    @Nullable
    IrValue getFirstInstruction();

    /**
     * Gets the last instruction in this basic block.
     *
     * @return The last instruction, or null if the block is empty
     * @throws IllegalStateException if the context has been disposed
     */
    @Nullable
    IrValue getLastInstruction();

    /**
     * Gets the terminator instruction in this basic block.
     *
     * @return The terminator instruction, or null if the block has no terminator
     * @throws IllegalStateException if the context has been disposed
     */
    @Nullable
    IrValue getTerminator();
}