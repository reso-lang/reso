package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reso.compiler.api.CompilerOptions;
import com.reso.compiler.core.CompilationResult;
import com.reso.compiler.core.ResoCompilerImpl;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrContext;
import com.reso.llvm.api.IrModule;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for tests that compile Reso source code to LLVM IR.
 * Provides utilities for compilation and IR verification.
 */
public abstract class BaseTest {

    protected ResoCompilerImpl compiler;
    protected CompilerOptions options;
    protected static final int POINTER_SIZE = getTargetPointerSize();

    @BeforeEach
    protected void setupCompiler() {
        // Create compiler options optimized for testing
        options = new CompilerOptions()
            .withOptimization(false)           // Disable optimization for predictable IR
            .withDebugInfo(false)              // No debug info for cleaner IR
            .withVerboseOutput(false)          // Quiet output
            .withPrintIr(false);               // We'll get IR from result

        compiler = new ResoCompilerImpl(options);
    }

    /**
     * Retrieves the target pointer size (in bytes) for the current platform.
     *
     * @return Pointer size in bytes
     */
    private static int getTargetPointerSize() {
        IrContext irContext = IrFactory.createContext();
        IrModule irModule = IrFactory.createModule(irContext, "temp");

        int pointerSize = IrFactory.getTargetPointerSize(irModule);

        irModule.close();
        irContext.close();
        return pointerSize;
    }

    /**
     * Compiles Reso source code to LLVM IR.
     *
     * @param sourceCode The Reso source code to compile
     * @param testName   Name of the test (used for error reporting)
     * @return The compilation result
     */
    @Nonnull
    protected CompilationResult compileToIr(@Nonnull String sourceCode, @Nonnull String testName) {
        return compiler.compileString(Map.of(testName + ".reso", sourceCode), null);
    }

    /**
     * Compiles multiple Reso source files to LLVM IR.
     *
     * @param sourceFiles Map of filename to source code
     * @return The compilation result
     */
    @Nonnull
    protected CompilationResult compileMultipleFiles(@Nonnull Map<String, String> sourceFiles) {
        return compiler.compileString(sourceFiles, null);
    }

    /**
     * Compiles Reso source code and expects successful compilation.
     *
     * @param sourceCode The Reso source code to compile
     * @param testName   Name of the test
     * @return The generated LLVM IR
     */
    @Nonnull
    protected String compileAndExpectSuccess(@Nonnull String sourceCode, @Nonnull String testName) {
        CompilationResult result = compileToIr(sourceCode, testName);

        assertTrue(result.isSuccessful(),
            "Compilation should succeed for " + testName + ". Errors: "
                + result.getErrorMessages());

        assertNotNull(result.llvmIr(), "LLVM IR should not be null for successful compilation");

        return result.llvmIr();
    }

    /**
     * Compiles multiple Reso source files and expects successful compilation.
     *
     * @param sourceFiles Map of filename to source code
     * @param testName    Name of the test
     * @return The generated LLVM IR
     */
    @Nonnull
    protected String compileMultipleFilesAndExpectSuccess(@Nonnull Map<String, String> sourceFiles,
                                                          @Nonnull String testName) {
        CompilationResult result = compileMultipleFiles(sourceFiles);

        assertTrue(result.isSuccessful(),
            "Compilation should succeed for " + testName + ". Errors: "
                + result.getErrorMessages());

        assertNotNull(result.llvmIr(), "LLVM IR should not be null for successful compilation");

        return result.llvmIr();
    }

    /**
     * Compiles Reso source code and expects compilation failure.
     *
     * @param sourceCode The Reso source code to compile
     * @param testName   Name of the test
     * @return The error messages
     */
    @Nonnull
    protected String compileAndExpectFailure(@Nonnull String sourceCode, @Nonnull String testName) {
        CompilationResult result = compileToIr(sourceCode, testName);

        assertFalse(result.isSuccessful(),
            "Compilation should fail for " + testName);

        assertNull(result.llvmIr(), "LLVM IR should be null for failed compilation");

        return result.getErrorMessages();
    }

