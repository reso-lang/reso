package com.reso.compiler.symbols;

import javax.annotation.Nonnull;

/**
 * Interface for symbols in the symbol table.
 */
public interface Symbol {
    /**
     * Gets the name of the symbol.
     *
     * @return The symbol name
     */
    @Nonnull
    String getName();

    /**
     * Gets the symbol kind.
     *
     * @return The symbol kind
     */
    @Nonnull
    SymbolKind getKind();
}