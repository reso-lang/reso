package com.reso.compiler.codegen.expressions;

import com.reso.compiler.codegen.common.CallGeneratorUtils;
import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.symbols.FunctionSymbol;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.api.IrValue;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for function calls.
 */
public class FunctionCallGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new function call generator.
     *
     * @param context The code generation context
     */
    public FunctionCallGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a function call.
     *
     * @param expr The function call expression
     * @return The result of the expression, or null if an error occurred
     */
    @Nullable
    public ResoValue generateFunctionCall(@Nullable ResoParser.FunctionCallExprContext expr) {
        if (expr == null) {
            return null;
        }

        final String functionName = expr.Identifier().getText();
        final int line = expr.getStart().getLine();
        final int column = expr.getStart().getCharPositionInLine();

        final FunctionSymbol function = lookupFunction(functionName, line, column);
        if (function == null) {
            return null;
        }

        if (!checkFunctionVisibility(function, line, column)) {
            return null;
        }

        final List<ResoParser.ExpressionContext> argumentExpressions =
            extractArgumentExpressions(expr);

        if (!CallGeneratorUtils.validateArgumentCount(function.getParameters(),
            argumentExpressions,
            functionName,
            context.getErrorReporter(),
            line,
            column)) {
            return null;
        }

        final IrValue[] argumentValues =
            CallGeneratorUtils.processArguments(function.getParameters(),
                argumentExpressions,
                functionName,
                List.of(),
                context.getExpressionGenerator(),
                context.getErrorReporter(),
                line,
                column);
        if (argumentValues == null) {
            return null;
        }

        return function.buildCall(context,
            argumentValues,
            line,
            column);
    }

    /**
     * Looks up a function in the symbol table.
     *
     * @param functionName The name of the function to look up
     * @param line         Source line number for error reporting
     * @param column       Source column number for error reporting
     * @return The function symbol, or null if not found
     */
    @Nullable
    private FunctionSymbol lookupFunction(@Nonnull String functionName, int line, int column) {
        final FunctionSymbol function = context.getSymbolTable().findFunction(functionName);

        if (function == null) {
            context.getErrorReporter().error("Function not defined: " + functionName, line, column);
        }

        return function;
    }

    /**
     * Checks visibility for function access.
     */
    private boolean checkFunctionVisibility(@Nonnull FunctionSymbol function,
                                            int line, int column) {
        boolean accessAllowed = context.getCurrentAccessContext()
            .canAccess(function.getVisibility(), function.getFileIdentifier());

        if (!accessAllowed) {
            context.getErrorReporter().error(
                "Function '" + function.getName() + "' with "
                    + function.getVisibility().name().toLowerCase()
                    + " visibility is not accessible from current context",
                line,
                column);
        }
        return accessAllowed;
    }

    /**
     * Extracts argument expressions from the function call.
     *
     * @param expr The function call expression
     * @return List of argument expressions (empty if no arguments)
     */
    @Nonnull
    private List<ResoParser.ExpressionContext> extractArgumentExpressions(
        @Nonnull ResoParser.FunctionCallExprContext expr) {
        return expr.expressionList() != null ? expr.expressionList().expression() : List.of();
    }
}