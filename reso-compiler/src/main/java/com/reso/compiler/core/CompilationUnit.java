package com.reso.compiler.core;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.parser.ParseResult;
import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a single source file unit during compilation.
 */
public class CompilationUnit {
    private final String sourceCode;
    private final String sourceName;
    private final Path sourcePath;
    private final ErrorReporter errorReporter;
    private ParseResult parseResult;

    /**
     * Creates a new compilation unit.
     *
     * @param sourceCode The source code content
     * @param sourceName The name of the source (for error reporting)
     * @param sourcePath The path to the source file (may be null for strings)
     */
    public CompilationUnit(@Nonnull String sourceCode, @Nonnull String sourceName,
                           @Nullable Path sourcePath) {
        this.sourceCode = Objects.requireNonNull(sourceCode, "Source code cannot be null");
        this.sourceName = Objects.requireNonNull(sourceName, "Source name cannot be null");
        this.sourcePath = sourcePath;
        this.errorReporter = new ErrorReporter(sourceName);
    }

    /**
     * Gets the source code content.
     *
     * @return The source code
     */
    @Nonnull
    public String getSourceCode() {
        return sourceCode;
    }

    /**
     * Gets the source name for error reporting.
     *
     * @return The source name
     */
    @Nonnull
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Gets the source file path.
     *
     * @return The source file path, or null if this is a string-based compilation unit
     */
    @Nullable
    public Path getSourcePath() {
        return sourcePath;
    }

    /**
     * Gets the error reporter for this compilation unit.
     *
     * @return The error reporter
     */
    @Nonnull
    public ErrorReporter getErrorReporter() {
        return errorReporter;
    }

    /**
     * Gets the parse result for this compilation unit.
     *
     * @return The parse result, or null if not yet parsed
     */
    @Nullable
    public ParseResult getParseResult() {
        return parseResult;
    }

    /**
     * Sets the parse result for this compilation unit.
     *
     * @param parseResult The parse result
     */
    public void setParseResult(@Nullable ParseResult parseResult) {
        this.parseResult = parseResult;
    }

    /**
     * Checks if this compilation unit has been successfully parsed.
     *
     * @return true if parsed without errors
     */
    public boolean isParsed() {
        return parseResult != null && !errorReporter.hasErrors();
    }

    /**
     * Gets a unique identifier for this compilation unit for symbol scoping.
     *
     * @return A unique file identifier
     */
    @Nonnull
    public String getFileIdentifier() {
        if (sourcePath != null) {
            return sourcePath.toString();
        }
        return sourceName;
    }

    @Override
    public String toString() {
        return "CompilationUnit{sourceName='" + sourceName + "', sourcePath=" + sourcePath + "}";
    }
}