    /**
     * Compiles multiple Reso source files and expects compilation failure.
     *
     * @param sourceFiles Map of filename to source code
     * @param testName    Name of the test
     * @return The compilation error messages
     */
    @Nonnull
    protected String compileMultipleFilesAndExpectFailure(@Nonnull Map<String, String> sourceFiles,
                                                          @Nonnull String testName) {
        CompilationResult result = compileMultipleFiles(sourceFiles);

        assertFalse(result.isSuccessful(),
            "Compilation should fail for " + testName + ". Generated IR: " + result.llvmIr());

        assertFalse(result.getErrorMessages().isEmpty(),
            "Error messages should not be empty for failed compilation");

        return result.getErrorMessages();
    }

    /**
     * Compiles Reso source code and expects successful compilation with warnings.
     *
     * @param sourceCode The Reso source code to compile
     * @param testName   Name of the test
     * @return The warning messages
     */
    protected String compileAndExpectWarnings(@Nonnull String sourceCode,
                                              @Nonnull String testName) {
        CompilationResult result = compileToIr(sourceCode, testName);

        assertTrue(result.isSuccessful(),
            "Compilation should succeed for " + testName + " but produced warnings");

        assertNotNull(result.llvmIr(),
            "LLVM IR should not be null for successful compilation with warnings");

        // Check if there are warnings
        assertFalse(result.getErrorMessages().isEmpty(),
            "Expected warnings but none were produced");

        return String.join("\n", result.getErrorMessages());
    }

    /**
     * Verifies that the generated IR contains expected patterns.
     *
     * @param ir               The generated LLVM IR
     * @param expectedPatterns Expected patterns that should be present in the IR
     */
    protected void assertIrContains(@Nonnull String ir, @Nonnull String... expectedPatterns) {

        for (String patternString : expectedPatterns) {
            String regexPattern = patternString
                .replaceAll("(\\S+)", "\\\\S*$1\\\\S*")
                .replaceAll("\\s+", "\\\\s+");

            Pattern pattern = Pattern.compile(regexPattern);
            Matcher matcher = pattern.matcher(ir);

            boolean found = matcher.find();

            assertTrue(found,
                "IR should contain pattern: " + patternString + "\n\nGenerated IR:\n" + ir);
        }
    }

    /**
     * Verifies that the generated IR contains expected patterns.
     *
     * @param ir               The generated LLVM IR
     * @param expectedPatterns Expected patterns that should be present in the IR
     */
    protected void assertIrContainsInOrder(@Nonnull String ir,
                                           @Nonnull String... expectedPatterns) {
        int lastPosition = 0;

        for (String patternString : expectedPatterns) {
            String regexPattern = patternString
                .replaceAll("(\\S+)", "\\\\S*$1\\\\S*")
                .replaceAll("\\s+", "\\\\s+");

            Pattern pattern = Pattern.compile(regexPattern);
            Matcher matcher = pattern.matcher(ir);

            boolean found = matcher.find(lastPosition);


            assertTrue(found,
                "IR should contain pattern: " + patternString
                    + " after position " + lastPosition
                    + "\n\nGenerated IR:\n" + ir);

            lastPosition = matcher.start();
        }
    }

    /**
     * Creates a simple Reso function wrapper for testing variable declarations.
     *
     * @param variableDeclarations The variable declarations to test
     * @return Complete Reso program with main function
     */
    @Nonnull
    protected String wrapInMainFunction(@Nonnull String variableDeclarations) {
        return """
            def main() -> i32:
            %s
                return 0
            """.formatted(indent(variableDeclarations, "    "));
    }

    /**
     * Indents each line of text with the specified prefix.
     *
     * @param text   The text to indent
     * @param prefix The indentation prefix
     * @return The indented text
     */
    @Nonnull
    private String indent(@Nonnull String text, @Nonnull String prefix) {
        return text.lines()
            .map(line -> line.trim().isEmpty() ? line : prefix + line)
            .reduce("", (a, b) -> a + "\n" + b);
    }

    /**
     * Extracts a specific function from the generated IR.
     *
     * @param ir           The complete LLVM IR
     * @param functionName The name of the function to extract
     * @return The function definition, or null if not found
     */
    @Nullable
    protected String extractFunction(@Nonnull String ir, @Nonnull String functionName) {
        Pattern pattern = Pattern.compile("define.*@" + functionName + "\\(");
        Matcher matcher = pattern.matcher(ir);
        if (!matcher.find()) {
            return null;
        }

        int start = matcher.start();

        // Find the end of the function (next define or end of file)
        int end = ir.indexOf("\ndefine", start + 1);
        if (end == -1) {
            end = ir.length();
        }

        return ir.substring(start, end).trim();
    }

