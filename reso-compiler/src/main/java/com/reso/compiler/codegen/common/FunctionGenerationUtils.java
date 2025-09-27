package com.reso.compiler.codegen.common;

import com.reso.compiler.codegen.functions.IfStatementChecker;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class for common method/function generation tasks.
 */
public final class FunctionGenerationUtils {
    private static final String UNIT_TYPE_NAME = "()";

    private FunctionGenerationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates an alloca instruction and stores a parameter value into it.
     * This is the standard pattern for handling function/method parameters.
     *
     * @param context    The code generation context
     * @param paramName  The parameter name
     * @param paramType  The parameter type
     * @param paramValue The parameter value to store
     * @return The alloca instruction
     */
    @Nonnull
    public static IrValue createParameterAlloca(
        @Nonnull CodeGenerationContext context,
        @Nonnull String paramName,
        @Nonnull ResoType paramType,
        @Nonnull IrValue paramValue) {

        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(paramName, "Parameter name cannot be null");
        Objects.requireNonNull(paramType, "Parameter type cannot be null");
        Objects.requireNonNull(paramValue, "Parameter value cannot be null");

        // Create alloca for parameter
        IrValue paramAlloca = IrFactory.createAlloca(
            context.getIrBuilder(), paramType.getType(), paramName);

        // Store parameter value into alloca
        IrFactory.createStore(context.getIrBuilder(), paramValue, paramAlloca);

        return paramAlloca;
    }

    /**
     * Processes simple statements in a method/function body.
     *
     * @param context     The code generation context
     * @param simpleStmts The simple statements context
     */
    public static void processSimpleStatements(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.Simple_stmtsContext simpleStmts) {

        for (ResoParser.Simple_stmtContext stmt : simpleStmts.simple_stmt()) {
            context.getStatementGenerator().visitSimpleStatement(stmt);

            if (hasTerminator(context.getIrBuilder())) {
                break;
            }
        }
    }

    /**
     * Processes block statements and returns whether the block ends with a returning if.
     *
     * @param context    The code generation context
     * @param statements The block statements
     * @return true if the block ends with a returning if statement
     */
    public static boolean processBlockStatements(
        @Nonnull CodeGenerationContext context,
        @Nonnull List<ResoParser.StatementContext> statements) {

        for (int i = 0; i < statements.size(); i++) {
            ResoParser.StatementContext stmtCtx = statements.get(i);

            context.getStatementGenerator().visitStatement(stmtCtx);

            if (hasTerminator(context.getIrBuilder())) {
                reportUnreachableCode(context.getErrorReporter(), statements, i);
                break;
            }

            if (isReturningIfStatement(stmtCtx)) {
                reportUnreachableCode(context.getErrorReporter(), statements, i);
                return true;
            }
        }

        // Check if the last statement is a returning if
        if (!statements.isEmpty()) {
            ResoParser.StatementContext lastStmt = statements.getLast();
            return isReturningIfStatement(lastStmt);
        }

        return false;
    }

    /**
     * Handles return statement requirements and adds implicit returns if needed.
     *
     * @param context             The code generation context
     * @param name                The method/function name
     * @param returnType          The return type
     * @param endsWithReturningIf Whether it ends with a returning if
     * @param line                Line number for error reporting
     * @param column              Column number for error reporting
     */
    public static void handleReturn(
        @Nonnull CodeGenerationContext context,
        @Nonnull String name,
        @Nonnull ResoType returnType,
        boolean endsWithReturningIf,
        int line,
        int column) {

        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(returnType, "Return type cannot be null");

        if (!hasTerminator(context.getIrBuilder())) {
            addImplicitReturn(context, name, returnType, endsWithReturningIf, line, column);
        }
    }

    /**
     * Checks if the current basic block has a terminator instruction.
     *
     * @param builder The IR builder
     * @return true if the current block has a terminator
     */
    public static boolean hasTerminator(@Nonnull IrBuilder builder) {
        IrBasicBlock currentBlock = builder.getCurrentBlock();
        return currentBlock != null && currentBlock.getTerminator() != null;
    }

    /**
     * Adds an implicit return if needed.
     */
    private static void addImplicitReturn(
        @Nonnull CodeGenerationContext context,
        @Nonnull String name,
        @Nonnull ResoType returnType,
        boolean endsWithReturningIf,
        int line,
        int column) {

        if (endsWithReturningIf) {
            // All branches return, no additional return needed
            return;
        }

        if (UNIT_TYPE_NAME.equals(returnType.getName())) {
            addUnitReturn(context);
        } else {
            reportMissingReturn(context, name, returnType, line, column);
        }
    }

    /**
     * Adds unit return.
     */
    private static void addUnitReturn(@Nonnull CodeGenerationContext context) {
        ResoType unitType = context.getTypeSystem().getUnitType();
        IrValue unitValue = IrFactory.createConstantNamedStruct(unitType.getType(), new IrValue[0]);
        IrFactory.createReturn(context.getIrBuilder(), unitValue);
    }

    /**
     * Reports missing return value error.
     */
    private static void reportMissingReturn(
        @Nonnull CodeGenerationContext context,
        @Nonnull String name,
        @Nonnull ResoType returnType,
        int line,
        int column) {

        context.getErrorReporter().error(
            "'" + name + "' must return a value of type " + returnType.getName(),
            line, column);
    }

    /**
     * Checks if all branches return.
     */
    private static boolean isReturningIfStatement(@Nonnull ResoParser.StatementContext stmtCtx) {
        return stmtCtx.compound_stmt() != null
            && stmtCtx.compound_stmt().ifStatement() != null
            && IfStatementChecker.allBranchesReturn(stmtCtx.compound_stmt().ifStatement());
    }

    /**
     * Reports unreachable code warning.
     */
    private static void reportUnreachableCode(
        @Nonnull ErrorReporter errorReporter,
        @Nonnull List<ResoParser.StatementContext> statements,
        int currentIndex) {

        if (currentIndex < statements.size() - 1) {
            ResoParser.StatementContext nextStmt = statements.get(currentIndex + 1);
            errorReporter.warning(
                "Unreachable code detected",
                nextStmt.getStart().getLine(),
                nextStmt.getStart().getCharPositionInLine());
        }
    }

    /**
     * Processes the body (either simple statements or block statements).
     *
     * @param context  The code generation context
     * @param blockCtx The block context (can be from function or method)
     * @return true if the body ends with a returning if statement
     */
    public static boolean processBody(
        @Nonnull CodeGenerationContext context,
        @Nullable ResoParser.BlockContext blockCtx) {

        if (blockCtx == null) {
            return false;
        }

        boolean endsWithReturningIf = false;

        if (blockCtx.simple_stmts() != null) {
            processSimpleStatements(context, blockCtx.simple_stmts());
        } else if (blockCtx.statement() != null && !blockCtx.statement().isEmpty()) {
            endsWithReturningIf = processBlockStatements(context, blockCtx.statement());
        }

        return endsWithReturningIf;
    }
}