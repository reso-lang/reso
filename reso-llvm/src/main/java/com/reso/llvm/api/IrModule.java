package com.reso.llvm.api;

import java.io.Closeable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public interface for LLVM module operations.
 * A module is a container for functions, global variables, and other entities.
 */
public interface IrModule extends Closeable {

    /**
     * Gets the context this module belongs to.
     *
     * @return The context
     */
    @Nonnull
    IrContext getContext();

    /**
     * Gets a function from a module by name.
     *
     * @param name         The function name
     * @param functionType The expected function type
     * @return The function value, or null if not found
     */
    @Nullable
    IrValue getFunction(@Nonnull String name, @Nonnull IrType functionType);

    /**
     * Checks if a function exists in this module.
     *
     * @param name The function name
     * @return true if the function exists
     * @throws IllegalStateException if the module has been disposed
     */
    boolean hasFunction(@Nonnull String name);

    /**
     * Sets the data layout for this module.
     *
     * @param dataLayout The data layout string
     * @throws IllegalStateException if the module has been disposed
     */
    void setDataLayout(@Nonnull String dataLayout);

    /**
     * Gets the data layout for this module.
     *
     * @return The data layout string
     * @throws IllegalStateException if the module has been disposed
     */
    @Nonnull
    String getDataLayout();

    /**
     * Sets the target triple for this module.
     *
     * @param triple The target triple string
     * @throws IllegalStateException if the module has been disposed
     */
    void setTargetTriple(@Nonnull String triple);

    /**
     * Gets the target triple for this module.
     *
     * @return The target triple string
     * @throws IllegalStateException if the module has been disposed
     */
    @Nonnull
    String getTargetTriple();

    /**
     * Closes this module and disposes any native resources.
     */
    @Override
    void close();

    /**
     * Checks if this module has been disposed.
     *
     * @return true if this module has been disposed
     */
    boolean isDisposed();
}