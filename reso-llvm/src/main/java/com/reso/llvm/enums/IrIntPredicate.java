package com.reso.llvm.enums;

/**
 * Enumeration of integer comparison predicates for LLVM.
 */
public enum IrIntPredicate {
    /**
     * Equal.
     */
    EQ(org.bytedeco.llvm.global.LLVM.LLVMIntEQ),

    /**
     * Not equal.
     */
    NE(org.bytedeco.llvm.global.LLVM.LLVMIntNE),

    /**
     * Unsigned greater than.
     */
    UGT(org.bytedeco.llvm.global.LLVM.LLVMIntUGT),

    /**
     * Unsigned greater than or equal.
     */
    UGE(org.bytedeco.llvm.global.LLVM.LLVMIntUGE),

    /**
     * Unsigned less than.
     */
    ULT(org.bytedeco.llvm.global.LLVM.LLVMIntULT),

    /**
     * Unsigned less than or equal.
     */
    ULE(org.bytedeco.llvm.global.LLVM.LLVMIntULE),

    /**
     * Signed greater than.
     */
    SGT(org.bytedeco.llvm.global.LLVM.LLVMIntSGT),

    /**
     * Signed greater than or equal.
     */
    SGE(org.bytedeco.llvm.global.LLVM.LLVMIntSGE),

    /**
     * Signed less than.
     */
    SLT(org.bytedeco.llvm.global.LLVM.LLVMIntSLT),

    /**
     * Signed less than or equal.
     */
    SLE(org.bytedeco.llvm.global.LLVM.LLVMIntSLE);

    private final int predicate;

    /**
     * Constructs a ResoIntPredicate with the specified LLVM predicate.
     *
     * @param predicate The LLVM predicate
     */
    IrIntPredicate(int predicate) {
        this.predicate = predicate;
    }

    /**
     * Gets the LLVM predicate.
     *
     * @return The LLVM predicate
     */
    public int getPredicate() {
        return predicate;
    }
}