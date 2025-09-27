package com.reso.compiler.codegen.statements;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for code blocks.
 * This class handles the generation of LLVM IR code for different types of blocks:
 * - Simple statement blocks (single line)
 * - Complex statement blocks (multiple statements)
 * - Empty blocks
 */
public class BlockGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new block generator.
     *
     * @param context The code generation context
     * @throws NullPointerException if context is null
     */
    public BlockGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a block.
     *
     * @param block    The block context to generate code for
     * @param endBlock The end block to branch to after the block (may be null)
     * @return The result of the block (usually null for most block types)
     */
    @Nullable
    public ResoValue generateBlock(
        @Nullable ResoParser.BlockContext block,
        @Nullable IrBasicBlock endBlock) {

        if (block == null) {
            return handleEmptyBlock(endBlock);
        }

        if (hasSimpleStatements(block)) {
            return generateSimpleStatementsBlock(block, endBlock);
        }

        if (hasMultipleStatements(block)) {
            return generateMultipleStatementsBlock(block, endBlock);
        }

        return handleEmptyBlock(endBlock);
    }

    /**
     * Checks if the block contains simple statements.
     */
    private boolean hasSimpleStatements(@Nonnull ResoParser.BlockContext block) {
        return block.simple_stmts() != null;
    }

    /**
     * Checks if the block contains multiple statements.
     */
    private boolean hasMultipleStatements(@Nonnull ResoParser.BlockContext block) {
        return block.statement() != null && !block.statement().isEmpty();
    }

    /**
     * Generates code for a block with simple statements (single line).
     */
    @Nullable
    private ResoValue generateSimpleStatementsBlock(
        @Nonnull ResoParser.BlockContext block,
        @Nullable IrBasicBlock endBlock) {

        ResoValue result =
            context.getStatementGenerator().visitSimpleStatements(block.simple_stmts());
        addBranchToEndBlockIfNeeded(endBlock);
        return result;
    }

    /**
     * Generates code for a block with multiple statements.
     */
    @Nullable
    private ResoValue generateMultipleStatementsBlock(
        @Nonnull ResoParser.BlockContext block,
        @Nullable IrBasicBlock endBlock) {

        List<ResoParser.StatementContext> statements = block.statement();
        ResoValue lastResult = null;

        for (int i = 0; i < statements.size(); i++) {
            ResoParser.StatementContext currentStatement = statements.get(i);
            lastResult = context.getStatementGenerator().visitStatement(currentStatement);

            if (hasTerminator()) {
                handleUnreachableCodeIfExists(statements, i);
                break;
            }
        }

        addBranchToEndBlockIfNeeded(endBlock);
        return lastResult;
    }

    /**
     * Handles an empty block by branching to the end block if provided.
     */
    @Nullable
    private ResoValue handleEmptyBlock(@Nullable IrBasicBlock endBlock) {
        addBranchToEndBlockIfNeeded(endBlock);
        return null;
    }

    /**
     * Checks if the current block has a terminator instruction.
     */
    private boolean hasTerminator() {
        IrBasicBlock currentBlock = getCurrentBlock();
        return currentBlock != null && currentBlock.getTerminator() != null;
    }

    /**
     * Gets the current basic block from the IR builder.
     */
    @Nullable
    private IrBasicBlock getCurrentBlock() {
        return context.getIrBuilder().getCurrentBlock();
    }

    /**
     * Adds a branch to the end block if no terminator exists and end block is provided.
     */
    private void addBranchToEndBlockIfNeeded(@Nullable IrBasicBlock endBlock) {
        if (endBlock != null && !hasTerminator()) {
            IrFactory.createBr(context.getIrBuilder(), endBlock);
        }
    }

    /**
     * Reports unreachable code warnings for statements after a terminator.
     */
    private void handleUnreachableCodeIfExists(
        @Nonnull List<ResoParser.StatementContext> statements,
        int currentIndex) {

        if (hasMoreStatements(statements, currentIndex)) {
            ResoParser.StatementContext nextStatement = statements.get(currentIndex + 1);
            reportUnreachableCodeWarning(nextStatement);
        }
    }

    /**
     * Checks if there are more statements after the given index.
     */
    private boolean hasMoreStatements(
        @Nonnull List<ResoParser.StatementContext> statements,
        int currentIndex) {
        return currentIndex < statements.size() - 1;
    }

    /**
     * Reports an unreachable code warning for the given statement.
     */
    private void reportUnreachableCodeWarning(@Nonnull ResoParser.StatementContext statement) {
        context.getErrorReporter().warning(
            "Unreachable code",
            statement.getStart().getLine(),
            statement.getStart().getCharPositionInLine());
    }
}