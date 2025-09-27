package com.reso.compiler.core;

import com.reso.compiler.api.CompilerOptions;
import com.reso.compiler.api.ResoCompiler;
import com.reso.compiler.codegen.LlvmCodeGenerator;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.parser.ParseResult;
import com.reso.compiler.parser.Parser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrModule;
import com.reso.llvm.api.IrPassBuilderOptions;
import com.reso.llvm.api.IrTargetMachine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Implementation of the Reso compiler.
 */
public class ResoCompilerImpl implements ResoCompiler {
    private final CompilerOptions options;

    /**
     * Creates a new Reso compiler with the specified options.
     *
     * @param options The compiler options
     */
    public ResoCompilerImpl(@Nonnull CompilerOptions options) {
        this.options = Objects.requireNonNull(options, "Options cannot be null");
    }

    @Override
    @Nonnull
    public CompilationResult compile(@Nonnull List<String> sourceFiles, @Nullable String outputFile)
        throws IOException {
        Objects.requireNonNull(sourceFiles, "Source files cannot be null");

        if (sourceFiles.isEmpty()) {
            throw new IllegalArgumentException("Source files list cannot be empty");
        }

        // Create compilation units for all source files
        List<CompilationUnit> compilationUnits = transformToCompilationUnits(sourceFiles);

        return compileUnits(compilationUnits, outputFile);
    }

    @Override
    @Nonnull
    public CompilationResult compileString(@Nonnull Map<String, String> sources,
                                           @Nullable String outputFile) {
        Objects.requireNonNull(sources, "Sources cannot be null");

        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Sources map cannot be empty");
        }

        // Create compilation units for all source strings
        List<CompilationUnit> compilationUnits = sources.entrySet().stream()
            .map(entry -> new CompilationUnit(entry.getValue(), entry.getKey(), null))
            .collect(Collectors.toList());

