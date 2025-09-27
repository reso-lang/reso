package com.reso.llvm.api;

import java.io.Closeable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public interface for LLVM instruction builder operations.
 */
public interface IrBuilder extends Closeable {

    /**
     * Gets the context this builder belongs to.
     *
     * @return The context
     */
    @Nonnull
    IrContext getContext();

    /**
     * Gets the basic block the builder is currently positioned at.
     *
     * @return The current basic block, or null if not positioned
     * @throws IllegalStateException if the builder has been disposed
     */
    @Nullable
    IrBasicBlock getCurrentBlock();

    /**
     * Closes this builder and disposes any native resources.
     */
    @Override
    void close();

    /**
     * Checks if this builder has been disposed.
     *
     * @return true if this builder has been disposed
     */
    boolean isDisposed();
}