    /**
     * Creates expected LLVM IR patterns for variable allocations.
     */
    protected static class IrPatterns {
        public static String alloca(String variableName, String type) {
            return "%" + variableName + " = alloca " + type;
        }

        public static String store(String value, String type, String variable) {
            return "store " + type + " " + value + " ptr %" + variable;
        }

        public static String load(String type, String variable) {
            return "load " + type + ", ptr %" + variable;
        }

        /**
         * Pattern for sign extension instruction (signed integer widening).
         */
        public static String sext(String fromType, String variable, String toType) {
            return "sext " + fromType + " %" + variable + " to " + toType;
        }

        /**
         * Pattern for zero extension instruction (unsigned integer widening).
         */
        public static String zext(String fromType, String variable, String toType) {
            return "zext " + fromType + " %" + variable + " to " + toType;
        }

        /**
         * Pattern for truncation instruction (integer narrowing).
         */
        public static String trunc(String fromType, String variable, String toType) {
            return "trunc " + fromType + " %" + variable + " to " + toType;
        }

        /**
         * Pattern for floating-point extension instruction (float widening).
         */
        public static String fpext(String fromType, String variable, String toType) {
            return "fpext " + fromType + " %" + variable + " to " + toType;
        }

        /**
         * Pattern for floating-point truncation instruction (float narrowing).
         */
        public static String fptrunc(String fromType, String variable, String toType) {
            return "fptrunc " + fromType + " %" + variable + " to " + toType;
        }

        /**
         * Pattern for signed integer to floating-point conversion.
         */
        public static String sitofp(String fromType, String variable, String toType) {
            return "sitofp " + fromType + " %" + variable + " to " + toType;
        }

        /**
         * Pattern for unsigned integer to floating-point conversion.
         */
        public static String uitofp(String fromType, String variable, String toType) {
            return "uitofp " + fromType + " %" + variable + " to " + toType;
        }

        /**
         * Pattern for floating-point to signed integer conversion.
         */
        public static String fptosi(String fromType, String variable, String toType) {
            return "fptosi " + fromType + " %" + variable + " to " + toType;
        }

        /**
         * Pattern for floating-point to unsigned integer conversion.
         */
        public static String fptoui(String fromType, String variable, String toType) {
            return "fptoui " + fromType + " %" + variable + " to " + toType;
        }

        /**
         * Pattern for addition operation with left and right operands.
         */
        public static String add(String type, String left, String right) {
            return "add " + type + " " + left + " " + right;
        }

        /**
         * Pattern for subtraction operation with left and right operands.
         */
        public static String sub(String type, String left, String right) {
            return "sub " + type + " " + left + " " + right;
        }

        /**
         * Pattern for multiplication operation with left and right operands.
         */
        public static String mul(String type, String left, String right) {
            return "mul " + type + " " + left + " " + right;
        }

        /**
         * Pattern for signed division operation with left and right operands.
         */
        public static String sdiv(String type, String left, String right) {
            return "sdiv " + type + " " + left + " " + right;
        }

        /**
         * Pattern for unsigned division operation with left and right operands.
         */
        public static String udiv(String type, String left, String right) {
            return "udiv " + type + " " + left + " " + right;
        }

        /**
         * Pattern for signed remainder operation with left and right operands.
         */
        public static String srem(String type, String left, String right) {
            return "srem " + type + " " + left + " " + right;
        }

        /**
         * Pattern for unsigned remainder operation with left and right operands.
         */
        public static String urem(String type, String left, String right) {
            return "urem " + type + " " + left + " " + right;
        }

        /**
         * Pattern for floating-point addition operation with left and right operands.
         */
        public static String fadd(String type, String left, String right) {
            return "fadd " + type + " " + left + " " + right;
        }

        /**
         * Pattern for floating-point subtraction operation with left and right operands.
         */
        public static String fsub(String type, String left, String right) {
            return "fsub " + type + " " + left + " " + right;
        }

        /**
         * Pattern for floating-point multiplication operation with left and right operands.
         */
        public static String fmul(String type, String left, String right) {
            return "fmul " + type + " " + left + " " + right;
        }

        /**
         * Pattern for floating-point division operation with left and right operands.
         */
        public static String fdiv(String type, String left, String right) {
            return "fdiv " + type + " " + left + " " + right;
        }

