package com.reso.compiler.codegen.statements;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.codegen.common.LoopContext;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for while statements.
 */
public class WhileStatementGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new while statement generator.
     *
     * @param context The code generation context
     */
    public WhileStatementGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a while statement.
     *
     * @param whileStmt The while statement context
     * @return Always null (while statements don't return a value)
     */
    @Nullable
    public ResoValue generateWhile(@Nullable ResoParser.WhileStatementContext whileStmt) {
        if (whileStmt == null) {
            return null;
        }

        int line = whileStmt.getStart().getLine();
        int column = whileStmt.getStart().getCharPositionInLine();

        // Create basic blocks for the while loop
        IrBasicBlock condBlock = IrFactory.createBasicBlock(
            context.getCurrentFunction(), "while_cond");

        // Branch to the condition block from the current block
        IrFactory.createBr(context.getIrBuilder(), condBlock);

        // Position at the condition block
        IrFactory.positionAtEnd(context.getIrBuilder(), condBlock);

        // Evaluate the condition
        ResoValue condition = context.getExpressionGenerator().visit(whileStmt.expression());

        ConcreteResoValue concreteCondition = condition != null
            ? condition.concretize(context.getTypeSystem().getBoolType(),
                context.getErrorReporter()) : null;

        // Check that the condition is a boolean
        if (concreteCondition == null) {
            context.getErrorReporter().error(
                "While condition must be a boolean expression", line, column);
            return null;
        }

        IrBasicBlock bodyBlock = IrFactory.createBasicBlock(
            context.getCurrentFunction(), "while_body");
        IrBasicBlock endBlock = IrFactory.createBasicBlock(
            context.getCurrentFunction(), "while_end");

        // Create the conditional branch
        IrFactory.createCondBr(
            context.getIrBuilder(),
            concreteCondition.getValue(),
            bodyBlock,
            endBlock);

        // Position at the body block
        IrFactory.positionAtEnd(context.getIrBuilder(), bodyBlock);

        // Push loop context for break/continue statements
        LoopContext loopContext = new LoopContext(condBlock, endBlock);
        context.getLoopContexts().push(loopContext);

        // Enter a new scope for the loop body
        context.getSymbolTable().enterScope();

        // Process the block
        context.getStatementGenerator().generateBlock(whileStmt.block(), null);

        // Exit the scope
        context.getSymbolTable().exitScope(context.getErrorReporter(), line, column);

        // Pop the loop context
        context.getLoopContexts().pop();

        // Branch back to condition block if not terminated
        IrBasicBlock currentBlock = context.getIrBuilder().getCurrentBlock();
        if (currentBlock != null && currentBlock.getTerminator() == null) {
            IrFactory.createBr(context.getIrBuilder(), condBlock);
        }

        // Position at the end block
        IrFactory.positionAtEnd(context.getIrBuilder(), endBlock);

        // Return null since while statements have no value
        return null;
    }
}