package com.reso.compiler.symbols;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.symbols.resources.FieldSymbol;
import com.reso.compiler.symbols.resources.MethodSymbol;
import com.reso.compiler.symbols.resources.ResourceSymbol;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.types.ResourceType;
import com.reso.llvm.api.IrValue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Symbol table.
 */
public class SymbolTable {
    private Scope currentScope;
    private final Deque<ResoType> functionReturnTypes = new ArrayDeque<>();

    /**
     * Creates a new symbol table with a global scope.
     */
    public SymbolTable() {
        this.currentScope = new Scope(null);
    }

    /**
     * Enters a new function scope.
     *
     * @param functionReturnType The return type of the function
     */
    public void enterFunctionScope(@Nonnull ResoType functionReturnType) {
        Objects.requireNonNull(functionReturnType, "Function return type cannot be null");
        enterScope();
        functionReturnTypes.push(functionReturnType);
    }

    /**
     * Enters a new block scope.
     */
    public void enterScope() {
        currentScope = new Scope(currentScope);
    }

    /**
     * Exits the current scope.
     *
     * @param reporter The error reporter
     * @param line     Line number for error reporting
     * @param column   Column number for error reporting
     * @return true if the scope was exited successfully
     */
    public boolean exitScope(@Nonnull ErrorReporter reporter, int line, int column) {
        Objects.requireNonNull(reporter, "Error reporter cannot be null");

        if (currentScope.getParent() != null) {
            currentScope = currentScope.getParent();
            return true;
        } else {
            reporter.error("Cannot exit global scope", line, column);
            return false;
        }
    }

    /**
     * Exits a function scope.
     *
     * @param reporter The error reporter
     * @param line     Line number for error reporting
     * @param column   Column number for error reporting
     * @return true if the scope was exited successfully
     */
    public boolean exitFunctionScope(@Nonnull ErrorReporter reporter, int line, int column) {
        Objects.requireNonNull(reporter, "Error reporter cannot be null");

        if (functionReturnTypes.isEmpty()) {
            reporter.error("Not in a function scope", line, column);
            return false;
        }

        functionReturnTypes.pop();
        return exitScope(reporter, line, column);
    }

    /**
     * Checks if we are in the global scope.
     *
     * @return true if we are in the global scope
     */
    public boolean isInGlobalScope() {
        return currentScope.getParent() == null;
    }

    /**
     * Checks if we're in a function scope.
     *
     * @return true if in a function scope
     */
    public boolean isInFunctionScope() {
        return !functionReturnTypes.isEmpty();
    }

    /**
     * Gets the current function return type.
     *
     * @return The return type, or null if not in a function
     */
    @Nullable
    public ResoType getCurrentFunctionReturnType() {
        return functionReturnTypes.isEmpty() ? null : functionReturnTypes.peek();
    }

    /**
     * Gets the current scope.
     *
     * @return The current scope
     */
    @Nonnull
    public Scope getCurrentScope() {
        return currentScope;
    }

    /**
     * Finds a symbol by name.
     *
     * @param name The symbol name
     * @param kind The symbol kind
     * @return The symbol, or null if not found
     */
    @Nullable
    public Symbol findSymbol(@Nonnull String name, @Nonnull SymbolKind kind) {
        Objects.requireNonNull(name, "Symbol name cannot be null");
        Objects.requireNonNull(kind, "Symbol kind cannot be null");

        return currentScope.find(name, kind);
    }

