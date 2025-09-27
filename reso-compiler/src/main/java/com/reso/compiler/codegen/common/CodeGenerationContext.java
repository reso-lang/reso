package com.reso.compiler.codegen.common;

import com.reso.compiler.codegen.expressions.ExpressionGenerator;
import com.reso.compiler.codegen.functions.FunctionGenerator;
import com.reso.compiler.codegen.resources.ResourceGenerator;
import com.reso.compiler.codegen.statements.StatementGenerator;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.symbols.AccessContext;
import com.reso.compiler.symbols.SymbolTable;
import com.reso.compiler.types.TypeSystem;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrContext;
import com.reso.llvm.api.IrModule;
import com.reso.llvm.api.IrValue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared context for code generation.
 */
public class CodeGenerationContext {
    private final IrContext irContext;
    private final IrModule irModule;
    private final IrBuilder irBuilder;
    private final TypeSystem typeSystem;
    private final SymbolTable symbolTable;
    private ErrorReporter errorReporter;

    private ExpressionGenerator expressionGenerator;
    private StatementGenerator statementGenerator;
    private FunctionGenerator functionGenerator;
    private ResourceGenerator resourceGenerator;

    private AccessContext currentAccessContext;
    private IrValue currentFunction;
    private final Deque<LoopContext> loopContexts = new ArrayDeque<>();
    private final Map<String, IrValue> stringLiteralCache = new ConcurrentHashMap<>();

    /**
     * Creates a new code generation context.
     *
     * @param irContext   The LLVM context
     * @param irModule    The LLVM module
     * @param irBuilder   The LLVM builder
     * @param typeSystem  The type system
     * @param symbolTable The symbol table
     */
    public CodeGenerationContext(
        @Nonnull IrContext irContext,
        @Nonnull IrModule irModule,
        @Nonnull IrBuilder irBuilder,
        @Nonnull TypeSystem typeSystem,
        @Nonnull SymbolTable symbolTable) {

        this.irContext = Objects.requireNonNull(irContext, "IR context cannot be null");
        this.irModule = Objects.requireNonNull(irModule, "IR module cannot be null");
        this.irBuilder = Objects.requireNonNull(irBuilder, "IR builder cannot be null");
        this.typeSystem = Objects.requireNonNull(typeSystem, "Type system cannot be null");
        this.symbolTable = Objects.requireNonNull(symbolTable, "Symbol table cannot be null");

        this.currentAccessContext = AccessContext.createGlobal();
    }

    /**
     * Initializes the generator components.
     * Must be called after construction but before using the context.
     *
     * @param expressionGenerator The expression generator
     * @param statementGenerator  The statement generator
     * @param functionGenerator   The function generator
     * @param resourceGenerator   The resource generator
     */
    public void initializeGenerators(
        @Nonnull ExpressionGenerator expressionGenerator,
        @Nonnull StatementGenerator statementGenerator,
        @Nonnull FunctionGenerator functionGenerator,
        @Nonnull ResourceGenerator resourceGenerator) {

        this.expressionGenerator =
            Objects.requireNonNull(expressionGenerator, "Expression generator cannot be null");
        this.statementGenerator =
            Objects.requireNonNull(statementGenerator, "Statement generator cannot be null");
        this.functionGenerator =
            Objects.requireNonNull(functionGenerator, "Function generator cannot be null");
        this.resourceGenerator =
            Objects.requireNonNull(resourceGenerator, "Resource generator cannot be null");
    }

    /**
     * Gets the LLVM context.
     *
     * @return The LLVM context
     */
    @Nonnull
    public IrContext getIrContext() {
        return irContext;
    }

    /**
     * Gets the LLVM module.
     *
     * @return The LLVM module
     */
    @Nonnull
    public IrModule getIrModule() {
        return irModule;
    }

    /**
     * Gets the LLVM builder.
     *
     * @return The LLVM builder
     */
    @Nonnull
    public IrBuilder getIrBuilder() {
        return irBuilder;
    }

