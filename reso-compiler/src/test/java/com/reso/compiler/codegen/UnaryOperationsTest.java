package com.reso.compiler.codegen;

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
 * Tests for unary operations in Reso.
 * Tests compilation from Reso source code to LLVM IR with verification of:
 * - All unary operators (+, -, not, ~)
 * - Type compatibility enforcement
 * - Error handling for invalid operations
 */
public class UnaryOperationsTest extends BaseTest {

    // ============================================================================
    // Basic Unary Operations
    // ============================================================================

    @Test
    public void testBasicUnaryPlusOperations() {
        String sourceCode = wrapInMainFunction("""
            var int_val: i32 = 42
            var float_val: f64 = 3.14
            var plus_int: i32 = +int_val
            var plus_float: f64 = +float_val
            var plus_literal: i32 = +123
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_unary_plus");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("int_val", "i32"),
            IrPatterns.alloca("float_val", "double"),
            IrPatterns.alloca("plus_int", "i32"),
            IrPatterns.alloca("plus_float", "double"),
            IrPatterns.alloca("plus_literal", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("42", "i32", "int_val"),
            IrPatterns.store("3.140000e\\+00", "double", "float_val"),
            IrPatterns.load("i32", "int_val"),     // +int_val (identity operation)
            IrPatterns.load("double", "float_val"), // +float_val (identity operation)
            IrPatterns.store("123", "i32", "plus_literal") // +123 (compile-time constant)
        );
    }

    @Test
    public void testBasicUnaryMinusOperations() {
        String sourceCode = wrapInMainFunction("""
            var int_val: i32 = 42
            var float_val: f64 = 3.14
            var neg_int: i32 = -int_val
            var neg_float: f64 = -float_val
            var neg_literal: i32 = -123
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_unary_minus");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("int_val", "i32"),
            IrPatterns.alloca("float_val", "double"),
            IrPatterns.alloca("neg_int", "i32"),
            IrPatterns.alloca("neg_float", "double"),
            IrPatterns.alloca("neg_literal", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("42", "i32", "int_val"),
            IrPatterns.store("3.140000e\\+00", "double", "float_val"),
            IrPatterns.load("i32", "int_val"),
            IrPatterns.unaryNeg("i32", "int_val"),      // Integer negation
            IrPatterns.load("double", "float_val"),
            IrPatterns.unaryFNeg("double", "float_val"), // Float negation
            IrPatterns.store("-123", "i32", "neg_literal") // Compile-time constant
        );
    }

    @Test
    public void testBasicLogicalNotOperations() {
        String sourceCode = wrapInMainFunction("""
            var bool_val: bool = true
            var not_bool: bool = not bool_val
            var not_literal: bool = not false
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_logical_not");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("bool_val", "i1"),
            IrPatterns.alloca("not_bool", "i1"),
            IrPatterns.alloca("not_literal", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("true", "i1", "bool_val"),
            IrPatterns.load("i1", "bool_val"),
            IrPatterns.logicalNot("i1", "bool_val"),     // Logical NOT with XOR 1
            IrPatterns.store("true", "i1", "not_literal")     // not false = true
        );
    }

    @Test
    public void testBasicBitwiseNotOperations() {
        String sourceCode = wrapInMainFunction("""
            var int_val: i32 = 42
            var uint_val: u16 = 255
            var not_int: i32 = ~int_val
            var not_uint: u16 = ~uint_val
            var not_literal: i8 = ~(5 as i8)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_bitwise_not");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("int_val", "i32"),
            IrPatterns.alloca("uint_val", "i16"),
            IrPatterns.alloca("not_int", "i32"),
            IrPatterns.alloca("not_uint", "i16"),
            IrPatterns.alloca("not_literal", "i8")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("42", "i32", "int_val"),
            IrPatterns.store("255", "i16", "uint_val"),
            IrPatterns.load("i32", "int_val"),
            IrPatterns.bitwiseNot("i32", "int_val"),     // Bitwise NOT with XOR all-ones
            IrPatterns.load("i16", "uint_val"),
            IrPatterns.bitwiseNot("i16", "uint_val")     // Bitwise NOT with XOR all-ones
        );
    }

    @Test
    public void testUnaryOperationsAsExpressionStatements() {
        String sourceCode = """
            def get_value() -> i32:
                return 42
                            
            def get_bool() -> bool:
                return true
                            
            def main() -> i32:
                -get_value()
                +get_value()
                ~get_value()
                not get_bool()
            """;
        String ir = compileAndExpectSuccess(sourceCode, "unary_expression_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in IR");

        // Verify all unary operations are performed
        assertIrContains(mainFunc,
            IrPatterns.functionCall("get_value", "i32", Collections.emptyList()),
            IrPatterns.sub("i32", "0", "get_value"), // unary minus: 0 - value
            IrPatterns.functionCall("get_value", "i32", Collections.emptyList()),
            // unary plus (identity)
            IrPatterns.functionCall("get_value", "i32", Collections.emptyList()),
            IrPatterns.bitwiseNot("i32", "get_value"),
            IrPatterns.logicalNot("i1", "get_bool")
        );
    }

    // ============================================================================
    // Unary Operations in Variable Initialization
    // ============================================================================

    @ParameterizedTest
    @MethodSource("unaryOperatorTestCases")
    public void testUnaryOperationsInVariableInitialization(String operator, String operandType,
                                                            String operandValue,
                                                            String expectedPattern) {
        String sourceCode = wrapInMainFunction("""
            var operand: %s = %s
            var result: %s = %s operand
            """.formatted(operandType, operandValue, operandType, operator));

        String ir = compileAndExpectSuccess(sourceCode,
            "unary_" + operator.replace("~", "bitnot").replace(" ", "") + "_in_init");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("operand", getLlvmType(operandType)),
            IrPatterns.alloca("result", getLlvmType(operandType))
        );

        if (!expectedPattern.isEmpty()) {
            assertIrContains(mainFunc, expectedPattern);
        }
    }

