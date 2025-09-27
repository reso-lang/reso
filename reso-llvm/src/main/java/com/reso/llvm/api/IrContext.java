package com.reso.llvm.api;

import java.io.Closeable;

/**
 * Public interface for LLVM context operations.
 * A context is the primary container for LLVM entities.
 */
public interface IrContext extends Closeable {

    /**
     * Closes this context and disposes any native resources.
     */
    @Override
    void close();

    /**
     * Checks if this context has been disposed.
     *
     * @return true if this context has been disposed
     */
    boolean isDisposed();
}