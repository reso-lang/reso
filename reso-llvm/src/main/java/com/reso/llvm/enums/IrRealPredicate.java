package com.reso.llvm.enums;

/**
 * Enumeration of floating-point comparison predicates for LLVM.
 */
public enum IrRealPredicate {
    /**
     * False - always returns false.
     */
    FALSE(org.bytedeco.llvm.global.LLVM.LLVMRealPredicateFalse),

    /**
     * Equal (ordered).
     */
    OEQ(org.bytedeco.llvm.global.LLVM.LLVMRealOEQ),

    /**
     * Greater than (ordered).
     */
    OGT(org.bytedeco.llvm.global.LLVM.LLVMRealOGT),

    /**
     * Greater than or equal (ordered).
     */
    OGE(org.bytedeco.llvm.global.LLVM.LLVMRealOGE),

    /**
     * Less than (ordered).
     */
    OLT(org.bytedeco.llvm.global.LLVM.LLVMRealOLT),

    /**
     * Less than or equal (ordered).
     */
    OLE(org.bytedeco.llvm.global.LLVM.LLVMRealOLE),

    /**
     * Not equal (ordered).
     */
    ONE(org.bytedeco.llvm.global.LLVM.LLVMRealONE),

    /**
     * Ordered (no NaN).
     */
    ORD(org.bytedeco.llvm.global.LLVM.LLVMRealORD),

    /**
     * Unordered (either operand is a NaN).
     */
    UNO(org.bytedeco.llvm.global.LLVM.LLVMRealUNO),

    /**
     * Equal (unordered).
     */
    UEQ(org.bytedeco.llvm.global.LLVM.LLVMRealUEQ),

    /**
     * Greater than (unordered).
     */
    UGT(org.bytedeco.llvm.global.LLVM.LLVMRealUGT),

    /**
     * Greater than or equal (unordered).
     */
    UGE(org.bytedeco.llvm.global.LLVM.LLVMRealUGE),

    /**
     * Less than (unordered).
     */
    ULT(org.bytedeco.llvm.global.LLVM.LLVMRealULT),

    /**
     * Less than or equal (unordered).
     */
    ULE(org.bytedeco.llvm.global.LLVM.LLVMRealULE),

    /**
     * Not equal (unordered).
     */
    UNE(org.bytedeco.llvm.global.LLVM.LLVMRealUNE),

    /**
     * True - always returns true.
     */
    TRUE(org.bytedeco.llvm.global.LLVM.LLVMRealPredicateTrue);

    private final int predicate;

    /**
     * Constructs a ResoRealPredicate with the specified LLVM predicate.
     *
     * @param predicate The LLVM predicate
     */
    IrRealPredicate(int predicate) {
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