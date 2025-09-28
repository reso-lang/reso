package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for function visibility rules in Reso.
 * Tests the visibility system where:
 * - Default function visibility is fileprivate
 * - Functions can be made global with 'pub def'
 * - Cross-file access respects visibility rules
 */
public class FunctionVisibilityTest extends BaseTest {

    // ============================================================================
    // Single File Function Visibility Tests
    // ============================================================================

    @Test
    public void testDefaultFunctionVisibilityIsFileprivate() {
        String sourceCode = """
            def helper_function() -> i32:
                return 42
            
            def main() -> i32:
                return helper_function()  # Should work - same file
            """;

        String ir = compileAndExpectSuccess(sourceCode, "default_function_visibility");

        assertIrContains(ir, IrPatterns.functionDefinition("helper_function", "i32"));
        assertIrContains(ir,
            IrPatterns.functionCall("helper_function", "i32", Collections.emptyList()));
    }

    @Test
    public void testExplicitPublicFunctionVisibility() {
        String sourceCode = """
            pub def public_function() -> i32:
                return 42
            
            def main() -> i32:
                return public_function()
            """;

        String ir = compileAndExpectSuccess(sourceCode, "explicit_public_function_visibility");

        assertIrContains(ir, IrPatterns.functionDefinition("public_function", "i32"));
        assertIrContains(ir,
            IrPatterns.functionCall("public_function", "i32", Collections.emptyList()));
    }

    @Test
    public void testMixedFunctionVisibilities() {
        String sourceCode = """
            def private_helper() -> i32:
                return 10
            
            pub def public_function() -> i32:
                return private_helper() + 32  # Should work - same file
            
            def main() -> i32:
                return public_function()
            """;

        String ir = compileAndExpectSuccess(sourceCode, "mixed_function_visibilities");

        assertIrContains(ir, IrPatterns.functionDefinition("private_helper", "i32"));
        assertIrContains(ir, IrPatterns.functionDefinition("public_function", "i32"));
        assertIrContains(ir,
            IrPatterns.functionCall("private_helper", "i32", Collections.emptyList()));
        assertIrContains(ir,
            IrPatterns.functionCall("public_function", "i32", Collections.emptyList()));
    }

    // ============================================================================
    // Cross-File Public Function Access Tests
    // ============================================================================

    @Test
    public void testPublicFunctionCrossFileAccess() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("math.reso", """
                pub def add(a: i32, b: i32) -> i32:
                    return a + b
                
                pub def multiply(a: i32, b: i32) -> i32:
                    return a * b
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    var sum: i32 = add(5, 3)        # Should work - public function
                    var product: i32 = multiply(4, 2)  # Should work - public function
                    return sum + product
                """)
            .build();

        String ir =
            compileMultipleFilesAndExpectSuccess(sourceFiles, "public_function_cross_file_access");

        assertIrContains(ir, IrPatterns.functionDefinition("add", "i32"));
        assertIrContains(ir, IrPatterns.functionDefinition("multiply", "i32"));
        assertIrContains(ir, IrPatterns.functionCall("add", "i32",
            List.of(Map.entry("i32", "5"), Map.entry("i32", "3"))));
        assertIrContains(ir, IrPatterns.functionCall("multiply", "i32",
            List.of(Map.entry("i32", "4"), Map.entry("i32", "2"))));
    }

    @Test
    public void testPublicFunctionWithPrivateHelper() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("utils.reso", """
                def validate_input(x: i32) -> bool:  # Private helper
                    return x >= 0
                
                pub def safe_divide(a: i32, b: i32) -> i32:  # Public API
                    if validate_input(a) and validate_input(b) and b != 0:
                        return a div b
                    return -1
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    return safe_divide(10, 2)  # Should work - public function
                """)
            .build();

        String ir = compileMultipleFilesAndExpectSuccess(sourceFiles,
            "public_function_with_private_helper");

        assertIrContains(ir, IrPatterns.functionDefinition("validate_input", "i1"));
        assertIrContains(ir, IrPatterns.functionDefinition("safe_divide", "i32"));
        assertIrContains(ir, IrPatterns.functionCall("safe_divide", "i32",
            List.of(Map.entry("i32", "10"), Map.entry("i32", "2"))));
    }

    @Test
    public void testMultiplePublicFunctionsAcrossFiles() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("math.reso", """
                pub def square(x: i32) -> i32:
                    return x * x
                """)
            .addFile("string_utils.reso", """
                pub def get_length(c: char) -> i32:
                    return 42  # Simplified for test
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    var squared: i32 = square(5)
                    var length: i32 = get_length('A')
                    return squared + length
                """)
            .build();

        String ir = compileMultipleFilesAndExpectSuccess(sourceFiles,
            "multiple_public_functions_across_files");

        assertIrContains(ir, IrPatterns.functionDefinition("square", "i32"));
        assertIrContains(ir, IrPatterns.functionDefinition("get_length", "i32"));
        assertIrContains(ir,
            IrPatterns.functionCall("square", "i32", List.of(Map.entry("i32", "5"))));
        assertIrContains(ir,
            IrPatterns.functionCall("get_length", "i32", List.of(Map.entry("i32", "65"))));
    }

    // ============================================================================
    // Cross-File Private Function Access Error Tests
    // ============================================================================

    @Test
    public void testPrivateFunctionCrossFileAccessError() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("utils.reso", """
                def private_helper() -> i32:  # Fileprivate function
                    return 42
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    return private_helper()  # Should fail - private function
                """)
            .build();

        String errors = compileMultipleFilesAndExpectFailure(sourceFiles,
            "private_function_cross_file_access_error");

        assertTrue(errors.contains(
                "Function 'private_helper' with fileprivate visibility is not accessible"),
            "Expected visibility error for accessing private function across files");
    }

    @Test
    public void testMixedVisibilityAccessError() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("library.reso", """
                def internal_function() -> i32:  # Private
                    return 10
                
                pub def public_function() -> i32:  # Public
                    return internal_function() + 32
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    var result1: i32 = public_function()    # Should work
                    var result2: i32 = internal_function()  # Should fail
                    return result1 + result2
                """)
            .build();

        String errors =
            compileMultipleFilesAndExpectFailure(sourceFiles, "mixed_visibility_access_error");

        assertTrue(errors.contains(
                "Function 'internal_function' with fileprivate visibility is not accessible"),
            "Expected visibility error for accessing private function across files");
    }
}