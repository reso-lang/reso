package com.reso.compiler.codegen.statements;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.api.IrBasicBlock;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for statements.
 */
public class StatementGenerator {
    private final CodeGenerationContext context;
    private final VariableGenerator variableGenerator;
    private final AssignmentGenerator assignmentGenerator;
    private final IfStatementGenerator ifStatementGenerator;
    private final WhileStatementGenerator whileStatementGenerator;
    private final ReturnStatementGenerator returnStatementGenerator;
    private final BreakStatementGenerator breakStatementGenerator;
    private final ContinueStatementGenerator continueStatementGenerator;
    private final BlockGenerator blockGenerator;

    /**
     * Creates a new statement generator.
     *
     * @param context The code generation context
     */
    public StatementGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");

        // Initialize specialized generators
        this.variableGenerator = new VariableGenerator(context);
        this.assignmentGenerator = new AssignmentGenerator(context);
        this.ifStatementGenerator = new IfStatementGenerator(context);
        this.whileStatementGenerator = new WhileStatementGenerator(context);
        this.returnStatementGenerator = new ReturnStatementGenerator(context);
        this.breakStatementGenerator = new BreakStatementGenerator(context);
        this.continueStatementGenerator = new ContinueStatementGenerator(context);
        this.blockGenerator = new BlockGenerator(context);
    }

    /**
     * Visits a statement.
     *
     * @param stmt The statement context
     * @return The result of the statement
     */
    @Nullable
    public ResoValue visitStatement(@Nullable ResoParser.StatementContext stmt) {
        if (stmt == null) {
            return null;
        }

        if (stmt.simple_stmts() != null) {
            return visitSimpleStatements(stmt.simple_stmts());
        } else if (stmt.compound_stmt() != null) {
            return visitCompoundStatement(stmt.compound_stmt());
        }

        context.getErrorReporter().error("Unsupported statement type",
            stmt.getStart().getLine(), stmt.getStart().getCharPositionInLine());
        return null;
    }

    /**
     * Visits simple statements.
     *
     * @param stmts The simple statements context
     * @return The result of the last statement
     */
    @Nullable
    public ResoValue visitSimpleStatements(@Nullable ResoParser.Simple_stmtsContext stmts) {
        if (stmts == null) {
            return null;
        }

        ResoValue result = null;

        for (ResoParser.Simple_stmtContext stmt : stmts.simple_stmt()) {
            result = visitSimpleStatement(stmt);

            // Check if this statement added a terminator (like return)
            if (context.getIrBuilder().getCurrentBlock() != null
                && context.getIrBuilder().getCurrentBlock().getTerminator() != null) {
                break;
            }
        }

        return result;
    }

    /**
     * Visits a simple statement.
     *
     * @param stmt The simple statement context
     * @return The result of the statement
     */
    @Nullable
    public ResoValue visitSimpleStatement(@Nullable ResoParser.Simple_stmtContext stmt) {
        if (stmt == null) {
            return null;
        }

        if (stmt.variableDeclaration() != null) {
            return variableGenerator.generateVariable(stmt.variableDeclaration());
        } else if (stmt.assignmentStatement() != null) {
            return assignmentGenerator.generateAssignment(stmt.assignmentStatement());
        } else if (stmt.expressionStatement() != null) {
            ResoValue expressionValue =
                context.getExpressionGenerator().visit(stmt.expressionStatement().expression());

            if (expressionValue != null) {
                if (expressionValue.isUntyped() && expressionValue.isReference()) {
                    context.getErrorReporter().error("Cannot infere type of expression result",
                        stmt.getStart().getLine(), stmt.getStart().getCharPositionInLine());
                    return null;
                } else {
                    expressionValue =
                        expressionValue.concretizeToDefault(context.getErrorReporter());
                }
            }

            return expressionValue;
        } else if (stmt.returnStatement() != null) {
            return returnStatementGenerator.generateReturn(stmt.returnStatement());
        } else if (stmt.breakStatement() != null) {
            return breakStatementGenerator.generateBreak(stmt.breakStatement());
        } else if (stmt.continueStatement() != null) {
            return continueStatementGenerator.generateContinue(stmt.continueStatement());
        } else if (stmt.pass_stmt() != null) {
            // 'pass' does nothing
            return null;
        }

        context.getErrorReporter().error("Unsupported simple statement type",
            stmt.getStart().getLine(), stmt.getStart().getCharPositionInLine());
        return null;
    }

    /**
     * Visits a compound statement.
     *
     * @param stmt The compound statement context
     * @return The result of the statement
     */
    @Nullable
    public ResoValue visitCompoundStatement(@Nullable ResoParser.Compound_stmtContext stmt) {
        if (stmt == null) {
            return null;
        }

        if (stmt.ifStatement() != null) {
            return ifStatementGenerator.generateIf(stmt.ifStatement());
        } else if (stmt.whileStatement() != null) {
            return whileStatementGenerator.generateWhile(stmt.whileStatement());
        }

        context.getErrorReporter().error("Unsupported compound statement type",
            stmt.getStart().getLine(), stmt.getStart().getCharPositionInLine());
        return null;
    }

    /**
     * Generates code for a block.
     *
     * @param block    The block context
     * @param endBlock The end block to branch to after the block
     * @return The result of the block
     */
    @Nullable
    public ResoValue generateBlock(
        @Nullable ResoParser.BlockContext block,
        @Nullable IrBasicBlock endBlock) {

        if (block == null) {
            return null;
        }

        return blockGenerator.generateBlock(block, endBlock);
    }
}