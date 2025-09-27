package com.reso.compiler.api;

import com.reso.compiler.core.CompilationResult;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface for the Reso compiler.
 */
public interface ResoCompiler {

    /**
     * Compiles source files.
     *
     * @param sourceFiles The list of source file paths
     * @param outputFile  The output file path, or null to not write to a file
     * @return The compilation result
     * @throws IOException If an I/O error occurs reading any source file
     */
    @Nonnull
    CompilationResult compile(@Nonnull List<String> sourceFiles, @Nullable String outputFile)
        throws IOException;

    /**
     * Compiles source code strings.
     * Each entry in the sources map represents a file with
     * its name as the key and content as the value.
     *
     * @param sources    A map of source name to source code content
     * @param outputFile The output file path, or null to not write to a file
     * @return The compilation result
     */
    @Nonnull
    CompilationResult compileString(@Nonnull Map<String, String> sources,
                                    @Nullable String outputFile);
}