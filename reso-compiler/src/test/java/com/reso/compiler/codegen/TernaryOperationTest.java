package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for ternary operations (conditional expressions) in Reso.
 * Tests compilation from Reso source code to LLVM IR with verification of:
 * - Type compatibility between true and false branches
 * - Error handling for invalid ternary expressions
 * - LLVM select instruction generation
 */
public class TernaryOperationTest extends BaseTest {

    // ============================================================================
    // Basic Ternary Operations
    // ============================================================================

    @Test
    public void testBasicTernaryWithSameTypes() {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = true
            var a: i32 = 10
            var b: i32 = 20
            var result: i32 = a if condition else b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_ternary_same_types");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("result", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("true", "i1", "condition"),
            IrPatterns.store("10", "i32", "a"),
            IrPatterns.store("20", "i32", "b"),
            IrPatterns.select("i1", "condition", "i32", "a", "b")
        );
    }

    @Test
    public void testTernaryWithOnlyIntegerLiterals() {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = true
            var result = 42 if condition else 24
            var negative_result = -10 if condition else -20
            var large_result = 1000000 if condition else 999999
            """);
        String ir = compileAndExpectSuccess(sourceCode, "ternary_integer_literals_only");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.alloca("negative_result", "i32"),
            IrPatterns.alloca("large_result", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "condition", "i32", "42", "24"),
            IrPatterns.select("i1", "condition", "i32", "-10", "-20"),
            IrPatterns.select("i1", "condition", "i32", "1000000", "999999")
        );
    }

    @Test
    public void testTernaryWithOnlyFloatLiterals() {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = false
            var result = 3.14 if condition else 2.71
            var negative_result = -1.5 if condition else -2.5
            var scientific_result = 1.23e10 if condition else 4.56e-5
            """);
        String ir = compileAndExpectSuccess(sourceCode, "ternary_float_literals_only");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("result", "double"),
            IrPatterns.alloca("negative_result", "double"),
            IrPatterns.alloca("scientific_result", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "condition", "double", "3.14", "2.71"),
            IrPatterns.select("i1", "condition", "double", "-1.5", "-2.5")
        );
    }

    @ParameterizedTest
    @MethodSource("literalTypeConversions")
    public void testTernaryLiteralTypeConversions(String targetType, String llvmType,
                                                  String literal1, String literal2) {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = true
            var result: %s = %s if condition else %s
            """.formatted(targetType, literal1, literal2));
        String ir = compileAndExpectSuccess(sourceCode,
            "ternary_literal_conversion_" + targetType.toLowerCase());

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("result", llvmType)
        );

        assertIrContains(mainFunc,
            IrPatterns.select("i1", "condition", llvmType, literal1, literal2)
        );
    }

    @Test
    public void testTernaryWithCompatibleTypes() {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = true
            var typed_value: i32 = 100
            var result = typed_value if condition else 50
            """);
        String ir = compileAndExpectSuccess(sourceCode, "ternary_compatible_types");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("typed_value", "i32"),
            IrPatterns.alloca("result", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "condition", "i32", "typed_value", "50")
        );
    }

    // ============================================================================
    // Ternary Operations in Different Contexts
    // ============================================================================

    @Test
    public void testTernaryInVariableInitialization() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 5
            var y: i32 = 10
            var max: i32 = x if (x > y) else y
            var min: i32 = x if (x < y) else y
            """);
        String ir = compileAndExpectSuccess(sourceCode, "ternary_in_initialization");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.alloca("max", "i32"),
            IrPatterns.alloca("min", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("sgt", "i32", "x", "y"),
            IrPatterns.select("i1", "icmp", "i32", "x", "y"),
            IrPatterns.icmp("slt", "i32", "x", "y"),
            IrPatterns.select("i1", "icmp", "i32", "x", "y")
        );
    }

    @Test
    public void testTernaryInAssignments() {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = true
            var a: i32 = 15
            var b: i32 = 25
            var result: i32 = 0
            result = a if condition else b
            result = a + 10 if (a > b) else b - 5
            """);
        String ir = compileAndExpectSuccess(sourceCode, "ternary_in_assignments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("result", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "condition", "i32", "a", "b"),
            IrPatterns.icmp("sgt", "i32", "a", "b"),
            IrPatterns.add("i32", "a", "10"),
            IrPatterns.sub("i32", "b", "5"),
            IrPatterns.select("i1", "icmp", "i32", "add", "sub")
        );
    }

    @Test
    public void testTernaryInIfConditions() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var y: i32 = 5
            var threshold: i32 = 8
            if true if (x > y) else false:
                var max: i32 = x if (x > threshold) else threshold
            """);
        String ir = compileAndExpectSuccess(sourceCode, "ternary_in_if_condition");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.alloca("threshold", "i32")
        );

        assertIrContains(mainFunc,
            IrPatterns.icmp("sgt", "i32", "x", "y"),
            IrPatterns.select("i1", "icmp", "i1", "true", "false"),
            IrPatterns.select("i1", "icmp", "i32", "x", "threshold")
        );
    }

    @Test
    public void testTernaryInWhileConditions() {
        String sourceCode = wrapInMainFunction("""
            var counter: i32 = 0
            var limit: i32 = 5
            var use_limit: bool = true
            while counter < (limit if use_limit else 10):
                counter = counter + 1
            """);
        String ir = compileAndExpectSuccess(sourceCode, "ternary_in_while_condition");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("counter", "i32"),
            IrPatterns.alloca("limit", "i32"),
            IrPatterns.alloca("use_limit", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "use_limit", "i32", "limit", "10"),
            IrPatterns.icmp("slt", "i32", "counter", "ternary")
        );
    }

    @Test
    public void testTernaryAsFunctionArguments() {
        String sourceCode = """
            def calculate(a: i32, b: i32) -> i32:
                return a + b
                            
            def main() -> i32:
                var x: i32 = 10
                var y: i32 = 5
                var condition: bool = true
                var result: i32 = calculate(x if condition else y, y + 1 if condition else x - 1)
                return result
            """;
        String ir = compileAndExpectSuccess(sourceCode, "ternary_as_function_args");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("result", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "condition", "i32", "x", "y"),
            IrPatterns.add("i32", "y", "1"),
            IrPatterns.sub("i32", "x", "1"),
            IrPatterns.select("i1", "condition", "i32", "add", "sub")
        );
    }

    @Test
    public void testTernaryInReturnStatements() {
        String sourceCode = """
            def max(a: i32, b: i32) -> i32:
                return a if (a > b) else b
                            
            def absolute(value: i32) -> i32:
                return -value if (value < 0) else value
                            
            def main() -> i32:
                var result1: i32 = max(10, 5)
                var result2: i32 = absolute(-15)
                return result1 + result2
            """;
        String ir = compileAndExpectSuccess(sourceCode, "ternary_in_return");

        String maxFunc = extractFunction(ir, "max");
        String absoluteFunc = extractFunction(ir, "absolute");

        assertNotNull(maxFunc, "Max function should be present in IR");
        assertIrContainsInOrder(maxFunc,
            IrPatterns.icmp("sgt", "i32", "a", "b"),
            IrPatterns.select("i1", "icmp", "i32", "a", "b")
        );

        assertNotNull(absoluteFunc, "Absolute function should be present in IR");
        assertIrContains(absoluteFunc,
            IrPatterns.icmp("slt", "i32", "value", "0"),
            IrPatterns.select("i1", "icmp", "i32", "neg", "value")
        );
    }

    // ============================================================================
    // Nested Ternary Operations
    // ============================================================================

    @Test
    public void testNestedTernaryExpressions() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 5
            var c: i32 = 15
            var condition1: bool = true
            var condition2: bool = false
            var result: i32 = (a if condition2 else b) if condition1 else c
            """);
        String ir = compileAndExpectSuccess(sourceCode, "nested_ternary");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("c", "i32"),
            IrPatterns.alloca("condition1", "i1"),
            IrPatterns.alloca("condition2", "i1"),
            IrPatterns.alloca("result", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "condition2", "i32", "a", "b"),
            IrPatterns.select("i1", "condition1", "i32", "ternary", "c")
        );
    }

    @Test
    public void testTripleNestedTernary() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 1
            var y: i32 = 2
            var cond1: bool = true
            var cond2: bool = false
            var cond3: bool = true
            var result: i32 = (x if cond2 else (-561 if cond3 else y)) if cond1 else 5
            """);
        String ir = compileAndExpectSuccess(sourceCode, "triple_nested_ternary");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "cond3", "i32", "-561", "y"),
            IrPatterns.select("i1", "cond2", "i32", "x", "ternary"),
            IrPatterns.select("i1", "cond1", "i32", "ternary", "5")
        );
    }

    // ============================================================================
    // Different Data Types
    // ============================================================================

    @ParameterizedTest
    @MethodSource("signedIntegerTypes")
    public void testTernaryWithSignedIntegerTypes(String resoType, String llvmType) {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = true
            var a: %s = 10
            var b: %s = 20
            var result: %s = a if condition else b
            """.formatted(resoType, resoType, resoType));
        String ir = compileAndExpectSuccess(sourceCode, "ternary_" + resoType.toLowerCase());

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("a", llvmType),
            IrPatterns.alloca("b", llvmType),
            IrPatterns.alloca("result", llvmType)
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "condition", llvmType, "a", "b")
        );
    }

    @ParameterizedTest
    @MethodSource("unsignedIntegerTypes")
    public void testTernaryWithUnsignedIntegerTypes(String resoType, String llvmType) {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = false
            var a: %s = 100
            var b: %s = 200
            var result: %s = a if condition else b
            """.formatted(resoType, resoType, resoType));
        String ir = compileAndExpectSuccess(sourceCode, "ternary_" + resoType.toLowerCase());

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("a", llvmType),
            IrPatterns.alloca("b", llvmType),
            IrPatterns.alloca("result", llvmType)
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "condition", llvmType, "a", "b")
        );
    }

    @ParameterizedTest
    @MethodSource("floatingPointTypes")
    public void testTernaryWithFloatingPointTypes(String resoType, String llvmType) {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = true
            var a: %s = 3.14
            var b: %s = 2.71
            var result: %s = a if condition else b
            """.formatted(resoType, resoType, resoType));
        String ir = compileAndExpectSuccess(sourceCode, "ternary_" + resoType.toLowerCase());

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("a", llvmType),
            IrPatterns.alloca("b", llvmType),
            IrPatterns.alloca("result", llvmType)
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "condition", llvmType, "a", "b")
        );
    }

    @Test
    public void testTernaryWithBooleanValues() {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = true
            var flag1: bool = true
            var flag2: bool = false
            var result: bool = flag1 if condition else flag2
            """);
        String ir = compileAndExpectSuccess(sourceCode, "ternary_boolean_values");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("flag1", "i1"),
            IrPatterns.alloca("flag2", "i1"),
            IrPatterns.alloca("result", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "condition", "i1", "flag1", "flag2")
        );
    }

    // ============================================================================
    // Complex Expressions in Ternary Operations
    // ============================================================================

    @Test
    public void testTernaryWithArithmeticExpressions() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var y: i32 = 5
            var condition: bool = true
            var result: i32 = (x + y * 2) if condition else (x - y div 2)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "ternary_with_arithmetic");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.alloca("condition", "i1"),
            IrPatterns.alloca("result", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.mul("i32", "y", "2"),
            IrPatterns.add("i32", "x", "mul"),
            IrPatterns.sdiv("i32", "y", "2"),
            IrPatterns.sub("i32", "x", "sdiv"),
            IrPatterns.select("i1", "condition", "i32", "add", "sub")
        );
    }

    @Test
    public void testTernaryWithComparisons() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 15
            var b: i32 = 10
            var c: i32 = 20
            var result: bool = (a > c) if (a > b) else (b > c)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "ternary_with_comparisons");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("c", "i32"),
            IrPatterns.alloca("result", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("sgt", "i32", "a", "b"),
            IrPatterns.icmp("sgt", "i32", "a", "c"),
            IrPatterns.icmp("sgt", "i32", "b", "c"),
            IrPatterns.select("i1", "icmp", "i1", "icmp", "icmp")
        );
    }

    @Test
    public void testTernaryWithFunctionCalls() {
        String sourceCode = """
            def add(x: i32, y: i32) -> i32:
                return x + y
                            
            def multiply(x: i32, y: i32) -> i32:
                return x * y
                            
            def main() -> i32:
                var a: i32 = 5
                var b: i32 = 3
                var use_add: bool = true
                var result: i32 = add(a, b) if use_add else multiply(a, b)
                return result
            """;
        String ir = compileAndExpectSuccess(sourceCode, "ternary_with_function_calls");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("use_add", "i1"),
            IrPatterns.alloca("result", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("add", "i32",
                List.of(Map.entry("i32", "a"), Map.entry("i32", "b"))),
            IrPatterns.functionCall("multiply", "i32",
                List.of(Map.entry("i32", "a"), Map.entry("i32", "b"))),
            IrPatterns.select("i1", "use_add", "i32", "add", "multiply")
        );
    }

    @Test
    public void testTernaryOperationsAsExpressionStatements() {
        String sourceCode = """
            def get_true_value() -> i32:
                return 100
                            
            def get_false_value() -> i32:
                return 200
                            
            def get_condition() -> bool:
                return true
                            
            def main() -> i32:
                get_true_value() if get_condition() else get_false_value()
                42 if true else get_false_value()
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "ternary_expression_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in IR");

        assertIrContains(mainFunc,
            IrPatterns.functionCall("get_condition", "i1", Collections.emptyList()),
            IrPatterns.functionCall("get_true_value", "i32", Collections.emptyList()),
            IrPatterns.functionCall("get_false_value", "i32", Collections.emptyList()),
            IrPatterns.select("i1", "get_condition", "i32", "get_true_value", "get_false_value"),
            IrPatterns.functionCall("get_false_value", "i32", Collections.emptyList()),
            IrPatterns.select("i1", "true", "i32", "42", "get_false_value")
        );
    }

    @Test
    public void testWithUnitFunctionError() {
        String sourceCode = """
            def unit_a():
                return
                            
            def unit_b() -> ():
                return
                            
            def main() -> i32:
                var condition: bool = true
                unit_a() if condition else unit_b()
            """;
        String ir = compileAndExpectSuccess(sourceCode, "ternary_with_unit_functions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in IR");

        assertIrContains(mainFunc,
            IrPatterns.functionCall("unit_a", "%unit", Collections.emptyList()),
            IrPatterns.functionCall("unit_b", "%unit", Collections.emptyList()),
            IrPatterns.select("i1", "condition", "%unit", "unit_a", "unit_b")
        );
    }

    // ============================================================================
    // Error Cases
    // ============================================================================

    @Test
    public void testIncompatibleTypesError() {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = true
            var int_val: i32 = 10
            var float_val: f64 = 5.5
            var result: i32 = int_val if condition else float_val
            """);
        String errors = compileAndExpectFailure(sourceCode, "incompatible_types_ternary");

        assertFalse(errors.isEmpty(),
            "Should report error for incompatible types in ternary expression");
        assertTrue(errors.contains("Cannot determine result type")
                || errors.contains("incompatible types")
                || errors.contains("type mismatch"),
            "Error should mention type incompatibility");
    }

    @Test
    public void testTernaryWithMixedLiteralTypes() {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = true
            var result: f64 = 42 if condition else 3.14
            """);
        String errors = compileAndExpectFailure(sourceCode, "ternary_mixed_literals");

        assertFalse(errors.isEmpty(),
            "Should report error for mixed literal types in ternary expression");
        assertTrue(errors.contains("Cannot determine result type")
            || errors.contains("incompatible types")
            || errors.contains("type mismatch"));
    }

    @Test
    public void testNonBooleanConditionError() {
        String sourceCode = wrapInMainFunction("""
            var condition: i32 = 1
            var a: i32 = 10
            var b: i32 = 20
            var result: i32 = a if condition else b
            """);
        String errors = compileAndExpectFailure(sourceCode, "non_boolean_condition");

        assertFalse(errors.isEmpty(),
            "Should report error for non-boolean condition");
        assertTrue(errors.contains("must be a boolean")
                || errors.contains("boolean")
                || errors.contains("bool"),
            "Error should mention boolean requirement");
    }

    @Test
    public void testMissingElseBranchError() {
        String sourceCode = wrapInMainFunction("""
            var condition: bool = true
            var a: i32 = 10
            var result: i32 = condition if a
            """);
        String errors = compileAndExpectFailure(sourceCode, "missing_else_branch");

        assertFalse(errors.isEmpty(),
            "Should report syntax error for missing else branch");
    }

    @Test
    public void testIncompatibleNestedTernaryError() {
        String sourceCode = wrapInMainFunction("""
            var condition1: bool = true
            var condition2: bool = false
            var int_val: i32 = 10
            var bool_val: bool = true
            var result: i32 = (int_val ? condition2 : bool_val) if condition1 else 20
            """);
        String errors = compileAndExpectFailure(sourceCode, "incompatible_nested_ternary");

        assertFalse(errors.isEmpty(),
            "Should report error for incompatible types in nested ternary");
        assertTrue(errors.contains("Cannot determine result type")
                || errors.contains("incompatible")
                || errors.contains("type"),
            "Error should mention type issues");
    }

    // ============================================================================
    // Parameterized Test Data Sources
    // ============================================================================

    static Stream<Arguments> signedIntegerTypes() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            Arguments.of("i8", "i8"),
            Arguments.of("i16", "i16"),
            Arguments.of("i32", "i32"),
            Arguments.of("i64", "i64"),
            Arguments.of("isize", sizeType)
        );
    }

    static Stream<Arguments> unsignedIntegerTypes() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            Arguments.of("u8", "i8"),
            Arguments.of("u16", "i16"),
            Arguments.of("u32", "i32"),
            Arguments.of("u64", "i64"),
            Arguments.of("usize", sizeType)
        );
    }

    static Stream<Arguments> floatingPointTypes() {
        return Stream.of(
            Arguments.of("f32", "float"),
            Arguments.of("f64", "double")
        );
    }

    static Stream<Arguments> literalTypeConversions() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            Arguments.of("i8", "i8", "100", "50"),
            Arguments.of("i16", "i16", "1000", "500"),
            Arguments.of("i32", "i32", "100000", "50000"),
            Arguments.of("i64", "i64", "1000000000", "500000000"),
            Arguments.of("isize", sizeType, "1000", "500"),
            Arguments.of("u8", "i8", "103", "100"),
            Arguments.of("u16", "i16", "30005", "30000"),
            Arguments.of("u32", "i32", "2000000002", "2000000000"),
            Arguments.of("u64", "i64", "9000000000000000001", "9000000000000000000"),
            Arguments.of("usize", sizeType, "30005", "30000"),
            Arguments.of("f32", "float", "1.0", "2.0"),
            Arguments.of("f64", "double", "3.1", "2.1")
        );
    }
}