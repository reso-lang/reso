package com.reso.compiler.codegen.statements;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.codegen.common.LoopContext;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for continue statements in loop constructs.
 *
 * <p>This generator handles the creation of LLVM IR code for continue statements,
 * which transfer control to the beginning of the innermost enclosing loop.
 * Continue statements are only valid within loop contexts.</p>
 *
 * <p>The generator performs the following operations:</p>
 * <ul>
 *   <li>Validates that the continue statement is within a loop context</li>
 *   <li>Generates appropriate error messages for invalid usage</li>
 *   <li>Creates LLVM IR branch instructions to the loop's continue target</li>
 * </ul>
 */
public class ContinueStatementGenerator {

    private static final String ERROR_CONTINUE_OUTSIDE_LOOP = "Continue statement outside of loop";

    private final CodeGenerationContext context;

    /**
     * Creates a new continue statement generator.
     *
     * @param context The code generation context
     * @throws NullPointerException if context is null
     */
    public ContinueStatementGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates LLVM IR code for a continue statement.
     *
     * <p>This method validates the continue statement context and generates
     * the appropriate IR instructions. Continue statements always transfer
     * control and never return a value.</p>
     *
     * @param statement The continue statement parse tree context
     * @return Always null (continue statements don't produce values)
     */
    @Nullable
    public ResoValue generateContinue(@Nullable ResoParser.ContinueStatementContext statement) {
        if (!isValidStatement(statement)) {
            return null;
        }

        if (!isInLoopContext()) {
            reportLoopContextError(statement);
            return null;
        }

        generateContinueBranch();
        return null;
    }

    /**
     * Validates that the statement context is not null.
     *
     * @param statement The statement to validate
     * @return true if the statement is valid, false otherwise
     */
    private boolean isValidStatement(@Nullable ResoParser.ContinueStatementContext statement) {
        return statement != null;
    }

    /**
     * Checks if the current code generation context is within a loop.
     *
     * @return true if currently in a loop context, false otherwise
     */
    private boolean isInLoopContext() {
        return !context.getLoopContexts().isEmpty();
    }

    /**
     * Reports an error for continue statements used outside of loop contexts.
     *
     * @param statement The statement causing the error (used for location information)
     */
    private void reportLoopContextError(@Nonnull ResoParser.ContinueStatementContext statement) {
        int line = statement.getStart().getLine();
        int column = statement.getStart().getCharPositionInLine();

        ErrorReporter errorReporter = context.getErrorReporter();
        errorReporter.error(ERROR_CONTINUE_OUTSIDE_LOOP, line, column);
    }

    /**
     * Generates the LLVM IR branch instruction to the continue target.
     *
     * <p>This method retrieves the innermost loop context and creates
     * an unconditional branch to the loop's continue block.</p>
     */
    private void generateContinueBranch() {
        LoopContext loopContext = getInnermostLoopContext();
        IrBasicBlock continueTarget = loopContext.continueBlock();

        IrFactory.createBr(context.getIrBuilder(), continueTarget);
    }

    /**
     * Retrieves the innermost loop context from the context stack.
     *
     * <p>This method assumes that {@link #isInLoopContext()} has already
     * been called to ensure that the loop context stack is not empty.</p>
     *
     * @return The innermost loop context
     * @throws java.util.EmptyStackException if no loop context exists
     */
    @Nonnull
    private LoopContext getInnermostLoopContext() {
        return context.getLoopContexts().peek();
    }
}