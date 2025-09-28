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
            var intVal: i32 = 42
            var floatVal: f64 = 3.14
            var plusInt: i32 = +intVal
            var plusFloat: f64 = +floatVal
            var plusLiteral: i32 = +123
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_unary_plus");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("intVal", "i32"),
            IrPatterns.alloca("floatVal", "double"),
            IrPatterns.alloca("plusInt", "i32"),
            IrPatterns.alloca("plusFloat", "double"),
            IrPatterns.alloca("plusLiteral", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("42", "i32", "intVal"),
            IrPatterns.store("3.140000e\\+00", "double", "floatVal"),
            IrPatterns.load("i32", "intVal"),     // +intVal (identity operation)
            IrPatterns.load("double", "floatVal"), // +floatVal (identity operation)
            IrPatterns.store("123", "i32", "plusLiteral") // +123 (compile-time constant)
        );
    }

    @Test
    public void testBasicUnaryMinusOperations() {
        String sourceCode = wrapInMainFunction("""
            var intVal: i32 = 42
            var floatVal: f64 = 3.14
            var negInt: i32 = -intVal
            var negFloat: f64 = -floatVal
            var negLiteral: i32 = -123
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_unary_minus");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("intVal", "i32"),
            IrPatterns.alloca("floatVal", "double"),
            IrPatterns.alloca("negInt", "i32"),
            IrPatterns.alloca("negFloat", "double"),
            IrPatterns.alloca("negLiteral", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("42", "i32", "intVal"),
            IrPatterns.store("3.140000e\\+00", "double", "floatVal"),
            IrPatterns.load("i32", "intVal"),
            IrPatterns.unaryNeg("i32", "intVal"),      // Integer negation
            IrPatterns.load("double", "floatVal"),
            IrPatterns.unaryFNeg("double", "floatVal"), // Float negation
            IrPatterns.store("-123", "i32", "negLiteral") // Compile-time constant
        );
    }

    @Test
    public void testBasicLogicalNotOperations() {
        String sourceCode = wrapInMainFunction("""
            var boolVal: bool = true
            var notBool: bool = not boolVal
            var notLiteral: bool = not false
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_logical_not");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("boolVal", "i1"),
            IrPatterns.alloca("notBool", "i1"),
            IrPatterns.alloca("notLiteral", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("true", "i1", "boolVal"),
            IrPatterns.load("i1", "boolVal"),
            IrPatterns.logicalNot("i1", "boolVal"),     // Logical nOT with xOR 1
            IrPatterns.store("true", "i1", "notLiteral")     // not false = true
        );
    }

    @Test
    public void testBasicBitwiseNotOperations() {
        String sourceCode = wrapInMainFunction("""
            var intVal: i32 = 42
            var uintVal: u16 = 255
            var notInt: i32 = ~intVal
            var notUint: u16 = ~uintVal
            var notLiteral: i8 = ~(5 as i8)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_bitwise_not");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("intVal", "i32"),
            IrPatterns.alloca("uintVal", "i16"),
            IrPatterns.alloca("notInt", "i32"),
            IrPatterns.alloca("notUint", "i16"),
            IrPatterns.alloca("notLiteral", "i8")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("42", "i32", "intVal"),
            IrPatterns.store("255", "i16", "uintVal"),
            IrPatterns.load("i32", "intVal"),
            IrPatterns.bitwiseNot("i32", "intVal"),     // Bitwise nOT with xOR all-ones
            IrPatterns.load("i16", "uintVal"),
            IrPatterns.bitwiseNot("i16", "uintVal")     // Bitwise nOT with xOR all-ones
        );
    }

    @Test
    public void testUnaryOperationsAsExpressionStatements() {
        String sourceCode = """
            def getValue() -> i32:
                return 42
            
            def getBool() -> bool:
                return true
            
            def main() -> i32:
                -getValue()
                +getValue()
                ~getValue()
                not getBool()
            """;
        String ir = compileAndExpectSuccess(sourceCode, "unary_expression_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in IR");

        // Verify all unary operations are performed
        assertIrContains(mainFunc,
            IrPatterns.functionCall("getValue", "i32", Collections.emptyList()),
            IrPatterns.sub("i32", "0", "getValue"), // unary minus: 0 - value
            IrPatterns.functionCall("getValue", "i32", Collections.emptyList()),
            // unary plus (identity)
            IrPatterns.functionCall("getValue", "i32", Collections.emptyList()),
            IrPatterns.bitwiseNot("i32", "getValue"),
            IrPatterns.logicalNot("i1", "getBool")
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
            def processInt(value: i32) -> i32:
                return value * 2
            
            def processBool(flag: bool) -> bool:
                return flag
            
            def processFloat(num: f64) -> f64:
                return num + 1.0
            
            def main() -> i32:
                var x: i32 = 10
                var flag: bool = true
                var num: f64 = 3.14
                var bits: u8 = 7
            
                var result1: i32 = processInt(-x)
                var result2: bool = processBool(not flag)
                var result3: f64 = processFloat(+num)
                var result4: u8 = ~bits
            
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "unary_as_function_args");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.load("i32", "x"),
            IrPatterns.unaryNeg("i32", "x"),
            IrPatterns.functionCall("processInt", "i32", List.of(Map.entry("i32", "neg_tmp"))),

            IrPatterns.load("i1", "flag"),
            IrPatterns.logicalNot("i1", "flag"),
            IrPatterns.functionCall("processBool", "i1", List.of(Map.entry("i1", "not_tmp"))),

            IrPatterns.load("double", "num"),
            IrPatterns.functionCall("processFloat", "double", List.of(Map.entry("double", "num"))),

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
            def negateInt(value: i32) -> i32:
                return -value
            
            def invertBool(flag: bool) -> bool:
                return not flag
            
            def identity(num: f32) -> f32:
                return +num
            
            def complement(bits: u16) -> u16:
                return ~bits
            
            def main() -> i32:
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "unary_in_return_statements");

        String negateIntFunction = extractFunction(ir, "negateInt");
        assertNotNull(negateIntFunction, "negateInt function should be present in the IR");

        String invertBoolFunction = extractFunction(ir, "invertBool");
        assertNotNull(invertBoolFunction, "invertBool function should be present in the IR");

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
            
            var doubleNeg: i32 = --x           # Should be equivalent to x
            var doubleNot: bool = not not y    # Should be equivalent to y
            var complexChain: i32 = -+-x       # Should be equivalent to -x
            """);
        String ir = compileAndExpectSuccess(sourceCode, "chained_unary_operations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i1"),
            IrPatterns.alloca("z", "i8"),
            IrPatterns.alloca("doubleNeg", "i32"),
            IrPatterns.alloca("doubleNot", "i1"),
            IrPatterns.alloca("complexChain", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            // --x (double negation)
            IrPatterns.load("i32", "x"),
            IrPatterns.unaryNeg("i32", "x"),
            IrPatterns.unaryNeg("i32", "neg_tmp"),

            // not not y (double logical nOT)
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
            var floatVal: f32 = 3.14
            var result: f32 = ~floatVal
            """);

        String errors = compileAndExpectFailure(sourceCode, "bitwise_not_float_error");

        assertTrue(errors.contains("Bitwise NOT (~) requires an integer operand, got f32"),
            "Should report type error for bitwise NOT on float");
    }

    @Test
    public void testLogicalNotWithNonBoolean() {
        String sourceCode = wrapInMainFunction("""
            var intVal: i32 = 42
            var result: bool = not intVal
            """);

        String errors = compileAndExpectFailure(sourceCode, "logical_not_int_error");

        assertTrue(errors.contains("Logical NOT (not) requires a boolean operand, got i32"),
            "Should report type error for logical NOT on integer");
    }

    @Test
    public void testArithmeticUnaryWithNonNumeric() {
        String sourceCode = wrapInMainFunction("""
            var boolVal: bool = true
            var result: bool = -boolVal
            """);

        String errors = compileAndExpectFailure(sourceCode, "arithmetic_unary_bool_error");

        assertTrue(errors.contains("Unary - requires a numeric operand, got bool"),
            "Should report type error for arithmetic unary on boolean");
    }

    @Test
    public void testUnaryOperationsWithUnitType() {
        String sourceCode = """
            def doNothing():
                return
            
            def main() -> i32:
                var result1: i32 = -doNothing()
                var result2: bool = not doNothing()
                var result3: i32 = ~doNothing()
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
            var negInt: i32 = -42
            var negFloat: f64 = -3.14
            var notBool: bool = not true
            var complement: u8 = ~(255 as u8)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "unary_with_literals");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("negInt", "i32"),
            IrPatterns.alloca("negFloat", "double"),
            IrPatterns.alloca("notBool", "i1"),
            IrPatterns.alloca("complement", "i8")
        );

        // Literals with unary operators should be compile-time constants
        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("-42", "i32", "negInt"),
            IrPatterns.store("-3.140000e\\+00", "double", "negFloat"),
            IrPatterns.store("false", "i1", "notBool")  // not true = false
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