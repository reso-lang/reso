package com.reso.compiler.codegen.common;

import com.reso.llvm.api.IrBasicBlock;
import javax.annotation.Nonnull;

/**
 * Loop context for break and continue statements.
 *
 * @param continueBlock Block to jump to for continue statements
 * @param breakBlock    Block to jump to for break statements
 */
public record LoopContext(
    @Nonnull IrBasicBlock continueBlock,
    @Nonnull IrBasicBlock breakBlock) {
}