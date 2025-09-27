package com.reso.compiler.symbols;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Scope class for the symbol table.
 */
public class Scope {
    private record SymbolKey(String name, SymbolKind kind) {
    }

    private final Map<SymbolKey, Symbol> symbols = new HashMap<>();
    private final Scope parent;

    /**
     * Creates a new scope.
     *
     * @param parent The parent scope, or null for the global scope
     */
    public Scope(@Nullable Scope parent) {
        this.parent = parent;
    }

    /**
     * Finds a symbol in this scope or parent scopes.
     *
     * @param name The symbol name
     * @param kind The symbol kind
     * @return The symbol, or null if not found
     */
    @Nullable
    public Symbol find(@Nonnull String name, @Nonnull SymbolKind kind) {
        Objects.requireNonNull(name, "Symbol name cannot be null");
        Objects.requireNonNull(kind, "Symbol kind cannot be null");

        SymbolKey key = new SymbolKey(name, kind);

        Symbol symbol = symbols.get(key);
        if (symbol != null) {
            return symbol;
        }

        return parent != null ? parent.find(name, kind) : null;
    }

    /**
     * Adds a symbol to this scope.
     *
     * @param symbol The symbol to add
     * @return true if the symbol was added, false if it already exists
     */
    public boolean add(@Nonnull Symbol symbol) {
        Objects.requireNonNull(symbol, "Symbol cannot be null");

        SymbolKey key = new SymbolKey(symbol.getName(), symbol.getKind());

        if (symbols.containsKey(key)) {
            return false;
        }

        symbols.put(key, symbol);
        return true;
    }

    /**
     * Checks if this scope contains a symbol.
     *
     * @param name The symbol name
     * @param kind The symbol kind
     * @return true if this scope contains the symbol
     */
    public boolean contains(@Nonnull String name, @Nonnull SymbolKind kind) {
        Objects.requireNonNull(name, "Symbol name cannot be null");
        Objects.requireNonNull(kind, "Symbol kind cannot be null");

        SymbolKey key = new SymbolKey(name, kind);

        return symbols.containsKey(key);
    }

    /**
     * Updates a symbol in this scope.
     *
     * @param symbol The symbol to update
     * @return true if the symbol was updated, false if it doesn't exist
     */
    public boolean update(@Nonnull Symbol symbol) {
        Objects.requireNonNull(symbol, "Symbol cannot be null");

        SymbolKey key = new SymbolKey(symbol.getName(), symbol.getKind());

        if (!symbols.containsKey(key)) {
            return false;
        }

        symbols.put(key, symbol);
        return true;
    }

    /**
     * Gets the parent scope.
     *
     * @return The parent scope, or null for the global scope
     */
    @Nullable
    public Scope getParent() {
        return parent;
    }
}
