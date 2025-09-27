package com.reso.compiler.errors;

/**
 * Represents a single error encountered during compilation.
 */
public class CompilerError {
    /**
     * Severity levels for compiler errors.
     */
    public enum Severity {
        /**
         * Warning - compilation continues, but may have issues.
         */
        WARNING,

        /**
         * Error - compilation fails.
         */
        ERROR,

        /**
         * Fatal error - compilation is halted immediately.
         */
        FATAL
    }

    private final String message;
    private final String sourceName;
    private final int line;
    private final int column;
    private final Severity severity;
    private final Throwable cause;

    /**
     * Creates a new compiler error with a cause.
     *
     * @param message    Message describing the error
     * @param sourceName Name of the source file
     * @param line       Line number where the error occurred
     * @param column     Column number where the error occurred
     * @param severity   Severity of the error
     * @param cause      Exception that caused the error
     */
    public CompilerError(String message, String sourceName, int line, int column, Severity severity,
                         Throwable cause) {
        this.message = message;
        this.sourceName = sourceName;
        this.line = line;
        this.column = column;
        this.severity = severity;
        this.cause = cause;
    }

    /**
     * Creates a new compiler error.
     *
     * @param message    Message describing the error
     * @param sourceName Name of the source file
     * @param line       Line number where the error occurred
     * @param column     Column number where the error occurred
     * @param severity   Severity of the error
     */
    public CompilerError(String message, String sourceName, int line, int column,
                         Severity severity) {
        this(message, sourceName, line, column, severity, null);
    }

    /**
     * Gets the error message.
     *
     * @return The error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the source file name.
     *
     * @return The source file name
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Gets the line number where the error occurred.
     *
     * @return The line number
     */
    public int getLine() {
        return line;
    }

    /**
     * Gets the column number where the error occurred.
     *
     * @return The column number
     */
    public int getColumn() {
        return column;
    }

    /**
     * Gets the severity of the error.
     *
     * @return The error severity
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Gets the exception that caused the error.
     *
     * @return The cause, or null if not available
     */
    public Throwable getCause() {
        return cause;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (sourceName != null) {
            sb.append(sourceName).append(":");
        }

        if (line >= 0 && column >= 0) {
            sb.append("line ").append(line).append(", column ").append(column).append(": ");
        } else if (line >= 0) {
            sb.append("line ").append(line).append(": ");
        } else if (column >= 0) {
            sb.append("column ").append(column).append(": ");
        }
        sb.append(" ");

        sb.append(severity).append(": ").append(message);

        return sb.toString();
    }
}