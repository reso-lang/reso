package com.reso.compiler.errors;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Collects and reports compilation errors with source location information.
 * Supports different error severities and provides formatted error messages.
 */
public class ErrorReporter {
    private final List<CompilerError> errors = new ArrayList<>();
    private final String sourceName;

    /**
     * Creates a new error reporter for the given source file.
     *
     * @param sourceName The name of the source file being compiled
     */
    public ErrorReporter(String sourceName) {
        this.sourceName = sourceName;
    }

    /**
     * Gets the source name associated with this error reporter.
     *
     * @return The source name
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Reports a fatal error without source location information.
     *
     * @param message Message describing the error
     */
    public void fatal(String message) {
        errors.add(new CompilerError(message, sourceName, -1, -1, CompilerError.Severity.FATAL));
    }

    /**
     * Reports a fatal error from an exception.
     *
     * @param message Message describing the error
     * @param cause   Exception that caused the error
     */
    public void fatal(String message, Throwable cause) {
        errors.add(
            new CompilerError(message, sourceName, -1, -1, CompilerError.Severity.FATAL, cause));
    }

    /**
     * Reports a fatal error with source location information.
     *
     * @param message Message describing the error
     * @param line    Line number where the error occurred
     * @param column  Column number where the error occurred
     */
    public void fatal(String message, int line, int column) {
        errors.add(
            new CompilerError(message, sourceName, line, column, CompilerError.Severity.FATAL));
    }

    /**
     * Reports a fatal error from an exception with source location information.
     *
     * @param message Message describing the error
     * @param line    Line number where the error occurred
     * @param column  Column number where the error occurred
     * @param cause   Exception that caused the error
     */
    public void fatal(String message, int line, int column, Throwable cause) {
        errors.add(
            new CompilerError(message, sourceName, line, column, CompilerError.Severity.FATAL,
                cause));
    }

    /**
     * Reports an error without source location information.
     *
     * @param message Message describing the error
     */
    public void error(String message) {
        errors.add(new CompilerError(message, sourceName, -1, -1, CompilerError.Severity.ERROR));
    }

    /**
     * Reports an error from an exception.
     *
     * @param message Message describing the error
     * @param cause   Exception that caused the error
     */
    public void error(String message, Throwable cause) {
        errors.add(
            new CompilerError(message, sourceName, -1, -1, CompilerError.Severity.ERROR, cause));
    }

    /**
     * Reports an error with source location information.
     *
     * @param message Message describing the error
     * @param line    Line number where the error occurred
     * @param column  Column number where the error occurred
     */
    public void error(String message, int line, int column) {
        errors.add(
            new CompilerError(message, sourceName, line, column, CompilerError.Severity.ERROR));
    }

    /**
     * Reports an error from an exception with source location information.
     *
     * @param message Message describing the error
     * @param line    Line number where the error occurred
     * @param column  Column number where the error occurred
     * @param cause   Exception that caused the error
     */
    public void error(String message, int line, int column, Throwable cause) {
        errors.add(
            new CompilerError(message, sourceName, line, column, CompilerError.Severity.ERROR,
                cause));
    }

    /**
     * Reports a warning without source location information.
     *
     * @param message Message describing the warning
     */
    public void warning(String message) {
        errors.add(new CompilerError(message, sourceName, -1, -1, CompilerError.Severity.WARNING));
    }

    /**
     * Reports a warning from an exception.
     *
     * @param message Message describing the warning
     * @param cause   Exception that caused the warning
     */
    public void warning(String message, Throwable cause) {
        errors.add(
            new CompilerError(message, sourceName, -1, -1, CompilerError.Severity.WARNING, cause));
    }

    /**
     * Reports a warning with source location information.
     *
     * @param message Message describing the warning
     * @param line    Line number where the warning occurred
     * @param column  Column number where the warning occurred
     */
    public void warning(String message, int line, int column) {
        errors.add(
            new CompilerError(message, sourceName, line, column, CompilerError.Severity.WARNING));
    }

    /**
     * Reports a warning from an exception with source location information.
     *
     * @param message Message describing the warning
     * @param line    Line number where the warning occurred
     * @param column  Column number where the warning occurred
     * @param cause   Exception that caused the warning
     */
    public void warning(String message, int line, int column, Throwable cause) {
        errors.add(
            new CompilerError(message, sourceName, line, column, CompilerError.Severity.WARNING,
                cause));
    }

    /**
     * Checks if there are any errors (including fatal).
     *
     * @return true if there are any errors
     */
    public boolean hasErrors() {
        return errors.stream().anyMatch(e ->
            e.getSeverity() == CompilerError.Severity.ERROR
                || e.getSeverity() == CompilerError.Severity.FATAL);
    }

    /**
     * Checks if there are any fatal errors.
     *
     * @return true if there are any fatal errors
     */
    public boolean hasFatalErrors() {
        return errors.stream().anyMatch(e -> e.getSeverity() == CompilerError.Severity.FATAL);
    }

    /**
     * Gets all reported errors and warnings.
     *
     * @return List of compiler errors
     */
    public List<CompilerError> getErrors() {
        return new ArrayList<>(errors);
    }

    /**
     * Gets a formatted string of all errors and warnings.
     *
     * @return Formatted error messages
     */
    public String getErrorMessages() {
        return errors.stream()
            .map(CompilerError::toString)
            .collect(Collectors.joining("\n"));
    }
}