    // ============================================================================
    // Unary Operations in Variable Assignments
    // ============================================================================

    @Test
    public void testUnaryOperationsInAssignments() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var y: f32 = 2.5
            var z: bool = true
            var w: u8 = 15
                            
            x = -x
            y = +y
            z = not z
            w = ~w
            """);
        String ir = compileAndExpectSuccess(sourceCode, "unary_in_assignments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "float"),
            IrPatterns.alloca("z", "i1"),
            IrPatterns.alloca("w", "i8")
        );

        assertIrContainsInOrder(mainFunc,
            // Initial assignments
            IrPatterns.store("10", "i32", "x"),
            IrPatterns.store("2.500000e\\+00", "float", "y"),
            IrPatterns.store("true", "i1", "z"),
            IrPatterns.store("15", "i8", "w"),

            // Unary operations in reassignments
            IrPatterns.load("i32", "x"),
            IrPatterns.unaryNeg("i32", "x"),
            IrPatterns.load("float", "y"),  // +y is identity for floats
            IrPatterns.load("i1", "z"),
            IrPatterns.logicalNot("i1", "z"),
            IrPatterns.load("i8", "w"),
            IrPatterns.bitwiseNot("i8", "w")
        );
    }

    // ============================================================================
    // Unary Operations in Control Flow
    // ============================================================================

    @Test
    public void testUnaryOperationsInIfConditions() {
        String sourceCode = wrapInMainFunction("""
            var flag: bool = false
            var value: i32 = 0
                            
            if not flag:
                value = 1
                            
            if -value < 0:
                value = 2
            """);
        String ir = compileAndExpectSuccess(sourceCode, "unary_in_if_conditions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("flag", "i1"),
            IrPatterns.alloca("value", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.load("i1", "flag"),
            IrPatterns.logicalNot("i1", "flag"),      // not flag
            IrPatterns.load("i32", "value"),
            IrPatterns.unaryNeg("i32", "value"),      // -value
            IrPatterns.icmp("slt", "i32", "neg_tmp", "0") // -value < 0
        );
    }

    @Test
    public void testUnaryOperationsInWhileConditions() {
        String sourceCode = wrapInMainFunction("""
            var active: bool = true
            var counter: i32 = 5
                            
            while not active:
                counter = counter + 1
                            
            while -counter > -10:
                counter = counter - 1
            """);
        String ir = compileAndExpectSuccess(sourceCode, "unary_in_while_conditions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("active", "i1"),
            IrPatterns.alloca("counter", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.load("i1", "active"),
            IrPatterns.logicalNot("i1", "active"),    // not active
            IrPatterns.load("i32", "counter"),
            IrPatterns.unaryNeg("i32", "counter")     // -counter
        );
    }

    // ============================================================================
    // Unary Operations as Function Arguments
    // ============================================================================

    @Test
    public void testUnaryOperationsAsFunctionArguments() {
        String sourceCode = """
            def process_int(value: i32) -> i32:
                return value * 2
                            
            def process_bool(flag: bool) -> bool:
                return flag
                            
            def process_float(num: f64) -> f64:
                return num + 1.0
                            
            def main() -> i32:
                var x: i32 = 10
                var flag: bool = true
                var num: f64 = 3.14
                var bits: u8 = 7
                            
