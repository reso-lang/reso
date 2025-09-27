package com.reso.compiler.api;

/**
 * Options for the Reso compiler.
 */
public class CompilerOptions {
    private boolean optimizationEnabled = false;
    private int optimizationLevel = 2; // 0-3, where 0 is no optimization
    private boolean debugInfoEnabled = false;
    private boolean verboseOutput = false;
    private boolean printIr = false;

    /**
     * Creates new compiler options with default values.
     */
    public CompilerOptions() {
        // Default values set in field initializers
    }

    /**
     * Enables or disables optimization.
     *
     * @param enabled Whether optimization is enabled
     * @return This options instance for method chaining
     */
    public CompilerOptions withOptimization(boolean enabled) {
        this.optimizationEnabled = enabled;
        return this;
    }

    /**
     * Sets the optimization level.
     *
     * @param level The optimization level (0-3)
     * @return This options instance for method chaining
     */
    public CompilerOptions withOptimizationLevel(int level) {
        if (level < 0 || level > 3) {
            throw new IllegalArgumentException("Optimization level must be between 0 and 3");
        }
        this.optimizationLevel = level;
        return this;
    }

    /**
     * Enables or disables debug information.
     *
     * @param enabled Whether debug information is enabled
     * @return This options instance for method chaining
     */
    public CompilerOptions withDebugInfo(boolean enabled) {
        this.debugInfoEnabled = enabled;
        return this;
    }

    /**
     * Enables or disables verbose output.
     *
     * @param enabled Whether verbose output is enabled
     * @return This options instance for method chaining
     */
    public CompilerOptions withVerboseOutput(boolean enabled) {
        this.verboseOutput = enabled;
        return this;
    }

    /**
     * Enables or disables printing of the LLVM IR.
     *
     * @param enabled Whether printing of the LLVM IR is enabled
     * @return This options instance for method chaining
     */
    public CompilerOptions withPrintIr(boolean enabled) {
        this.printIr = enabled;
        return this;
    }

    /**
     * Checks if optimization is enabled.
     *
     * @return true if optimization is enabled
     */
    public boolean isOptimizationEnabled() {
        return optimizationEnabled;
    }

    /**
     * Gets the optimization level.
     *
     * @return The optimization level (0-3)
     */
    public int getOptimizationLevel() {
        return optimizationLevel;
    }

    /**
     * Checks if debug information is enabled.
     *
     * @return true if debug information is enabled
     */
    public boolean isDebugInfoEnabled() {
        return debugInfoEnabled;
    }

    /**
     * Checks if verbose output is enabled.
     *
     * @return true if verbose output is enabled
     */
    public boolean isVerboseOutput() {
        return verboseOutput;
    }

    /**
     * Checks if printing of the LLVM IR is enabled.
     *
     * @return true if printing of the LLVM IR is enabled
     */
    public boolean isPrintIr() {
        return printIr;
    }
}