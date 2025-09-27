package com.reso.llvm.core;

import com.reso.llvm.api.IrBasicBlock;
import com.reso.llvm.api.IrContext;
import com.reso.llvm.api.IrValue;
import com.reso.llvm.exception.IrException;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

/**
 * Implementation of ResoBasicBlock that wraps an LLVMBasicBlockRef.
 */
@Immutable
public class IrBasicBlockImpl implements IrBasicBlock {
    private final LLVMBasicBlockRef blockRef;
    private final IrContextImpl context;

    /**
     * Creates a new basic block in a function.
     *
     * @param function The function to create the block in
     * @param name     The name of the block
     * @return A new basic block
     */
    @Nonnull
    public static IrBasicBlockImpl create(@Nonnull IrValueImpl function, @Nonnull String name) {
        Objects.requireNonNull(function, "Function cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!function.isFunction()) {
            throw new IllegalArgumentException("Not a function: " + function);
        }

        IrContextImpl context = function.getContext();
        LLVMBasicBlockRef blockRef = LLVM.LLVMAppendBasicBlockInContext(
            context.getLlvmContext(),
            function.getLlvmValue(),
            name
        );

        return new IrBasicBlockImpl(blockRef, context);
    }

    /**
     * Wraps an existing LLVMBasicBlockRef.
     *
     * @param blockRef The LLVM basic block reference to wrap
     * @param context  The context this basic block belongs to
     */
    public IrBasicBlockImpl(@Nonnull LLVMBasicBlockRef blockRef, @Nonnull IrContextImpl context) {
        this.blockRef = Objects.requireNonNull(blockRef, "Block reference cannot be null");
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Gets the underlying LLVM basic block reference.
     *
     * @return The underlying LLVM basic block reference
     * @throws IllegalStateException if the context has been disposed
     */
    @Nonnull
    public LLVMBasicBlockRef getLlvmBasicBlock() {
        if (context.isDisposed()) {
            throw new IllegalStateException("Context has been disposed");
        }
        return blockRef;
    }

    @Override
    @Nonnull
    public IrContext getContext() {
        return context;
    }

    @Override
    @Nullable
    public IrValue getParent() {
        if (context.isDisposed()) {
            throw new IllegalStateException("Context has been disposed");
        }

        try {
            LLVMValueRef parentRef = LLVM.LLVMGetBasicBlockParent(getLlvmBasicBlock());
            if (parentRef == null) {
                return null;
            }

            // Create a value with the correct function type
            return new IrValueImpl(parentRef, context);
        } catch (Exception e) {
            throw new IrException("Failed to get parent function: " + e.getMessage(), e);
        }
    }

    @Override
    @Nullable
    public IrValue getFirstInstruction() {
        if (context.isDisposed()) {
            throw new IllegalStateException("Context has been disposed");
        }

        try {
            LLVMValueRef firstInst = LLVM.LLVMGetFirstInstruction(getLlvmBasicBlock());
            if (firstInst == null) {
                return null;
            }

            // Create a value with the inferred type
            return new IrValueImpl(firstInst, context);
        } catch (Exception e) {
            throw new IrException("Failed to get first instruction: " + e.getMessage(), e);
        }
    }

    @Override
    @Nullable
    public IrValue getLastInstruction() {
        if (context.isDisposed()) {
            throw new IllegalStateException("Context has been disposed");
        }

        try {
            LLVMValueRef lastInst = LLVM.LLVMGetLastInstruction(getLlvmBasicBlock());
            if (lastInst == null) {
                return null;
            }

            // Create a value with the inferred type
            return new IrValueImpl(lastInst, context);
        } catch (Exception e) {
            throw new IrException("Failed to get last instruction: " + e.getMessage(), e);
        }
    }

    @Override
    @Nullable
    public IrValue getTerminator() {
        if (context.isDisposed()) {
            throw new IllegalStateException("Context has been disposed");
        }

        try {
            LLVMValueRef terminator = LLVM.LLVMGetBasicBlockTerminator(getLlvmBasicBlock());
            if (terminator == null) {
                return null;
            }

            // Create a value with the inferred type
            return new IrValueImpl(terminator, context);
        } catch (Exception e) {
            throw new IrException("Failed to get terminator: " + e.getMessage(), e);
        }
    }

    @Override
    @Nonnull
    public String toString() {
        return "ResoBasicBlock[]";
    }
}