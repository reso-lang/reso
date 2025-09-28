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
            def helperFunction() -> i32:
                return 42
            
            def main() -> i32:
                return helperFunction()  # Should work - same file
            """;

        String ir = compileAndExpectSuccess(sourceCode, "default_function_visibility");

        assertIrContains(ir, IrPatterns.functionDefinition("helperFunction", "i32"));
        assertIrContains(ir,
            IrPatterns.functionCall("helperFunction", "i32", Collections.emptyList()));
    }

    @Test
    public void testExplicitPublicFunctionVisibility() {
        String sourceCode = """
            pub def publicFunction() -> i32:
                return 42
            
            def main() -> i32:
                return publicFunction()
            """;

        String ir = compileAndExpectSuccess(sourceCode, "explicit_public_function_visibility");

        assertIrContains(ir, IrPatterns.functionDefinition("publicFunction", "i32"));
        assertIrContains(ir,
            IrPatterns.functionCall("publicFunction", "i32", Collections.emptyList()));
    }

    @Test
    public void testMixedFunctionVisibilities() {
        String sourceCode = """
            def privateHelper() -> i32:
                return 10
            
            pub def publicFunction() -> i32:
                return privateHelper() + 32  # Should work - same file
            
            def main() -> i32:
                return publicFunction()
            """;

        String ir = compileAndExpectSuccess(sourceCode, "mixed_function_visibilities");

        assertIrContains(ir, IrPatterns.functionDefinition("privateHelper", "i32"));
        assertIrContains(ir, IrPatterns.functionDefinition("publicFunction", "i32"));
        assertIrContains(ir,
            IrPatterns.functionCall("privateHelper", "i32", Collections.emptyList()));
        assertIrContains(ir,
            IrPatterns.functionCall("publicFunction", "i32", Collections.emptyList()));
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
                def validateInput(x: i32) -> bool:  # Private helper
                    return x >= 0
                
                pub def safeDivide(a: i32, b: i32) -> i32:  # Public aPI
                    if validateInput(a) and validateInput(b) and b != 0:
                        return a div b
                    return -1
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    return safeDivide(10, 2)  # Should work - public function
                """)
            .build();

        String ir = compileMultipleFilesAndExpectSuccess(sourceFiles,
            "public_function_with_private_helper");

        assertIrContains(ir, IrPatterns.functionDefinition("validateInput", "i1"));
        assertIrContains(ir, IrPatterns.functionDefinition("safeDivide", "i32"));
        assertIrContains(ir, IrPatterns.functionCall("safeDivide", "i32",
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
                pub def getLength(c: char) -> i32:
                    return 42  # Simplified for test
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    var squared: i32 = square(5)
                    var length: i32 = getLength('A')
                    return squared + length
                """)
            .build();

        String ir = compileMultipleFilesAndExpectSuccess(sourceFiles,
            "multiple_public_functions_across_files");

        assertIrContains(ir, IrPatterns.functionDefinition("square", "i32"));
        assertIrContains(ir, IrPatterns.functionDefinition("getLength", "i32"));
        assertIrContains(ir,
            IrPatterns.functionCall("square", "i32", List.of(Map.entry("i32", "5"))));
        assertIrContains(ir,
            IrPatterns.functionCall("getLength", "i32", List.of(Map.entry("i32", "65"))));
    }

    // ============================================================================
    // Cross-File Private Function Access Error Tests
    // ============================================================================

    @Test
    public void testPrivateFunctionCrossFileAccessError() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("utils.reso", """
                def privateHelper() -> i32:  # Fileprivate function
                    return 42
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    return privateHelper()  # Should fail - private function
                """)
            .build();

        String errors = compileMultipleFilesAndExpectFailure(sourceFiles,
            "private_function_cross_file_access_error");

        assertTrue(errors.contains(
                "Function 'privateHelper' with fileprivate visibility is not accessible"),
            "Expected visibility error for accessing private function across files");
    }

    @Test
    public void testMixedVisibilityAccessError() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("library.reso", """
                def internalFunction() -> i32:  # Private
                    return 10
                
                pub def publicFunction() -> i32:  # Public
                    return internalFunction() + 32
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    var result1: i32 = publicFunction()    # Should work
                    var result2: i32 = internalFunction()  # Should fail
                    return result1 + result2
                """)
            .build();

        String errors =
            compileMultipleFilesAndExpectFailure(sourceFiles, "mixed_visibility_access_error");

        assertTrue(errors.contains(
                "Function 'internalFunction' with fileprivate visibility is not accessible"),
            "Expected visibility error for accessing private function across files");
    }
}