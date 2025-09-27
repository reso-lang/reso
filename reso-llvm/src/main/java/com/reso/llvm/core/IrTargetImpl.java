package com.reso.llvm.core;

import com.reso.llvm.api.IrTarget;
import com.reso.llvm.api.IrTargetMachine;
import com.reso.llvm.exception.IrException;
import com.reso.llvm.util.IrInitializer;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMTargetRef;
import org.bytedeco.llvm.global.LLVM;

/**
 * Implementation of ResoTarget that wraps an LLVMTargetRef.
 */
@Immutable
public class IrTargetImpl implements IrTarget {
    private final LLVMTargetRef targetRef;

    /**
     * Gets a target from a triple.
     *
     * @param triple The target triple (e.g., "x86_64-unknown-linux-gnu")
     * @return The target
     * @throws IrException if the target is not found
     */
    @Nonnull
    public static IrTargetImpl fromTriple(@Nonnull String triple) {
        Objects.requireNonNull(triple, "Triple cannot be null");

        // Ensure LLVM target is initialized
        IrInitializer.initializeTargetMachine();

        try {
            PointerPointer<LLVMTargetRef> targetPtr = new PointerPointer<>(1);
            BytePointer error = new BytePointer();

            if (LLVM.LLVMGetTargetFromTriple(triple, targetPtr, error) != 0) {
                String errorMsg = error.getString();
                LLVM.LLVMDisposeMessage(error);
                throw new IrException("Failed to get target: " + errorMsg);
            }

            LLVMTargetRef target = targetPtr.get(LLVMTargetRef.class);
            LLVM.LLVMDisposeMessage(error);

            return new IrTargetImpl(target);
        } catch (Exception e) {
            throw new IrException("Failed to get target from triple: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the default target triple for the host platform.
     *
     * @return The default target triple
     */
    @Nonnull
    public static String getDefaultTargetTriple() {
        // Ensure LLVM target is initialized
        IrInitializer.initializeTargetMachine();

        try {
            BytePointer triplePtr = LLVM.LLVMGetDefaultTargetTriple();
            String triple = triplePtr.getString();
            LLVM.LLVMDisposeMessage(triplePtr);
            return triple;
        } catch (Exception e) {
            throw new IrException("Failed to get default target triple: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the host CPU name.
     *
     * @return The host CPU name
     */
    @Nonnull
    public static String getHostCpuName() {
        // Ensure LLVM target is initialized
        IrInitializer.initializeTargetMachine();

        try {
            BytePointer cpuPtr = LLVM.LLVMGetHostCPUName();
            String cpu = cpuPtr.getString();
            LLVM.LLVMDisposeMessage(cpuPtr);
            return cpu;
        } catch (Exception e) {
            throw new IrException("Failed to get host CPU name: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the host CPU features.
     *
     * @return The host CPU features
     */
    @Nonnull
    public static String getHostCpuFeatures() {
        // Ensure LLVM target is initialized
        IrInitializer.initializeTargetMachine();

        try {
            BytePointer featuresPtr = LLVM.LLVMGetHostCPUFeatures();
            String features = featuresPtr.getString();
            LLVM.LLVMDisposeMessage(featuresPtr);
            return features;
        } catch (Exception e) {
            throw new IrException("Failed to get host CPU features: " + e.getMessage(), e);
        }
    }

    /**
     * Wraps an existing LLVMTargetRef.
     *
     * @param targetRef The LLVM target reference to wrap
     */
    public IrTargetImpl(@Nonnull LLVMTargetRef targetRef) {
        this.targetRef = Objects.requireNonNull(targetRef, "Target reference cannot be null");
    }

    /**
     * Gets the underlying LLVM target reference.
     *
     * @return The underlying LLVM target reference
     */
    @Nonnull
    public LLVMTargetRef getLlvmTarget() {
        return targetRef;
    }

    @Override
    public boolean hasJit() {
        return LLVM.LLVMTargetHasJIT(targetRef) != 0;
    }

    @Override
    public boolean hasTargetMachine() {
        return LLVM.LLVMTargetHasTargetMachine(targetRef) != 0;
    }

    @Override
    public boolean hasAsmBackend() {
        return LLVM.LLVMTargetHasAsmBackend(targetRef) != 0;
    }

    @Override
    @Nonnull
    public String getName() {
        try {
            return LLVM.LLVMGetTargetName(targetRef).getString();
        } catch (Exception e) {
            throw new IrException("Failed to get target name: " + e.getMessage(), e);
        }
    }

    @Override
    @Nonnull
    public String getDescription() {
        try {
            return LLVM.LLVMGetTargetDescription(targetRef).getString();
        } catch (Exception e) {
            throw new IrException("Failed to get target description: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a target machine.
     *
     * @param targetTriple The target triple
     * @param cpu          The CPU name (can be empty)
     * @param features     The CPU features (can be empty)
     * @param optLevel     The optimization level
     * @param relocMode    The relocation model
     * @param codeModel    The code model
     * @return The created target machine
     */
    @Nonnull
    public IrTargetMachine createTargetMachine(
        @Nonnull String targetTriple,
        @Nonnull String cpu,
        @Nonnull String features,
        int optLevel,
        int relocMode,
        int codeModel) {

        Objects.requireNonNull(targetTriple, "Target triple cannot be null");
        Objects.requireNonNull(cpu, "CPU cannot be null");
        Objects.requireNonNull(features, "Features cannot be null");

        try {
            return new IrTargetMachineImpl(
                LLVM.LLVMCreateTargetMachine(
                    targetRef,
                    targetTriple,
                    cpu,
                    features,
                    optLevel,
                    relocMode,
                    codeModel
                )
            );
        } catch (Exception e) {
            throw new IrException("Failed to create target machine: " + e.getMessage(), e);
        }
    }

    @Override
    @Nonnull
    public String toString() {
        return "ResoTarget[name=" + getName() + "]";
    }
}