        return compileUnits(compilationUnits, outputFile);
    }

    /**
     * Compiles compilation units.
     *
     * @param compilationUnits The compilation units to process
     * @param outputFile       The output file path, or null to not write to a file
     * @return The compilation result
     */
    @Nonnull
    private CompilationResult compileUnits(@Nonnull List<CompilationUnit> compilationUnits,
                                           @Nullable String outputFile) {
        // Create a combined error reporter for global errors
        ErrorReporter globalErrorReporter = new ErrorReporter("compilation_process");

        List<CompilationUnit> combinedUnits = new ArrayList<>();
        combinedUnits.addAll(getBuiltinCompilationUnits());
        combinedUnits.addAll(compilationUnits);

        try (LlvmCodeGenerator codeGenerator = new LlvmCodeGenerator("reso_module",
            globalErrorReporter)) {

            // Phase 1: Parse all files
            parseAllFiles(combinedUnits);

            // Check if any files failed to parse
            boolean hasParseErrors = combinedUnits.stream()
                .anyMatch(unit -> unit.getErrorReporter().hasErrors());

            if (hasParseErrors) {
                return createResult(null, combinedUnits, globalErrorReporter);
            }

            // Phase 2: Register all resource declarations across all files
            codeGenerator.registerResourceDeclarations(codeGenerator, combinedUnits);

            // Phase 3: Register all function declarations across all files
            codeGenerator.registerFunctionDeclarations(codeGenerator, combinedUnits);

            // Check for declaration registration errors
            if (hasAnyErrors(combinedUnits) || globalErrorReporter.hasErrors()) {
                return createResult(null, combinedUnits, globalErrorReporter);
            }

            // Phase 4: Generate code for all files
            // At this point, all symbols are registered and cross-file references can be resolved
            generateCodeForAllFiles(codeGenerator, combinedUnits);

            // Check for code generation errors
            if (hasAnyErrors(combinedUnits) || globalErrorReporter.hasErrors()) {
                return createResult(null, combinedUnits, globalErrorReporter);
            }

            // Complete compilation pipeline
            CompilationResult singleResult =
                finishCompilation(codeGenerator, globalErrorReporter, outputFile);

            // Convert to multi-unit result
            return createResult(singleResult.llvmIr(), combinedUnits, globalErrorReporter);

        } catch (Exception e) {
            globalErrorReporter.fatal("Unexpected compilation error: " + e.getMessage(), e);
            return createResult(null, combinedUnits, globalErrorReporter);
        }
    }

    /**
     * Parses all compilation units.
     *
     * @param compilationUnits The compilation units to parse
     */
    private void parseAllFiles(@Nonnull List<CompilationUnit> compilationUnits) {
        for (CompilationUnit unit : compilationUnits) {
            if (!unit.getErrorReporter().hasErrors()) {
                ParseResult parseResult =
                    Parser.parse(unit.getSourceCode(), unit.getErrorReporter());
                unit.setParseResult(parseResult);
            }
        }
    }

    /**
     * Generates code for all files.
     * This method processes all files in the context of the shared symbol table.
     *
     * @param codeGenerator    The code generator
     * @param compilationUnits The compilation units
     */
    private void generateCodeForAllFiles(@Nonnull LlvmCodeGenerator codeGenerator,
                                         @Nonnull List<CompilationUnit> compilationUnits) {
        for (CompilationUnit unit : compilationUnits) {
            if (unit.isParsed()) {
                codeGenerator.generateCode(unit.getParseResult().getTree(), unit.getErrorReporter(),
                    unit.getFileIdentifier());
            }
        }
    }

    /**
     * Checks if any compilation unit has errors.
     *
     * @param compilationUnits The compilation units to check
     * @return true if any unit has errors
     */
    private boolean hasAnyErrors(@Nonnull List<CompilationUnit> compilationUnits) {
        return compilationUnits.stream().anyMatch(unit -> unit.getErrorReporter().hasErrors());
    }

    /**
     * Creates a compilation result from compilation units.
     *
     * @param llvmIr           The generated LLVM IR
     * @param compilationUnits The compilation units
     * @return The compilation result
     */
    @Nonnull
    private CompilationResult createResult(@Nullable String llvmIr,
                                           @Nonnull List<CompilationUnit> compilationUnits,
                                           @Nonnull ErrorReporter globalErrorReporter) {
        Map<String, ErrorReporter> errorReporters = compilationUnits.stream()
            .collect(Collectors.toMap(
                CompilationUnit::getSourceName,
                CompilationUnit::getErrorReporter,
                (existing, replacement) -> existing, // Keep first if duplicate names
                LinkedHashMap::new
            ));

        if ((globalErrorReporter.hasErrors() || !globalErrorReporter.getErrors().isEmpty())) {
            errorReporters.put("[compilation]", globalErrorReporter);
        }

        List<String> sourceFiles = compilationUnits.stream()
            .map(CompilationUnit::getSourceName)
            .collect(Collectors.toList());

        return new CompilationResult(llvmIr, errorReporters, sourceFiles);
    }

    /**
     * Completes the compilation pipeline (verification, optimization, IR generation, file writing).
     *
     * @param codeGenerator The code generator
     * @param errorReporter The error reporter
     * @param outputFile    The output file path, or null to not write to a file
     * @return The compilation result
     */
    @Nonnull
    private CompilationResult finishCompilation(@Nonnull LlvmCodeGenerator codeGenerator,
                                                @Nonnull ErrorReporter errorReporter,
                                                @Nullable String outputFile) {
        try {
            // Verify module
            IrModule irModule = codeGenerator.getCodeGenerationContext().getIrModule();
            if (!IrFactory.verifyModule(irModule, options.isVerboseOutput())) {
                errorReporter.fatal("Module verification failed");
                return new CompilationResult(null, errorReporter);
            }

            // Run optimization if enabled
            if (options.isOptimizationEnabled()) {
                IrFactory.initializeTargetMachine();

                try (IrTargetMachine targetMachine = IrFactory.createHostTargetMachine()) {
                    // Create optimization options
                    IrPassBuilderOptions passOptions = IrFactory.createPassBuilderOptions();
                    passOptions.setLoopVectorization(true)
                        .setSlpVectorization(true)
                        .setLoopUnrolling(true)
                        .setLoopInterleaving(true)
                        .setVerifyEach(options.isDebugInfoEnabled())
                        .setDebugLogging(options.isVerboseOutput());

                    // Run optimization passes
                    IrFactory.optimizeModule(irModule, targetMachine,
                        options.getOptimizationLevel());

                    passOptions.close();
                }
            }

            // Generate IR
            String ir = codeGenerator.generateIr();

            // Write to output file if specified
            if (outputFile != null) {
                try {
                    Path outputPath = Path.of(outputFile);
                    // Create the parent directory if it doesn't exist
                    Path parentDir = outputPath.getParent();
                    if (parentDir != null) {
                        Files.createDirectories(parentDir);
                    }
                    // Write the file
                    Files.writeString(outputPath, ir);
                } catch (IOException e) {
                    errorReporter.error("Failed to write output file: " + e.getMessage());
                    return new CompilationResult(null, errorReporter);
                }
            }

            return new CompilationResult(ir, errorReporter);

        } catch (Exception e) {
            errorReporter.fatal("Error during compilation process: " + e.getMessage(), e);
            return new CompilationResult(null, errorReporter);
        }
    }

    private List<CompilationUnit> transformToCompilationUnits(@Nonnull List<String> sourceFiles) {
        List<CompilationUnit> compilationUnits = new ArrayList<>();

        for (String sourceFile : sourceFiles) {
            Path path = Path.of(sourceFile);
            try {
                String sourceCode = Files.readString(path);
                CompilationUnit unit = new CompilationUnit(sourceCode, sourceFile, path);
                compilationUnits.add(unit);
            } catch (IOException e) {
                // Create a compilation unit with error for this file
                CompilationUnit unit = new CompilationUnit("", sourceFile, path);
                unit.getErrorReporter().fatal("Failed to read source file: " + e.getMessage(), e);
                compilationUnits.add(unit);
            }
        }

        return compilationUnits;
    }

    private List<CompilationUnit> getBuiltinCompilationUnits() {
        List<String> builtinSources = List.of(
            "/builtin/string.reso"
        );

        return builtinSources.stream()
            .map(this::loadBuiltinResource)
            .collect(Collectors.toList());
    }

    private CompilationUnit loadBuiltinResource(String resourcePath) {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Builtin resource not found: " + resourcePath);
            }

            String sourceCode = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return new CompilationUnit(sourceCode, resourcePath, Path.of(resourcePath));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load builtin resource: " + resourcePath, e);
        }
    }
}