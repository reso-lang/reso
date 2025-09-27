package com.reso.llvm.api;

import javax.annotation.Nonnull;

/**
 * Public interface for LLVM target operations.
 */
public interface IrTarget {

    /**
     * Checks if this target has JIT capability.
     *
     * @return true if this target has JIT capability
     */
    boolean hasJit();

    /**
     * Checks if this target has target machine capability.
     *
     * @return true if this target has target machine capability
     */
    boolean hasTargetMachine();

    /**
     * Checks if this target has ASM backend capability.
     *
     * @return true if this target has ASM backend capability
     */
    boolean hasAsmBackend();

    /**
     * Gets the target name.
     *
     * @return The target name
     */
    @Nonnull
    String getName();

    /**
     * Gets the target description.
     *
     * @return The target description
     */
    @Nonnull
    String getDescription();
}