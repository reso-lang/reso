package com.reso.llvm.api;

import java.io.Closeable;
import javax.annotation.Nonnull;

/**
 * Public interface for LLVM pass builder options.
 */
public interface IrPassBuilderOptions extends Closeable {

    /**
     * Enables or disables loop vectorization.
     *
     * @param enabled Whether loop vectorization is enabled
     * @return This options instance for method chaining
     * @throws IllegalStateException if the options have been disposed
     */
    @Nonnull
    IrPassBuilderOptions setLoopVectorization(boolean enabled);

    /**
     * Enables or disables SLP vectorization.
     *
     * @param enabled Whether SLP vectorization is enabled
     * @return This options instance for method chaining
     * @throws IllegalStateException if the options have been disposed
     */
    @Nonnull
    IrPassBuilderOptions setSlpVectorization(boolean enabled);

    /**
     * Enables or disables loop unrolling.
     *
     * @param enabled Whether loop unrolling is enabled
     * @return This options instance for method chaining
     * @throws IllegalStateException if the options have been disposed
     */
    @Nonnull
    IrPassBuilderOptions setLoopUnrolling(boolean enabled);

    /**
     * Enables or disables loop interleaving.
     *
     * @param enabled Whether loop interleaving is enabled
     * @return This options instance for method chaining
     * @throws IllegalStateException if the options have been disposed
     */
    @Nonnull
    IrPassBuilderOptions setLoopInterleaving(boolean enabled);

    /**
     * Enables or disables verification of each pass.
     *
     * @param enabled Whether verification is enabled
     * @return This options instance for method chaining
     * @throws IllegalStateException if the options have been disposed
     */
    @Nonnull
    IrPassBuilderOptions setVerifyEach(boolean enabled);

    /**
     * Enables or disables debug logging.
     *
     * @param enabled Whether debug logging is enabled
     * @return This options instance for method chaining
     * @throws IllegalStateException if the options have been disposed
     */
    @Nonnull
    IrPassBuilderOptions setDebugLogging(boolean enabled);

    /**
     * Closes these options and disposes any native resources.
     */
    @Override
    void close();

    /**
     * Checks if these options have been disposed.
     *
     * @return true if these options have been disposed
     */
    boolean isDisposed();
}