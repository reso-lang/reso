package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for comparison operations in Reso.
 * Tests compilation from Reso source code to LLVM IR with verification of:
 * - All comparison operations (==, !=, <, <=, >, >=)
 * - Type compatibility enforcement
 * - Error handling for invalid comparisons
 */
public class ComparisonTest extends BaseTest {

    // ============================================================================
    // Basic Comparison Operations
    // ============================================================================

    @Test
    public void testBasicSignedIntegerComparisons() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 5
            var equal: bool = a == b
            var not_equal: bool = a != b
            var greater: bool = a > b
            var greater_equal: bool = a >= b
            var less: bool = a < b
            var less_equal: bool = a <= b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_signed_comparisons");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Variable allocations
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("equal", "i1"),
            IrPatterns.alloca("not_equal", "i1"),
            IrPatterns.alloca("greater", "i1"),
            IrPatterns.alloca("greater_equal", "i1"),
            IrPatterns.alloca("less", "i1"),
            IrPatterns.alloca("less_equal", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            // Initial stores
            IrPatterns.store("10", "i32", "a"),
            IrPatterns.store("5", "i32", "b"),

            // Signed integer comparison operations
            IrPatterns.icmp("eq", "i32", "a", "b"),   // ==
            IrPatterns.icmp("ne", "i32", "a", "b"),   // !=
            IrPatterns.icmp("sgt", "i32", "a", "b"),  // > (signed greater than)
            IrPatterns.icmp("sge", "i32", "a", "b"),  // >= (signed greater equal)
            IrPatterns.icmp("slt", "i32", "a", "b"),  // < (signed less than)
            IrPatterns.icmp("sle", "i32", "a", "b")   // <= (signed less equal)
        );
    }

    @Test
    public void testBasicUnsignedIntegerComparisons() {
        String sourceCode = wrapInMainFunction("""
            var a: u32 = 4294967290
            var b: u32 = 10
            var equal: bool = a == b
            var not_equal: bool = a != b
            var greater: bool = a > b
            var greater_equal: bool = a >= b
            var less: bool = a < b
            var less_equal: bool = a <= b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_unsigned_comparisons");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Variable allocations
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("equal", "i1"),
            IrPatterns.alloca("not_equal", "i1"),
            IrPatterns.alloca("greater", "i1"),
            IrPatterns.alloca("greater_equal", "i1"),
            IrPatterns.alloca("less", "i1"),
            IrPatterns.alloca("less_equal", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            // Initial stores
            IrPatterns.store("-6", "i32", "a"),  // Large unsigned value as signed
            IrPatterns.store("10", "i32", "b"),

            // Unsigned integer comparison operations
            IrPatterns.icmp("eq", "i32", "a", "b"),   // ==
            IrPatterns.icmp("ne", "i32", "a", "b"),   // !=
            IrPatterns.icmp("ugt", "i32", "a", "b"),  // > (unsigned greater than)
            IrPatterns.icmp("uge", "i32", "a", "b"),  // >= (unsigned greater equal)
            IrPatterns.icmp("ult", "i32", "a", "b"),  // < (unsigned less than)
            IrPatterns.icmp("ule", "i32", "a", "b")   // <= (unsigned less equal)
        );
    }

    @Test
    public void testBasicFloatingPointComparisons() {
        String sourceCode = wrapInMainFunction("""
            var a: f64 = 10.5
            var b: f64 = 5.2
            var equal: bool = a == b
            var not_equal: bool = a != b
            var greater: bool = a > b
            var greater_equal: bool = a >= b
            var less: bool = a < b
            var less_equal: bool = a <= b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_float_comparisons");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Variable allocations
            IrPatterns.alloca("a", "double"),
            IrPatterns.alloca("b", "double"),
            IrPatterns.alloca("equal", "i1"),
            IrPatterns.alloca("not_equal", "i1"),
            IrPatterns.alloca("greater", "i1"),
            IrPatterns.alloca("greater_equal", "i1"),
            IrPatterns.alloca("less", "i1"),
            IrPatterns.alloca("less_equal", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            // Initial stores
            IrPatterns.store("1.050000e\\+01", "double", "a"),
            IrPatterns.store("5.200000e\\+00", "double", "b"),

            // Floating-point comparison operations (ordered)
            IrPatterns.fcmp("oeq", "double", "a", "b"),  // == (ordered equal)
            IrPatterns.fcmp("one", "double", "a", "b"),  // != (ordered not equal)
            IrPatterns.fcmp("ogt", "double", "a", "b"),  // > (ordered greater than)
            IrPatterns.fcmp("oge", "double", "a", "b"),  // >= (ordered greater equal)
            IrPatterns.fcmp("olt", "double", "a", "b"),  // < (ordered less than)
            IrPatterns.fcmp("ole", "double", "a", "b")   // <= (ordered less equal)
        );
    }

    // ============================================================================
    // Comparison Operations in Different Contexts
    // ============================================================================

    @Test
    public void testComparisonsInIfConditions() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var y: i32 = 5
            if x > y:
                var z: i32 = 1
                            
            var a: f64 = 3.14
            var b: f64 = 2.0
            if a <= b:
                var c: f64 = 0.0
            else:
                var d: f64 = 1.0
                            
            var p: u16 = 100
            var q: u16 = 200
            if p != q:
                var result: bool = true
            """);
        String ir = compileAndExpectSuccess(sourceCode, "comparisons_in_if");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.alloca("a", "double"),
            IrPatterns.alloca("b", "double"),
            IrPatterns.alloca("p", "i16"),
            IrPatterns.alloca("q", "i16")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("sgt", "i32", "x", "y"),    // x > y (signed)
            IrPatterns.fcmp("ole", "double", "a", "b"), // a <= b (ordered)
            IrPatterns.icmp("ne", "i16", "p", "q")      // p != q (unsigned)
        );
    }

    @Test
    public void testComparisonsInWhileConditions() {
        String sourceCode = wrapInMainFunction("""
            var counter: i32 = 0
            var limit: i32 = 10
            while counter < limit:
                counter = counter + 1
                            
            var value: f32 = 1.0
            var threshold: f32 = 100.0
            while value <= threshold:
                value = value * 2.0
                            
            var unsigned_counter: u8 = 255
            while unsigned_counter >= 1:
                unsigned_counter = unsigned_counter - 1
            """);
        String ir = compileAndExpectSuccess(sourceCode, "comparisons_in_while");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("counter", "i32"),
            IrPatterns.alloca("limit", "i32"),
            IrPatterns.alloca("value", "float"),
            IrPatterns.alloca("threshold", "float"),
            IrPatterns.alloca("unsigned_counter", "i8")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("slt", "i32", "counter", "limit"),       // counter < limit (signed)
            IrPatterns.fcmp("ole", "float", "value", "threshold"),   // value <= threshold (ordered)
            IrPatterns.icmp("uge", "i8", "unsigned_counter", "1")
        );
    }

    @Test
    public void testComparisonsInVariableInitialization() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 20
            var is_greater = a > b
            var is_equal: bool = a == b
                            
            var x: f64 = 3.14159
            var y: f64 = 2.71828
            var comparison: bool = x >= y
                            
            var p: u32 = 100
            var q: u32 = 50
            var result: bool = p != q
            """);
        String ir = compileAndExpectSuccess(sourceCode, "comparisons_in_init");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("is_greater", "i1"),
            IrPatterns.alloca("is_equal", "i1"),
            IrPatterns.alloca("x", "double"),
            IrPatterns.alloca("y", "double"),
            IrPatterns.alloca("comparison", "i1"),
            IrPatterns.alloca("p", "i32"),
            IrPatterns.alloca("q", "i32"),
            IrPatterns.alloca("result", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("sgt", "i32", "a", "b"),   // a > b (signed)
            IrPatterns.icmp("eq", "i32", "a", "b"),    // a == b
            IrPatterns.fcmp("oge", "double", "x", "y"), // x >= y (ordered)
            IrPatterns.icmp("ne", "i32", "p", "q")     // p != q (unsigned)
        );
    }

    @Test
    public void testComparisonsInVariableAssignments() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 15
            var b: i32 = 25
            var result: bool = false
                            
            result = a < b
            result = a >= b
            result = a != b
                            
            var x: f32 = 1.5
            var y: f32 = 2.5
            var float_result: bool = false
                            
            float_result = x <= y
            float_result = x == y
            """);
        String ir = compileAndExpectSuccess(sourceCode, "comparisons_in_assignments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("result", "i1"),
            IrPatterns.alloca("x", "float"),
            IrPatterns.alloca("y", "float"),
            IrPatterns.alloca("float_result", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("slt", "i32", "a", "b"),   // a < b (signed)
            IrPatterns.icmp("sge", "i32", "a", "b"),   // a >= b (signed)
            IrPatterns.icmp("ne", "i32", "a", "b"),    // a != b
            IrPatterns.fcmp("ole", "float", "x", "y"), // x <= y (ordered)
            IrPatterns.fcmp("oeq", "float", "x", "y")  // x == y (ordered)
        );
    }

    @Test
    public void testComparisonsInReturnStatements() {
        String sourceCode = """
            def compare_ints(a: i32, b: i32) -> bool:
                return a > b
                            
            def compare_floats(x: f64, y: f64) -> bool:
                return x <= y
                            
            def compare_unsigned(p: u16, q: u16) -> bool:
                return p != q
                            
            def main() -> i32:
                var result1: bool = compare_ints(10, 5)
                var result2: bool = compare_floats(3.14, 2.71)
                var result3: bool = compare_unsigned(100, 200)
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "comparisons_in_return");

        String compareIntsFunc = extractFunction(ir, "compare_ints");
        String compareFloatsFunc = extractFunction(ir, "compare_floats");
        String compareUnsignedFunc = extractFunction(ir, "compare_unsigned");

        assertNotNull(compareIntsFunc, "Should find compare_ints function in IR");
        assertNotNull(compareFloatsFunc, "Should find compare_floats function in IR");
        assertNotNull(compareUnsignedFunc, "Should find compare_unsigned function in IR");

        // Check for proper comparison operations in return statements
        assertIrContains(compareIntsFunc,
            IrPatterns.icmp("sgt", "i32", "%a", "%b")  // a > b (signed)
        );

        assertIrContains(compareFloatsFunc,
            IrPatterns.fcmp("ole", "double", "%x", "%y")  // x <= y (ordered)
        );

        assertIrContains(compareUnsignedFunc,
            IrPatterns.icmp("ne", "i16", "%p", "%q")  // p != q (unsigned)
        );
    }

    // ============================================================================
    // Literal and Type Compatibility Testing
    // ============================================================================

    @Test
    public void testComparisonWithLiterals() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var result1: bool = x > 5
            var result2: bool = x == 10
            var result3: bool = 15 < x
            var result4: bool = 20 != x
                            
            var y: f64 = 3.14
            var result5: bool = y >= 3.0
            var result6: bool = y < 4.0
            var result7: bool = 2.5 <= y
            """);
        String ir = compileAndExpectSuccess(sourceCode, "comparison_with_literals");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "double"),
            IrPatterns.alloca("result1", "i1"),
            IrPatterns.alloca("result2", "i1"),
            IrPatterns.alloca("result3", "i1"),
            IrPatterns.alloca("result4", "i1"),
            IrPatterns.alloca("result5", "i1"),
            IrPatterns.alloca("result6", "i1"),
            IrPatterns.alloca("result7", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("sgt", "i32", "x", "5"),     // x > 5
            IrPatterns.icmp("eq", "i32", "x", "10"),     // x == 10
            IrPatterns.icmp("slt", "i32", "15", "x"),    // 15 < x
            IrPatterns.icmp("ne", "i32", "20", "x"),     // 20 != x
            IrPatterns.fcmp("oge", "double", "y", "3.000000e\\+00"), // y >= 3.0
            IrPatterns.fcmp("olt", "double", "y", "4.000000e\\+00"), // y < 4.0
            IrPatterns.fcmp("ole", "double", "2.500000e\\+00", "y")  // 2.5 <= y
        );
    }

    @Test
    public void testLiteralToLiteralComparisons() {
        String sourceCode = wrapInMainFunction("""
            var result1: bool = 10 > 5
            var result2: bool = 3.14 <= 2.71
            var result3: bool = 100 == 100
            var result4: bool = 1.0 != 2.0
            var result5: bool = true == false
            """);
        String ir = compileAndExpectSuccess(sourceCode, "literal_to_literal");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("result1", "i1"),
            IrPatterns.alloca("result2", "i1"),
            IrPatterns.alloca("result3", "i1"),
            IrPatterns.alloca("result4", "i1"),
            IrPatterns.alloca("result5", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("true", "i1", "result1"),     // 10 > 5
            IrPatterns.store("false", "i1", "result2"),    // 3.14 <= 2.71
            IrPatterns.store("true", "i1", "result3"),     // 100 == 100
            IrPatterns.store("true", "i1", "result4"),     // 1.0 != 2.0
            IrPatterns.store("false", "i1", "result5")     // true == false
        );
    }

    // ============================================================================
    // Parametrized Tests for Different Integer Types
    // ============================================================================

    @ParameterizedTest
    @MethodSource("signedIntegerComparisonData")
    public void testSignedIntegerComparisons(String typeName, String llvmType, long leftValue,
                                             long rightValue, String operator,
                                             String expectedPredicate) {
        String sourceCode = wrapInMainFunction("""
            var a: %s = %d
            var b: %s = %d
            var result: bool = a %s b
            """.formatted(typeName, leftValue, typeName, rightValue, operator));
        String ir = compileAndExpectSuccess(sourceCode, "signed_" + typeName.toLowerCase() + "_"
            + operator.replace("=", "eq").replace("!", "ne").replace("<", "lt").replace(">", "gt"));

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", llvmType),
            IrPatterns.alloca("b", llvmType),
            IrPatterns.alloca("result", "i1")
        );

        assertIrContains(mainFunc,
            IrPatterns.icmp(expectedPredicate, llvmType, "a", "b")
        );
    }

    static Stream<Arguments> signedIntegerComparisonData() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            // i8 tests
            Arguments.of("i8", "i8", 10L, 5L, ">", "sgt"),
            Arguments.of("i8", "i8", -10L, 5L, "<", "slt"),
            Arguments.of("i8", "i8", 42L, 42L, "==", "eq"),
            Arguments.of("i8", "i8", 10L, 20L, "!=", "ne"),
            Arguments.of("i8", "i8", 15L, 10L, ">=", "sge"),
            Arguments.of("i8", "i8", 5L, 10L, "<=", "sle"),

            // i16 tests
            Arguments.of("i16", "i16", 1000L, 500L, ">", "sgt"),
            Arguments.of("i16", "i16", -1000L, 500L, "<", "slt"),
            Arguments.of("i16", "i16", 32767L, 32767L, "==", "eq"),
            Arguments.of("i16", "i16", 100L, 200L, "!=", "ne"),
            Arguments.of("i16", "i16", 1500L, 1000L, ">=", "sge"),
            Arguments.of("i16", "i16", 500L, 1000L, "<=", "sle"),

            // i32 tests
            Arguments.of("i32", "i32", 100000L, 50000L, ">", "sgt"),
            Arguments.of("i32", "i32", -100000L, 50000L, "<", "slt"),
            Arguments.of("i32", "i32", 2147483647L, 2147483647L, "==", "eq"),
            Arguments.of("i32", "i32", 1000L, 2000L, "!=", "ne"),
            Arguments.of("i32", "i32", 150000L, 100000L, ">=", "sge"),
            Arguments.of("i32", "i32", 50000L, 100000L, "<=", "sle"),

            // i64 tests
            Arguments.of("i64", "i64", 10000000000L, 5000000000L, ">", "sgt"),
            Arguments.of("i64", "i64", -10000000000L, 5000000000L, "<", "slt"),
            Arguments.of("i64", "i64", 9223372036854775807L, 9223372036854775807L, "==", "eq"),
            Arguments.of("i64", "i64", 1000000000L, 2000000000L, "!=", "ne"),
            Arguments.of("i64", "i64", 15000000000L, 10000000000L, ">=", "sge"),
            Arguments.of("i64", "i64", 5000000000L, 10000000000L, "<=", "sle"),

            // isize tests
            Arguments.of("isize", sizeType, 100L, 50L, ">", "sgt"),
            Arguments.of("isize", sizeType, -100L, 50L, "<", "slt"),
            Arguments.of("isize", sizeType, 123456789L, 123456789L, "==", "eq"),
            Arguments.of("isize", sizeType, 1000L, 2000L, "!=", "ne"),
            Arguments.of("isize", sizeType, 150L, 100L, ">=", "sge"),
            Arguments.of("isize", sizeType, 50L, 100L, "<=", "sle")
        );
    }

    @ParameterizedTest
    @MethodSource("unsignedIntegerComparisonData")
    public void testUnsignedIntegerComparisons(String typeName, String llvmType, long leftValue,
                                               long rightValue, String operator,
                                               String expectedPredicate) {
        String sourceCode = wrapInMainFunction("""
            var a: %s = %d
            var b: %s = %d
            var result: bool = a %s b
            """.formatted(typeName, leftValue, typeName, rightValue, operator));
        String ir = compileAndExpectSuccess(sourceCode, "unsigned_" + typeName.toLowerCase() + "_"
            + operator.replace("=", "eq").replace("!", "ne").replace("<", "lt").replace(">", "gt"));

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", llvmType),
            IrPatterns.alloca("b", llvmType),
            IrPatterns.alloca("result", "i1")
        );

        assertIrContains(mainFunc,
            IrPatterns.icmp(expectedPredicate, llvmType, "a", "b")
        );
    }

    static Stream<Arguments> unsignedIntegerComparisonData() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            // u8 tests
            Arguments.of("u8", "i8", 200L, 100L, ">", "ugt"),
            Arguments.of("u8", "i8", 50L, 200L, "<", "ult"),
            Arguments.of("u8", "i8", 255L, 255L, "==", "eq"),
            Arguments.of("u8", "i8", 100L, 200L, "!=", "ne"),
            Arguments.of("u8", "i8", 200L, 150L, ">=", "uge"),
            Arguments.of("u8", "i8", 100L, 200L, "<=", "ule"),

            // u16 tests
            Arguments.of("u16", "i16", 50000L, 25000L, ">", "ugt"),
            Arguments.of("u16", "i16", 25000L, 50000L, "<", "ult"),
            Arguments.of("u16", "i16", 65535L, 65535L, "==", "eq"),
            Arguments.of("u16", "i16", 1000L, 2000L, "!=", "ne"),
            Arguments.of("u16", "i16", 50000L, 40000L, ">=", "uge"),
            Arguments.of("u16", "i16", 25000L, 50000L, "<=", "ule"),

            // u32 tests
            Arguments.of("u32", "i32", 3000000000L, 2000000000L, ">", "ugt"),
            Arguments.of("u32", "i32", 2000000000L, 3000000000L, "<", "ult"),
            Arguments.of("u32", "i32", 4294967295L, 4294967295L, "==", "eq"),
            Arguments.of("u32", "i32", 1000000L, 2000000L, "!=", "ne"),
            Arguments.of("u32", "i32", 3000000000L, 2500000000L, ">=", "uge"),
            Arguments.of("u32", "i32", 2000000000L, 3000000000L, "<=", "ule"),

            // u64 tests
            Arguments.of("u64", "i64", 10000000000L, 5000000000L, ">", "ugt"),
            Arguments.of("u64", "i64", 5000000000L, 10000000000L, "<", "ult"),
            Arguments.of("u64", "i64", 1000000000L, 2000000000L, "!=", "ne"),
            Arguments.of("u64", "i64", 15000000000L, 10000000000L, ">=", "uge"),
            Arguments.of("u64", "i64", 5000000000L, 10000000000L, "<=", "ule"),

            // usize tests
            Arguments.of("usize", sizeType, 200L, 100L, ">", "ugt"),
            Arguments.of("usize", sizeType, 50L, 200L, "<", "ult"),
            Arguments.of("usize", sizeType, 123456789L, 123456789L, "==", "eq"),
            Arguments.of("usize", sizeType, 1000L, 2000L, "!=", "ne"),
            Arguments.of("usize", sizeType, 200L, 150L, ">=", "uge"),
            Arguments.of("usize", sizeType, 100L, 200L, "<=", "ule")
        );
    }

    @ParameterizedTest
    @MethodSource("floatingPointComparisonData")
    public void testFloatingPointComparisons(String typeName, String llvmType, double leftValue,
                                             double rightValue, String operator,
                                             String expectedPredicate) {
        String sourceCode = wrapInMainFunction("""
            var a: %s = %s
            var b: %s = %s
            var result: bool = a %s b
            """.formatted(typeName, leftValue, typeName, rightValue, operator));
        String ir = compileAndExpectSuccess(sourceCode, "float_" + typeName.toLowerCase() + "_"
            + operator.replace("=", "eq").replace("!", "ne").replace("<", "lt").replace(">", "gt"));

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", llvmType),
            IrPatterns.alloca("b", llvmType),
            IrPatterns.alloca("result", "i1")
        );

        assertIrContains(mainFunc,
            IrPatterns.fcmp(expectedPredicate, llvmType, "a", "b")
        );
    }

    static Stream<Arguments> floatingPointComparisonData() {
        return Stream.of(
            // f32 tests
            Arguments.of("f32", "float", 10.5, 5.2, ">", "ogt"),
            Arguments.of("f32", "float", 5.2, 10.5, "<", "olt"),
            Arguments.of("f32", "float", 3.14159, 3.14159, "==", "oeq"),
            Arguments.of("f32", "float", 1.0, 2.0, "!=", "one"),
            Arguments.of("f32", "float", 10.5, 10.0, ">=", "oge"),
            Arguments.of("f32", "float", 5.2, 10.5, "<=", "ole"),

            // f64 tests
            Arguments.of("f64", "double", 123.456789, 98.765432, ">", "ogt"),
            Arguments.of("f64", "double", 98.765432, 123.456789, "<", "olt"),
            Arguments.of("f64", "double", 2.718281828, 2.718281828, "==", "oeq"),
            Arguments.of("f64", "double", 3.14159, 2.71828, "!=", "one"),
            Arguments.of("f64", "double", 123.456789, 123.0, ">=", "oge"),
            Arguments.of("f64", "double", 98.765432, 123.456789, "<=", "ole")
        );
    }

    // ============================================================================
    // Error Cases and Type Compatibility
    // ============================================================================

    @Test
    public void testIncompatibleIntFloatComparisons() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: f64 = 5.0
            var result: bool = a > b
            """);

        String errors = compileAndExpectFailure(sourceCode, "int_float_comparison");
        assertTrue(errors.contains("Cannot compare i32 and f64"),
            "Should report type compatibility error");
    }

    @Test
    public void testIncompatibleIntBoolComparison() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: bool = true
            var result: bool = a == b
            """);
        String errors = compileAndExpectFailure(sourceCode, "int_bool_comparison");
        assertTrue(errors.contains("Cannot compare i32 and bool"),
            "Should report type compatibility error");
    }

    @Test
    public void testUnsupportedComparisonOperations() {
        String sourceCode = wrapInMainFunction("""
            var a: bool = true
            var b: bool = false
            var result: bool = a > b
            """);
        String errors = compileAndExpectFailure(sourceCode, "bool_ordering_comparison");
        assertTrue(errors.contains("not supported")
                || errors.contains("invalid")
                || errors.contains("operator"),
            "Should report unsupported operation error");
    }

    @Test
    public void testValidBooleanEqualityComparisons() {
        String sourceCode = wrapInMainFunction("""
            var a: bool = true
            var b: bool = false
            var equal: bool = a == b
            var not_equal: bool = a != b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "boolean_equality");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i1"),
            IrPatterns.alloca("b", "i1"),
            IrPatterns.alloca("equal", "i1"),
            IrPatterns.alloca("not_equal", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("eq", "i1", "a", "b"),  // a == b
            IrPatterns.icmp("ne", "i1", "a", "b")   // a != b
        );
    }

    @Test
    public void testComparisonWithUnitTypes() {
        String sourceCode = """
            def unit_a():
                return ()
                            
            def unit_b() -> ():
                return
                            
            def main() -> i32:
                var equal = unit_a() == unit_b()
                var not_equal = unit_a() != unit_b()
            """;
        String ir = compileAndExpectSuccess(sourceCode, "unit_type_comparison");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("true", "i1", "equal"),
            IrPatterns.store("false", "i1", "not_equal")
        );
    }

    // ============================================================================
    // Complex Comparison Expressions
    // ============================================================================

    @Test
    public void testChainedComparisons() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 5
            var b: i32 = 10
            var c: i32 = 15
            var result1: bool = (a < b) and (b < c)
            var result2: bool = (a > b) or (b == 10)
            var result3: bool = not(a >= c)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "chained_comparisons");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("c", "i32"),
            IrPatterns.alloca("result1", "i1"),
            IrPatterns.alloca("result2", "i1"),
            IrPatterns.alloca("result3", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("slt", "i32", "a", "b"),  // a < b
            IrPatterns.icmp("slt", "i32", "b", "c"),  // b < c
            IrPatterns.icmp("sgt", "i32", "a", "b"),  // a > b
            IrPatterns.icmp("eq", "i32", "b", "10"),  // b == 10
            IrPatterns.icmp("sge", "i32", "a", "c")   // a >= c
        );
    }

    @Test
    public void testNestedComparisonsInComplexExpressions() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var y: i32 = 20
            var z: i32 = 30
                            
            if (x < y) and (y < z):
                var temp: i32 = x + y
                if temp > z:
                    var result: bool = true
                else:
                    var result: bool = false
            """);
        String ir = compileAndExpectSuccess(sourceCode, "nested_comparisons");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.alloca("z", "i32")
        );

        assertIrContains(mainFunc,
            IrPatterns.icmp("slt", "i32", "x", "y"),  // x < y
            IrPatterns.icmp("slt", "i32", "y", "z"),  // y < z
            IrPatterns.icmp("sgt", "i32", "temp", "z") // temp > z
        );
    }

    @Test
    public void testComparisonOperationsAsExpressionStatements() {
        String sourceCode = """
            def get_left() -> i32:
                return 10
                            
            def get_right() -> i32:
                return 20
                            
            def main() -> i32:
                get_left() > get_right()
                get_left() != 3
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "comparison_expression_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in IR");

        // All comparison operations should be present
        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("get_left", "i32", Collections.emptyList()),
            IrPatterns.functionCall("get_right", "i32", Collections.emptyList()),
            IrPatterns.icmp("sgt", "i32", "get_left", "get_right"),
            IrPatterns.functionCall("get_left", "i32", Collections.emptyList()),
            IrPatterns.icmp("ne", "i32", "get_left", "3")
        );
    }
}