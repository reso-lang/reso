package com.reso.compiler.codegen.statements;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for if statements.
 */
public class IfStatementGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new if statement generator.
     *
     * @param context The code generation context
     */
    public IfStatementGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for an if statement.
     *
     * @param ifStmt The if statement context
     * @return Always null (if statements don't return a value)
     */
    @Nullable
    public ResoValue generateIf(@Nullable ResoParser.IfStatementContext ifStmt) {
        if (ifStmt == null) {
            return null;
        }

        int line = ifStmt.getStart().getLine();
        int column = ifStmt.getStart().getCharPositionInLine();

        // Get all expressions and blocks
        List<ResoParser.ExpressionContext> expressions = ifStmt.expression();
        List<ResoParser.BlockContext> blocks = ifStmt.block();

        // Determine if there's an else block
        boolean hasElse = blocks.size() > expressions.size();

        // Create an end block where all branches will converge (unless all branches return)
        IrBasicBlock endBlock = null;
        if (!com.reso.compiler.codegen.functions.IfStatementChecker.allBranchesReturn(ifStmt)) {
            endBlock = IrFactory.createBasicBlock(context.getCurrentFunction(), "if_end");
        }

        // Current block where we'll start the first if condition
        IrBasicBlock currentBlock = context.getIrBuilder().getCurrentBlock();

        // Process the main if condition and all else-if conditions
        for (int i = 0; i < expressions.size(); i++) {
            // Create blocks for this condition
            IrBasicBlock thenBlock = IrFactory.createBasicBlock(
                context.getCurrentFunction(),
                i == 0 ? "if_then" : "elseif_then_" + i);

            // Create the next block where we'll go if this condition is false
            IrBasicBlock nextBlock;

            if (i == expressions.size() - 1) {
                // Last condition - next block is either else or end
                nextBlock = hasElse
                    ? IrFactory.createBasicBlock(context.getCurrentFunction(), "else") :
                    endBlock;
            } else {
                // Not last condition - next block is the next condition
                nextBlock = IrFactory.createBasicBlock(
                    context.getCurrentFunction(),
                    "elseif_cond_" + (i + 1));
            }

            // Position at the current block
            IrFactory.positionAtEnd(context.getIrBuilder(), currentBlock);

            // Evaluate the condition
            ResoValue condition = context.getExpressionGenerator().visit(expressions.get(i));

            ConcreteResoValue concretizedCondition = condition != null ? condition.concretize(
                context.getTypeSystem().getBoolType(),
                context.getErrorReporter()) : null;

            // Check that the condition is a boolean
            if (concretizedCondition == null) {
                context.getErrorReporter().error(
                    (i == 0 ? "If" : "Else-if") + " condition must be a boolean expression",
                    line, column);
                return null;
            }

            // Create the conditional branch
            IrFactory.createCondBr(
                context.getIrBuilder(),
                concretizedCondition.getValue(),
                thenBlock,
                nextBlock);

            // Generate code for the then block
            IrFactory.positionAtEnd(context.getIrBuilder(), thenBlock);

            // Enter a new scope for the block
            context.getSymbolTable().enterScope();

            // Process the block
            context.getStatementGenerator().generateBlock(blocks.get(i), endBlock);

            // Exit the scope
            context.getSymbolTable().exitScope(context.getErrorReporter(), line, column);

            // Update current block for next iteration
            currentBlock = nextBlock;
        }

        // Process the else block if it exists
        if (hasElse) {
            // Position at the else block
            IrFactory.positionAtEnd(context.getIrBuilder(), currentBlock);

            // Enter a new scope for the else block
            context.getSymbolTable().enterScope();

            // Process the else block
            context.getStatementGenerator().generateBlock(blocks.getLast(), endBlock);

            // Exit the scope
            context.getSymbolTable().exitScope(context.getErrorReporter(), line, column);
        }

        // Position at the end block if it exists
        if (endBlock != null) {
            IrFactory.positionAtEnd(context.getIrBuilder(), endBlock);
        }

        // Return null since if statements have no value
        return null;
    }
}