    /**
     * Gets the type system.
     *
     * @return The type system
     */
    @Nonnull
    public TypeSystem getTypeSystem() {
        return typeSystem;
    }

    /**
     * Gets the symbol table.
     *
     * @return The symbol table
     */
    @Nonnull
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    /**
     * Gets the error reporter.
     *
     * @return The error reporter
     */
    @Nonnull
    public ErrorReporter getErrorReporter() {
        return errorReporter;
    }

    /**
     * Sets the error reporter.
     *
     * @param errorReporter The error reporter to set
     */
    public void setErrorReporter(@Nonnull ErrorReporter errorReporter) {
        this.errorReporter = Objects.requireNonNull(errorReporter, "Error reporter cannot be null");
    }

    /**
     * Gets the expression generator.
     *
     * @return The expression generator
     */
    @Nonnull
    public ExpressionGenerator getExpressionGenerator() {
        if (expressionGenerator == null) {
            throw new IllegalStateException("Expression generator not initialized");
        }
        return expressionGenerator;
    }

    /**
     * Gets the statement generator.
     *
     * @return The statement generator
     */
    @Nonnull
    public StatementGenerator getStatementGenerator() {
        if (statementGenerator == null) {
            throw new IllegalStateException("Statement generator not initialized");
        }
        return statementGenerator;
    }

    /**
     * Gets the function generator.
     *
     * @return The function generator
     */
    @Nonnull
    public FunctionGenerator getFunctionGenerator() {
        if (functionGenerator == null) {
            throw new IllegalStateException("Function generator not initialized");
        }
        return functionGenerator;
    }

    /**
     * Gets the resource generator.
     *
     * @return The resource generator
     */
    @Nonnull
    public ResourceGenerator getResourceGenerator() {
        if (resourceGenerator == null) {
            throw new IllegalStateException("Resource generator not initialized");
        }
        return resourceGenerator;
    }

    /**
     * Gets the current access context for visibility checking.
     *
     * @return The current access context
     */
    @Nonnull
    public AccessContext getCurrentAccessContext() {
        return currentAccessContext;
    }

    /**
     * Exits the current access context by moving to the parent context.
     */
    public void exitAccessContext() {
        if (currentAccessContext.getParent() != null) {
            currentAccessContext = currentAccessContext.getParent();
        }
    }

    /**
     * Enters a new access context by creating a child context.
     *
     * @param newContext The new access context
     */
    private void enterAccessContext(@Nonnull AccessContext newContext) {
        Objects.requireNonNull(newContext, "New context cannot be null");
        this.currentAccessContext = newContext;
    }

    /**
     * Enters a file context.
     *
     * @param fileIdentifier The file identifier for the resource
     */
    public void enterFileContext(@Nonnull String fileIdentifier) {
        Objects.requireNonNull(fileIdentifier, "File identifier cannot be null");

        AccessContext resourceContext =
            AccessContext.createFilePrivate(fileIdentifier, currentAccessContext);
        enterAccessContext(resourceContext);
    }

    /**
     * Gets the current function.
     *
     * @return The current function
     */
    @Nullable
    public IrValue getCurrentFunction() {
        return currentFunction;
    }

    /**
     * Sets the current function.
     *
     * @param function The current function
     */
    public void setCurrentFunction(@Nullable IrValue function) {
        this.currentFunction = function;
    }

    /**
     * Gets the loop contexts.
     *
     * @return The loop contexts
     */
    @Nonnull
    public Deque<LoopContext> getLoopContexts() {
        return loopContexts;
    }

    /**
     * Gets or creates a cached global string literal.
     *
     * @param content The string content
     * @return The cached global string IRValue
     */
    @Nonnull
    public IrValue getOrCreateGlobalString(@Nonnull String content) {
        return stringLiteralCache.computeIfAbsent(content,
            key -> IrFactory.createGlobalString(irBuilder, key));
    }
}