        /**
         * Pattern for floating-point remainder operation with left and right operands.
         */
        public static String frem(String type, String left, String right) {
            return "frem " + type + " " + left + " " + right;
        }

        /**
         * Pattern for integer comparison operation with left and right operands.
         */
        public static String icmp(String condition, String type, String left, String right) {
            return "icmp " + condition + " " + type + " " + left + " " + right;
        }

        /**
         * Pattern for floating-point comparison operation with left and right operands.
         */
        public static String fcmp(String condition, String type, String left, String right) {
            return "fcmp " + condition + " " + type + " " + left + " " + right;
        }

        /**
         * Pattern for bitwise AND instruction.
         */
        public static String bitwiseAnd(String type, String left, String right) {
            return "and " + type + " " + left + " " + right;
        }

        /**
         * Pattern for bitwise OR instruction.
         */
        public static String bitwiseOr(String type, String left, String right) {
            return "or " + type + " " + left + " " + right;
        }

        /**
         * Pattern for bitwise XOR instruction.
         */
        public static String bitwiseXor(String type, String left, String right) {
            return "xor " + type + " " + left + " " + right;
        }

        /**
         * Pattern for left shift instruction.
         */
        public static String leftShift(String type, String value, String amount) {
            return "shl " + type + " " + value + " " + amount;
        }

        /**
         * Pattern for arithmetic right shift instruction (signed integers).
         */
        public static String arithmeticRightShift(String type, String value, String amount) {
            return "ashr " + type + " " + value + " " + amount;
        }

        /**
         * Pattern for logical right shift instruction (unsigned integers).
         */
        public static String logicalRightShift(String type, String value, String amount) {
            return "lshr " + type + " " + value + " " + amount;
        }

        /**
         * Pattern for function call instruction.
         */
        public static String functionCall(String functionName, String returnType,
                                          List<Map.Entry<String, String>> args) {
            return functionCall(functionName, functionName, returnType, args);
        }

        /**
         * Pattern for function call instruction.
         */
        public static String functionCall(String functionName, String variable, String returnType,
                                          List<Map.Entry<String, String>> args) {
            StringBuilder pattern = new StringBuilder();
            if (!"void".equals(returnType)) {
                pattern.append("%").append(variable).append("_result = ");
            }
            pattern.append("call ").append(returnType).append(" @").append(functionName)
                .append("\\(");

            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    pattern.append(" ");
                }
                String argType = args.get(i).getKey();
                pattern.append(argType).append(" ").append(args.get(i).getValue());
            }

