package com.reso.compiler.codegen.statements;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.codegen.common.LoopContext;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for break statements in loops.
 *
 * <p>This generator handles the creation of LLVM IR for break statements,
 * which transfer control to the end of the nearest enclosing loop.
 */
public class BreakStatementGenerator {

    private static final String ERROR_BREAK_OUTSIDE_LOOP = "Break statement outside of loop";

    private final CodeGenerationContext context;

    /**
     * Creates a new break statement generator.
     *
     * @param context The code generation context, must not be null
     * @throws NullPointerException if context is null
     */
    public BreakStatementGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates LLVM IR code for a break statement.
     *
     * <p>The break statement transfers control to the break target of the
     * innermost enclosing loop. If no loop context exists, an error is reported.
     *
     * @param breakStmt The break statement context from the parser, may be null
     * @return Always null, as break statements don't produce a value
     */
    @Nullable
    public ResoValue generateBreak(@Nullable ResoParser.BreakStatementContext breakStmt) {
        if (!isValidBreakStatement(breakStmt)) {
            return null;
        }

        if (!isWithinLoop()) {
            reportBreakOutsideLoopError(breakStmt);
            return null;
        }

        generateBreakInstruction();
        return null;
    }

    /**
     * Validates that the break statement context is not null.
     *
     * @param breakStmt The break statement context to validate
     * @return true if the statement is valid, false otherwise
     */
    private boolean isValidBreakStatement(@Nullable ResoParser.BreakStatementContext breakStmt) {
        return breakStmt != null;
    }

    /**
     * Checks if the current context is within a loop.
     *
     * @return true if currently within a loop, false otherwise
     */
    private boolean isWithinLoop() {
        return !context.getLoopContexts().isEmpty();
    }

    /**
     * Reports an error for a break statement that appears outside of a loop.
     *
     * @param breakStmt The break statement context for error location
     */
    private void reportBreakOutsideLoopError(@Nonnull ResoParser.BreakStatementContext breakStmt) {
        int line = breakStmt.getStart().getLine();
        int column = breakStmt.getStart().getCharPositionInLine();
        context.getErrorReporter().error(ERROR_BREAK_OUTSIDE_LOOP, line, column);
    }

    /**
     * Generates the actual LLVM IR instruction for the break statement.
     *
     * <p>Creates an unconditional branch to the break target of the
     * innermost loop context.
     */
    private void generateBreakInstruction() {
        LoopContext innermostLoop = context.getLoopContexts().peek();
        IrBasicBlock breakTarget = innermostLoop.breakBlock();
        IrFactory.createBr(context.getIrBuilder(), breakTarget);
    }
}