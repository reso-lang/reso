package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for type conversion operations in Reso.
 * Tests compilation from Reso source code to LLVM IR with exact verification
 * of all supported numeric type conversions.
 */
public class TypeConversionTest extends BaseTest {

    // ============================================================================
    // Integer to Integer Conversions
    // ============================================================================

    @Test
    public void testSignedIntegerWidening() {
        String sourceCode = wrapInMainFunction("""
            var small: i8 = 42
            var medium: i16 = small as i16
            var large: i32 = medium as i32
            var xlarge: i64 = large as i64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "signed_integer_widening");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("small", "i8"),
            IrPatterns.alloca("medium", "i16"),
            IrPatterns.alloca("large", "i32"),
            IrPatterns.alloca("xlarge", "i64")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("42", "i8", "small"),
            IrPatterns.sext("i8", "small", "i16"),      // i8 -> i16 (sign extend)
            IrPatterns.sext("i16", "medium", "i32"),    // i16 -> i32 (sign extend)
            IrPatterns.sext("i32", "large", "i64")      // i32 -> i64 (sign extend)
        );
    }

    @Test
    public void testUnsignedIntegerWidening() {
        String sourceCode = wrapInMainFunction("""
            var small: u8 = 200
            var medium: u16 = small as u16
            var large: u32 = medium as u32
            var xlarge: u64 = large as u64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "unsigned_integer_widening");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("small", "i8"),
            IrPatterns.alloca("medium", "i16"),
            IrPatterns.alloca("large", "i32"),
            IrPatterns.alloca("xlarge", "i64")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("-56", "i8", "small"),
            IrPatterns.zext("i8", "small", "i16"),      // u8 -> u16 (zero extend)
            IrPatterns.zext("i16", "medium", "i32"),    // u16 -> u32 (zero extend)
            IrPatterns.zext("i32", "large", "i64")      // u32 -> u64 (zero extend)
        );
    }

    @Test
    public void testIntegerNarrowing() {
        String sourceCode = wrapInMainFunction("""
            var large: i64 = 1000
            var medium: i32 = large as i32
            var small: i16 = medium as i16
            var tiny: i8 = small as i8
            """);
        String ir = compileAndExpectSuccess(sourceCode, "integer_narrowing");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("large", "i64"),
            IrPatterns.alloca("medium", "i32"),
            IrPatterns.alloca("small", "i16"),
            IrPatterns.alloca("tiny", "i8")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("1000", "i64", "large"),
            IrPatterns.trunc("i64", "large", "i32"),    // i64 -> i32 (truncate)
            IrPatterns.trunc("i32", "medium", "i16"),   // i32 -> i16 (truncate)
            IrPatterns.trunc("i16", "small", "i8")      // i16 -> i8 (truncate)
        );
    }

    @Test
    public void testSignedUnsignedConversion() {
        String sourceCode = wrapInMainFunction("""
            var signed: i32 = -42
            var unsigned: u32 = signed as u32
            var backToSigned: i32 = unsigned as i32
            """);
        String ir = compileAndExpectSuccess(sourceCode, "signed_unsigned_conversion");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("signed", "i32"),
            IrPatterns.alloca("unsigned", "i32"),
            IrPatterns.alloca("backToSigned", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("-42", "i32", "signed"),
            IrPatterns.load("i32", "signed"),
            IrPatterns.load("i32", "unsigned")
        );
    }

    // ============================================================================
    // Integer to Float Conversions
    // ============================================================================

    @Test
    public void testSignedIntegerToFloat() {
        String sourceCode = wrapInMainFunction("""
            var int8Val: i8 = -42
            var int32Val: i32 = 1000
            var float32From8: f32 = int8Val as f32
            var float64From32: f64 = int32Val as f64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "signed_int_to_float");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("int8Val", "i8"),
            IrPatterns.alloca("int32Val", "i32"),
            IrPatterns.alloca("float32From8", "float"),
            IrPatterns.alloca("float64From32", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("-42", "i8", "int8Val"),
            IrPatterns.store("1000", "i32", "int32Val"),
            IrPatterns.sitofp("i8", "int8Val", "float"),      // Signed int to float
            IrPatterns.sitofp("i32", "int32Val", "double")     // Signed int to double
        );
    }

    @Test
    public void testUnsignedIntegerToFloat() {
        String sourceCode = wrapInMainFunction("""
            var uint8Val: u8 = 200
            var uint32Val: u32 = 3000000000
            var float32From8: f32 = uint8Val as f32
            var float64From32: f64 = uint32Val as f64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "unsigned_int_to_float");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("uint8Val", "i8"),
            IrPatterns.alloca("uint32Val", "i32"),
            IrPatterns.alloca("float32From8", "float"),
            IrPatterns.alloca("float64From32", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("-56", "i8", "uint8Val"),
            IrPatterns.store("-1294967296", "i32", "uint32Val"),
            IrPatterns.uitofp("i8", "uint8Val", "float"),      // Unsigned int to float
            IrPatterns.uitofp("i32", "uint32Val", "double")    // Unsigned int to double
        );
    }

    // ============================================================================
    // Float to Integer Conversions
    // ============================================================================

    @Test
    public void testFloatToSignedInteger() {
        String sourceCode = wrapInMainFunction("""
            var float32Val: f32 = 42.7
            var float64Val: f64 = -1000.9
            var int8From32: i8 = float32Val as i8
            var int32From64: i32 = float64Val as i32
            """);
        String ir = compileAndExpectSuccess(sourceCode, "float_to_signed_int");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("float32Val", "float"),
            IrPatterns.alloca("float64Val", "double"),
            IrPatterns.alloca("int8From32", "i8"),
            IrPatterns.alloca("int32From64", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0x40455999A0000000", "float", "float32Val"),
            IrPatterns.store("-1.000900e\\+03", "double", "float64Val"),
            IrPatterns.fptosi("float", "float32Val", "i8"),      // Float to signed int
            IrPatterns.fptosi("double", "float64Val", "i32")     // Double to signed int
        );
    }

    @Test
    public void testFloatToUnsignedInteger() {
        String sourceCode = wrapInMainFunction("""
            var float32Val: f32 = 200.5
            var float64Val: f64 = 3000000000.0
            var uint8From32: u8 = float32Val as u8
            var uint32From64: u32 = float64Val as u32
            """);
        String ir = compileAndExpectSuccess(sourceCode, "float_to_unsigned_int");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("float32Val", "float"),
            IrPatterns.alloca("float64Val", "double"),
            IrPatterns.alloca("uint8From32", "i8"),
            IrPatterns.alloca("uint32From64", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("2.005000e\\+02", "float", "float32Val"),
            IrPatterns.store("3.000000e\\+09", "double", "float64Val"),
            IrPatterns.fptoui("float", "float32Val", "i8"),      // Float to unsigned int
            IrPatterns.fptoui("double", "float64Val", "i32")     // Double to unsigned int
        );
    }

    // ============================================================================
    // Float to Float Conversions
    // ============================================================================

    @Test
    public void testFloatWidening() {
        String sourceCode = wrapInMainFunction("""
            var float32Val: f32 = 3.14
            var float64Val: f64 = float32Val as f64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "float_widening");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("float32Val", "float"),
            IrPatterns.alloca("float64Val", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0x40091EB860000000", "float", "float32Val"),
            IrPatterns.fpext("float", "float32Val", "double")    // f32 -> f64 (extend)
        );
    }

    @Test
    public void testFloatNarrowing() {
        String sourceCode = wrapInMainFunction("""
            var float64Val: f64 = 3.141592653589793
            var float32Val: f32 = float64Val as f32
            """);
        String ir = compileAndExpectSuccess(sourceCode, "float_narrowing");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("float64Val", "double"),
            IrPatterns.alloca("float32Val", "float")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0x400921FB54442D18", "double", "float64Val"),
            IrPatterns.fptrunc("double", "float64Val", "float")  // f64 -> f32 (truncate)
        );
    }

    // ============================================================================
    // char Type Conversions
    // ============================================================================

    @Test
    public void testCharToInteger() {
        String sourceCode = wrapInMainFunction("""
            var charVal: char = 'A'
            var int8Val: i8 = charVal as i8
            var uint16Val: u16 = charVal as u16
            var int32Val: i32 = charVal as i32
            var int64Val: i64 = charVal as i64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "char_to_integer");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("charVal", "i32"),     // char is i32 in lLVM
            IrPatterns.alloca("int8Val", "i8"),
            IrPatterns.alloca("int32Val", "i32"),
            IrPatterns.alloca("uint16Val", "i16"),
            IrPatterns.alloca("int64Val", "i64")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("65", "i32", "charVal"), // 'A' = 65
            IrPatterns.trunc("i32", "charVal", "i8"),           // char -> i8 (truncate)
            IrPatterns.load("i32", "charVal"),
            // char -> i32 (same size)
            IrPatterns.trunc("i32", "charVal", "i16"),            // char -> u16 (truncate)
            IrPatterns.zext("i32", "charVal", "i64")             // char -> i64 (zero extend)
        );
    }

    @Test
    public void testIntegerToChar() {
        String sourceCode = wrapInMainFunction("""
            var int8Val: i8 = 65
            var int32Val: i32 = 97
            var uint16Val: u16 = 48
            var int64Val: i64 = 1000
            var char8: char = int8Val as char
            var char32: char = int32Val as char
            var char16: char = uint16Val as char
            var char64: char = int64Val as char
            """);
        String ir = compileAndExpectSuccess(sourceCode, "integer_to_char");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("int8Val", "i8"),
            IrPatterns.alloca("int32Val", "i32"),
            IrPatterns.alloca("uint16Val", "i16"),
            IrPatterns.alloca("int64Val", "i64"),
            IrPatterns.alloca("char8", "i32"),
            IrPatterns.alloca("char32", "i32"),
            IrPatterns.alloca("char16", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("65", "i8", "int8Val"),   // 'A'
            IrPatterns.store("97", "i32", "int32Val"), // 'a'
            IrPatterns.store("48", "i16", "uint16Val"), // '0'
            IrPatterns.store("1000", "i64", "int64Val"), // 1000
            IrPatterns.sext("i8", "int8Val", "i32"),             // i8 -> char (sign extend)
            IrPatterns.load("i32", "int32Val"),
            // i32 -> char (same size)
            IrPatterns.zext("i16", "uint16Val", "i32"),           // u16 -> char (zero extend)
            IrPatterns.trunc("i64", "int64Val", "i32")            // i64 -> char (truncate)
        );
    }

    @Test
    public void testCharToFloat() {
        String sourceCode = wrapInMainFunction("""
            var charVal: char = 'X'
            var float32Val: f32 = charVal as f32
            var float64Val: f64 = charVal as f64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "char_to_float");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("charVal", "i32"),
            IrPatterns.alloca("float32Val", "float"),
            IrPatterns.alloca("float64Val", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("88", "i32", "charVal"), // 'X' = 88
            IrPatterns.uitofp("i32", "charVal", "float"),        // char -> f32 (unsigned)
            IrPatterns.uitofp("i32", "charVal", "double")        // char -> f64 (unsigned)
        );
    }

    @Test
    public void testFloatToChar() {
        String sourceCode = wrapInMainFunction("""
            var float32Val: f32 = 65.7
            var float64Val: f64 = 90.1
            var charFrom32: char = float32Val as char
            var charFrom64: char = float64Val as char
            """);
        String ir = compileAndExpectSuccess(sourceCode, "float_to_char");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("float32Val", "float"),
            IrPatterns.alloca("float64Val", "double"),
            IrPatterns.alloca("charFrom32", "i32"),
            IrPatterns.alloca("charFrom64", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0x40506CCCC0000000", "float", "float32Val"),
            IrPatterns.fptoui("float", "float32Val", "i32"),
            IrPatterns.fptoui("double", "float64Val", "i32")
        );
    }

    // ============================================================================
    // Complex Conversion Chains
    // ============================================================================

    @Test
    public void testComplexConversionChain() {
        String sourceCode = wrapInMainFunction("""
            var original: i32 = 1000
            var asFloat: f64 = original as f64
            var backToInt: i16 = asFloat as i16
            var asChar: char = backToInt as char
            var finalInt: u8 = asChar as u8
            """);
        String ir = compileAndExpectSuccess(sourceCode, "complex_conversion_chain");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("original", "i32"),
            IrPatterns.alloca("asFloat", "double"),
            IrPatterns.alloca("backToInt", "i16"),
            IrPatterns.alloca("asChar", "i32"),
            IrPatterns.alloca("finalInt", "i8")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("1000", "i32", "original"),
            IrPatterns.sitofp("i32", "original", "double"),     // i32 -> f64
            IrPatterns.fptosi("double", "asFloat", "i16"),      // f64 -> i16
            IrPatterns.sext("i16", "backToInt", "i32"),       // i16 -> char
            IrPatterns.trunc("i32", "asChar", "i8")            // char -> u8
        );
    }

    // ============================================================================
    // Parameterized Tests for All Type Combinations
    // ============================================================================

    @ParameterizedTest
    @MethodSource("validIntegerConversionPairs")
    public void testValidIntegerConversions(String fromType, String toType, String fromIrType,
                                            String toIrType, String expectedInstruction) {
        String sourceCode = wrapInMainFunction(String.format("""
            var source: %s = 42
            var target: %s = source as %s
            """, fromType, toType, toType));

        String ir = compileAndExpectSuccess(sourceCode,
            String.format("conversion_%s_to_%s", fromType.toLowerCase(), toType.toLowerCase()));

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("source", fromIrType),
            IrPatterns.alloca("target", toIrType)
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("42", fromIrType, "source")
        );

        if (expectedInstruction != null) {
            assertIrContainsInOrder(mainFunc, expectedInstruction);
        }
    }

    static Stream<Arguments> validIntegerConversionPairs() {
        String sizeType = "i" + POINTER_SIZE;

        String iWideConversion = sizeType.equals("i32") ? "sext i32" : null;
        String uWideConversion = sizeType.equals("i32") ? "zext i32" : null;

        String iTruncConversion = sizeType.equals("i64") ? "trunc i64" : null;
        String uTruncConversion = sizeType.equals("i64") ? "trunc i64" : null;

        return Stream.of(
            // Signed integer widening (sext)
            Arguments.of("i8", "i16", "i8", "i16", "sext i8"),
            Arguments.of("i8", "i32", "i8", "i32", "sext i8"),
            Arguments.of("i8", "i64", "i8", "i64", "sext i8"),
            Arguments.of("i16", "i32", "i16", "i32", "sext i16"),
            Arguments.of("i16", "i64", "i16", "i64", "sext i16"),
            Arguments.of("i32", "i64", "i32", "i64", "sext i32"),
            Arguments.of("isize", "i64", sizeType, "i64", iWideConversion),

            // Unsigned integer widening (zext)
            Arguments.of("u8", "u16", "i8", "i16", "zext i8"),
            Arguments.of("u8", "u32", "i8", "i32", "zext i8"),
            Arguments.of("u8", "u64", "i8", "i64", "zext i8"),
            Arguments.of("u16", "u32", "i16", "i32", "zext i16"),
            Arguments.of("u16", "u64", "i16", "i64", "zext i16"),
            Arguments.of("u32", "u64", "i32", "i64", "zext i32"),
            Arguments.of("usize", "u64", sizeType, "i64", uWideConversion),

            // Integer narrowing (trunc)
            Arguments.of("i64", "i32", "i64", "i32", "trunc i64"),
            Arguments.of("i64", "i16", "i64", "i16", "trunc i64"),
            Arguments.of("i64", "i8", "i64", "i8", "trunc i64"),
            Arguments.of("i32", "i16", "i32", "i16", "trunc i32"),
            Arguments.of("i32", "i8", "i32", "i8", "trunc i32"),
            Arguments.of("i16", "i8", "i16", "i8", "trunc i16"),
            Arguments.of("isize", "i32", sizeType, "i32", iTruncConversion),
            Arguments.of("usize", "u32", sizeType, "i32", uTruncConversion),

            // Same width conversions (no instruction)
            Arguments.of("i32", "u32", "i32", "i32", null),
            Arguments.of("u32", "i32", "i32", "i32", null),
            Arguments.of("i16", "u16", "i16", "i16", null),
            Arguments.of("u16", "i16", "i16", "i16", null)
        );
    }

    // ============================================================================
    // Invalid Type Conversion Tests
    // ============================================================================

    @Test
    public void testInvalidBooleanConversions() {
        String sourceCode = wrapInMainFunction("""
            var boolVal: bool = true
            var intVal: i32 = boolVal as i32  # Should fail
            """);

        String errors = compileAndExpectFailure(sourceCode, "invalid_bool_conversion");
        assertTrue(errors.contains("Cannot convert from bool to i32"),
            "Should report error for boolean to integer conversion");
    }

    @Test
    public void testInvalidUnitConversion() {
        String sourceCode = """
            def doNothing():
                return
            
            def main() -> i32:
                var intVal: i32 = doNothing() as i32  # Should fail
            """;

        String errors = compileAndExpectFailure(sourceCode, "invalid_unit_conversion");
        assertTrue(errors.contains("Cannot convert from () to i32"),
            "Should report error for unit to integer conversion");
    }

    @Test
    public void testInvalidStringConversions() {
        String sourceCode = wrapInMainFunction("""
            var stringVal: String = "hello"
            var intVal: i32 = stringVal as i32  # Should fail
            """);

        String errors = compileAndExpectFailure(sourceCode, "invalid_string_conversion");
        assertTrue(errors.contains("Cannot convert from String to i32"),
            "Should report error for string to integer conversion");
    }

    @Test
    public void testInvalidConversionToBool() {
        String sourceCode = wrapInMainFunction("""
            var intVal: i32 = 42
            var boolVal: bool = bool(intVal)  # Should fail
            """);

        String errors = compileAndExpectFailure(sourceCode, "invalid_conversion_to_bool");
        assertFalse(errors.isEmpty(),
            "Should report error for integer to boolean conversion");
    }

    @Test
    public void testInvalidConversionToString() {
        String sourceCode = wrapInMainFunction("""
            var intVal: i32 = 42
            var stringVal: String = String(intVal)  # Should fail
            """);

        String errors = compileAndExpectFailure(sourceCode, "invalid_conversion_toString");
        assertFalse(errors.isEmpty(),
            "Should report error for integer to string conversion");
    }

    // ============================================================================
    // Edge Cases and Special Values
    // ============================================================================

    @Test
    public void testZeroConversions() {
        String sourceCode = wrapInMainFunction("""
            var zeroInt: i32 = 0
            var zeroFloat: f64 = zeroInt as f64
            var backToInt: i8 = zeroFloat as i8
            var zeroChar: char = backToInt as char
            """);
        String ir = compileAndExpectSuccess(sourceCode, "zero_conversions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0", "i32", "zeroInt"),
            IrPatterns.sitofp("i32", "zeroInt", "double"),
            IrPatterns.fptosi("double", "zeroFloat", "i8"),
            IrPatterns.sext("i8", "backToInt", "i32")
        );
    }

    @Test
    public void testNegativeNumberConversions() {
        String sourceCode = wrapInMainFunction("""
            var negInt: i32 = -100
            var negFloat: f64 = negInt as f64
            var backToSmallInt: i8 = negFloat as i8
            """);
        String ir = compileAndExpectSuccess(sourceCode, "negative_conversions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("-100", "i32", "negInt"),
            IrPatterns.sitofp("i32", "negInt", "double"),
            IrPatterns.fptosi("double", "negFloat", "i8")
        );
    }

    // ============================================================================
    // Literal Value Conversions
    // ============================================================================

    @Test
    public void testLiteralConversions() {
        String sourceCode = wrapInMainFunction("""
            var conversionFromLiteral: i8 = 123 as i8
            var floatFromLiteral: f32 = 253 as f32
            var charFromLiteral: char = 65.8 as char
            """);
        String ir = compileAndExpectSuccess(sourceCode, "literal_conversions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("conversionFromLiteral", "i8"),
            IrPatterns.alloca("floatFromLiteral", "float"),
            IrPatterns.alloca("charFromLiteral", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("123", "i8", "conversionFromLiteral"),
            IrPatterns.store("2.530000e\\+02", "float", "floatFromLiteral"),
            IrPatterns.store("65", "i32", "charFromLiteral")       // 'A'
        );
    }
}