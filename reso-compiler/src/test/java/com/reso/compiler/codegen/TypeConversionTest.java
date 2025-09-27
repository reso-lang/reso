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
            var back_to_signed: i32 = unsigned as i32
            """);
        String ir = compileAndExpectSuccess(sourceCode, "signed_unsigned_conversion");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("signed", "i32"),
            IrPatterns.alloca("unsigned", "i32"),
            IrPatterns.alloca("back_to_signed", "i32")
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
            var int8_val: i8 = -42
            var int32_val: i32 = 1000
            var float32_from8: f32 = int8_val as f32
            var float64_from32: f64 = int32_val as f64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "signed_int_to_float");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("int8_val", "i8"),
            IrPatterns.alloca("int32_val", "i32"),
            IrPatterns.alloca("float32_from8", "float"),
            IrPatterns.alloca("float64_from32", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("-42", "i8", "int8_val"),
            IrPatterns.store("1000", "i32", "int32_val"),
            IrPatterns.sitofp("i8", "int8_val", "float"),      // Signed int to float
            IrPatterns.sitofp("i32", "int32_val", "double")     // Signed int to double
        );
    }

    @Test
    public void testUnsignedIntegerToFloat() {
        String sourceCode = wrapInMainFunction("""
            var uint8_val: u8 = 200
            var uint32_val: u32 = 3000000000
            var float32_from8: f32 = uint8_val as f32
            var float64_from32: f64 = uint32_val as f64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "unsigned_int_to_float");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("uint8_val", "i8"),
            IrPatterns.alloca("uint32_val", "i32"),
            IrPatterns.alloca("float32_from8", "float"),
            IrPatterns.alloca("float64_from32", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("-56", "i8", "uint8_val"),
            IrPatterns.store("-1294967296", "i32", "uint32_val"),
            IrPatterns.uitofp("i8", "uint8_val", "float"),      // Unsigned int to float
            IrPatterns.uitofp("i32", "uint32_val", "double")    // Unsigned int to double
        );
    }

    // ============================================================================
    // Float to Integer Conversions
    // ============================================================================

    @Test
    public void testFloatToSignedInteger() {
        String sourceCode = wrapInMainFunction("""
            var float32_val: f32 = 42.7
            var float64_val: f64 = -1000.9
            var int8_from32: i8 = float32_val as i8
            var int32_from64: i32 = float64_val as i32
            """);
        String ir = compileAndExpectSuccess(sourceCode, "float_to_signed_int");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("float32_val", "float"),
            IrPatterns.alloca("float64_val", "double"),
            IrPatterns.alloca("int8_from32", "i8"),
            IrPatterns.alloca("int32_from64", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0x40455999A0000000", "float", "float32_val"),
            IrPatterns.store("-1.000900e\\+03", "double", "float64_val"),
            IrPatterns.fptosi("float", "float32_val", "i8"),      // Float to signed int
            IrPatterns.fptosi("double", "float64_val", "i32")     // Double to signed int
        );
    }

    @Test
    public void testFloatToUnsignedInteger() {
        String sourceCode = wrapInMainFunction("""
            var float32_val: f32 = 200.5
            var float64_val: f64 = 3000000000.0
            var uint8_from32: u8 = float32_val as u8
            var uint32_from64: u32 = float64_val as u32
            """);
        String ir = compileAndExpectSuccess(sourceCode, "float_to_unsigned_int");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("float32_val", "float"),
            IrPatterns.alloca("float64_val", "double"),
            IrPatterns.alloca("uint8_from32", "i8"),
            IrPatterns.alloca("uint32_from64", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("2.005000e\\+02", "float", "float32_val"),
            IrPatterns.store("3.000000e\\+09", "double", "float64_val"),
            IrPatterns.fptoui("float", "float32_val", "i8"),      // Float to unsigned int
            IrPatterns.fptoui("double", "float64_val", "i32")     // Double to unsigned int
        );
    }

    // ============================================================================
    // Float to Float Conversions
    // ============================================================================

    @Test
    public void testFloatWidening() {
        String sourceCode = wrapInMainFunction("""
            var float32_val: f32 = 3.14
            var float64_val: f64 = float32_val as f64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "float_widening");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("float32_val", "float"),
            IrPatterns.alloca("float64_val", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0x40091EB860000000", "float", "float32_val"),
            IrPatterns.fpext("float", "float32_val", "double")    // f32 -> f64 (extend)
        );
    }

    @Test
    public void testFloatNarrowing() {
        String sourceCode = wrapInMainFunction("""
            var float64_val: f64 = 3.141592653589793
            var float32_val: f32 = float64_val as f32
            """);
        String ir = compileAndExpectSuccess(sourceCode, "float_narrowing");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("float64_val", "double"),
            IrPatterns.alloca("float32_val", "float")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0x400921FB54442D18", "double", "float64_val"),
            IrPatterns.fptrunc("double", "float64_val", "float")  // f64 -> f32 (truncate)
        );
    }

    // ============================================================================
    // char Type Conversions
    // ============================================================================

    @Test
    public void testCharToInteger() {
        String sourceCode = wrapInMainFunction("""
            var char_val: char = 'A'
            var int8_val: i8 = char_val as i8
            var uint16_val: u16 = char_val as u16
            var int32_val: i32 = char_val as i32
            var int64_val: i64 = char_val as i64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "char_to_integer");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("char_val", "i32"),     // char is i32 in LLVM
            IrPatterns.alloca("int8_val", "i8"),
            IrPatterns.alloca("int32_val", "i32"),
            IrPatterns.alloca("uint16_val", "i16"),
            IrPatterns.alloca("int64_val", "i64")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("65", "i32", "char_val"), // 'A' = 65
            IrPatterns.trunc("i32", "char_val", "i8"),           // char -> i8 (truncate)
            IrPatterns.load("i32", "char_val"),
            // char -> i32 (same size)
            IrPatterns.trunc("i32", "char_val", "i16"),            // char -> u16 (truncate)
            IrPatterns.zext("i32", "char_val", "i64")             // char -> i64 (zero extend)
        );
    }

    @Test
    public void testIntegerToChar() {
        String sourceCode = wrapInMainFunction("""
            var int8_val: i8 = 65
            var int32_val: i32 = 97
            var uint16_val: u16 = 48
            var int64_val: i64 = 1000
            var char8: char = int8_val as char
            var char32: char = int32_val as char
            var char16: char = uint16_val as char
            var char64: char = int64_val as char
            """);
        String ir = compileAndExpectSuccess(sourceCode, "integer_to_char");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("int8_val", "i8"),
            IrPatterns.alloca("int32_val", "i32"),
            IrPatterns.alloca("uint16_val", "i16"),
            IrPatterns.alloca("int64_val", "i64"),
            IrPatterns.alloca("char8", "i32"),
            IrPatterns.alloca("char32", "i32"),
            IrPatterns.alloca("char16", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("65", "i8", "int8_val"),   // 'A'
            IrPatterns.store("97", "i32", "int32_val"), // 'a'
            IrPatterns.store("48", "i16", "uint16_val"), // '0'
            IrPatterns.store("1000", "i64", "int64_val"), // 1000
            IrPatterns.sext("i8", "int8_val", "i32"),             // i8 -> char (sign extend)
            IrPatterns.load("i32", "int32_val"),
            // i32 -> char (same size)
            IrPatterns.zext("i16", "uint16_val", "i32"),           // u16 -> char (zero extend)
            IrPatterns.trunc("i64", "int64_val", "i32")            // i64 -> char (truncate)
        );
    }

    @Test
    public void testCharToFloat() {
        String sourceCode = wrapInMainFunction("""
            var char_val: char = 'X'
            var float32_val: f32 = char_val as f32
            var float64_val: f64 = char_val as f64
            """);
        String ir = compileAndExpectSuccess(sourceCode, "char_to_float");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("char_val", "i32"),
            IrPatterns.alloca("float32_val", "float"),
            IrPatterns.alloca("float64_val", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("88", "i32", "char_val"), // 'X' = 88
            IrPatterns.uitofp("i32", "char_val", "float"),        // char -> f32 (unsigned)
            IrPatterns.uitofp("i32", "char_val", "double")        // char -> f64 (unsigned)
        );
    }

    @Test
    public void testFloatToChar() {
        String sourceCode = wrapInMainFunction("""
            var float32_val: f32 = 65.7
            var float64_val: f64 = 90.1
            var char_from32: char = float32_val as char
            var char_from64: char = float64_val as char
            """);
        String ir = compileAndExpectSuccess(sourceCode, "float_to_char");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("float32_val", "float"),
            IrPatterns.alloca("float64_val", "double"),
            IrPatterns.alloca("char_from32", "i32"),
            IrPatterns.alloca("char_from64", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0x40506CCCC0000000", "float", "float32_val"),
            IrPatterns.fptoui("float", "float32_val", "i32"),
            IrPatterns.fptoui("double", "float64_val", "i32")
        );
    }

    // ============================================================================
    // Complex Conversion Chains
    // ============================================================================

    @Test
    public void testComplexConversionChain() {
        String sourceCode = wrapInMainFunction("""
            var original: i32 = 1000
            var as_float: f64 = original as f64
            var back_to_int: i16 = as_float as i16
            var as_char: char = back_to_int as char
            var final_int: u8 = as_char as u8
            """);
        String ir = compileAndExpectSuccess(sourceCode, "complex_conversion_chain");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("original", "i32"),
            IrPatterns.alloca("as_float", "double"),
            IrPatterns.alloca("back_to_int", "i16"),
            IrPatterns.alloca("as_char", "i32"),
            IrPatterns.alloca("final_int", "i8")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("1000", "i32", "original"),
            IrPatterns.sitofp("i32", "original", "double"),     // i32 -> f64
            IrPatterns.fptosi("double", "as_float", "i16"),      // f64 -> i16
            IrPatterns.sext("i16", "back_to_int", "i32"),       // i16 -> char
            IrPatterns.trunc("i32", "as_char", "i8")            // char -> u8
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
            var bool_val: bool = true
            var int_val: i32 = bool_val as i32  # Should fail
            """);

        String errors = compileAndExpectFailure(sourceCode, "invalid_bool_conversion");
        assertTrue(errors.contains("Cannot convert from bool to i32"),
            "Should report error for boolean to integer conversion");
    }

    @Test
    public void testInvalidUnitConversion() {
        String sourceCode = """
            def do_nothing():
                return
                            
            def main() -> i32:
                var int_val: i32 = do_nothing() as i32  # Should fail
            """;

        String errors = compileAndExpectFailure(sourceCode, "invalid_unit_conversion");
        assertTrue(errors.contains("Cannot convert from () to i32"),
            "Should report error for unit to integer conversion");
    }

    @Test
    public void testInvalidStringConversions() {
        String sourceCode = wrapInMainFunction("""
            var string_val: String = "hello"
            var int_val: i32 = string_val as i32  # Should fail
            """);

        String errors = compileAndExpectFailure(sourceCode, "invalid_string_conversion");
        assertTrue(errors.contains("Cannot convert from String to i32"),
            "Should report error for string to integer conversion");
    }

    @Test
    public void testInvalidConversionToBool() {
        String sourceCode = wrapInMainFunction("""
            var int_val: i32 = 42
            var bool_val: bool = bool(int_val)  # Should fail
            """);

        String errors = compileAndExpectFailure(sourceCode, "invalid_conversion_to_bool");
        assertFalse(errors.isEmpty(),
            "Should report error for integer to boolean conversion");
    }

    @Test
    public void testInvalidConversionToString() {
        String sourceCode = wrapInMainFunction("""
            var int_val: i32 = 42
            var string_val: String = String(int_val)  # Should fail
            """);

        String errors = compileAndExpectFailure(sourceCode, "invalid_conversion_to_string");
        assertFalse(errors.isEmpty(),
            "Should report error for integer to string conversion");
    }

    // ============================================================================
    // Edge Cases and Special Values
    // ============================================================================

    @Test
    public void testZeroConversions() {
        String sourceCode = wrapInMainFunction("""
            var zero_int: i32 = 0
            var zero_float: f64 = zero_int as f64
            var back_to_int: i8 = zero_float as i8
            var zero_char: char = back_to_int as char
            """);
        String ir = compileAndExpectSuccess(sourceCode, "zero_conversions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0", "i32", "zero_int"),
            IrPatterns.sitofp("i32", "zero_int", "double"),
            IrPatterns.fptosi("double", "zero_float", "i8"),
            IrPatterns.sext("i8", "back_to_int", "i32")
        );
    }

    @Test
    public void testNegativeNumberConversions() {
        String sourceCode = wrapInMainFunction("""
            var neg_int: i32 = -100
            var neg_float: f64 = neg_int as f64
            var back_to_small_int: i8 = neg_float as i8
            """);
        String ir = compileAndExpectSuccess(sourceCode, "negative_conversions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("-100", "i32", "neg_int"),
            IrPatterns.sitofp("i32", "neg_int", "double"),
            IrPatterns.fptosi("double", "neg_float", "i8")
        );
    }

    // ============================================================================
    // Literal Value Conversions
    // ============================================================================

    @Test
    public void testLiteralConversions() {
        String sourceCode = wrapInMainFunction("""
            var conversion_from_literal: i8 = 123 as i8
            var float_from_literal: f32 = 253 as f32
            var char_from_literal: char = 65.8 as char
            """);
        String ir = compileAndExpectSuccess(sourceCode, "literal_conversions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("conversion_from_literal", "i8"),
            IrPatterns.alloca("float_from_literal", "float"),
            IrPatterns.alloca("char_from_literal", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("123", "i8", "conversion_from_literal"),
            IrPatterns.store("2.530000e\\+02", "float", "float_from_literal"),
            IrPatterns.store("65", "i32", "char_from_literal")       // 'A'
        );
    }
}