            return pattern.toString();
        }

        /**
         * Pattern for select instruction (ternary operator).
         */
        public static String select(String conditionType, String conditionVar, String valueType,
                                    String trueValue, String falseValue) {
            return "select " + conditionType + " " + conditionVar + " " + valueType + " "
                + trueValue + " " + valueType + " " + falseValue;
        }


        /**
         * Pattern for integer negation instruction (unary minus for integers).
         */
        public static String unaryNeg(String type, String operand) {
            return "sub " + type + " 0, %" + operand;
        }

        /**
         * Pattern for floating-point negation instruction (unary minus for floats).
         */
        public static String unaryFNeg(String type, String operand) {
            return "fneg " + type + " %" + operand;
        }

        /**
         * Pattern for logical NOT operation (for boolean values).
         */
        public static String logicalNot(String type, String operand) {
            return "xor " + type + " %" + operand + " true";
        }

        /**
         * Pattern for bitwise NOT operation (bitwise complement for integers).
         */
        public static String bitwiseNot(String type, String operand) {
            return "xor " + type + " %" + operand + " -1";
        }

        /**
         * Pattern for conditional branch instruction (used in short-circuit evaluation).
         */
        public static String conditionalBranch(String condition, String trueBlock,
                                               String falseBlock) {
            return "br i1 %" + condition + " label %" + trueBlock + " label %" + falseBlock;
        }

        /**
         * Pattern for unconditional branch instruction.
         */
        public static String unconditionalBranch(String targetBlock) {
            return "br label %" + targetBlock;
        }

        /**
         * Pattern for basic block label.
         */
        public static String label(String labelName) {
            return labelName + ":";
        }

        /**
         * Pattern for complete PHI node with specific incoming values and blocks.
         * This verifies the exact PHI structure including which values come from which blocks.
         */
        public static String phi(String type, String name, String value1, String block1,
                                 String value2, String block2) {
            return "%" + name + " = phi " + type + " \\[ " + value1 + " " + block1 + " \\], \\[ "
                + value2 + " " + block2 + " \\]";
        }

        /**
         * Pattern for logical AND PHI node with standard short-circuit structure.
         * For AND: false from original block, right value from eval_right block.
         */
        public static String logicalAndPhi(String name, String originalBlock, String rightValue,
                                           String evalRightBlock) {
            return "%" + name + " = phi i1 \\[ false " + originalBlock + " \\], \\[ " + rightValue
                + " " + evalRightBlock + " \\]";
        }

        /**
         * Pattern for logical OR PHI node with standard short-circuit structure.
         * For OR: true from original block, right value from eval_right block.
         */
        public static String logicalOrPhi(String name, String originalBlock, String rightValue,
                                          String evalRightBlock) {
            return "%" + name + " = phi i1 \\[ true " + originalBlock + " \\], \\[  " + rightValue
                + " " + evalRightBlock + " \\]";
        }

        /**
         * Pattern for function definition.
         */
        public static String functionDefinition(String functionName, String returnType) {
            return "define " + returnType + " @" + functionName + "\\(";
        }

        /**
         * Pattern for return statement with value.
         */
        public static String returnStatement(String type, String value) {
            return "ret " + type + " " + value;
        }

        /**
         * Pattern for struct type definition.
         */
        public static String structType(String structName) {
            return "%" + structName + " = type";
        }

        /**
         * Pattern for field access in resource.
         */
        public static String fieldAccess(String variable, String structType, String deref,
                                         int fieldIndex) {
            return variable + " = getelementptr inbounds nuw " + structType + " ptr %" + deref
                + " i32 0 i32 " + fieldIndex;
        }

        /**
         * Pattern for field access in resource.
         */
        public static String arrayAccess(String variable, String structType, String deref,
                                         int fieldIndex) {
            return variable + " = getelementptr inbounds " + structType + " ptr %" + deref + " i"
                + POINTER_SIZE + " " + fieldIndex;
        }

        /**
         * Pattern for field access in resource.
         */
        public static String arrayAccess(String variable, String structType, String deref,
                                         String index) {
            return variable + " = getelementptr inbounds " + structType + " ptr %" + deref + " i"
                + POINTER_SIZE + " " + index;
        }

        /**
         * Pattern for struct memory allocation with size calculation.
         * Matches the specific malloc pattern with ptrtoint and getelementptr for size calculation.
         */
        public static String malloc(String variable, String structType) {
            return variable + " = call ptr @GC_malloc\\(i" + POINTER_SIZE
                + " ptrtoint \\(ptr getelementptr \\(%" + structType + " ptr null i32 1\\) to i"
                + POINTER_SIZE;
        }

        /**
         * Pattern for atomic struct memory allocation with size calculation.
         * Matches the specific malloc_atomic pattern.
         */
        public static String atomicMalloc(String variable, String structType) {
            return variable + " = call ptr @GC_malloc_atomic\\(i" + POINTER_SIZE
                + " ptrtoint \\(ptr getelementptr \\(%" + structType + " ptr null i32 1\\) to i"
                + POINTER_SIZE;
        }

        /**
         * Pattern for array memory allocation with size calculation.
         * Matches the specific malloc pattern with ptrtoint and getelementptr for size calculation.
         */
        public static String arrayMalloc(String variable, String elementType) {
            return variable + " = call ptr @GC_malloc\\(i" + POINTER_SIZE + " mul \\(i"
                + POINTER_SIZE + " ptrtoint \\(ptr getelementptr \\(" + elementType
                + " ptr null i32 1\\) to i" + POINTER_SIZE;
        }
    }

    /**
     * Helper class for creating multi-file test scenarios.
     */
    public static class MultiFileBuilder {
        private final Map<String, String> files = new java.util.HashMap<>();

        /**
         * Adds a file to the multi-file compilation.
         *
         * @param filename The filename (should end with .reso)
         * @param content  The file content
         * @return This builder for chaining
         */
        public MultiFileBuilder addFile(String filename, String content) {
            files.put(filename, content);
            return this;
        }

        /**
         * Builds the map of files.
         *
         * @return Map of filename to content
         */
        public Map<String, String> build() {
            return new java.util.HashMap<>(files);
        }
    }
}