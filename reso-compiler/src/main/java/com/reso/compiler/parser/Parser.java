package com.reso.compiler.parser;

import com.reso.compiler.errors.CompilerErrorListener;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.grammar.ResoLexer;
import com.reso.grammar.ResoParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Parser for Reso source code.
 * Uses ANTLR to parse source code into an AST.
 */
public final class Parser {
    private Parser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Parses Reso source code.
     *
     * @param sourceCode    The source code to parse
     * @param errorReporter Error reporter for reporting errors
     * @return The parse result
     */
    public static ParseResult parse(String sourceCode, ErrorReporter errorReporter) {
        // Set up lexer and parser
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(sourceCode));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ResoParser parser = new ResoParser(tokens);

        // Add error listener
        CompilerErrorListener errorListener = new CompilerErrorListener(errorReporter);
        parser.removeErrorListeners(); // Remove default listener
        parser.addErrorListener(errorListener);

        // Parse the program
        ResoParser.ProgramContext programCtx = parser.program();

        // Create parse result
        return new ParseResult(programCtx);
    }
}