package com.reso.llvm.core;

import com.reso.llvm.api.IrPassBuilderOptions;
import com.reso.llvm.exception.IrException;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import org.bytedeco.llvm.LLVM.LLVMPassBuilderOptionsRef;
import org.bytedeco.llvm.global.LLVM;

/**
 * Implementation of ResoPassBuilderOptions that wraps an LLVMPassBuilderOptionsRef.
 */
@NotThreadSafe
public class IrPassBuilderOptionsImpl implements IrPassBuilderOptions {
    private final LLVMPassBuilderOptionsRef optionsRef;
    private boolean disposed = false;

    /**
     * Creates a new pass builder options.
     *
     * @return A new ResoPassBuilderOptions instance
     */
    @Nonnull
    public static IrPassBuilderOptionsImpl create() {
        return new IrPassBuilderOptionsImpl(LLVM.LLVMCreatePassBuilderOptions());
    }

    /**
     * Wraps an existing LLVMPassBuilderOptionsRef.
     *
     * @param optionsRef The LLVM pass builder options reference to wrap
     */
    public IrPassBuilderOptionsImpl(@Nonnull LLVMPassBuilderOptionsRef optionsRef) {
        this.optionsRef = optionsRef;
    }

    /**
     * Gets the underlying LLVM pass builder options reference.
     *
     * @return The underlying LLVM pass builder options reference
     * @throws IllegalStateException if the options have been disposed
     */
    @Nonnull
    public LLVMPassBuilderOptionsRef getLlvmPassBuilderOptions() {
        if (disposed) {
            throw new IllegalStateException("Pass builder options have been disposed");
        }
        return optionsRef;
    }

    @Override
    @Nonnull
    public IrPassBuilderOptions setLoopVectorization(boolean enabled) {
        if (disposed) {
            throw new IllegalStateException("Pass builder options have been disposed");
        }

        try {
            LLVM.LLVMPassBuilderOptionsSetLoopVectorization(getLlvmPassBuilderOptions(),
                enabled ? 1 : 0);
            return this;
        } catch (Exception e) {
            throw new IrException("Failed to set loop vectorization: " + e.getMessage(), e);
        }
    }

    @Override
    @Nonnull
    public IrPassBuilderOptions setSlpVectorization(boolean enabled) {
        if (disposed) {
            throw new IllegalStateException("Pass builder options have been disposed");
        }

        try {
            LLVM.LLVMPassBuilderOptionsSetSLPVectorization(getLlvmPassBuilderOptions(),
                enabled ? 1 : 0);
            return this;
        } catch (Exception e) {
            throw new IrException("Failed to set SLP vectorization: " + e.getMessage(), e);
        }
    }

    @Override
    @Nonnull
    public IrPassBuilderOptions setLoopUnrolling(boolean enabled) {
        if (disposed) {
            throw new IllegalStateException("Pass builder options have been disposed");
        }

        try {
            LLVM.LLVMPassBuilderOptionsSetLoopUnrolling(getLlvmPassBuilderOptions(),
                enabled ? 1 : 0);
            return this;
        } catch (Exception e) {
            throw new IrException("Failed to set loop unrolling: " + e.getMessage(), e);
        }
    }

    @Override
    @Nonnull
    public IrPassBuilderOptions setLoopInterleaving(boolean enabled) {
        if (disposed) {
            throw new IllegalStateException("Pass builder options have been disposed");
        }

        try {
            LLVM.LLVMPassBuilderOptionsSetLoopInterleaving(getLlvmPassBuilderOptions(),
                enabled ? 1 : 0);
            return this;
        } catch (Exception e) {
            throw new IrException("Failed to set loop interleaving: " + e.getMessage(), e);
        }
    }

    @Override
    @Nonnull
    public IrPassBuilderOptions setVerifyEach(boolean enabled) {
        if (disposed) {
            throw new IllegalStateException("Pass builder options have been disposed");
        }

        try {
            LLVM.LLVMPassBuilderOptionsSetVerifyEach(getLlvmPassBuilderOptions(), enabled ? 1 : 0);
            return this;
        } catch (Exception e) {
            throw new IrException("Failed to set verify each: " + e.getMessage(), e);
        }
    }

    @Override
    @Nonnull
    public IrPassBuilderOptions setDebugLogging(boolean enabled) {
        if (disposed) {
            throw new IllegalStateException("Pass builder options have been disposed");
        }

        try {
            LLVM.LLVMPassBuilderOptionsSetDebugLogging(getLlvmPassBuilderOptions(),
                enabled ? 1 : 0);
            return this;
        } catch (Exception e) {
            throw new IrException("Failed to set debug logging: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (!disposed) {
            LLVM.LLVMDisposePassBuilderOptions(optionsRef);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}