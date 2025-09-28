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
 * Tests for bitwise operations in Reso.
 * Tests compilation from Reso source code to LLVM IR with exact verification of:
 * - All bitwise operations (AND, OR, XOR, left shift, right shift)
 * - Type compatibility enforcement
 * - Error handling for invalid operations
 * - Comprehensive literal type handling
 */
public class BitwiseTest extends BaseTest {

    // ============================================================================
    // Basic Bitwise Operations
    // ============================================================================

    @Test
    public void testBasicSignedIntegerBitwiseOperations() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 15
            var b: i32 = 7
            var and_result: i32 = a & b
            var or_result: i32 = a | b
            var xor_result: i32 = a ^ b
            var left_shift: i32 = a << 2
            var right_shift: i32 = a >> 2
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_signed_bitwise");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Variable allocations
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("and_result", "i32"),
            IrPatterns.alloca("or_result", "i32"),
            IrPatterns.alloca("xor_result", "i32"),
            IrPatterns.alloca("left_shift", "i32"),
            IrPatterns.alloca("right_shift", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            // Initial stores
            IrPatterns.store("15", "i32", "a"),
            IrPatterns.store("7", "i32", "b"),

            // Bitwise operations
            IrPatterns.bitwiseAnd("i32", "a", "b"),
            IrPatterns.bitwiseOr("i32", "a", "b"),
            IrPatterns.bitwiseXor("i32", "a", "b"),
            IrPatterns.leftShift("i32", "a", "2"),
            IrPatterns.arithmeticRightShift("i32", "a", "2")  // Signed right shift
        );
    }

    @Test
    public void testBasicUnsignedIntegerBitwiseOperations() {
        String sourceCode = wrapInMainFunction("""
            var a: u32 = 240
            var b: u32 = 15
            var and_result: u32 = a & b
            var or_result: u32 = a | b
            var xor_result: u32 = a ^ b
            var left_shift: u32 = b << 3
            var right_shift: u32 = a >> 4
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_unsigned_bitwise");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Variable allocations
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("and_result", "i32"),
            IrPatterns.alloca("or_result", "i32"),
            IrPatterns.alloca("xor_result", "i32"),
            IrPatterns.alloca("left_shift", "i32"),
            IrPatterns.alloca("right_shift", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            // Initial stores
            IrPatterns.store("240", "i32", "a"),
            IrPatterns.store("15", "i32", "b"),

            // Bitwise operations
            IrPatterns.bitwiseAnd("i32", "a", "b"),
            IrPatterns.bitwiseOr("i32", "a", "b"),
            IrPatterns.bitwiseXor("i32", "a", "b"),
            IrPatterns.leftShift("i32", "b", "3"),
            IrPatterns.logicalRightShift("i32", "a", "4")  // Unsigned right shift
        );
    }

    // ============================================================================
    // Type Compatibility Testing
    // ============================================================================

    @ParameterizedTest
    @MethodSource("sameTypeCompatiblePairs")
    public void testBitwiseOperationsWithSameCompatibleTypes(String typeName, String llvmType,
                                                             String value1, String value2,
                                                             String operator, String operatorName) {
        String sourceCode = wrapInMainFunction("""
            var a: %s = %s as %s
            var b: %s = %s as %s
            var result: %s = a %s b
            """.formatted(typeName, value1, typeName, typeName, value2, typeName, typeName,
            operator));
        String ir = compileAndExpectSuccess(sourceCode,
            "same_type_" + typeName.toLowerCase() + "_" + operatorName);

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", llvmType),
            IrPatterns.alloca("b", llvmType),
            IrPatterns.alloca("result", llvmType)
        );

        // Verify the correct operation is used
        switch (operator) {
            case "&" -> assertIrContains(mainFunc, IrPatterns.bitwiseAnd(llvmType, "a", "b"));
            case "|" -> assertIrContains(mainFunc, IrPatterns.bitwiseOr(llvmType, "a", "b"));
            case "^" -> assertIrContains(mainFunc, IrPatterns.bitwiseXor(llvmType, "a", "b"));
            case "<<" -> assertIrContains(mainFunc, IrPatterns.leftShift(llvmType, "a", "b"));
            case ">>" -> {
                if (typeName.startsWith("u")) {
                    assertIrContains(mainFunc, IrPatterns.logicalRightShift(llvmType, "a", "b"));
                } else {
                    assertIrContains(mainFunc, IrPatterns.arithmeticRightShift(llvmType, "a", "b"));
                }
            }
        }
    }

    static Stream<Arguments> sameTypeCompatiblePairs() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            // Signed integer types
            Arguments.of("i8", "i8", "5", "3", "&", "and"),
            Arguments.of("i8", "i8", "8", "4", "|", "or"),
            Arguments.of("i8", "i8", "15", "7", "^", "xor"),
            Arguments.of("i8", "i8", "3", "2", "<<", "shl"),
            Arguments.of("i8", "i8", "24", "2", ">>", "shr"),

            Arguments.of("i16", "i16", "255", "127", "&", "and"),
            Arguments.of("i16", "i16", "256", "128", "|", "or"),
            Arguments.of("i16", "i16", "1023", "511", "^", "xor"),
            Arguments.of("i16", "i16", "5", "3", "<<", "shl"),
            Arguments.of("i16", "i16", "80", "3", ">>", "shr"),

            Arguments.of("i32", "i32", "65535", "32767", "&", "and"),
            Arguments.of("i32", "i32", "1024", "512", "|", "or"),
            Arguments.of("i32", "i32", "4095", "2047", "^", "xor"),
            Arguments.of("i32", "i32", "7", "4", "<<", "shl"),
            Arguments.of("i32", "i32", "224", "4", ">>", "shr"),

            Arguments.of("i64", "i64", "1048575", "524287", "&", "and"),
            Arguments.of("i64", "i64", "4096", "2048", "|", "or"),
            Arguments.of("i64", "i64", "16383", "8191", "^", "xor"),
            Arguments.of("i64", "i64", "9", "5", "<<", "shl"),
            Arguments.of("i64", "i64", "576", "5", ">>", "shr"),

            Arguments.of("isize", sizeType, "65535", "32767", "&", "and"),
            Arguments.of("isize", sizeType, "1024", "512", "|", "or"),
            Arguments.of("isize", sizeType, "4095", "2047", "^", "xor"),
            Arguments.of("isize", sizeType, "7", "4", "<<", "shl"),
            Arguments.of("isize", sizeType, "224", "4", ">>", "shr"),

            // Unsigned integer types
            Arguments.of("u8", "i8", "255", "127", "&", "and"),
            Arguments.of("u8", "i8", "128", "64", "|", "or"),
            Arguments.of("u8", "i8", "240", "128", "^", "xor"),
            Arguments.of("u8", "i8", "3", "2", "<<", "shl"),
            Arguments.of("u8", "i8", "240", "2", ">>", "shr"),

            Arguments.of("u16", "i16", "65535", "32767", "&", "and"),
            Arguments.of("u16", "i16", "32768", "16384", "|", "or"),
            Arguments.of("u16", "i16", "61440", "32768", "^", "xor"),
            Arguments.of("u16", "i16", "5", "3", "<<", "shl"),
            Arguments.of("u16", "i16", "61440", "3", ">>", "shr"),

            Arguments.of("u32", "i32", "4294967295", "2147483647", "&", "and"),
            Arguments.of("u32", "i32", "2048", "1024", "|", "or"),
            Arguments.of("u32", "i32", "1536", "512", "^", "xor"),
            Arguments.of("u32", "i32", "7", "4", "<<", "shl"),
            Arguments.of("u32", "i32", "3584", "4", ">>", "shr"),

            Arguments.of("u64", "i64", "1024", "512", "&", "and"),
            Arguments.of("u64", "i64", "8192", "4096", "|", "or"),
            Arguments.of("u64", "i64", "6144", "2048", "^", "xor"),
            Arguments.of("u64", "i64", "9", "5", "<<", "shl"),
            Arguments.of("u64", "i64", "18432", "5", ">>", "shr"),

            Arguments.of("usize", sizeType, "4294967295", "2147483647", "&", "and"),
            Arguments.of("usize", sizeType, "2048", "1024", "|", "or"),
            Arguments.of("usize", sizeType, "1536", "512", "^", "xor"),
            Arguments.of("usize", sizeType, "7", "4", "<<", "shl"),
            Arguments.of("usize", sizeType, "3584", "4", ">>", "shr")
        );
    }

    @ParameterizedTest
    @MethodSource("incompatibleTypePairs")
    public void testBitwiseOperationsWithIncompatibleTypes(String type1, String type2,
                                                           String operator, String operatorName) {
        String sourceCode = wrapInMainFunction("""
            var a: %s = %s(10)
            var b: %s = %s(5)
            var result: %s = a %s b
            """.formatted(type1, type1, type2, type2, type1, operator));
        String errors = compileAndExpectFailure(sourceCode,
            "incompatible_" + type1.toLowerCase() + "_" + type2.toLowerCase() + "_" + operatorName);

        assertTrue(errors.contains("Cannot perform bitwise operation")
                || errors.contains("different types")
                || errors.contains("type")
                || errors.contains("compatible"),
            "Should report type compatibility error for " + type1 + " " + operator + " " + type2);
    }

    static Stream<Arguments> incompatibleTypePairs() {
        return Stream.of(
            // Mixed signed/unsigned of same size
            Arguments.of("i8", "u8", "&", "and"),
            Arguments.of("i16", "u16", "|", "or"),
            Arguments.of("i32", "u32", "^", "xor"),
            Arguments.of("u16", "i16", "&", "and"),
            Arguments.of("u32", "i32", "|", "or"),
            Arguments.of("u64", "i64", "^", "xor"),
            Arguments.of("isize", "usize)", "&", "and"),

            // Different sizes, same signedness
            Arguments.of("i8", "i16", "&", "and"),
            Arguments.of("i16", "i32", "|", "or"),
            Arguments.of("i32", "i64", "^", "xor"),
            Arguments.of("u16", "u32", "&", "and"),
            Arguments.of("u32", "u64", "|", "or"),
            Arguments.of("u64", "u8", "^", "xor"),
            Arguments.of("usize", "u32", "&", "and"),
            Arguments.of("usize", "u64", "|", "or"),
            Arguments.of("isize", "i32", "|", "or"),
            Arguments.of("isize", "i64", "^", "xor"),

            // Different sizes, different signedness
            Arguments.of("i8", "u16", "&", "and"),
            Arguments.of("i16", "u32", "|", "or"),
            Arguments.of("i32", "u64", "^", "xor")
        );
    }

    @ParameterizedTest
    @MethodSource("nonIntegerTypes")
    public void testBitwiseOperationsOnNonIntegerTypes(String nonIntegerType, String integerType,
                                                       String operator, String operatorName) {
        String sourceCode = wrapInMainFunction("""
            var a: %s = %s
            var b: %s = %s(5)
            var result = a %s b
            """.formatted(nonIntegerType, getNonIntegerValue(nonIntegerType), integerType,
            integerType, operator));
        String errors = compileAndExpectFailure(sourceCode,
            "non_integer_" + nonIntegerType.toLowerCase() + "_" + operatorName);

        assertTrue(errors.contains("non-integer")
                || errors.contains("integer")
                || errors.contains("bitwise")
                || errors.contains("Cannot perform bitwise operation"),
            "Should report non-integer type error for " + nonIntegerType + " " + operator + " "
                + integerType);
    }

    static Stream<Arguments> nonIntegerTypes() {
        return Stream.of(
            Arguments.of("f32", "i32", "&", "and"),
            Arguments.of("f64", "i32", "|", "or"),
            Arguments.of("String", "i32", "^", "xor"),
            Arguments.of("boolean", "i32", "<<", "shl"),
            Arguments.of("f32", "u32", ">>", "shr"),
            Arguments.of("f64", "u32", "&", "and"),
            Arguments.of("String", "u32", "|", "or"),
            Arguments.of("boolean", "u32", "^", "xor")
        );
    }

    private String getNonIntegerValue(String type) {
        return switch (type) {
            case "f32" -> "10.5";
            case "f64" -> "3.14";
            case "String" -> "\"hello\"";
            case "boolean" -> "true";
            default -> throw new IllegalArgumentException("Unknown non-integer type: " + type);
        };
    }

    @Test
    public void testBitwiseOperationWithUnitType() {
        String sourceCode = """
            def unit_a():
                return ()
            
            def unit_b():
                return ()
            
            def main() -> i32:
                unit_a() | unit_b()
            """;
        String errors = compileAndExpectFailure(sourceCode, "unit_type_bitwise");

        assertTrue(errors.contains("Cannot perform bitwise operation '|' on non-integer types"),
            "Should report error for bitwise operation with unit type");
    }

    // ============================================================================
    // Literal Type Compatibility Testing
    // ============================================================================

    @ParameterizedTest
    @MethodSource("literalWithConcreteTypeCombinations")
    public void testBitwiseOperationsWithLiteralsAndConcreteTypes(String concreteType,
                                                                  String llvmType,
                                                                  String literalValue,
                                                                  String operator,
                                                                  String operatorName) {
        String sourceCode = wrapInMainFunction("""
            var value: %s = 100 as %s
            var result: %s = value %s %s
            var result2: %s = %s %s value
            """.formatted(concreteType, concreteType, concreteType, operator, literalValue,
            concreteType, literalValue, operator));
        String ir = compileAndExpectSuccess(sourceCode,
            "literal_concrete_" + concreteType.toLowerCase() + "_" + operatorName);

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("value", llvmType),
            IrPatterns.alloca("result", llvmType),
            IrPatterns.alloca("result2", llvmType)
        );

        // Verify operations are generated correctly
        switch (operator) {
            case "&" -> {
                assertIrContains(mainFunc, IrPatterns.bitwiseAnd(llvmType, "value", literalValue));
                assertIrContains(mainFunc, IrPatterns.bitwiseAnd(llvmType, literalValue, "value"));
            }
            case "|" -> {
                assertIrContains(mainFunc, IrPatterns.bitwiseOr(llvmType, "value", literalValue));
                assertIrContains(mainFunc, IrPatterns.bitwiseOr(llvmType, literalValue, "value"));
            }
            case "^" -> {
                assertIrContains(mainFunc, IrPatterns.bitwiseXor(llvmType, "value", literalValue));
                assertIrContains(mainFunc, IrPatterns.bitwiseXor(llvmType, literalValue, "value"));
            }
        }
    }

    static Stream<Arguments> literalWithConcreteTypeCombinations() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            // All integer types with literals
            Arguments.of("i8", "i8", "15", "&", "and"),
            Arguments.of("i8", "i8", "127", "|", "or"),
            Arguments.of("i8", "i8", "64", "^", "xor"),

            Arguments.of("i16", "i16", "255", "&", "and"),
            Arguments.of("i16", "i16", "512", "|", "or"),
            Arguments.of("i16", "i16", "1024", "^", "xor"),

            Arguments.of("i32", "i32", "65535", "&", "and"),
            Arguments.of("i32", "i32", "131072", "|", "or"),
            Arguments.of("i32", "i32", "262144", "^", "xor"),

            Arguments.of("i64", "i64", "1048575", "&", "and"),
            Arguments.of("i64", "i64", "2097152", "|", "or"),
            Arguments.of("i64", "i64", "4194304", "^", "xor"),

            Arguments.of("isize", sizeType, "65535", "&", "and"),
            Arguments.of("isize", sizeType, "131072", "|", "or"),
            Arguments.of("isize", sizeType, "262144", "^", "xor"),

            Arguments.of("u8", "i8", "15", "&", "and"),
            Arguments.of("u8", "i8", "128", "|", "or"),
            Arguments.of("u8", "i8", "64", "^", "xor"),

            Arguments.of("u16", "i16", "255", "&", "and"),
            Arguments.of("u16", "i16", "512", "|", "or"),
            Arguments.of("u16", "i16", "1024", "^", "xor"),

            Arguments.of("u32", "i32", "65535", "&", "and"),
            Arguments.of("u32", "i32", "131072", "|", "or"),
            Arguments.of("u32", "i32", "262144", "^", "xor"),

            Arguments.of("u64", "i64", "1048575", "&", "and"),
            Arguments.of("u64", "i64", "2097152", "|", "or"),
            Arguments.of("u64", "i64", "4194304", "^", "xor"),

            Arguments.of("usize", sizeType, "65535", "&", "and"),
            Arguments.of("usize", sizeType, "131072", "|", "or"),
            Arguments.of("usize", sizeType, "262144", "^", "xor")
        );
    }

    @Test
    public void testBitwiseOperationsWithTwoLiterals() {
        String sourceCode = wrapInMainFunction("""
            # Bitwise operations with literal values
            var and_result: i8 = 15 & 7
            var or_result = 8 | 4
            var xor_result = 255 ^ 128
            var shift_left: i16 = 5 << 2
            var shift_right: i64 = 80 >> 3
            """);
        String ir = compileAndExpectSuccess(sourceCode, "bitwise_two_literals");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("and_result", "i8"),
            IrPatterns.alloca("or_result", "i32"),
            IrPatterns.alloca("xor_result", "i32"),
            IrPatterns.alloca("shift_left", "i16"),
            IrPatterns.alloca("shift_right", "i64")
        );

        // These operations should be computed at compile time
        assertIrContains(mainFunc,
            IrPatterns.store("7", "i8", "and_result"),      // 15 & 7 = 7
            IrPatterns.store("12", "i32", "or_result"),     // 8 | 4 = 12
            IrPatterns.store("127", "i32", "xor_result"),   // 255 ^ 128 = 127
            IrPatterns.store("20", "i16", "shift_left"),    // 5 << 2 = 20
            IrPatterns.store("10", "i64", "shift_right")    // 80 >> 3 = 10
        );
    }

    @ParameterizedTest
    @MethodSource("incompatibleLiteralTypes")
    public void testIncompatibleLiteralTypes(String explicitType, String incompatibleLiteral,
                                             String operator) {
        String sourceCode = wrapInMainFunction("""
            var value: %s = %s(5)
            var result = value %s %s
            """.formatted(explicitType, explicitType, operator, incompatibleLiteral));

        String errors = compileAndExpectFailure(sourceCode, "literal_compat_test");

        assertTrue(
            errors.contains("type") || errors.contains("compatible") || errors.contains("literal"));
    }

    static Stream<Arguments> incompatibleLiteralTypes() {
        int pointerSize = POINTER_SIZE;
        String iSizeOverflow = pointerSize == 32 ? "2147483648" : "9223372036854775808";
        String uSizeOverflow = pointerSize == 32 ? "4294967296" : "18446744073709551616";

        return Stream.of(
            // Test literals that might overflow the target type
            Arguments.of("i8", "256", "&"),    // 256 > i8.MAX
            Arguments.of("i8", "1000", "|"),   // 1000 > i8.MAX
            Arguments.of("u8", "300", "^"),   // 300 > u8.MAX
            Arguments.of("i16", "70000", "&"), // 70000 > i16.MAX
            Arguments.of("u16", "70000", "|"), // 70000 > u16.MAX
            Arguments.of("isize", iSizeOverflow, "^"),
            Arguments.of("usize", uSizeOverflow, "&")
        );
    }

    // ============================================================================
    // Shift Operations Special Rules
    // ============================================================================

    @ParameterizedTest
    @MethodSource("shiftOperationTypeCombinations")
    public void testShiftOperationsWithDifferentShiftAmountTypes(String valueType, String shiftType,
                                                                 String llvmValueType,
                                                                 String llvmShiftType,
                                                                 String llvmShiftAmount) {
        String sourceCode = wrapInMainFunction("""
            var value: %s = 100 as %s
            var shift_amount: %s = 3 as %s
            var left_result: %s = value << shift_amount
            var right_result: %s = value >> shift_amount
            """.formatted(valueType, valueType, shiftType, shiftType, valueType, valueType));
        String ir = compileAndExpectSuccess(sourceCode,
            "shift_types_" + valueType.toLowerCase() + "_" + shiftType.toLowerCase());

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("value", llvmValueType),
            IrPatterns.alloca("shift_amount", llvmShiftType),
            IrPatterns.alloca("left_result", llvmValueType),
            IrPatterns.alloca("right_result", llvmValueType)
        );

        // Verify shift operations are generated

        assertIrContains(mainFunc, IrPatterns.leftShift(llvmValueType, "value", llvmShiftAmount));

        if (valueType.startsWith("u")) {
            assertIrContains(mainFunc,
                IrPatterns.logicalRightShift(llvmValueType, "value", llvmShiftAmount));
        } else {
            assertIrContains(mainFunc,
                IrPatterns.arithmeticRightShift(llvmValueType, "value", llvmShiftAmount));
        }
    }

    static Stream<Arguments> shiftOperationTypeCombinations() {
        return Stream.of(
            // Same type combinations
            Arguments.of("i32", "i32", "i32", "i32", "shift_amount"),
            Arguments.of("u32", "u32", "i32", "i32", "shift_amount"),

            // Different integer types for shift amount
            Arguments.of("i32", "i8", "i32", "i8", "sext"),
            Arguments.of("i32", "i16", "i32", "i16", "sext"),
            Arguments.of("i32", "i64", "i32", "i64", "trunc"),
            Arguments.of("i32", "u8", "i32", "i8", "zext"),
            Arguments.of("i32", "u16", "i32", "i16", "zext"),
            Arguments.of("i32", "u32", "i32", "i32", "shift_amount"),
            Arguments.of("i32", "u64", "i32", "i64", "trunc"),

            Arguments.of("u64", "i8", "i64", "i8", "sext"),
            Arguments.of("u64", "i16", "i64", "i16", "sext"),
            Arguments.of("u64", "i32", "i64", "i32", "sext"),
            Arguments.of("u64", "u8", "i64", "i8", "zext"),
            Arguments.of("u64", "u16", "i64", "i16", "zext"),
            Arguments.of("u64", "u32", "i64", "i32", "zext")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidShiftAmountTypes")
    public void testShiftOperationsWithInvalidShiftAmountTypes(String valueType,
                                                               String invalidShiftType) {
        String sourceCode = wrapInMainFunction("""
            var value: %s = %s(100)
            var shift_amount: %s = %s
            var result: %s = value << shift_amount
            """.formatted(valueType, valueType, invalidShiftType,
            getNonIntegerValue(invalidShiftType), valueType));
        String errors = compileAndExpectFailure(sourceCode,
            "invalid_shift_" + valueType.toLowerCase() + "_" + invalidShiftType.toLowerCase());

        assertTrue(errors.contains("non-integer")
                || errors.contains("integer")
                || errors.contains("shift")
                || errors.contains("bitwise"),
            "Should report invalid shift amount type error");
    }

    static Stream<Arguments> invalidShiftAmountTypes() {
        return Stream.of(
            Arguments.of("i32", "f32"),
            Arguments.of("i32", "f64"),
            Arguments.of("i32", "String"),
            Arguments.of("i32", "boolean"),
            Arguments.of("u32", "f32"),
            Arguments.of("u32", "f64"),
            Arguments.of("u32", "String"),
            Arguments.of("u32", "boolean"),
            Arguments.of("usize", "f32"),
            Arguments.of("isize", "String")
        );
    }

    // ============================================================================
    // Bitwise Operations in Control Structures
    // ============================================================================

    @Test
    public void testBitwiseOperationsInIfConditions() {
        String sourceCode = wrapInMainFunction("""
            var flags: i32 = 12  # Binary: 1100
            var mask: i32 = 8    # Binary: 1000
            
            if (flags & mask) != 0:
                var result: i32 = 1
            
            if (flags | mask) == 12:
                var result2: i32 = 2
            
            if (flags ^ mask) == 4:
                var result3: i32 = 3
            """);
        String ir = compileAndExpectSuccess(sourceCode, "bitwise_in_if_conditions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("flags", "i32"),
            IrPatterns.alloca("mask", "i32"),
            IrPatterns.bitwiseAnd("i32", "flags", "mask"),
            IrPatterns.bitwiseOr("i32", "flags", "mask"),
            IrPatterns.bitwiseXor("i32", "flags", "mask")
        );

        // Verify conditional branches are created
        assertIrContains(mainFunc, "br i1", "label %");
    }

    @Test
    public void testBitwiseOperationsInWhileConditions() {
        String sourceCode = wrapInMainFunction("""
            var counter: i32 = 16
            var mask: i32 = 1
            
            while (counter & mask) == 0:
                counter = counter >> 1
            
            var result: i32 = counter
            """);
        String ir = compileAndExpectSuccess(sourceCode, "bitwise_in_while_conditions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("counter", "i32"),
            IrPatterns.alloca("mask", "i32"),
            IrPatterns.bitwiseAnd("i32", "counter", "mask"),
            IrPatterns.arithmeticRightShift("i32", "counter", "1")
        );

        // Verify loop structure
        assertIrContains(mainFunc, "br i1", "while.cond", "while.body", "while.end");
    }

    @Test
    public void testShiftOperationsInLoops() {
        String sourceCode = wrapInMainFunction("""
            var value: u32 = 128
            var shifts: i32 = 0
            
            while value > 0:
                value = value >> 1  # Logical shift for unsigned
                shifts = shifts + 1
            
            var total_shifts: i32 = shifts
            """);
        String ir = compileAndExpectSuccess(sourceCode, "shift_operations_in_loops");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("value", "i32"),
            IrPatterns.alloca("shifts", "i32"),
            IrPatterns.logicalRightShift("i32", "value", "1")  // Unsigned right shift
        );
    }

    // ============================================================================
    // Variable Assignments and Initialization
    // ============================================================================

    @Test
    public void testBitwiseOperationsInVariableInitialization() {
        String sourceCode = wrapInMainFunction("""
            var base: i16 = 42
            var shifted_left: i16 = base << 2
            var shifted_right: i16 = base >> 1
            var masked: i16 = base & 15
            var combined: i16 = base | 128
            var flipped: i16 = base ^ 255
            """);
        String ir = compileAndExpectSuccess(sourceCode, "bitwise_in_initialization");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("base", "i16"),
            IrPatterns.alloca("shifted_left", "i16"),
            IrPatterns.alloca("shifted_right", "i16"),
            IrPatterns.alloca("masked", "i16"),
            IrPatterns.alloca("combined", "i16"),
            IrPatterns.alloca("flipped", "i16")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("42", "i16", "base"),
            IrPatterns.leftShift("i16", "base", "2"),
            IrPatterns.arithmeticRightShift("i16", "base", "1"),
            IrPatterns.bitwiseAnd("i16", "base", "15"),
            IrPatterns.bitwiseOr("i16", "base", "128"),
            IrPatterns.bitwiseXor("i16", "base", "255")
        );
    }

    @Test
    public void testBitwiseOperationsInCompoundAssignments() {
        String sourceCode = wrapInMainFunction("""
            var flags: i32 = 5
            
            flags = flags & 3    # Clear upper bits
            flags = flags | 8    # Set bit 3
            flags = flags ^ 2    # Toggle bit 1
            flags = flags << 1   # Shift left
            flags = flags >> 1   # Shift right
            """);
        String ir = compileAndExpectSuccess(sourceCode, "bitwise_compound_assignments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("flags", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("5", "i32", "flags"),
            IrPatterns.bitwiseAnd("i32", "flags", "3"),
            IrPatterns.bitwiseOr("i32", "flags", "8"),
            IrPatterns.bitwiseXor("i32", "flags", "2"),
            IrPatterns.leftShift("i32", "flags", "1"),
            IrPatterns.arithmeticRightShift("i32", "flags", "1")
        );
    }

    // ============================================================================
    // Function Arguments and Return Values
    // ============================================================================

    @Test
    public void testBitwiseOperationsAsFunctionArguments() {
        String sourceCode = """
            def compute_mask(value: i32, mask: i32) -> i32:
                return value & mask
            
            def main() -> i32:
                var data: i32 = 255
                var filter: i32 = 15
            
                # Pass bitwise operation results as arguments
                var result1: i32 = compute_mask(data | 16, filter << 1)
                var result2: i32 = compute_mask(data ^ 128, filter >> 1)
            
                return result1 + result2
            """;
        String ir = compileAndExpectSuccess(sourceCode, "bitwise_as_function_args");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("data", "i32"),
            IrPatterns.alloca("filter", "i32"),
            IrPatterns.bitwiseOr("i32", "data", "16"),
            IrPatterns.leftShift("i32", "filter", "1"),
            IrPatterns.bitwiseXor("i32", "data", "128"),
            IrPatterns.arithmeticRightShift("i32", "filter", "1")
        );

        // Verify function calls are generated
        assertIrContains(mainFunc, "call i32 @compute_mask");
    }

    @Test
    public void testBitwiseOperationsInReturnStatements() {
        String sourceCode = """
            def get_masked_value(value: i32, mask: i32) -> i32:
                return value & mask
            
            def get_combined_flags(flag1: i32, flag2: i32) -> i32:
                return flag1 | flag2
            
            def get_toggled_bits(value: i32, toggle: i32) -> i32:
                return value ^ toggle
            
            def get_shifted_value(value: i32, shift_amount: i32) -> i32:
                return value << shift_amount
            
            def main() -> i32:
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "bitwise_in_returns");

        String getMaskedFunc = extractFunction(ir, "get_masked_value");
        String getCombinedFunc = extractFunction(ir, "get_combined_flags");
        String getToggledFunc = extractFunction(ir, "get_toggled_bits");
        String getShiftedFunc = extractFunction(ir, "get_shifted_value");

        assertNotNull(getMaskedFunc, "Should find get_masked_value function");
        assertNotNull(getCombinedFunc, "Should find get_combined_flags function");
        assertNotNull(getToggledFunc, "Should find get_toggled_bits function");
        assertNotNull(getShiftedFunc, "Should find get_shifted_value function");

        assertIrContains(getMaskedFunc, IrPatterns.bitwiseAnd("i32", "value", "mask"));
        assertIrContains(getCombinedFunc, IrPatterns.bitwiseOr("i32", "flag1", "flag2"));
        assertIrContains(getToggledFunc, IrPatterns.bitwiseXor("i32", "value", "toggle"));
        assertIrContains(getShiftedFunc, IrPatterns.leftShift("i32", "value", "shift_amount"));
    }

    // ============================================================================
    // Complex Bitwise Expressions
    // ============================================================================

    @Test
    public void testChainedBitwiseOperations() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 255
            var b: i32 = 128
            var c: i32 = 64
            var d: i32 = 32
            
            # Chain multiple bitwise operations
            var result1: i32 = a & b | c
            var result2: i32 = a ^ b & c
            var result3: i32 = (a | b) ^ (c & d)
            var result4: i32 = a << 1 | b >> 1
            """);
        String ir = compileAndExpectSuccess(sourceCode, "chained_bitwise_operations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.bitwiseAnd("i32", "a", "b"),
            IrPatterns.bitwiseOr("i32", "bitand", "c"),
            IrPatterns.bitwiseAnd("i32", "b", "c"),
            IrPatterns.bitwiseXor("i32", "a", "bitand"),
            IrPatterns.bitwiseOr("i32", "a", "b"),
            IrPatterns.bitwiseAnd("i32", "c", "d"),
            IrPatterns.bitwiseXor("i32", "bitor", "bitand"),
            IrPatterns.leftShift("i32", "a", "1"),
            IrPatterns.arithmeticRightShift("i32", "b", "1"),
            IrPatterns.bitwiseOr("i32", "shl", "ashr")
        );
    }

    @Test
    public void testBitwiseOperationsWithParentheses() {
        String sourceCode = wrapInMainFunction("""
            var flags: i32 = 15
            var mask1: i32 = 8
            var mask2: i32 = 4
            
            # Test operator precedence with parentheses
            var result1: i32 = flags & (mask1 | mask2)
            var result2: i32 = (flags | mask1) & mask2
            var result3: i32 = flags ^ (mask1 & mask2)
            var result4: i32 = (flags << 1) | (mask1 >> 1)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "bitwise_with_parentheses");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.bitwiseOr("i32", "mask1", "mask2"),
            IrPatterns.bitwiseAnd("i32", "flags", "bitor"),
            IrPatterns.bitwiseOr("i32", "flags", "mask1"),
            IrPatterns.bitwiseAnd("i32", "bitor", "mask2"),
            IrPatterns.bitwiseAnd("i32", "mask1", "mask2"),
            IrPatterns.bitwiseXor("i32", "flags", "bitand"),
            IrPatterns.leftShift("i32", "flags", "1"),
            IrPatterns.arithmeticRightShift("i32", "mask1", "1"),
            IrPatterns.bitwiseOr("i32", "shl", "ashr")
        );
    }

    @Test
    public void testBitwiseOperationsAsExpressionStatements() {
        String sourceCode = """
            def get_value() -> i32:
                return 15
            
            def get_shift() -> i32:
                return 2
            
            def main() -> i32:
                get_value() & 7
                get_value() >> get_shift()
                ~get_value()
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "bitwise_expression_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("get_value", "i32", Collections.emptyList()),
            IrPatterns.bitwiseAnd("i32", "get_value", "7"),
            IrPatterns.functionCall("get_value", "i32", Collections.emptyList()),
            IrPatterns.functionCall("get_shift", "i32", Collections.emptyList()),
            IrPatterns.arithmeticRightShift("i32", "get_value", "get_shift"),
            IrPatterns.functionCall("get_value", "i32", Collections.emptyList()),
            IrPatterns.bitwiseNot("i32", "get_value")
        );
    }

    @Test
    public void testComplexBitwiseExpressionsAsStatement() {
        String sourceCode = """
            def get_mask() -> i32:
                return 255
            
            def get_value() -> i32:
                return 42
            
            def main() -> i32:
                (get_value() & get_mask()) | (~get_value() ^ get_mask())
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "complex_bitwise_expression_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("get_value", "i32", Collections.emptyList()),
            IrPatterns.functionCall("get_mask", "i32", Collections.emptyList()),
            IrPatterns.functionCall("get_value", "i32", Collections.emptyList()),
            IrPatterns.functionCall("get_mask", "i32", Collections.emptyList()),
            IrPatterns.bitwiseAnd("i32", "get_value", "get_mask"),
            IrPatterns.bitwiseNot("i32", "get_value"),
            IrPatterns.bitwiseXor("i32", "bitnot", "get_mask"),
            IrPatterns.bitwiseOr("i32", "bitand", "bitxor")
        );
    }
}