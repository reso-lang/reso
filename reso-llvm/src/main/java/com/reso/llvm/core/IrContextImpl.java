package com.reso.llvm.core;

import com.reso.llvm.api.IrContext;
import com.reso.llvm.util.IrInitializer;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.global.LLVM;

/**
 * Implementation of ResoContext that wraps an LLVMContextRef.
 */
@NotThreadSafe
public class IrContextImpl implements IrContext {
    private final LLVMContextRef contextRef;
    private boolean disposed = false;

    /**
     * Creates a new LLVM context.
     *
     * @return A new ResoContext instance
     */
    @Nonnull
    public static IrContextImpl create() {
        IrInitializer.initializeLlvm();
        return new IrContextImpl(LLVM.LLVMContextCreate());
    }

    /**
     * Wraps an existing LLVMContextRef.
     *
     * @param contextRef The LLVM context reference to wrap
     */
    public IrContextImpl(@Nonnull LLVMContextRef contextRef) {
        this.contextRef = Objects.requireNonNull(contextRef, "Context reference cannot be null");
    }

    /**
     * Gets the underlying LLVM context reference.
     *
     * @return The underlying LLVM context reference
     * @throws IllegalStateException if the context has been disposed
     */
    @Nonnull
    public LLVMContextRef getLlvmContext() {
        if (disposed) {
            throw new IllegalStateException("Context has been disposed");
        }
        return contextRef;
    }

    @Override
    public void close() {
        if (!disposed) {
            LLVM.LLVMContextDispose(contextRef);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}