    /**
     * Finds a readable variable by name.
     *
     * @param name     The variable name
     * @param reporter The error reporter
     * @param line     Line number for error reporting
     * @param column   Column number for error reporting
     * @return The variable symbol, or null if not found or not readable
     */
    @Nullable
    public VariableSymbol findReadableVariable(
        @Nonnull String name,
        @Nonnull ErrorReporter reporter,
        int line,
        int column) {

        Objects.requireNonNull(name, "Variable name cannot be null");
        Objects.requireNonNull(reporter, "Error reporter cannot be null");

        Symbol symbol = findSymbol(name, SymbolKind.VARIABLE);

        if (symbol == null) {
            reporter.error("Variable not defined: " + name, line, column);
            return null;
        }

        VariableSymbol varSymbol = (VariableSymbol) symbol;
        if (!varSymbol.isReadable()) {
            reporter.error("Cannot read from " + name + " because it is not initialized.", line,
                column);
            return null;
        }

        return varSymbol;
    }

    /**
     * Checks if a variable is initialized.
     *
     * @param name The variable name
     * @return true if the variable is initialized, false otherwise
     */
    public boolean isVariableInitialized(String name) {
        Objects.requireNonNull(name, "Variable name cannot be null");

        Symbol symbol = findSymbol(name, SymbolKind.VARIABLE);
        if (symbol == null) {
            return false; // Not a variable or not found
        }

        VariableSymbol varSymbol = (VariableSymbol) symbol;
        return varSymbol.isInitialized();
    }

    /**
     * Finds a function by name.
     *
     * @param name The function name
     * @return The function symbol, or null if not found
     */
    @Nullable
    public FunctionSymbol findFunction(@Nonnull String name) {
        Objects.requireNonNull(name, "Function name cannot be null");

        Symbol symbol = findSymbol(name, SymbolKind.FUNCTION);

        if (symbol != null) {
            return (FunctionSymbol) symbol;
        }

        return null;
    }

    /**
     * Finds a type by name.
     *
     * @param name The type name
     * @return The type symbol, or null if not found
     */
    @Nullable
    public TypeSymbol findType(@Nonnull String name) {
        Objects.requireNonNull(name, "Type name cannot be null");

        Symbol symbol = findSymbol(name, SymbolKind.TYPE);

        if (symbol != null) {
            return (TypeSymbol) symbol;
        }

        return findResource(name);
    }

    /**
     * Finds a resource by name.
     *
     * @param name The resource name
     * @return The resource symbol, or null if not found
     */
    @Nullable
    public ResourceSymbol findResource(@Nonnull String name) {
        Objects.requireNonNull(name, "Resource name cannot be null");

        Symbol symbol = findSymbol(name, SymbolKind.RESOURCE);

        if (symbol != null) {
            return (ResourceSymbol) symbol;
        }

        return null;
    }

