package com.reso.compiler.codegen.functions;

import com.reso.grammar.ResoParser;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Utility for checking if an if statement has returns in all branches.
 */
public final class IfStatementChecker {
    private IfStatementChecker() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks if an if statement has return statements in all branches.
     *
     * @param ifStmt The if statement context
     * @return true if all branches have return statements
     */
    public static boolean allBranchesReturn(@Nonnull ResoParser.IfStatementContext ifStmt) {
        Objects.requireNonNull(ifStmt, "If statement cannot be null");

        // Get all expressions and blocks from the if statement
        List<ResoParser.ExpressionContext> expressions = ifStmt.expression();
        List<ResoParser.BlockContext> blocks = ifStmt.block();

        // For all branches to return, we need an else branch
        boolean hasElse = blocks.size() > expressions.size();
        if (!hasElse) {
            return false; // Without an else, not all paths return
        }

        // Check that each block has a return statement
        for (ResoParser.BlockContext block : blocks) {
            if (!blockHasReturn(block)) {
                return false; // One branch doesn't have a return
            }
        }

        return true; // All branches have returns
    }

    /**
     * Checks if a block has a return statement or an if statement where all branches return.
     *
     * @param block The block context
     * @return true if the block has a return statement
     */
    private static boolean blockHasReturn(@Nonnull ResoParser.BlockContext block) {
        Objects.requireNonNull(block, "Block cannot be null");

        // Check for simple statements
        if (block.simple_stmts() != null) {
            for (ResoParser.Simple_stmtContext stmt : block.simple_stmts().simple_stmt()) {
                if (stmt.returnStatement() != null) {
                    return true;
                }
            }
            return false;
        }

        // Check for regular statements in indented block
        for (ResoParser.StatementContext stmt : block.statement()) {
            // Check if this is a simple_stmts with a return
            if (stmt.simple_stmts() != null) {
                for (ResoParser.Simple_stmtContext simpleStmt : stmt.simple_stmts().simple_stmt()) {
                    if (simpleStmt.returnStatement() != null) {
                        return true;
                    }
                }
            } else if (stmt.compound_stmt() != null
                && stmt.compound_stmt().ifStatement() != null
                && allBranchesReturn(stmt.compound_stmt().ifStatement())) {
                return true;
            }
        }

        return false;
    }
}