package com.reso.compiler.core;

import com.reso.compiler.errors.CompilerError;
import com.reso.compiler.errors.ErrorReporter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Result of a compilation.
 */
public class CompilationResult {
    private final String llvmIr;
    private final Map<String, ErrorReporter> errorReporters;
    private final List<String> sourceFiles;

    /**
     * Creates a new compilation result for a single source file.
     *
     * @param llvmIr        The generated LLVM IR, or null if compilation failed
     * @param errorReporter The error reporter
     */
    public CompilationResult(@Nullable String llvmIr, @Nonnull ErrorReporter errorReporter) {
        this.llvmIr = llvmIr;
        this.errorReporters = new LinkedHashMap<>();
        this.sourceFiles = new ArrayList<>();

        Objects.requireNonNull(errorReporter, "Error reporter cannot be null");
        this.errorReporters.put(errorReporter.getSourceName(), errorReporter);
        this.sourceFiles.add(errorReporter.getSourceName());
    }

    /**
     * Creates a new compilation result for multiple source files.
     *
     * @param llvmIr         The generated LLVM IR, or null if compilation failed
     * @param errorReporters Map of source names to error reporters
     * @param sourceFiles    List of source file names in compilation order
     */
    public CompilationResult(@Nullable String llvmIr,
                             @Nonnull Map<String, ErrorReporter> errorReporters,
                             @Nonnull List<String> sourceFiles) {
        this.llvmIr = llvmIr;
        this.errorReporters = new LinkedHashMap<>(
            Objects.requireNonNull(errorReporters, "Error reporters cannot be null"));
        this.sourceFiles =
            new ArrayList<>(Objects.requireNonNull(sourceFiles, "Source files cannot be null"));
    }

    /**
     * Checks if the compilation was successful.
     *
     * @return true if the compilation was successful
     */
    public boolean isSuccessful() {
        return llvmIr != null
            && errorReporters.values().stream().noneMatch(ErrorReporter::hasErrors);
    }

    /**
     * Gets the generated LLVM IR.
     *
     * @return The LLVM IR, or null if compilation failed
     */
    @Nullable
    public String llvmIr() {
        return llvmIr;
    }

    /**
     * Gets the error reporter for a specific source file.
     *
     * @param sourceName The source file name
     * @return The error reporter, or null if not found
     */
    @Nullable
    public ErrorReporter getErrorReporter(@Nonnull String sourceName) {
        return errorReporters.get(sourceName);
    }

    /**
     * Gets all error reporters mapped by source name.
     *
     * @return Map of source name to error reporter
     */
    @Nonnull
    public Map<String, ErrorReporter> getAllErrorReporters() {
        return Collections.unmodifiableMap(errorReporters);
    }

    /**
     * Gets all errors from all source files.
     *
     * @return List of all compiler errors
     */
    @Nonnull
    public List<CompilerError> getAllErrors() {
        return errorReporters.values().stream()
            .flatMap(reporter -> reporter.getErrors().stream())
            .collect(Collectors.toList());
    }

    /**
     * Gets formatted error messages from all source files.
     *
     * @return Formatted error messages
     */
    @Nonnull
    public String getErrorMessages() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ErrorReporter> errorReporterEntry : errorReporters.entrySet()) {
            String sourceFile = errorReporterEntry.getKey();
            ErrorReporter reporter = errorReporterEntry.getValue();
            if (reporter != null && !reporter.getErrorMessages().isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append("Errors in ").append(sourceFile).append(":\n");
                sb.append(reporter.getErrorMessages());
            }
        }
        return sb.toString();
    }

    /**
     * Gets the total number of source files processed.
     *
     * @return Total number of files
     */
    public int getTotalFileCount() {
        return sourceFiles.size();
    }

    /**
     * Gets the list of source files in compilation order.
     *
     * @return List of source file names
     */
    @Nonnull
    public List<String> getSourceFiles() {
        return Collections.unmodifiableList(sourceFiles);
    }

    /**
     * Checks if any source file has fatal errors.
     *
     * @return true if any file has fatal errors
     */
    public boolean hasFatalErrors() {
        return errorReporters.values().stream().anyMatch(ErrorReporter::hasFatalErrors);
    }

    @Override
    public String toString() {
        return String.format("CompilationResult{successful=%b, errors=%d}",
            isSuccessful(), getAllErrors().size());
    }
}