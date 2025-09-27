package com.reso.llvm.core;

import com.reso.llvm.api.IrModule;
import com.reso.llvm.api.IrTargetMachine;
import com.reso.llvm.enums.IrCodeGenFileType;
import com.reso.llvm.exception.IrException;
import com.reso.llvm.util.IrInitializer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.llvm.LLVM.LLVMMemoryBufferRef;
import org.bytedeco.llvm.LLVM.LLVMTargetDataRef;
import org.bytedeco.llvm.LLVM.LLVMTargetMachineRef;
import org.bytedeco.llvm.global.LLVM;

/**
 * Implementation of ResoTargetMachine that wraps an LLVMTargetMachineRef.
 */
@NotThreadSafe
public class IrTargetMachineImpl implements IrTargetMachine {
    private final LLVMTargetMachineRef machineRef;
    private boolean disposed = false;

    /**
     * Creates a target machine for the host platform.
     *
     * @return A new target machine
     */
    @Nonnull
    public static IrTargetMachineImpl createHostTargetMachine() {
        // Ensure LLVM target is initialized
        IrInitializer.initializeTargetMachine();

        String triple = IrTargetImpl.getDefaultTargetTriple();
        String cpu = IrTargetImpl.getHostCpuName();
        String features = IrTargetImpl.getHostCpuFeatures();

        IrTargetImpl target = IrTargetImpl.fromTriple(triple);

        return (IrTargetMachineImpl) target.createTargetMachine(
            triple,
            cpu,
            features,
            LLVM.LLVMCodeGenLevelDefault,
            LLVM.LLVMRelocDefault,
            LLVM.LLVMCodeModelDefault
        );
    }

    /**
     * Wraps an existing LLVMTargetMachineRef.
     *
     * @param machineRef The LLVM target machine reference to wrap
     */
    public IrTargetMachineImpl(@Nonnull LLVMTargetMachineRef machineRef) {
        this.machineRef =
            Objects.requireNonNull(machineRef, "Target machine reference cannot be null");
    }

    /**
     * Gets the underlying LLVM target machine reference.
     *
     * @return The underlying LLVM target machine reference
     * @throws IllegalStateException if the target machine has been disposed
     */
    @Nonnull
    public LLVMTargetMachineRef getLlvmTargetMachine() {
        if (disposed) {
            throw new IllegalStateException("Target machine has been disposed");
        }
        return machineRef;
    }

    @Override
    @Nonnull
    public String getTriple() {
        if (disposed) {
            throw new IllegalStateException("Target machine has been disposed");
        }

        try {
            BytePointer triplePtr = LLVM.LLVMGetTargetMachineTriple(getLlvmTargetMachine());
            String triple = triplePtr.getString();
            LLVM.LLVMDisposeMessage(triplePtr);
            return triple;
        } catch (Exception e) {
            throw new IrException("Failed to get target triple: " + e.getMessage(), e);
        }
    }

    @Override
    @Nonnull
    public String getDataLayout() {
        if (disposed) {
            throw new IllegalStateException("Target machine has been disposed");
        }

        try {
            LLVMTargetDataRef dataLayoutRef = LLVM.LLVMCreateTargetDataLayout(getLlvmTargetMachine());
            BytePointer dataLayoutPtr = LLVM.LLVMCopyStringRepOfTargetData(dataLayoutRef);
            String dataLayout = dataLayoutPtr.getString();

            // Clean up native resources
            LLVM.LLVMDisposeMessage(dataLayoutPtr);
            LLVM.LLVMDisposeTargetData(dataLayoutRef);

            return dataLayout;
        } catch (Exception e) {
            throw new IrException("Failed to get data layout: " + e.getMessage(), e);
        }
    }

    @Override
    public void emitToFile(
        @Nonnull IrModule module,
        @Nonnull Path outputPath,
        @Nonnull IrCodeGenFileType fileType) throws IOException {

        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(outputPath, "Output path cannot be null");
        Objects.requireNonNull(fileType, "File type cannot be null");

        if (disposed) {
            throw new IllegalStateException("Target machine has been disposed");
        }

        if ((module).isDisposed()) {
            throw new IllegalStateException("Module has been disposed");
        }

        // Get the output path as a string
        String outputFile = outputPath.toAbsolutePath().toString();

        // Create the output directory if it doesn't exist
        Files.createDirectories(outputPath.getParent());

        // Emit the code
        BytePointer errorMessage = new BytePointer();
        try {
            if (LLVM.LLVMTargetMachineEmitToFile(
                getLlvmTargetMachine(),
                ((IrModuleImpl) module).getLlvmModule(),
                new BytePointer(outputFile),
                fileType.getCodeGenFileType(),
                errorMessage) != 0) {

                String error = errorMessage.getString();
                throw new IrException("Failed to emit code: " + error);
            }
        } finally {
            LLVM.LLVMDisposeMessage(errorMessage);
        }
    }

    @Override
    @Nonnull
    public String emitToMemory(@Nonnull IrModule module, @Nonnull IrCodeGenFileType fileType) {
        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(fileType, "File type cannot be null");

        if (disposed) {
            throw new IllegalStateException("Target machine has been disposed");
        }

        if ((module).isDisposed()) {
            throw new IllegalStateException("Module has been disposed");
        }

        // Emit the code to a memory buffer
        LLVMMemoryBufferRef bufferRef = null;
        BytePointer errorMessage = new BytePointer();

        try {
            if (LLVM.LLVMTargetMachineEmitToMemoryBuffer(
                getLlvmTargetMachine(),
                ((IrModuleImpl) module).getLlvmModule(),
                fileType.getCodeGenFileType(),
                errorMessage,
                bufferRef) != 0) {

                String error = errorMessage.getString();
                throw new IrException("Failed to emit code: " + error);
            }

            // Get the memory buffer content as a string
            BytePointer contentPtr = LLVM.LLVMGetBufferStart(bufferRef);
            long size = LLVM.LLVMGetBufferSize(bufferRef);

            byte[] bytes = new byte[(int) size];
            contentPtr.get(bytes);

            return new String(bytes);
        } finally {
            LLVM.LLVMDisposeMessage(errorMessage);
        }
    }

    @Override
    public void close() {
        if (!disposed) {
            LLVM.LLVMDisposeTargetMachine(machineRef);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}