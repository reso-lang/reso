package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for arithmetic operations in Reso.
 * Tests compilation from Reso source code to LLVM IR with verification of:
 * - Correct arithmetic operations for signed and unsigned integers
 * - Floating-point arithmetic operations
 * - Type compatibility enforcement
 * - Proper error handling for invalid operations
 */
public class ArithmeticTest extends BaseTest {

    // ============================================================================
    // Basic Arithmetic Operations
    // ============================================================================

    @Test
    public void testBasicSignedIntegerArithmetic() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 5
            var sum: i32 = a + b
            var diff: i32 = a - b
            var product: i32 = a * b
            var quotient: i32 = a div b
            var remainder: i32 = a rem b
            var modulus: i32 = a mod b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_signed_arithmetic");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Variable allocations
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("sum", "i32"),
            IrPatterns.alloca("diff", "i32"),
            IrPatterns.alloca("product", "i32"),
            IrPatterns.alloca("quotient", "i32"),
            IrPatterns.alloca("remainder", "i32"),
            IrPatterns.alloca("modulus", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            // Initial stores
            IrPatterns.store("10", "i32", "a"),
            IrPatterns.store("5", "i32", "b"),

            // Arithmetic operations - signed integer operations
            IrPatterns.add("i32", "a", "b"),
            IrPatterns.sub("i32", "a", "b"),
            IrPatterns.mul("i32", "a", "b"),
            IrPatterns.sdiv("i32", "a", "b"),   // Signed division
            IrPatterns.srem("i32", "a", "b"),    // Signed remainder
            IrPatterns.select("i1", "needs_adjustment", "i32", "adjusted_result", "srem")
        );
    }

    @Test
    public void testBasicUnsignedIntegerArithmetic() {
        String sourceCode = wrapInMainFunction("""
            var a: u32 = 10
            var b: u32 = 5
            var sum: u32 = a + b
            var diff: u32 = a - b
            var product: u32 = a * b
            var quotient: u32 = a div b
            var remainder: u32 = a rem b
            var modulus: u32 = a mod b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_unsigned_arithmetic");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Variable allocations
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("sum", "i32"),
            IrPatterns.alloca("diff", "i32"),
            IrPatterns.alloca("product", "i32"),
            IrPatterns.alloca("quotient", "i32"),
            IrPatterns.alloca("remainder", "i32"),
            IrPatterns.alloca("modulus", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            // Initial stores
            IrPatterns.store("10", "i32", "a"),
            IrPatterns.store("5", "i32", "b"),

            // Arithmetic operations - unsigned integer operations
            IrPatterns.add("i32", "a", "b"),
            IrPatterns.sub("i32", "a", "b"),
            IrPatterns.mul("i32", "a", "b"),
            IrPatterns.udiv("i32", "a", "b"),   // Unsigned division
            IrPatterns.urem("i32", "a", "b")    // Unsigned remainder and modulus
        );
    }

    @Test
    public void testBasicFloatingPointArithmetic() {
        String sourceCode = wrapInMainFunction("""
            var a: f64 = 10.5
            var b: f64 = 2.5
            var sum: f64 = a + b
            var diff: f64 = a - b
            var product: f64 = a * b
            var quotient: f64 = a div b
            var remainder: f64 = a rem b
            var modulus: f64 = a mod b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_float_arithmetic");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Variable allocations
            IrPatterns.alloca("a", "double"),
            IrPatterns.alloca("b", "double"),
            IrPatterns.alloca("sum", "double"),
            IrPatterns.alloca("diff", "double"),
            IrPatterns.alloca("product", "double"),
            IrPatterns.alloca("quotient", "double"),
            IrPatterns.alloca("remainder", "double"),
            IrPatterns.alloca("modulus", "double")
        );

        assertIrContainsInOrder(mainFunc,
            // Initial stores
            IrPatterns.store("1.050000e\\+01", "double", "a"),
            IrPatterns.store("2.500000e\\+00", "double", "b"),

            // Floating-point arithmetic operations
            IrPatterns.fadd("double", "a", "b"),
            IrPatterns.fsub("double", "a", "b"),
            IrPatterns.fmul("double", "a", "b"),
            IrPatterns.fdiv("double", "a", "b"),
            IrPatterns.frem("double", "a", "b"),
            IrPatterns.select("i1", "needs_adjustment", "double", "adjusted_result", "frem")
        );
    }

    // ============================================================================
    // Arithmetic in Different Contexts
    // ============================================================================

    @Test
    public void testArithmeticInIfConditions() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var y: i32 = 5
            if (x + y) > 12:
                var z: i32 = x * y
            
            var a = 3.14
            var b = 2.0
            if (a * b) < 10.0:
                var c: f64 = a + b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "arithmetic_in_if");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.alloca("a", "double"),
            IrPatterns.alloca("b", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.add("i32", "x", "y"),     // x + y in condition
            IrPatterns.icmp("sgt", "i32", "add", "12"),  // Greater than comparison
            IrPatterns.mul("i32", "x", "y")      // x * y in then block
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fmul("double", "a", "b"),     // a * b in condition
            IrPatterns.fcmp("olt", "double", "fmul", "1.000000e\\+01"), // Less than comparison
            IrPatterns.fadd("double", "a", "b")      // a + b in then block
        );
    }

    @Test
    public void testArithmeticInWhileConditions() {
        String sourceCode = wrapInMainFunction("""
            var counter: i32 = 0
            var limit: i32 = 10
            while (counter + 1) < limit:
                counter = counter + 2
            """);
        String ir = compileAndExpectSuccess(sourceCode, "arithmetic_in_while");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("counter", "i32"),
            IrPatterns.alloca("limit", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.add("i32", "counter", "1"),     // counter + 1 in condition
            IrPatterns.add("i32", "counter", "2")      // counter + 2 in body
        );
    }

    @Test
    public void testArithmeticInVariableInitialization() {
        String sourceCode = wrapInMainFunction("""
            var base: i32 = 5
            var doubled: i32 = base * 2
            var incremented: i32 = doubled + 1
            var float = 3.14
            var calculated: f64 = float * 2.0
            """);
        String ir = compileAndExpectSuccess(sourceCode, "arithmetic_in_initialization");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("base", "i32"),
            IrPatterns.alloca("doubled", "i32"),
            IrPatterns.alloca("incremented", "i32"),
            IrPatterns.alloca("calculated", "double"),
            IrPatterns.alloca("float", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("5", "i32", "base"),
            IrPatterns.mul("i32", "base", "2"),     // base * 2
            IrPatterns.add("i32", "double", "1"),     // doubled + 1
            IrPatterns.fmul("double", "float", "2.000000e\\+00") // float * 2.0
        );
    }

    @Test
    public void testArithmeticInAssignments() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var y = 5
            var result: i32 = 0
            result = x + y
            result = result * 2
            result = result - 3
            """);
        String ir = compileAndExpectSuccess(sourceCode, "arithmetic_in_assignments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.alloca("result", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.add("i32", "x", "y"),          // x + y
            IrPatterns.mul("i32", "result", "2"),     // result * 2
            IrPatterns.sub("i32", "result", "3")      // result - 3
        );
    }

    @Test
    public void testArithmeticAsFunctionArguments() {
        String sourceCode = """
            def calculate(a: i32, b: i32) -> i32:
                return a + b
            
            def main() -> i32:
                var x: i32 = 10
                var y: i32 = 5
                var result: i32 = calculate(x + 2, y * 3)
            """;
        String ir = compileAndExpectSuccess(sourceCode, "arithmetic_as_function_args");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.add("i32", "x", "2"),     // x + 2 as first argument
            IrPatterns.mul("i32", "y", "3")      // y * 3 as second argument
        );

        String calculateFunc = extractFunction(ir, "calculate");
        assertNotNull(calculateFunc, "Calculate function should be present in the IR");

        assertIrContains(calculateFunc,
            IrPatterns.add("i32", "a", "b")      // a + b in calculate function
        );
    }

    // ============================================================================
    // Type Compatibility Tests
    // ============================================================================

    @Test
    public void testSameTypeOperations() {
        String sourceCode = wrapInMainFunction("""
            var int8a: i8 = 10
            var int8b: i8 = 5
            var int8result: i8 = int8a + int8b
            
            var uint16a: u16 = 100
            var uint16b: u16 = 50
            var uint16result: u16 = uint16a - uint16b
            
            var float32a: f32 = 3.14
            var float32b: f32 = 2.0
            var float32result: f32 = float32a * float32b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "same_type_operations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("int8a", "i8"),
            IrPatterns.alloca("int8b", "i8"),
            IrPatterns.alloca("int8result", "i8"),
            IrPatterns.alloca("uint16a", "i16"),
            IrPatterns.alloca("uint16b", "i16"),
            IrPatterns.alloca("uint16result", "i16"),
            IrPatterns.alloca("float32a", "float"),
            IrPatterns.alloca("float32b", "float"),
            IrPatterns.alloca("float32result", "float")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.add("i8", "int8a", "int8b"),          // i8 + i8
            IrPatterns.sub("i16", "uint16a", "uint16b"),     // u16 - u16
            IrPatterns.fmul("float", "float32a", "float32b") // f32 * f32
        );
    }

    @Test
    public void testLiteralOperations() {
        String sourceCode = wrapInMainFunction("""
            var result1: i32 = 10 + 5
            var result2: f64 = 3.14 * 2.0
            var result3: u32 = 100 div 4
            """);
        String ir = compileAndExpectSuccess(sourceCode, "literal_operations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("result1", "i32"),
            IrPatterns.alloca("result2", "double"),
            IrPatterns.alloca("result3", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("15", "i32", "result1"),           // Constant folding: 10 + 5
            IrPatterns.store("6.280000e\\+00", "double", "result2"), // Constant folding: 3.14 * 2.0
            IrPatterns.store("25", "i32", "result3")            // Constant folding: 100 / 4
        );
    }

    @ParameterizedTest
    @MethodSource("signedIntegerTypes")
    public void testSignedIntegerDivisionAndRemainder(String typeName, String llvmType) {
        String sourceCode = wrapInMainFunction(String.format("""
            var a: %s = 10
            var b: %s = 3
            var quotient: %s = a div b
            var remainder: %s = a rem b
            var modulus: %s = a mod b
            """, typeName, typeName, typeName, typeName, typeName));
        String ir = compileAndExpectSuccess(sourceCode,
            "signed_" + typeName.toLowerCase() + "_div_rem");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.sdiv(llvmType, "a", "b"),   // Signed division
            IrPatterns.srem(llvmType, "a", "b"),   // Signed remainder
            IrPatterns.select("i1", "needs_adjustment", llvmType, "adjusted_result", "srem")
        );
    }

    @Test
    public void testSizeIntegerDivisionAndRemainder() {
        String sourceCode = wrapInMainFunction("""
            var a: isize = 10
            var b: isize = 3
            var quotient: isize = a div b
            var remainder: isize = a rem b
            var modulus: isize = a mod b
            
            var c: usize = 10
            var d: usize = 3
            var uquotient: usize = c div d
            var uremainder: usize = c rem d
            var umodulus: usize = c mod d
            """);
        String ir = compileAndExpectSuccess(sourceCode,
            "size_integer_div_rem");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        String irType = "i" + POINTER_SIZE;

        assertIrContainsInOrder(mainFunc,
            IrPatterns.sdiv(irType, "a", "b"),   // Signed division for isize
            IrPatterns.srem(irType, "a", "b"),   // Signed remainder for isize
            IrPatterns.select("i1", "needs_adjustment", irType, "adjusted_result", "srem"),
            // Modulus handling for isize

            IrPatterns.udiv(irType, "c", "d"),   // Unsigned division for usize
            IrPatterns.urem(irType, "c", "d")    // Unsigned remainder and modulus for usize
        );
    }

    @ParameterizedTest
    @MethodSource("unsignedIntegerTypes")
    public void testUnsignedIntegerDivisionAndRemainder(String typeName, String llvmType) {
        String sourceCode = wrapInMainFunction(String.format("""
            var a: %s = 10
            var b: %s = 3
            var quotient: %s = a div b
            var remainder: %s = a rem b
            var modulus: %s = a mod b
            """, typeName, typeName, typeName, typeName, typeName));
        String ir = compileAndExpectSuccess(sourceCode,
            "unsigned_" + typeName.toLowerCase() + "_div_rem");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.udiv(llvmType, "a", "b"),   // Unsigned division
            IrPatterns.urem(llvmType, "a", "b")    // Unsigned remainder and modulus
        );
    }

    @ParameterizedTest
    @MethodSource("floatingPointTypes")
    public void testFloatingPointOperations(String typeName, String llvmType) {
        String sourceCode = wrapInMainFunction(String.format("""
            var a: %s = 10.5
            var b: %s = 2.5
            var sum: %s = a + b
            var diff: %s = a - b
            var product: %s = a * b
            var quotient: %s = a div b
            var remainder: %s = a rem b
            var modulus: %s = a mod b
            """, typeName, typeName, typeName, typeName, typeName, typeName, typeName, typeName));
        String ir = compileAndExpectSuccess(sourceCode,
            "float_" + typeName.toLowerCase() + "_operations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fadd(llvmType, "a", "b"),
            IrPatterns.fsub(llvmType, "a", "b"),
            IrPatterns.fmul(llvmType, "a", "b"),
            IrPatterns.fdiv(llvmType, "a", "b"),
            IrPatterns.frem(llvmType, "a", "b"),
            IrPatterns.select("i1", "needs_adjustment", llvmType, "adjusted_result", "frem")
        );
    }

    // ============================================================================
    // Complex Arithmetic Expressions
    // ============================================================================

    @Test
    public void testComplexArithmeticExpressions() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 5
            var c: i32 = 2
            var result: i32 = (a + b) * c - (a div b)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "complex_arithmetic");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.add("i32", "a", "b"),     // a + b
            IrPatterns.mul("i32", "add", "c"),     // (a + b) * c
            IrPatterns.sdiv("i32", "a", "b"),   // a / b
            IrPatterns.sub("i32", "mul", "sdiv")      // final subtraction
        );
    }

    @Test
    public void testNestedArithmeticExpressions() {
        String sourceCode = wrapInMainFunction("""
            var x: f64 = 2.0
            var y: f64 = 3.0
            var z: f64 = 4.0
            var result: f64 = x * (y + z) div (y - z)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "nested_arithmetic");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fadd("double", "y", "z"), // y + z
            IrPatterns.fmul("double", "x", "add"), // x * (y + z)
            IrPatterns.fsub("double", "y", "z"), // y - z
            IrPatterns.fdiv("double", "fmul", "fsub")  // final division
        );
    }

    // ============================================================================
    // Error Cases
    // ============================================================================

    @Test
    public void testIncompatibleTypeError() {
        String sourceCode = wrapInMainFunction("""
            var int_val: i32 = 10
            var float_val: f64 = 5.5
            var result: i32 = int_val + float_val
            """);
        String errors = compileAndExpectFailure(sourceCode, "incompatible_types");

        assertFalse(errors.isEmpty(),
            "Should report error for arithmetic between i32 and f64");
        assertTrue(errors.contains("Cannot perform arithmetic operation")
                || errors.contains("different types")
                || errors.contains("incompatible"),
            "Error message should indicate type incompatibility");
    }

    @Test
    public void testSignedUnsignedMixingError() {
        String sourceCode = wrapInMainFunction("""
            var signed: i32 = 10
            var unsigned: u32 = 5
            var result: i32 = signed + unsigned
            """);
        String errors = compileAndExpectFailure(sourceCode, "signed_unsigned_mixing");

        assertFalse(errors.isEmpty(),
            "Should report error for mixing signed and unsigned integers");
        assertTrue(errors.contains("Cannot perform arithmetic operation")
                || errors.contains("different types")
                || errors.contains("signed")
                || errors.contains("unsigned"),
            "Error message should indicate signed/unsigned mismatch");
    }

    @Test
    public void testNonNumericTypeError() {
        String sourceCode = wrapInMainFunction("""
            var bool1: bool = true
            var bool2: bool = false
            var result: bool = bool1 + bool2
            """);
        String errors = compileAndExpectFailure(sourceCode, "non_numeric_arithmetic");

        assertFalse(errors.isEmpty(),
            "Should report error for arithmetic on bool types");
        assertTrue(errors.contains("arithmetic") && errors.contains("bool"),
            "Error message should indicate arithmetic cannot be performed on bool");
    }

    @Test
    public void testDifferentSizedIntegerError() {
        String sourceCode = wrapInMainFunction("""
            var small: i8 = 10
            var large: i64 = 100
            var result: i8 = small + large
            """);
        String errors = compileAndExpectFailure(sourceCode, "different_sized_integers");

        assertFalse(errors.isEmpty(),
            "Should report error for arithmetic between different sized integers");
    }

    @Test
    public void testDifferentFloatingPointTypesError() {
        String sourceCode = wrapInMainFunction("""
            var float32: f32 = 3.14
            var float64: f64 = 2.71
            var result: f32 = float32 + float64
            """);
        String errors = compileAndExpectFailure(sourceCode, "different_float_types");

        assertFalse(errors.isEmpty(),
            "Should report error for arithmetic between f32 and f64");
    }

    @Test
    public void testUnsupportedOperatorError() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 3
            var result: i32 = a ** b
            """);
        String errors = compileAndExpectFailure(sourceCode, "unsupported_operator");

        assertFalse(errors.isEmpty(),
            "Should report error for unsupported operator **");
    }

    @Test
    public void testMixedLiterals() {
        String sourceCode = wrapInMainFunction("""
            var result = 5 + 5.5
            """);
        String errors = compileAndExpectFailure(sourceCode, "mixed_literals");

        assertFalse(errors.isEmpty(),
            "Should report error for mixed integer and floating-point literals");
    }

    @Test
    public void testIntLiteralf32() {
        String sourceCode = wrapInMainFunction("""
            var f = 5.6
            var result = f + 5
            """);
        String errors = compileAndExpectFailure(sourceCode, "int_literal_float32");

        assertFalse(errors.isEmpty(),
            "Should report error for adding Int literal to f32 variable");
    }

    @Test
    public void testFloatLiterali32() {
        String sourceCode = wrapInMainFunction("""
            var f = 5.6
            var result = 5 + f
            """);
        String errors = compileAndExpectFailure(sourceCode, "float_literal_int32");

        assertFalse(errors.isEmpty(),
            "Should report error for adding Float literal to i32 variable");
    }

    @Test
    public void testUnitTypeError() {
        String sourceCode = """
            def unit_a():
                return
            def unit_b():
                return
            
            def main() -> i32:
                unit_a() + unit_b()
            """;
        String errors = compileAndExpectFailure(sourceCode, "void_type_error");

        assertTrue(errors.contains(
                "ERROR: Cannot perform arithmetic operation '+' on non-numeric types")
                && errors.contains("()"),
            "Error message should indicate arithmetic cannot be performed on unit type");
    }

    // ============================================================================
    // Compound Assignment Operations
    // ============================================================================

    @Test
    public void testCompoundArithmeticAssignments() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var y: i32 = 5
            x += y
            x -= 2
            x *= 3
            x div= 2
            x rem= 7
            x mod= 4
            """);
        String ir = compileAndExpectSuccess(sourceCode, "compound_assignments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("10", "i32", "x"),
            IrPatterns.store("5", "i32", "y"),
            IrPatterns.add("i32", "x", "y"),     // x += y
            IrPatterns.sub("i32", "x", "2"),     // x -= 2
            IrPatterns.mul("i32", "x", "3"),     // x *= 3
            IrPatterns.sdiv("i32", "x", "2"),   // x div= 2
            IrPatterns.srem("i32", "x", "7"),    // x rem= 7
            IrPatterns.select("i1", "needs_adjustment", "i32", "adjusted_result", "srem")
        );
    }

    @Test
    public void testCompoundAssignmentsWithUnsignedTypes() {
        String sourceCode = wrapInMainFunction("""
            var x: u32 = 20
            x div= 4
            x rem= 3
            x mod= 5
            """);
        String ir = compileAndExpectSuccess(sourceCode, "compound_unsigned_assignments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.store("20", "i32", "x"),
            IrPatterns.udiv("i32", "x", "4"),   // Unsigned division for u32
            IrPatterns.urem("i32", "x", "3"),    // Unsigned remainder for u32
            IrPatterns.urem("i32", "x", "5")    // Unsigned modulus for u32
        );
    }

    @Test
    public void testCompoundAssignmentsWithFloatingPoint() {
        String sourceCode = wrapInMainFunction("""
            var x: f64 = 10.0
            x += 5.5
            x -= 2.5
            x *= 1.5
            x div= 2.0
            """);
        String ir = compileAndExpectSuccess(sourceCode, "compound_float_assignments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("x", "double"),
            IrPatterns.store("1.000000e\\+01", "double", "x"),
            IrPatterns.fadd("double", "x", "5.500000e\\+00"),
            IrPatterns.fsub("double", "x", "2.500000e\\+00"),
            IrPatterns.fmul("double", "x", "1.500000e\\+00"),
            IrPatterns.fdiv("double", "x", "2.000000e\\+00")
        );
    }

    // ============================================================================
    // Edge Cases and Boundary Conditions
    // ============================================================================

    @Test
    public void testArithmeticOrder() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 5
            var c: i32 = 2
            var result: i32 = a + b * c    # Should be 10 + (5 * 2) = 20
            """);
        String ir = compileAndExpectSuccess(sourceCode, "arithmetic_order");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.mul("i32", "b", "c"),
            IrPatterns.add("i32", "a", "mul")
        );
    }

    @Test
    public void testArithmeticPrecedence() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 5
            var c: i32 = 2
            var result: i32 = (a + b) * c  # Should be (10 + 5) * 2 = 30
            """);
        String ir = compileAndExpectSuccess(sourceCode, "arithmetic_precedence");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.add("i32", "a", "b"),
            IrPatterns.mul("i32", "add", "c")
        );
    }

    // ============================================================================
    // Literal Type Inference and Compatibility
    // ============================================================================

    @Test
    public void testLiteralTypeInferenceWithIntegers() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var result1: i32 = a + 5      # i32 + integer literal
            var result2: i32 = 3 + a      # integer literal + i32
            var result3: i32 = 7 + 8      # literal + literal
            """);
        String ir = compileAndExpectSuccess(sourceCode, "literal_type_inference_int");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.add("i32", "a", "5"),     // a + 5
            IrPatterns.add("i32", "3", "a"),     // 3 + a
            IrPatterns.store("15", "i32", "result3") // Constant folding: 7 + 8
        );
    }

    @Test
    public void testLiteralTypeInferenceWithFloats() {
        String sourceCode = wrapInMainFunction("""
            var a: f64 = 3.14
            var result1: f64 = a + 2.86     # f64 + float literal
            var result2: f64 = 1.5 + a      # float literal + f64
            var result3: f64 = 2.5 * 4.0    # literal * literal
            """);
        String ir = compileAndExpectSuccess(sourceCode, "literal_type_inference_float");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fadd("double", "a", "2.860000e\\+00"), // a + 2.86
            IrPatterns.fadd("double", "1.500000e\\+00", "a"), // 1.5 + a
            IrPatterns.store("1.000000e\\+01", "double", "result3") // Constant folding: 2.5 * 4.0
        );
    }

    @Test
    public void testUntypedLiteralOperations() {
        String sourceCode = wrapInMainFunction("""
            # These should work with untyped literals
            var result1: i32 = 10 + 5
            var result2: f64 = 3.14 + 2.86
            var result3: u32 = 100 * 2
            """);
        String ir = compileAndExpectSuccess(sourceCode, "untyped_literal_operations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("15", "i32", "result1"),
            IrPatterns.store("6.000000e\\+00", "double", "result2"),
            IrPatterns.store("200", "i32", "result3")
        );
    }

    // ============================================================================
    // Enhanced Error Cases with Detailed Validation
    // ============================================================================

    @Test
    public void testDetailedIncompatibleTypeError() {
        String sourceCode = wrapInMainFunction("""
            var int_val: i32 = 10
            var float_val: f64 = 5.5
            var result: i32 = int_val + float_val
            """);
        String errors = compileAndExpectFailure(sourceCode, "detailed_incompatible_types");

        assertFalse(errors.isEmpty(),
            "Should report error for arithmetic between i32 and f64");
        assertTrue(errors.contains("i32") && errors.contains("f64"),
            "Error message should mention both i32 and f64 types");
    }

    @Test
    public void testDetailedSignedUnsignedError() {
        String sourceCode = wrapInMainFunction("""
            var signed: i16 = 10
            var unsigned: u16 = 5
            var result: i16 = signed * unsigned
            """);
        String errors = compileAndExpectFailure(sourceCode, "detailed_signed_unsigned");

        assertFalse(errors.isEmpty(),
            "Should report error for mixing i16 and u16");
        assertTrue(errors.contains("i16") && errors.contains("u16"),
            "Error message should mention both specific types");
    }

    @Test
    public void testDifferentSizedTypesError() {
        String sourceCode = wrapInMainFunction("""
            var small: i8 = 10
            var large: i32 = 100
            var result: i8 = small div large
            """);
        String errors = compileAndExpectFailure(sourceCode, "different_sized_types");

        assertFalse(errors.isEmpty(),
            "Should report error for arithmetic between i8 and i32");
        assertTrue(errors.contains("i8") && errors.contains("i32"),
            "Error message should mention both i8 and i32");
    }

    @Test
    public void testbooleanArithmeticError() {
        String sourceCode = wrapInMainFunction("""
            var flag1: bool = true
            var flag2: bool = false
            var result: bool = flag1 - flag2
            """);
        String errors = compileAndExpectFailure(sourceCode, "boolean_arithmetic");

        assertFalse(errors.isEmpty(),
            "Should report error for arithmetic on boolean types");
        assertTrue(errors.contains("bool"),
            "Error message should mention bool type");
        assertTrue(errors.contains("arithmetic") || errors.contains("numeric"),
            "Error message should indicate arithmetic/numeric operation restriction");
    }

    @Test
    public void testStringArithmeticError() {
        String sourceCode = wrapInMainFunction("""
            var str1: String = "hello"
            var str2: String = "world"
            var result: String = str1 + str2
            """);
        String errors = compileAndExpectFailure(sourceCode, "string_arithmetic");

        assertFalse(errors.isEmpty(),
            "Should report error for arithmetic on String types");
        assertTrue(errors.contains("String"),
            "Error message should mention String type");
    }

    @Test
    public void testCompoundAssignmentOnConstantError() {
        String sourceCode = wrapInMainFunction("""
            const x: i32 = 10
            x += 5
            """);
        String errors = compileAndExpectFailure(sourceCode, "compound_assignment_constant");

        assertFalse(errors.isEmpty(),
            "Should report error for compound assignment on constant");
        assertTrue(errors.contains("constant") || errors.contains("const"),
            "Error message should mention constant restriction");
    }

    @Test
    public void testCompoundAssignmentTypeError() {
        String sourceCode = wrapInMainFunction("""
            var int_val: i32 = 10
            var float_val: f64 = 5.5
            int_val += float_val
            """);
        String errors = compileAndExpectFailure(sourceCode, "compound_assignment_type_error");

        assertFalse(errors.isEmpty(),
            "Should report error for compound assignment with incompatible types");
    }

    // ============================================================================
    // Function Call Context Tests
    // ============================================================================

    @Test
    public void testArithmeticInComplexFunctionCalls() {
        String sourceCode = """
            def add(a: i32, b: i32) -> i32:
                return a + b
            
            def multiply(a: i32, b: i32) -> i32:
                return a * b
            
            def main() -> i32:
                var x: i32 = 5
                var y: i32 = 3
                var result: i32 = add(multiply(x + 1, y - 1), x * y)
            """;
        String ir = compileAndExpectSuccess(sourceCode, "complex_function_calls");

        String addFunc = extractFunction(ir, "add");
        assertNotNull(addFunc, "Add function should be present in the IR");

        assertIrContainsInOrder(addFunc,
            IrPatterns.add("i32", "a", "b")
        );

        String multiplyFunc = extractFunction(ir, "multiply");
        assertNotNull(multiplyFunc, "Multiply function should be present in the IR");

        assertIrContainsInOrder(multiplyFunc,
            IrPatterns.mul("i32", "a", "b")
        );

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.add("i32", "x", "1"),     // x + 1
            IrPatterns.sub("i32", "y", "1"),     // y - 1
            IrPatterns.mul("i32", "x", "y")      // x * y
        );
    }

    @Test
    public void testArithmeticInReturnStatements() {
        String sourceCode = """
            def calculate(a: i32, b: i32, c: i32) -> i32:
                return a * b + c
            
            def main() -> i32:
                var result: i32 = calculate(5, 3, 2)
            """;
        String ir = compileAndExpectSuccess(sourceCode, "arithmetic_in_return");

        String calculateFunc = extractFunction(ir, "calculate");
        assertNotNull(calculateFunc, "Calculate function should be present in the IR");

        assertIrContainsInOrder(calculateFunc,
            IrPatterns.mul("i32", "a", "b"),     // a * b
            IrPatterns.add("i32", "mul", "c")      // (a * b) + c
        );
    }

    @Test
    public void testArithmeticOperationAsExpressionStatement() {
        String sourceCode = """
            def get_value() -> i32:
                return 42
            
            def main() -> i32:
                get_value() + 10
            """;
        String ir = compileAndExpectSuccess(sourceCode, "arithmetic_expression_statements");

        // Verify that all arithmetic operations are performed
        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("get_value", "i32", Collections.emptyList()),
            IrPatterns.add("i32", "get_value", "10")
        );
    }

    @Test
    public void testNestedArithmeticExpressionsAsStatement() {
        String sourceCode = """
            def get_a() -> i32:
                return 10
            
            def get_b() -> i32:
                return 20
            
            def main() -> i32:
                (get_a() + get_b()) * (get_a() - get_b())
            """;
        String ir = compileAndExpectSuccess(sourceCode, "nested_arithmetic_expression_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("get_a", "i32", Collections.emptyList()),
            IrPatterns.functionCall("get_b", "i32", Collections.emptyList()),
            IrPatterns.functionCall("get_a", "i32", Collections.emptyList()),
            IrPatterns.functionCall("get_b", "i32", Collections.emptyList()),
            IrPatterns.add("i32", "get_a", "get_b"),
            IrPatterns.sub("i32", "get_a", "get_b"),
            IrPatterns.mul("i32", "add", "sub")
        );
    }

    @Test
    public void testUnaryExpressionAsStatement() {
        String sourceCode = """
            def get_value() -> i32:
                return 42
            
            def main() -> i32:
                -get_value()
            """;
        String ir = compileAndExpectSuccess(sourceCode, "unary_expression_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("get_value", "i32", Collections.emptyList()),
            IrPatterns.sub("i32", "0", "get_value") // Unary negation as subtraction from zero
        );
    }

    static Stream<Arguments> signedIntegerTypes() {
        return Stream.of(
            Arguments.of("i8", "i8"),
            Arguments.of("i16", "i16"),
            Arguments.of("i32", "i32"),
            Arguments.of("i64", "i64")
        );
    }

    static Stream<Arguments> unsignedIntegerTypes() {
        return Stream.of(
            Arguments.of("u8", "i8"),
            Arguments.of("u16", "i16"),
            Arguments.of("u32", "i32"),
            Arguments.of("u64", "i64")
        );
    }

    static Stream<Arguments> floatingPointTypes() {
        return Stream.of(
            Arguments.of("f32", "float"),
            Arguments.of("f64", "double")
        );
    }
}