                var result1: i32 = process_int(-x)
                var result2: bool = process_bool(not flag)
                var result3: f64 = process_float(+num)
                var result4: u8 = ~bits
                            
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "unary_as_function_args");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.load("i32", "x"),
            IrPatterns.unaryNeg("i32", "x"),
            IrPatterns.functionCall("process_int", "i32", List.of(Map.entry("i32", "neg_tmp"))),

            IrPatterns.load("i1", "flag"),
            IrPatterns.logicalNot("i1", "flag"),
            IrPatterns.functionCall("process_bool", "i1", List.of(Map.entry("i1", "not_tmp"))),

            IrPatterns.load("double", "num"),
            IrPatterns.functionCall("process_float", "double", List.of(Map.entry("double", "num"))),

            IrPatterns.load("i8", "bits"),
            IrPatterns.bitwiseNot("i8", "bits")
        );
    }

    // ============================================================================
    // Unary Operations in Return Statements
    // ============================================================================

    @Test
    public void testUnaryOperationsInReturnStatements() {
        String sourceCode = """
            def negate_int(value: i32) -> i32:
                return -value
                            
            def invert_bool(flag: bool) -> bool:
                return not flag
                            
            def identity(num: f32) -> f32:
                return +num
                            
            def complement(bits: u16) -> u16:
                return ~bits
                            
            def main() -> i32:
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "unary_in_return_statements");

        String negateIntFunction = extractFunction(ir, "negate_int");
        assertNotNull(negateIntFunction, "negate_int function should be present in the IR");

        String invertBoolFunction = extractFunction(ir, "invert_bool");
        assertNotNull(invertBoolFunction, "invert_bool function should be present in the IR");

        String identityFunction = extractFunction(ir, "identity");
        assertNotNull(identityFunction, "identity function should be present in the IR");

        String complementFunction = extractFunction(ir, "complement");
        assertNotNull(complementFunction, "complement function should be present in the IR");

        assertIrContains(negateIntFunction,
            IrPatterns.load("i32", "value"),
            IrPatterns.unaryNeg("i32", "value")
        );

        assertIrContains(invertBoolFunction,
            IrPatterns.load("i1", "flag"),
            IrPatterns.logicalNot("i1", "flag")
        );

        assertIrContains(identityFunction,
            IrPatterns.load("float", "num")  // +num is identity operation
        );

        assertIrContains(complementFunction,
            IrPatterns.load("i16", "bits"),
            IrPatterns.bitwiseNot("i16", "bits")
        );
    }

    // ============================================================================
    // Chained Unary Operations
    // ============================================================================

    @Test
    public void testChainedUnaryOperations() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 42
            var y: bool = false
            var z: u8 = 15
                            
            var double_neg: i32 = --x           # Should be equivalent to x
            var double_not: bool = not not y    # Should be equivalent to y
            var complex_chain: i32 = -+-x       # Should be equivalent to -x
            """);
        String ir = compileAndExpectSuccess(sourceCode, "chained_unary_operations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i1"),
            IrPatterns.alloca("z", "i8"),
            IrPatterns.alloca("double_neg", "i32"),
            IrPatterns.alloca("double_not", "i1"),
            IrPatterns.alloca("complex_chain", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            // --x (double negation)
            IrPatterns.load("i32", "x"),
            IrPatterns.unaryNeg("i32", "x"),
            IrPatterns.unaryNeg("i32", "neg_tmp"),

            // not not y (double logical NOT)
            IrPatterns.load("i1", "y"),
            IrPatterns.logicalNot("i1", "y"),
            IrPatterns.logicalNot("i1", "not_tmp"),

            // -+-x (complex chain: -(+(-x)))
            IrPatterns.load("i32", "x"),
            IrPatterns.unaryNeg("i32", "x")
        );
    }

    // ============================================================================
    // Type Compatibility Tests
    // ============================================================================

    @ParameterizedTest
    @MethodSource("validUnaryOperatorTypeCombinations")
    public void testValidUnaryOperatorTypeCombinations(String operator, String typeName,
                                                       String llvmType, String testValue) {
        String sourceCode = wrapInMainFunction("""
            var operand: %s = %s
            var result: %s = %s operand
            """.formatted(typeName, testValue, typeName, operator));

        String ir = compileAndExpectSuccess(sourceCode,
            "valid_" + operator.replace("~", "bitnot").replace(" ", "") + "_"
                + typeName.toLowerCase());

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("operand", llvmType),
            IrPatterns.alloca("result", llvmType)
        );
    }

    // ============================================================================
    // Error Cases - Invalid Type Combinations
    // ============================================================================

    @ParameterizedTest
    @MethodSource("invalidUnaryOperatorTypeCombinations")
    public void testInvalidUnaryOperatorTypeCombinations(String operator, String typeName,
                                                         String testValue,
                                                         String expectedErrorFragment) {
        String sourceCode = wrapInMainFunction("""
            var operand: %s = %s
            var result: %s = %s operand
            """.formatted(typeName, testValue, typeName, operator));

        String errors = compileAndExpectFailure(sourceCode,
            "invalid_" + operator.replace("~", "bitnot").replace(" ", "") + "_"
                + typeName.toLowerCase());

        assertTrue(errors.contains(expectedErrorFragment),
            "Error message should contain: " + expectedErrorFragment + "\nActual errors: "
                + errors);
    }

    @Test
    public void testBitwiseNotWithFloatingPoint() {
        String sourceCode = wrapInMainFunction("""
            var float_val: f32 = 3.14
            var result: f32 = ~float_val
            """);

        String errors = compileAndExpectFailure(sourceCode, "bitwise_not_float_error");

        assertTrue(errors.contains("Bitwise NOT (~) requires an integer operand, got f32"),
            "Should report type error for bitwise NOT on float");
    }

    @Test
    public void testLogicalNotWithNonBoolean() {
        String sourceCode = wrapInMainFunction("""
            var int_val: i32 = 42
            var result: bool = not int_val
            """);

        String errors = compileAndExpectFailure(sourceCode, "logical_not_int_error");

        assertTrue(errors.contains("Logical NOT (not) requires a boolean operand, got i32"),
            "Should report type error for logical NOT on integer");
    }

    @Test
    public void testArithmeticUnaryWithNonNumeric() {
        String sourceCode = wrapInMainFunction("""
            var bool_val: bool = true
            var result: bool = -bool_val
            """);

        String errors = compileAndExpectFailure(sourceCode, "arithmetic_unary_bool_error");

        assertTrue(errors.contains("Unary - requires a numeric operand, got bool"),
            "Should report type error for arithmetic unary on boolean");
    }

    @Test
    public void testUnaryOperationsWithUnitType() {
        String sourceCode = """
            def do_nothing():
                return
                            
            def main() -> i32:
                var result1: i32 = -do_nothing()
                var result2: bool = not do_nothing()
                var result3: i32 = ~do_nothing()
            """;
        String errors = compileAndExpectFailure(sourceCode, "unary_with_unit_type");

        assertTrue(errors.contains("Unary - requires a numeric operand, got ()"),
            "Should report type error for unary minus on unit");
        assertTrue(errors.contains("Logical NOT (not) requires a boolean operand, got ()"),
            "Should report type error for logical NOT on unit");
        assertTrue(errors.contains("Bitwise NOT (~) requires an integer operand, got ()"),
            "Should report type error for bitwise NOT on unit");
    }

    // ============================================================================
    // Complex Expression Tests
    // ============================================================================

    @Test
    public void testUnaryOperationsInComplexExpressions() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 5
            var flag: bool = true
                            
            var complex1: i32 = +-(-a + -b) * 2
            var complex2: bool = not (flag and (a > b))
            var complex3: i32 = ~(a & b) | (a ^ b)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "unary_in_complex_expressions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("flag", "i1"),
            IrPatterns.alloca("complex1", "i32"),
            IrPatterns.alloca("complex2", "i1"),
            IrPatterns.alloca("complex3", "i32")
        );

        assertIrContains(mainFunc,
            // -a in complex expression
            IrPatterns.load("i32", "a"),
            IrPatterns.unaryNeg("i32", "a"),

            // -b in complex expression
            IrPatterns.load("i32", "b"),
            IrPatterns.unaryNeg("i32", "b"),

            // -(-a + -b)
            IrPatterns.unaryNeg("i32", "add"),

            // not (flag and (a > b))
            IrPatterns.logicalNot("i1", "logical"),

            // (a & b)
            IrPatterns.bitwiseNot("i32", "bitand")
        );
    }

    // ============================================================================
    // Literal Operations
    // ============================================================================

    @Test
    public void testUnaryOperationsWithLiterals() {
        String sourceCode = wrapInMainFunction("""
            var neg_int: i32 = -42
            var neg_float: f64 = -3.14
            var not_bool: bool = not true
            var complement: u8 = ~(255 as u8)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "unary_with_literals");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("neg_int", "i32"),
            IrPatterns.alloca("neg_float", "double"),
            IrPatterns.alloca("not_bool", "i1"),
            IrPatterns.alloca("complement", "i8")
        );

        // Literals with unary operators should be compile-time constants
        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("-42", "i32", "neg_int"),
            IrPatterns.store("-3.140000e\\+00", "double", "neg_float"),
            IrPatterns.store("false", "i1", "not_bool")  // not true = false
        );
    }

    // ============================================================================
    // Helper Methods and Data Providers
    // ============================================================================

    private static Stream<Arguments> unaryOperatorTestCases() {
        return Stream.of(
            // [operator, operandType, operandValue, expectedIRPattern]
            Arguments.of("+", "i32", "42", ""),             // Identity operation
            Arguments.of("-", "i32", "42", "sub i32"),              // Integer negation
            Arguments.of("+", "f64", "3.14", ""),                   // Identity operation
            Arguments.of("-", "f64", "3.14", "fneg double"),        // Float negation
            Arguments.of("not", "bool", "true", "xor i1"),          // Logical NOT
            Arguments.of("~", "u8", "255", "xor i8")                // Bitwise NOT
        );
    }

    private static Stream<Arguments> validUnaryOperatorTypeCombinations() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            // Arithmetic unary operators (+, -) with numeric types
            Arguments.of("+", "i8", "i8", "42"),
            Arguments.of("+", "i16", "i16", "42"),
            Arguments.of("+", "i32", "i32", "42"),
            Arguments.of("+", "i64", "i64", "42"),
            Arguments.of("+", "isize", sizeType, "42"),
            Arguments.of("+", "u8", "i8", "42"),
            Arguments.of("+", "u16", "i16", "42"),
            Arguments.of("+", "u32", "i32", "42"),
            Arguments.of("+", "u64", "i64", "42"),
            Arguments.of("+", "usize", sizeType, "42"),
            Arguments.of("+", "f32", "float", "3.14"),
            Arguments.of("+", "f64", "double", "3.14"),

            Arguments.of("-", "i8", "i8", "42"),
            Arguments.of("-", "i16", "i16", "42"),
            Arguments.of("-", "i32", "i32", "42"),
            Arguments.of("-", "i64", "i64", "42"),
            Arguments.of("-", "isize", sizeType, "42"),
            Arguments.of("-", "u8", "i8", "42"),
            Arguments.of("-", "u16", "i16", "42"),
            Arguments.of("-", "u32", "i32", "42"),
            Arguments.of("-", "u64", "i64", "42"),
            Arguments.of("-", "usize", sizeType, "42"),
            Arguments.of("-", "f32", "float", "3.14"),
            Arguments.of("-", "f64", "double", "3.14"),

            // Logical NOT with boolean
            Arguments.of("not", "bool", "i1", "true"),

            // Bitwise NOT with integer types
            Arguments.of("~", "i8", "i8", "42"),
            Arguments.of("~", "i16", "i16", "42"),
            Arguments.of("~", "i32", "i32", "42"),
            Arguments.of("~", "i64", "i64", "42"),
            Arguments.of("~", "isize", sizeType, "42"),
            Arguments.of("~", "u8", "i8", "42"),
            Arguments.of("~", "u16", "i16", "42"),
            Arguments.of("~", "u32", "i32", "42"),
            Arguments.of("~", "u64", "i64", "42"),
            Arguments.of("~", "usize", sizeType, "42")
        );
    }

    private static Stream<Arguments> invalidUnaryOperatorTypeCombinations() {
        return Stream.of(
            // Logical NOT with non-boolean types
            Arguments.of("not", "i32", "42", "Logical NOT (not) requires a boolean operand"),
            Arguments.of("not", "f32", "3.14", "Logical NOT (not) requires a boolean operand"),
            Arguments.of("not", "u8", "255", "Logical NOT (not) requires a boolean operand"),

            // Bitwise NOT with non-integer types
            Arguments.of("~", "bool", "true", "Bitwise NOT (~) requires an integer operand"),
            Arguments.of("~", "f32", "3.14", "Bitwise NOT (~) requires an integer operand"),
            Arguments.of("~", "f64", "2.718", "Bitwise NOT (~) requires an integer operand"),

            // Arithmetic unary with non-numeric types
            Arguments.of("+", "bool", "true", "Unary + requires a numeric operand"),
            Arguments.of("-", "bool", "false", "Unary - requires a numeric operand")
        );
    }

    private String getLlvmType(String resoType) {
        return switch (resoType) {
            case "i8", "u8" -> "i8";
            case "i16", "u16" -> "i16";
            case "i32", "u32" -> "i32";
            case "i64", "u64" -> "i64";
            case "f32" -> "float";
            case "f64" -> "double";
            case "bool" -> "i1";
            default -> throw new IllegalArgumentException("Unknown type: " + resoType);
        };
    }
}