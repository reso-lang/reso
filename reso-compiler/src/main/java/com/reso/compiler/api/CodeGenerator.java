package com.reso.compiler.api;

import com.reso.compiler.codegen.LlvmCodeGenerator;
import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.core.CompilationUnit;
import com.reso.compiler.errors.ErrorReporter;
import java.util.List;
import javax.annotation.Nonnull;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Interface for code generators.
 */
public interface CodeGenerator extends AutoCloseable {
    /**
     * Registers function declarations from all compilation units.
     *
     * @param codeGenerator    The LLVM code generator
     * @param compilationUnits The list of compilation units
     */
    void registerFunctionDeclarations(@Nonnull LlvmCodeGenerator codeGenerator,
                                      @Nonnull List<CompilationUnit> compilationUnits);

    /**
     * Registers resource declarations from all compilation units.
     *
     * @param codeGenerator    The LLVM code generator
     * @param compilationUnits The list of compilation units
     */
    void registerResourceDeclarations(@Nonnull LlvmCodeGenerator codeGenerator,
                                      @Nonnull List<CompilationUnit> compilationUnits);

    /**
     * Generates code for a parse tree.
     *
     * @param tree           The parse tree
     * @param errorReporter  The error reporter
     * @param fileIdentifier A unique identifier for the source file
     */
    void generateCode(@Nonnull ParseTree tree, @Nonnull ErrorReporter errorReporter,
                      @Nonnull String fileIdentifier);

    /**
     * Generates LLVM IR code.
     *
     * @return The generated LLVM IR code
     */
    @Nonnull
    String generateIr();

    /**
     * Gets the code generation context.
     *
     * @return The code generation context
     */
    @Nonnull
    CodeGenerationContext getCodeGenerationContext();

    /**
     * Closes this code generator and releases any resources.
     *
     * @throws Exception if an error occurs
     */
    @Override
    void close() throws Exception;
}