    /**
     * Defines a variable.
     *
     * @param name          The variable name
     * @param value         The LLVM value
     * @param type          The variable type
     * @param isConstant    Whether the variable is constant
     * @param isInitialized Whether the variable is initialized
     * @param reporter      The error reporter
     * @param line          Line number for error reporting
     * @param column        Column number for error reporting
     * @return The variable symbol, or null if the definition failed
     */
    @Nullable
    public VariableSymbol defineVariable(
        @Nonnull String name,
        @Nonnull IrValue value,
        @Nonnull ResoType type,
        boolean isConstant,
        boolean isInitialized,
        @Nonnull ErrorReporter reporter,
        int line,
        int column) {

        Objects.requireNonNull(name, "Variable name cannot be null");
        Objects.requireNonNull(value, "LLVM value cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(reporter, "Error reporter cannot be null");

        if (isInGlobalScope()) {
            reporter.error("Cannot define global variable: " + name, line, column);
            return null;
        }

        if (currentScope.contains(name, SymbolKind.VARIABLE)) {
            reporter.error(name + " is already defined in current scope!", line, column);
            return null;
        }

        VariableSymbol symbol = new VariableSymbol(name, type, value, isConstant, isInitialized);
        return currentScope.add(symbol) ? symbol : null;
    }

    /**
     * Initializes a variable.
     *
     * @param name     The variable name
     * @param reporter The error reporter
     * @param line     Line number for error reporting
     * @param column   Column number for error reporting
     * @return true if the variable was initialized successfully
     */
    public boolean initializeVariable(
        @Nonnull String name,
        @Nonnull ErrorReporter reporter,
        int line,
        int column) {

        Objects.requireNonNull(name, "Variable name cannot be null");
        Objects.requireNonNull(reporter, "Error reporter cannot be null");

        Symbol symbol = findSymbol(name, SymbolKind.VARIABLE);

        if (symbol == null) {
            reporter.error("Cannot initialize undeclared variable: " + name, line, column);
            return false;
        }

        VariableSymbol varSymbol = (VariableSymbol) symbol;
        if (varSymbol.isConstant() && varSymbol.isInitialized()) {
            reporter.error("Cannot reinitialize: " + name, line, column);
            return false;
        }

        // Create initialized variable symbol
        VariableSymbol initializedSymbol = varSymbol.initialized();

        // Update the symbol in all necessary scopes
        Scope scope = currentScope;
        while (scope != null) {
            if (scope.contains(name, SymbolKind.VARIABLE)) {
                scope.update(initializedSymbol);
                return true;
            }
            scope = scope.getParent();
        }

        // Should never happen if findSymbol found the variable
        return false;
    }

    /**
     * Defines a function.
     *
     * @param name           The function name
     * @param value          The LLVM value
     * @param returnType     The return type
     * @param parameters     The parameters
     * @param visibility     The function visibility
     * @param fileIdentifier The source file identifier where this function is defined
     * @param reporter       The error reporter
     * @param line           Line number for error reporting
     * @param column         Column number for error reporting
     * @return true if the function was defined successfully
     */
    public boolean defineFunction(
        @Nonnull String name,
        @Nonnull IrValue value,
        @Nonnull ResoType returnType,
        @Nonnull List<Parameter> parameters,
        @Nonnull Visibility visibility,
        @Nonnull String fileIdentifier,
        @Nonnull ErrorReporter reporter,
        int line,
        int column) {

        Objects.requireNonNull(name, "Function name cannot be null");
        Objects.requireNonNull(value, "LLVM value cannot be null");
        Objects.requireNonNull(returnType, "Return type cannot be null");
        Objects.requireNonNull(parameters, "Parameters cannot be null");
        Objects.requireNonNull(visibility, "Visibility cannot be null");
        Objects.requireNonNull(fileIdentifier, "File identifier cannot be null");
        Objects.requireNonNull(reporter, "Error reporter cannot be null");

        // Find the global scope
        Scope globalScope = currentScope;
        while (globalScope.getParent() != null) {
            globalScope = globalScope.getParent();
        }

        // Check if function already exists in global scope
        if (globalScope.contains(name, SymbolKind.FUNCTION)) {
            reporter.error(name + " is already defined!", line, column);
            return false;
        }

        // Create function symbol
        FunctionSymbol symbol =
            new FunctionSymbol(name, returnType, parameters, value, visibility, fileIdentifier);

        // Add to global scope
        return globalScope.add(symbol);
    }

    /**
     * Defines a function.
     *
     * @param name           The function name
     * @param value          The LLVM value
     * @param returnType     The return type
     * @param parameters     The parameters
     * @param visibility     The function visibility
     * @param fileIdentifier The source file identifier where this function is defined
     * @param callFunction   The function to use for building calls to this function
     * @param reporter       The error reporter
     * @param line           Line number for error reporting
     * @param column         Column number for error reporting
     * @return true if the function was defined successfully
     */
    public boolean defineFunction(
        @Nonnull String name,
        @Nullable IrValue value,
        @Nonnull ResoType returnType,
        @Nonnull List<Parameter> parameters,
        @Nonnull Visibility visibility,
        @Nonnull String fileIdentifier,
        @Nonnull BuildFunctionCallFunction callFunction,
        @Nonnull ErrorReporter reporter,
        int line,
        int column) {

        Objects.requireNonNull(name, "Function name cannot be null");
        Objects.requireNonNull(returnType, "Return type cannot be null");
        Objects.requireNonNull(parameters, "Parameters cannot be null");
        Objects.requireNonNull(visibility, "Visibility cannot be null");
        Objects.requireNonNull(fileIdentifier, "File identifier cannot be null");
        Objects.requireNonNull(reporter, "Error reporter cannot be null");

        // Find the global scope
        Scope globalScope = currentScope;
        while (globalScope.getParent() != null) {
            globalScope = globalScope.getParent();
        }

        // Check if function already exists in global scope
        if (globalScope.contains(name, SymbolKind.FUNCTION)) {
            reporter.error(name + " is already defined!", line, column);
            return false;
        }

        // Create function symbol
        FunctionSymbol symbol =
            new FunctionSymbol(name, returnType, parameters, value, visibility, fileIdentifier,
                callFunction);

        // Add to global scope
        return globalScope.add(symbol);
    }

    /**
     * Defines a resource.
     *
     * @param name           The resource name
     * @param resourceType   The resource type
     * @param fields         The resource fields
     * @param methods        The resource methods
     * @param fileIdentifier The source file identifier where this resource is defined
     * @param reporter       The error reporter
     * @param line           Line number for error reporting
     * @param column         Column number for error reporting
     * @return true if the resource was defined successfully
     */
    public boolean defineResource(
        @Nonnull String name,
        @Nonnull ResourceType resourceType,
        @Nonnull List<FieldSymbol> fields,
        @Nonnull List<MethodSymbol> methods,
        @Nonnull String fileIdentifier,
        @Nonnull ErrorReporter reporter,
        int line,
        int column) {

        Objects.requireNonNull(name, "Resource name cannot be null");
        Objects.requireNonNull(resourceType, "Resource type cannot be null");
        Objects.requireNonNull(fields, "Fields cannot be null");
        Objects.requireNonNull(methods, "Methods cannot be null");
        Objects.requireNonNull(fileIdentifier, "File identifier cannot be null");
        Objects.requireNonNull(reporter, "Error reporter cannot be null");

        // Find the global scope
        Scope globalScope = currentScope;
        while (globalScope.getParent() != null) {
            globalScope = globalScope.getParent();
        }

        // Check if resource already exists in global scope
        if (globalScope.contains(name, SymbolKind.RESOURCE)) {
            reporter.error(name + " is already defined!", line, column);
            return false;
        }

        // Create resource symbol
        ResourceSymbol symbol =
            new ResourceSymbol(name, resourceType, fields, methods, fileIdentifier);

        // Add to global scope
        return globalScope.add(symbol);
    }

    /**
     * Defines a type.
     *
     * @param name           The type name
     * @param type           The type
     * @param methods        The type methods
     * @param fileIdentifier The source file identifier where this type is defined
     * @param reporter       The error reporter
     * @param line           Line number for error reporting
     * @param column         Column number for error reporting
     * @return true if the type was defined successfully
     */
    public boolean defineType(
        @Nonnull String name,
        @Nonnull ResoType type,
        @Nonnull List<MethodSymbol> methods,
        @Nonnull String fileIdentifier,
        @Nonnull ErrorReporter reporter,
        int line,
        int column) {

        Objects.requireNonNull(name, "Type name cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(methods, "Methods cannot be null");
        Objects.requireNonNull(fileIdentifier, "File identifier cannot be null");
        Objects.requireNonNull(reporter, "Error reporter cannot be null");

        // Find the global scope
        Scope globalScope = currentScope;
        while (globalScope.getParent() != null) {
            globalScope = globalScope.getParent();
        }

        // Check if type already exists in global scope
        if (globalScope.contains(name, SymbolKind.TYPE)) {
            reporter.error(name + " is already defined!", line, column);
            return false;
        }

        // Create type symbol
        TypeSymbol symbol = new TypeSymbol(name, type, methods, fileIdentifier);

        // Add to global scope
        return globalScope.add(symbol);
    }
}