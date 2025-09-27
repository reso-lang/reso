package com.reso.llvm.enums;

import org.bytedeco.llvm.global.LLVM;

/**
 * Enumeration of code generation file types for LLVM.
 */
public enum IrCodeGenFileType {
    /**
     * Assembly file.
     */
    ASSEMBLY(LLVM.LLVMAssemblyFile),

    /**
     * Object file.
     */
    OBJECT(LLVM.LLVMObjectFile);

    private final int codeGenFileType;

    /**
     * Constructs a ResoCodeGenFileType with the specified LLVM code generation file type.
     *
     * @param codeGenFileType The LLVM code generation file type
     */
    IrCodeGenFileType(int codeGenFileType) {
        this.codeGenFileType = codeGenFileType;
    }

    /**
     * Gets the LLVM code generation file type.
     *
     * @return The LLVM code generation file type
     */
    public int getCodeGenFileType() {
        return codeGenFileType;
    }
}