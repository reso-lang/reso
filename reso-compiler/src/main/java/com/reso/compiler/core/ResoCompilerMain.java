package com.reso.compiler.core;

import com.reso.compiler.api.CompilerOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the Reso compiler.
 */
public class ResoCompilerMain {
    /**
     * Main method.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            // Parse command line arguments
            CompilerArguments arguments = parseArguments(args);

            // Create compiler options
            CompilerOptions options = createCompilerOptions(arguments);

            // Create compiler
            ResoCompilerImpl compiler = new ResoCompilerImpl(options);

            // Compile source files
            CompilationResult result =
                compiler.compile(arguments.sourceFiles, arguments.outputFile);

            // Display results
            if (result.isSuccessful()) {
                displayCompilationOutput(result.llvmIr(), arguments.outputFile, options);

                System.out.println(result.getErrorMessages());
            } else {
                System.err.println("Compilation failed:");
                System.err.println(result.getErrorMessages());

                System.exit(1);
            }

        } catch (IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());
            printUsage();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error reading input files: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Displays compilation output (LLVM IR) based on options.
     */
    private static void displayCompilationOutput(String llvmIr, String outputFile,
                                                 CompilerOptions options) {
        if (outputFile != null) {
            System.out.println("LLVM IR written to: " + outputFile);
        } else if (options.isPrintIr()) {
            System.out.println(llvmIr);
        }
    }

    /**
     * Creates compiler options based on parsed arguments.
     */
    private static CompilerOptions createCompilerOptions(CompilerArguments arguments) {
        return new CompilerOptions()
            .withOptimization(arguments.enableOptimization)
            .withOptimizationLevel(arguments.optimizationLevel)
            .withDebugInfo(arguments.enableDebugInfo)
            .withVerboseOutput(arguments.verbose)
            .withPrintIr(arguments.printIr);
    }

    /**
     * Parses command line arguments.
     */
    private static CompilerArguments parseArguments(String[] args) {
        if (args.length == 0) {
            // Default behavior: compile sample file
            return new CompilerArguments(
                List.of("examples/HelloWorld.reso"),
                null,
                true,
                2,
                false,
                false,
                true
            );
        }

        List<String> sourceFiles = new ArrayList<>();
        String outputFile = null;
        boolean enableOptimization = false;
        int optimizationLevel = 2;
        boolean enableDebugInfo = false;
        boolean verbose = false;
        boolean printIr = true;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "-o", "--output":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException(
                            "Output file path required after " + arg);
                    }
                    outputFile = args[++i];
                    break;

                case "-O", "--optimize":
                    enableOptimization = true;
                    // Check if optimization level follows
                    if (i + 1 < args.length && args[i + 1].matches("\\d+")) {
                        optimizationLevel = Integer.parseInt(args[++i]);
                        if (optimizationLevel < 0 || optimizationLevel > 3) {
                            throw new IllegalArgumentException("Optimization level must be 0-3");
                        }
                    }
                    break;

                case "-g", "--debug":
                    enableDebugInfo = true;
                    break;

                case "-v", "--verbose":
                    verbose = true;
                    break;

                case "--no-print-ir":
                    printIr = false;
                    break;

                case "-h", "--help":
                    printUsage();
                    System.exit(0);
                    break;

                default:
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                    // It's a source file
                    sourceFiles.add(arg);
                    break;
            }
        }

        if (sourceFiles.isEmpty()) {
            throw new IllegalArgumentException("At least one source file must be specified");
        }

        return new CompilerArguments(sourceFiles, outputFile, enableOptimization,
            optimizationLevel, enableDebugInfo, verbose, printIr);
    }

    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Usage: [OPTIONS] <source-file> [<source-file>...]");
        System.out.println();
        System.out.println("Compiles one or more Reso source files into LLVM IR.");
        System.out.println(
            "Multiple files are compiled together for proper cross-file symbol resolution.");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -o, --output <file>     Write output to specified file");
        System.out.println("  -O, --optimize [level]  Enable optimization (default level: 2)");
        System.out.println(
            "                          Levels: 0 (none), 1 (basic), 2 (default), 3 (aggressive)");
        System.out.println("  -g, --debug             Include debug information");
        System.out.println("  -v, --verbose           Enable verbose output");
        System.out.println("  --no-print-ir"
            + "           Don't print LLVM IR to stdout when no output file specified");
        System.out.println("  -h, --help              Show this help message");
    }

    /**
     * Container for parsed command line arguments.
     */
    private record CompilerArguments(List<String> sourceFiles, String outputFile,
                                     boolean enableOptimization,
                                     int optimizationLevel, boolean enableDebugInfo,
                                     boolean verbose, boolean printIr) {
    }
}