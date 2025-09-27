package com.reso.llvm.exception;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Exception thrown when an LLVM operation fails.
 */
public class IrException extends RuntimeException {
    /**
     * Creates a new LLVM exception with a detail message.
     *
     * @param message The detail message
     */
    public IrException(@Nonnull String message) {
        super(message);
    }

    /**
     * Creates a new LLVM exception with a detail message and cause.
     *
     * @param message The detail message
     * @param cause   The exception cause
     */
    public IrException(@Nonnull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}