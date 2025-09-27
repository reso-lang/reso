package com.reso.compiler.errors;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Error listener for ANTLR parsing errors.
 * Converts ANTLR errors to compiler errors.
 */
public class CompilerErrorListener extends BaseErrorListener {
    private final ErrorReporter errorReporter;

    /**
     * Creates a new compiler error listener.
     *
     * @param errorReporter Error reporter for reporting errors
     */
    public CompilerErrorListener(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg, RecognitionException e) {
        // Report syntax error to the error reporter
        errorReporter.error(msg, line, charPositionInLine);
    }
}