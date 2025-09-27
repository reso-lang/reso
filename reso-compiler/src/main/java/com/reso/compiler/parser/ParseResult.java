package com.reso.compiler.parser;

import com.reso.grammar.ResoParser.ProgramContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Represents the result of parsing Reso source code.
 */
public class ParseResult {
    private final ProgramContext tree;

    /**
     * Creates a new parse result.
     *
     * @param tree The parsed program
     */
    public ParseResult(ProgramContext tree) {
        this.tree = tree;
    }

    /**
     * Gets the parsed program.
     *
     * @return The program parse tree
     */
    public ParseTree getTree() {
        return tree;
    }
}