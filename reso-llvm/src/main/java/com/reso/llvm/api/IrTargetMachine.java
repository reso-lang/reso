package com.reso.llvm.api;

import com.reso.llvm.enums.IrCodeGenFileType;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nonnull;

/**
 * Public interface for LLVM target machine operations.
 */
public interface IrTargetMachine extends Closeable {

    /**
     * Gets the target triple as a string.
     *
     * @return The target triple
     * @throws IllegalStateException if the target machine has been disposed
     */
    @Nonnull
    String getTriple();

    /**
     * Gets the data layout as a string.
     *
     * @return The data layout
     * @throws IllegalStateException if the target machine has been disposed
     */
    @Nonnull
    String getDataLayout();

    /**
     * Emits target code to a file.
     *
     * @param module     The module to emit code for
     * @param outputPath The path to write the output to
     * @param fileType   The type of file to emit
     * @throws IllegalStateException if the target machine or module has been disposed
     * @throws IOException           if an I/O error occurs
     */
    void emitToFile(
        @Nonnull IrModule module,
        @Nonnull Path outputPath,
        @Nonnull IrCodeGenFileType fileType) throws IOException;

    /**
     * Emits target code to a memory buffer.
     *
     * @param module   The module to emit code for
     * @param fileType The type of file to emit
     * @return The emitted code as a string
     * @throws IllegalStateException if the target machine or module has been disposed
     */
    @Nonnull
    String emitToMemory(@Nonnull IrModule module, @Nonnull IrCodeGenFileType fileType);

    /**
     * Closes this target machine and disposes any native resources.
     */
    @Override
    void close();

    /**
     * Checks if this target machine has been disposed.
     *
     * @return true if this target machine has been disposed
     */
    boolean isDisposed();
}