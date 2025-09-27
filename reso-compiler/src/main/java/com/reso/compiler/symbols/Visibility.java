package com.reso.compiler.symbols;

/**
 * Represents the visibility level of a symbol.
 * Controls where a symbol can be accessed from.
 */
public enum Visibility {
    /**
     * Only accessible within the same source file.
     */
    FILEPRIVATE,

    /**
     * Accessible from anywhere (public).
     */
    GLOBAL
}