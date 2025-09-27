package com.reso.llvm.util;

import org.bytedeco.llvm.global.LLVM;

/**
 * Utility class for initializing LLVM.
 */
public final class IrInitializer {
    private static boolean initialized = false;
    private static boolean targetInitialized = false;

    private IrInitializer() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Initializes the LLVM environment.
     * This must be called before any LLVM functions are used.
     * This method is idempotent and can be called multiple times.
     */
    public static synchronized void initializeLlvm() {
        if (!initialized) {
            LLVM.LLVMLinkInMCJIT();
            LLVM.LLVMInitializeNativeAsmPrinter();
            LLVM.LLVMInitializeNativeAsmParser();
            LLVM.LLVMInitializeNativeTarget();
            initialized = true;
        }
    }

    /**
     * Initializes target machine components.
     * This is needed for target-specific code generation.
     * This method is idempotent and can be called multiple times.
     */
    public static synchronized void initializeTargetMachine() {
        // Ensure base LLVM is initialized first
        initializeLlvm();

        if (!targetInitialized) {
            // Initialize all targets
            LLVM.LLVMInitializeAllTargetInfos();
            LLVM.LLVMInitializeAllTargets();
            LLVM.LLVMInitializeAllTargetMCs();
            LLVM.LLVMInitializeAllAsmParsers();
            LLVM.LLVMInitializeAllAsmPrinters();
            targetInitialized = true;
        }
    }

    /**
     * Checks if LLVM has been initialized.
     *
     * @return true if LLVM has been initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if target machine components have been initialized.
     *
     * @return true if target machine components have been initialized
     */
    public static boolean isTargetInitialized() {
        return targetInitialized;
    }
}