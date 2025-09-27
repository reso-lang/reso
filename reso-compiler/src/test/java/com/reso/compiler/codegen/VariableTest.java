package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for basic variable declarations in Reso.
 * Tests compilation from Reso source code to LLVM IR with exact verification.
 */
public class VariableTest extends BaseTest {

    // ============================================================================
    // Basic Assignment Operations
    // ============================================================================

    @Test
    public void testSimpleAssignment() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            x = 20
            var y: i32 = x
            """);
        String ir = compileAndExpectSuccess(sourceCode, "simple_assignment");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("10", "i32", "x"), // Initial assignment
            IrPatterns.store("20", "i32", "x"), // Reassignment
            IrPatterns.load("i32", "x")        // Reading for assignment
        );
    }

    @Test
    public void testMultipleAssignments() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 1
            var b: i32 = 2
            var c: i32 = 3
                            
            a = b
            b = c
            c = a
            """);
        String ir = compileAndExpectSuccess(sourceCode, "multiple_assignments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("c", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("1", "i32", "a"), // Initial assignment
            IrPatterns.store("2", "i32", "b"), // Initial assignment
            IrPatterns.store("3", "i32", "c"), // Initial assignment
            IrPatterns.load("i32", "b"),       // a = b
            IrPatterns.load("i32", "c")        // b = c
        );
    }

    @Test
    public void testChainedAssignments() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 20
            var c: i32 = 30
                            
            # Chain assignments: c = b = a
            b = a
            c = b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "chained_assignments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("c", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("10", "i32", "a"), // Initial assignment
            IrPatterns.store("20", "i32", "b"), // Initial assignment
            IrPatterns.store("30", "i32", "c"), // Initial assignment
            IrPatterns.load("i32", "a"),        // b = a
            IrPatterns.load("i32", "b")         // c = b
        );
    }

    // ============================================================================
    // Basic Typed Variable Declarations
    // ============================================================================

    @ParameterizedTest
    @MethodSource("validIntegerBoundaries")
    public void testValidIntegerBoundaries(String resoType, String llvmType, String value,
                                           String expectedValue) {
        String sourceCode = wrapInMainFunction("var num: " + resoType + " = " + value);
        String ir = compileAndExpectSuccess(sourceCode, resoType.toLowerCase() + "_declaration");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("num", llvmType),
            IrPatterns.store(expectedValue, llvmType, "num")
        );
    }

    private static Stream<Arguments> validIntegerBoundaries() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            // i8: -128 to 127
            Arguments.of("i8", "i8", "-128", "-128"),
            Arguments.of("i8", "i8", "127", "127"),

            // u8: 0 to 255
            Arguments.of("u8", "i8", "0", "0"),
            Arguments.of("u8", "i8", "-0", "0"),
            Arguments.of("u8", "i8", "255", "-1"),

            // i16: -32768 to 32767
            Arguments.of("i16", "i16", "-32768", "-32768"),
            Arguments.of("i16", "i16", "32767", "32767"),

            // u16: 0 to 65535
            Arguments.of("u16", "i16", "0", "0"),
            Arguments.of("u16", "i16", "-0", "0"),
            Arguments.of("u16", "i16", "65535", "-1"),

            // i32: -2147483648
            Arguments.of("i32", "i32", "-2147483648", "-2147483648"),
            Arguments.of("i32", "i32", "2147483647", "2147483647"),

            // u32: 0 to 4294967295
            Arguments.of("u32", "i32", "0", "0"),
            Arguments.of("u32", "i32", "-0", "0"),
            Arguments.of("u32", "i32", "4294967295", "-1"),

            // i64: boundary values
            Arguments.of("i64", "i64", "-9223372036854775808", "-9223372036854775808"),
            Arguments.of("i64", "i64", "9223372036854775807", "9223372036854775807"),

            // u64: 0 to 18446744073709551615
            Arguments.of("u64", "i64", "0", "0"),
            Arguments.of("u64", "i64", "-0", "0"),
            Arguments.of("u64", "i64", "18446744073709551615", "-1"),

            // isize: boundary values based on target architecture
            Arguments.of("isize", sizeType, getMinIsizeValue(sizeType), getMinIsizeValue(sizeType)),
            Arguments.of("isize", sizeType, getMaxIsizeValue(sizeType), getMaxIsizeValue(sizeType)),

            // usize: 0 to max based on target architecture
            Arguments.of("usize", sizeType, "0", "0"),
            Arguments.of("usize", sizeType, "-0", "0"),
            Arguments.of("usize", sizeType, getMaxUsizeValue(sizeType), "-1")
        );
    }

    private static String getMinIsizeValue(String sizeType) {
        return sizeType.equals("i64") ? "-9223372036854775808" : "-2147483648";
    }

    private static String getMaxIsizeValue(String sizeType) {
        return sizeType.equals("i64") ? "9223372036854775807" : "2147483647";
    }

    private static String getMaxUsizeValue(String sizeType) {
        return sizeType.equals("i64") ? "18446744073709551615" : "4294967295";
    }

    @ParameterizedTest
    @MethodSource("numericTypesSpecialValues")
    public void testNumericTypesSpecialValues(String resoType, String llvmType, String value,
                                              String expectedValue) {
        String sourceCode = wrapInMainFunction("var num: " + resoType + " = " + value);
        String ir = compileAndExpectSuccess(sourceCode, resoType.toLowerCase() + "_special_value");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("num", llvmType),
            IrPatterns.store(expectedValue, llvmType, "num")
        );
    }

    private static Stream<Arguments> numericTypesSpecialValues() {
        return Stream.of(
            Arguments.of("i8", "i8", "0", "0"),
            Arguments.of("i8", "i8", "--127", "127"),
            Arguments.of("i8", "i8", "---128", "-128"),
            Arguments.of("i16", "i16", "0", "0"),
            Arguments.of("i16", "i16", "--32767", "32767"),
            Arguments.of("i16", "i16", "---32768", "-32768"),
            Arguments.of("i32", "i32", "0", "0"),
            Arguments.of("i32", "i32", "--2147483647", "2147483647"),
            Arguments.of("i32", "i32", "---2147483648", "-2147483648"),
            Arguments.of("i64", "i64", "0", "0"),
            Arguments.of("i64", "i64", "--9223372036854775807", "9223372036854775807"),
            Arguments.of("i64", "i64", "---9223372036854775808", "-9223372036854775808"),
            Arguments.of("u8", "i8", "0", "0"),
            Arguments.of("u16", "i16", "0", "0"),
            Arguments.of("u32", "i32", "0", "0"),
            Arguments.of("u64", "i64", "0", "0"),
            Arguments.of("f32", "float", "0.0", "0.000000e\\+00"),
            Arguments.of("f64", "double", "0.0", "0.000000e\\+00")
        );
    }

    @Test
    public void testf32Boundaries() {
        String normalRange = wrapInMainFunction("""
            var min_normal: f32 = 1.1754943E+38
            var max_normal: f32 = 3.402823E38
            var negative: f32 = -3.402823e-38
            """);
        String ir = compileAndExpectSuccess(normalRange, "float32_boundaries");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("min_normal", "float"),
            IrPatterns.alloca("max_normal", "float"),
            IrPatterns.alloca("negative", "float")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0x47D61BCCA0000000", "float", "min_normal"),
            IrPatterns.store("0x47EFFFFFA0000000", "float", "max_normal"),
            IrPatterns.store("0xB827288DC0000000", "float", "negative")
        );
    }

    @Test
    public void testf64Boundaries() {
        String normalRange = wrapInMainFunction("""
            var min_normal: f64 = 2.225074e-308
            var max_normal: f64 = 1.797693e+308
            var negative: f64 = -1.797693e+308
            """);
        String ir = compileAndExpectSuccess(normalRange, "float64_boundaries");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("min_normal", "double"),
            IrPatterns.alloca("max_normal", "double"),
            IrPatterns.alloca("negative", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0x1000001111E1E5", "double", "min_normal"),
            IrPatterns.store("0x7FEFFFFFD7B9609A", "double", "max_normal"),
            IrPatterns.store("0xFFEFFFFFD7B9609A", "double", "negative")
        );
    }

    @Test
    public void testBooleanVariableDeclaration() {
        String sourceCode = wrapInMainFunction("""
            var is_true: bool = true
            var is_false: bool = false
            """);
        String ir = compileAndExpectSuccess(sourceCode, "boolean_declaration");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("is_true", "i1"),
            IrPatterns.alloca("is_false", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("true", "i1", "is_true"),
            IrPatterns.store("false", "i1", "is_false")
        );
    }

    @Test
    public void testCharacterLiteralBoundaries() {
        String sourceCode = wrapInMainFunction("""
            var min_char: char = '\0'         # Null character
            var max_char: char = '\\u{FF}'       # Max ASCII
            var regular_char: char = 'A'       # Regular ASCII
            var unicode_char: char = '\\u{0041}' # Unicode A
            var max_unicode_char: char = '\\u{10FFFF}' # Max Unicode
            """);
        String ir = compileAndExpectSuccess(sourceCode, "char_boundaries");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("min_char", "i32"),
            IrPatterns.alloca("max_char", "i32"),
            IrPatterns.alloca("regular_char", "i32"),
            IrPatterns.alloca("unicode_char", "i32"),
            IrPatterns.alloca("max_unicode_char", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0", "i32", "min_char"), // '\0'
            IrPatterns.store("255", "i32", "max_char"), // '\\u{FF}'
            IrPatterns.store("65", "i32", "regular_char"), // 'A'
            IrPatterns.store("65", "i32", "unicode_char"), // '\\u{0041}' (Unicode A)
            IrPatterns.store("1114111", "i32", "max_unicode_char") // '\\u{10FFFF}' (Max Unicode)
        );
    }

    // ============================================================================
    // Type Inference (Untyped Literals)
    // ============================================================================

    @Test
    public void testUntypedIntegerInference() {
        String sourceCode = wrapInMainFunction("var auto_int = 42");
        String ir = compileAndExpectSuccess(sourceCode, "untyped_int_inference");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        // Should default to i32
        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("auto_int", "i32"),
            IrPatterns.store("42", "i32", "auto_int")
        );
    }

    @Test
    public void testUntypedFloatInference() {
        String sourceCode = wrapInMainFunction("var auto_float = 3.14");
        String ir = compileAndExpectSuccess(sourceCode, "untyped_float_inference");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        // Should default to f64
        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("auto_float", "double"),
            IrPatterns.store("3.140000e\\+00", "double", "auto_float")
        );
    }

    @Test
    public void testUntypedBooleanInference() {
        String sourceCode = wrapInMainFunction("var auto_bool = true");
        String ir = compileAndExpectSuccess(sourceCode, "untyped_bool_inference");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("auto_bool", "i1"),
            IrPatterns.store("true", "i1", "auto_bool")
        );
    }

    // ============================================================================
    // Multiple Variable Declarations
    // ============================================================================

    @Test
    public void testMultipleVariableDeclarations() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 20
            var c: i32 = 30
            """);
        String ir = compileAndExpectSuccess(sourceCode, "multiple_declarations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("c", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("10", "i32", "a"),
            IrPatterns.store("20", "i32", "b"),
            IrPatterns.store("30", "i32", "c")
        );
    }

    @Test
    public void testMixedTypeDeclarations() {
        String sourceCode = wrapInMainFunction("""
            var int_var: i32 = 42
            var float_var: f64 = 3.14
            var bool_var: bool = true
            """);
        String ir = compileAndExpectSuccess(sourceCode, "mixed_type_declarations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("int_var", "i32"),
            IrPatterns.alloca("float_var", "double"),
            IrPatterns.alloca("bool_var", "i1")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("42", "i32", "int_var"),
            IrPatterns.store("3.140000e\\+00", "double", "float_var"),
            IrPatterns.store("true", "i1", "bool_var")
        );
    }

    // ============================================================================
    // Constant Variables
    // ============================================================================

    @Test
    public void testConstantVariableDeclaration() {
        String sourceCode = wrapInMainFunction("const PI: f64 = 3.14159");
        String ir = compileAndExpectSuccess(sourceCode, "constant_declaration");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("PI", "double"),
            IrPatterns.store("3.141590e\\+00", "double", "PI")
        );
    }

    @Test
    public void testConstantIntegerDeclaration() {
        String sourceCode = wrapInMainFunction("const MAX_SIZE: i32 = 1000");
        String ir = compileAndExpectSuccess(sourceCode, "constant_int_declaration");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("MAX_SIZE", "i32"),
            IrPatterns.store("1000", "i32", "MAX_SIZE")
        );
    }

    // ============================================================================
    // Zero and Special Values
    // ============================================================================

    @Test
    public void testZeroValueDeclarations() {
        String sourceCode = wrapInMainFunction("""
            var zero_int: i32 = 0
            var zero_float: f64 = 0.0
            var false_bool: bool = false
            var unit : () = ()
            var zero_unit = ()
            """);
        String ir = compileAndExpectSuccess(sourceCode, "zero_value_declarations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("zero_int", "i32"),
            IrPatterns.alloca("zero_float", "double"),
            IrPatterns.alloca("false_bool", "i1"),
            IrPatterns.alloca("unit", "%unit"),
            IrPatterns.alloca("zero_unit", "%unit")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0", "i32", "zero_int"),
            IrPatterns.store("0.000000e\\+00", "double", "zero_float"),
            IrPatterns.store("false", "i1", "false_bool"),
            IrPatterns.store("zeroinitializer", "%unit", "unit"),
            IrPatterns.store("zeroinitializer", "%unit", "zero_unit")
        );
    }

    @Test
    public void testNegativeValueDeclarations() {
        String sourceCode = wrapInMainFunction("""
            var neg_int: i32 = -42
            var neg_float: f64 = -3.14
            """);
        String ir = compileAndExpectSuccess(sourceCode, "negative_value_declarations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("neg_int", "i32"),
            IrPatterns.alloca("neg_float", "double")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("-42", "i32", "neg_int"),
            IrPatterns.store("-3.140000e\\+00", "double", "neg_float")
        );
    }

    // ============================================================================
    // Error Cases
    // ============================================================================

    @ParameterizedTest
    @MethodSource("invalidIntegerConversions")
    public void testInvalidIntegerConversions(String targetType, String outOfRangeValue) {
        String sourceCode = wrapInMainFunction("var x: " + targetType + " = " + outOfRangeValue);
        String errors = compileAndExpectFailure(sourceCode, "invalid_" + targetType.toLowerCase());

        assertFalse(errors.isEmpty(),
            "Should report error for out-of-range literal conversion: " + targetType + " = "
                + outOfRangeValue);
    }

    @Test
    public void testInvalidCharacterLiteral() {
        String sourceCode =
            wrapInMainFunction("var invalid_char: char = '\\u{110000}'"); // Out of Unicode range
        String errors = compileAndExpectFailure(sourceCode, "invalid_character_literal");

        assertFalse(errors.isEmpty(), "Should report error for invalid character literal");
    }

    @Test
    public void testVariableWithoutInitializerOrType() {
        String sourceCode = wrapInMainFunction("var incomplete");
        String errors = compileAndExpectFailure(sourceCode, "incomplete_variable");

        assertFalse(errors.isEmpty(), "Should report error for incomplete variable declaration");
    }

    @Test
    public void testDuplicateVariableDeclaration() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var x: i32 = 20
            """);
        String errors = compileAndExpectFailure(sourceCode, "duplicate_variable");

        assertFalse(errors.isEmpty(), "Should report error for duplicate variable declaration");
    }

    @ParameterizedTest
    @MethodSource("floatToIntegerAssignmentCases")
    public void testFloatLiteralToIntegerTypeError(String integerType, String floatLiteral) {
        String sourceCode = wrapInMainFunction("var x: " + integerType + " = " + floatLiteral);
        String errors = compileAndExpectFailure(sourceCode,
            "float_to_" + integerType.toLowerCase() + "_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning " + floatLiteral + " to " + integerType);
    }

    @ParameterizedTest
    @MethodSource("integerToFloatAssignmentCases")
    public void testIntegerLiteralToFloatTypeError(String floatType, String integerLiteral) {
        String sourceCode = wrapInMainFunction("var x: " + floatType + " = " + integerLiteral);
        String errors = compileAndExpectFailure(sourceCode,
            "int_to_" + floatType.toLowerCase() + "_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning " + integerLiteral + " to " + floatType);
    }

    @ParameterizedTest
    @MethodSource("signedToUnsignedSameSizeCases")
    public void testSignedToUnsignedSameSizeError(String signedType, String unsignedType,
                                                  String value) {
        String sourceCode = wrapInMainFunction("""
            var signed: %s = %s
            var unsigned: %s = signed
            """.formatted(signedType, value, unsignedType));
        String errors = compileAndExpectFailure(sourceCode,
            signedType.toLowerCase() + "_to_" + unsignedType.toLowerCase() + "_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning " + signedType + " to " + unsignedType);
    }

    @ParameterizedTest
    @MethodSource("unsignedToSignedSameSizeCases")
    public void testUnsignedToSignedSameSizeError(String unsignedType, String signedType,
                                                  String value) {
        String sourceCode = wrapInMainFunction("""
            var unsigned: %s = %s
            var signed: %s = unsigned
            """.formatted(unsignedType, value, signedType));
        String errors = compileAndExpectFailure(sourceCode,
            unsignedType.toLowerCase() + "_to_" + signedType.toLowerCase() + "_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning " + unsignedType + " to " + signedType);
    }

    @ParameterizedTest
    @MethodSource("smallerToLargerIntegerCases")
    public void testSmallerToLargerIntegerError(String smallerType, String largerType,
                                                String value) {
        String sourceCode = wrapInMainFunction("""
            var smaller: %s = %s
            var larger: %s = smaller
            """.formatted(smallerType, value, largerType));
        String errors = compileAndExpectFailure(sourceCode,
            smallerType.toLowerCase() + "_to_" + largerType.toLowerCase() + "_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning " + smallerType + " to " + largerType);
    }

    @ParameterizedTest
    @MethodSource("largerToSmallerIntegerCases")
    public void testLargerToSmallerIntegerError(String largerType, String smallerType,
                                                String value) {
        String sourceCode = wrapInMainFunction("""
            var larger: %s = %s
            var smaller: %s = larger
            """.formatted(largerType, value, smallerType));
        String errors = compileAndExpectFailure(sourceCode,
            largerType.toLowerCase() + "_to_" + smallerType.toLowerCase() + "_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning " + largerType + " to " + smallerType);
    }

    @Test
    public void testf32Tof64Error() {
        String sourceCode = wrapInMainFunction("""
            var f32_val: f32 = 3.14
            var f64_val: f64 = f32
            """);
        String errors = compileAndExpectFailure(sourceCode, "float32_to_float64_error");

        assertFalse(errors.isEmpty(), "Should report error when assigning f32 to f64");
    }

    @Test
    public void testf64Tof32Error() {
        String sourceCode = wrapInMainFunction("""
            var f64_val: f64 = 3.14159
            var f32_val: f32 = f64
            """);
        String errors = compileAndExpectFailure(sourceCode, "float64_to_float32_error");

        assertFalse(errors.isEmpty(), "Should report error when assigning f64 to f32");
    }

    @ParameterizedTest
    @MethodSource("mixedSignedUnsignedDifferentSizeCases")
    public void testMixedSignedUnsignedDifferentSizeError(String sourceType, String targetType,
                                                          String value) {
        String sourceCode = wrapInMainFunction("""
            var source: %s = %s
            var target: %s = source
            """.formatted(sourceType, value, targetType));
        String errors = compileAndExpectFailure(sourceCode,
            sourceType.toLowerCase() + "_to_" + targetType.toLowerCase() + "_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning " + sourceType + " to " + targetType);
    }

    @ParameterizedTest
    @MethodSource("allNumericTypes")
    public void testBoolToNumericTypeError(String numericType) {
        String sourceCode = wrapInMainFunction("""
            var bool_var: bool = true
            var num_var: %s = bool_var
            """.formatted(numericType));
        String errors =
            compileAndExpectFailure(sourceCode, "bool_to_" + numericType.toLowerCase() + "_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning bool to " + numericType);
    }

    @ParameterizedTest
    @MethodSource("numericTypesWithValues")
    public void testNumericTypeToBoolError(String numericType, String value) {
        String sourceCode = wrapInMainFunction("""
            var num_var: %s = %s
            var bool_var: bool = num_var
            """.formatted(numericType, value));
        String errors =
            compileAndExpectFailure(sourceCode, numericType.toLowerCase() + "_to_bool_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning " + numericType + " to bool");
    }

    @ParameterizedTest
    @MethodSource("allNumericTypes")
    public void testCharToNumericTypeError(String numericType) {
        String sourceCode = wrapInMainFunction("""
            var char_var: char = 'A'
            var num_var: %s = char_var
            """.formatted(numericType));
        String errors =
            compileAndExpectFailure(sourceCode, "char_to_" + numericType.toLowerCase() + "_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning char to " + numericType);
    }

    @ParameterizedTest
    @MethodSource("numericTypesWithValues")
    public void testNumericTypeToCharError(String numericType, String value) {
        String sourceCode = wrapInMainFunction("""
            var num_var: %s = %s
            var char_var: char = num_var
            """.formatted(numericType, value));
        String errors =
            compileAndExpectFailure(sourceCode, numericType.toLowerCase() + "_to_char_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning " + numericType + " to char");
    }

    @Test
    public void testUnitVariableDeclarationError() {
        String sourceCode = wrapInMainFunction("var unit_var: () = 3");
        String errors = compileAndExpectFailure(sourceCode, "unit_variable_declaration_error");

        assertFalse(errors.isEmpty(),
            "Should report error for variable declaration with Void type");
    }

    @Test
    public void testAssignNullToNonNullableTypeError() {
        String sourceCode = wrapInMainFunction("var x: i32 = null");
        String errors = compileAndExpectFailure(sourceCode, "assign_null_to_non_nullable_error");

        assertFalse(errors.isEmpty(),
            "Should report error when assigning null to non-nullable type");
    }

    // ============================================================================
    // Data Providers for Error Cases
    // ============================================================================

    private static Stream<Arguments> invalidIntegerConversions() {
        String sizeType = "i" + POINTER_SIZE;
        String overflowUsize = sizeType.equals("i64") ? "18446744073709551616" : "4294967296";
        String overflowIsize = sizeType.equals("i64") ? "9223372036854775808" : "2147483648";
        String underflowIsize = sizeType.equals("i64") ? "-9223372036854775809" : "-2147483649";

        return Stream.of(
            // i8 out of range
            Arguments.of("i8", "-129"),
            Arguments.of("i8", "-15029"),
            Arguments.of("i8", "128"),
            Arguments.of("i8", "256"),
            Arguments.of("i8", "--129"),
            Arguments.of("i8", "127.5"),

            // u8 out of range
            Arguments.of("u8", "-1"),
            Arguments.of("u8", "256"),
            Arguments.of("u8", "3000"),
            Arguments.of("u8", "---1"),
            Arguments.of("u8", "255.5"),

            // i16 out of range
            Arguments.of("i16", "-32769"),
            Arguments.of("i16", "-70000"),
            Arguments.of("i16", "32768"),
            Arguments.of("i16", "70000"),
            Arguments.of("i16", "--32769"),
            Arguments.of("i16", "32767.5"),

            // u16 out of range
            Arguments.of("u16", "-1"),
            Arguments.of("u16", "65536"),
            Arguments.of("u16", "70000"),
            Arguments.of("u16", "---1"),
            Arguments.of("u16", "65535.5"),

            // i32 out of range
            Arguments.of("i32", "-2147483649"),
            Arguments.of("i32", "-3000000000"),
            Arguments.of("i32", "2147483648"),
            Arguments.of("i32", "3000000000"),
            Arguments.of("i32", "--2147483649"),
            Arguments.of("i32", "2147483647.5"),

            // u32 out of range
            Arguments.of("u32", "-1"),
            Arguments.of("u32", "4294967296"),
            Arguments.of("u32", "5000000000"),
            Arguments.of("u32", "---1"),
            Arguments.of("u32", "4294967295.5"),

            // i64 out of range
            Arguments.of("i64", "-9223372036854775809"),
            Arguments.of("i64", "-10000000000000000000"),
            Arguments.of("i64", "9223372036854775808"),
            Arguments.of("i64", "10000000000000000000"),
            Arguments.of("i64", "--9223372036854775809"),
            Arguments.of("i64", "9223372036854775807.5"),

            // u64 out of range
            Arguments.of("u64", "-1"),
            Arguments.of("u64", "18446744073709551616"),
            Arguments.of("u64", "20000000000000000000"),
            Arguments.of("u64", "---1"),
            Arguments.of("u64", "18446744073709551615.5"),

            // isize out of range
            Arguments.of("isize", underflowIsize),
            Arguments.of("isize", overflowIsize),

            // usize out of range
            Arguments.of("usize", "-1"),
            Arguments.of("usize", overflowUsize)
        );
    }

    private static Stream<Arguments> floatToIntegerAssignmentCases() {
        return Stream.of(
            Arguments.of("i8", "3.14"),
            Arguments.of("i16", "3.14"),
            Arguments.of("i32", "3.14"),
            Arguments.of("i64", "3.14"),
            Arguments.of("isize", "3.14"),
            Arguments.of("u8", "3.14"),
            Arguments.of("u16", "3.14"),
            Arguments.of("u32", "3.14"),
            Arguments.of("u64", "3.14"),
            Arguments.of("usize", "3.14")
        );
    }

    private static Stream<Arguments> integerToFloatAssignmentCases() {
        return Stream.of(
            // Integer literals to f32
            Arguments.of("f32", "42"),
            Arguments.of("f32", "-42"),
            Arguments.of("f32", "1000"),

            // Integer literals to f64
            Arguments.of("f64", "42"),
            Arguments.of("f64", "-42"),
            Arguments.of("f64", "1000000")
        );
    }

    private static Stream<Arguments> signedToUnsignedSameSizeCases() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            Arguments.of("i8", "u8", "42"),
            Arguments.of("i16", "u16", "1000"),
            Arguments.of("i32", "u32", "100000"),
            Arguments.of("i64", "u64", "1000000000"),
            Arguments.of(sizeType, "usize", "1000")
        );
    }

    private static Stream<Arguments> unsignedToSignedSameSizeCases() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            Arguments.of("u8", "i8", "200"),
            Arguments.of("u16", "i16", "50000"),
            Arguments.of("u32", "i32", "3000000000"),
            Arguments.of("u64", "i64", "10000000000000000000"),
            Arguments.of("usize", sizeType, "10000000000")
        );
    }

    private static Stream<Arguments> smallerToLargerIntegerCases() {
        return Stream.of(
            // Signed integer promotions
            Arguments.of("i8", "i16", "42"),
            Arguments.of("i8", "i32", "42"),
            Arguments.of("i8", "i64", "42"),
            Arguments.of("i16", "i32", "1000"),
            Arguments.of("i16", "i64", "1000"),
            Arguments.of("i32", "i64", "100000"),
            Arguments.of("i8", "isize", "1000"),
            Arguments.of("i16", "size", "1000"),

            // Unsigned integer promotions
            Arguments.of("u8", "u16", "200"),
            Arguments.of("u8", "u32", "200"),
            Arguments.of("u8", "u64", "200"),
            Arguments.of("u16", "u32", "50000"),
            Arguments.of("u16", "u64", "50000"),
            Arguments.of("u32", "u64", "3000000000"),
            Arguments.of("u8", "usize", "1000000000"),
            Arguments.of("u16", "usize", "1000000000")
        );
    }

    private static Stream<Arguments> largerToSmallerIntegerCases() {
        return Stream.of(
            // Signed integer demotions
            Arguments.of("i16", "i8", "100"),
            Arguments.of("i32", "i8", "100"),
            Arguments.of("i64", "i8", "100"),
            Arguments.of("i32", "i16", "1000"),
            Arguments.of("i64", "i16", "1000"),
            Arguments.of("i64", "i32", "100000"),
            Arguments.of("isize", "i32", "1000"),
            Arguments.of("isize", "i64", "1000"),

            // Unsigned integer demotions
            Arguments.of("u16", "u8", "200"),
            Arguments.of("u32", "u8", "200"),
            Arguments.of("u64", "u8", "200"),
            Arguments.of("u32", "u16", "50000"),
            Arguments.of("u64", "u16", "50000"),
            Arguments.of("u64", "u32", "3000000000"),
            Arguments.of("usize", "u32", "1000000000"),
            Arguments.of("usize", "u64", "1000000000")
        );
    }

    private static Stream<Arguments> mixedSignedUnsignedDifferentSizeCases() {
        return Stream.of(
            // Signed to unsigned with different sizes
            Arguments.of("i8", "u16", "42"),
            Arguments.of("i8", "u32", "42"),
            Arguments.of("i8", "u64", "42"),
            Arguments.of("i16", "u8", "100"),
            Arguments.of("i16", "u32", "1000"),
            Arguments.of("i16", "u64", "1000"),
            Arguments.of("i32", "u8", "100"),
            Arguments.of("i32", "u16", "1000"),
            Arguments.of("i32", "u64", "100000"),
            Arguments.of("i64", "u8", "100"),
            Arguments.of("i64", "u16", "1000"),
            Arguments.of("i64", "u32", "100000"),
            Arguments.of("isize", "u32", "1000"),
            Arguments.of("isize", "u64", "1000"),

            // Unsigned to signed with different sizes
            Arguments.of("u8", "i16", "200"),
            Arguments.of("u8", "i32", "200"),
            Arguments.of("u8", "i64", "200"),
            Arguments.of("u16", "i8", "100"),
            Arguments.of("u16", "i32", "50000"),
            Arguments.of("u16", "i64", "50000"),
            Arguments.of("u32", "i8", "100"),
            Arguments.of("u32", "i16", "1000"),
            Arguments.of("u32", "i64", "3000000000"),
            Arguments.of("u64", "i8", "100"),
            Arguments.of("u64", "i16", "1000"),
            Arguments.of("u64", "i32", "100000"),
            Arguments.of("usize", "i32", "1000000000"),
            Arguments.of("usize", "i64", "1000000000")
        );
    }

    private static Stream<String> allNumericTypes() {
        return Stream.of(
            "i8", "i16", "i32", "i64", "isize",
            "u8", "u16", "u32", "u64", "usize",
            "f32", "f64"
        );
    }

    private static Stream<Arguments> numericTypesWithValues() {
        return Stream.of(
            Arguments.of("i8", "42"),
            Arguments.of("i16", "1000"),
            Arguments.of("i32", "100000"),
            Arguments.of("i64", "1000000000"),
            Arguments.of("isize", "1000"),
            Arguments.of("u8", "200"),
            Arguments.of("u16", "50000"),
            Arguments.of("u32", "3000000000"),
            Arguments.of("u64", "10000000000000000000"),
            Arguments.of("usize", "1000000000"),
            Arguments.of("f32", "3.14f"),
            Arguments.of("f64", "3.14159")
        );
    }

    // ============================================================================
    // Complex Scenarios
    // ============================================================================

    @Test
    public void testVariableInComplexFunction() {
        String sourceCode = """
            def calculate() -> i32:
                var a: i32 = 5
                var b: i32 = 10
                return a + b
                            
            def main() -> i32:
                return calculate()
            """;
        String ir = compileAndExpectSuccess(sourceCode, "complex_function_variables");

        String calculateFunc = extractFunction(ir, "calculate");
        assertNotNull(calculateFunc, "Should find calculate function in IR");

        assertIrContains(calculateFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32")
        );

        assertIrContainsInOrder(calculateFunc,
            IrPatterns.store("5", "i32", "a"),
            IrPatterns.store("10", "i32", "b")
        );
    }

    @Test
    public void testVariableScope() {
        String sourceCode = """
            def outer() -> i32:
                var x: i32 = 10
                return x
                            
            def main() -> i32:
                var x: i32 = 20
                return x
            """;
        String ir = compileAndExpectSuccess(sourceCode, "variable_scope");

        // Both functions should compile without conflict
        String outerFunc = extractFunction(ir, "outer");
        String mainFunc = extractFunction(ir, "main");

        assertNotNull(outerFunc, "Should find outer function");
        assertNotNull(mainFunc, "Should find main function");

        // Both should have their own variable allocations
        assertIrContainsInOrder(outerFunc, IrPatterns.alloca("x", "i32"),
            IrPatterns.store("10", "i32", "x"));
        assertIrContainsInOrder(mainFunc, IrPatterns.alloca("x", "i32"),
            IrPatterns.store("20", "i32", "x"));
    }

    @Test
    public void testVariableShadowingInNestedScopes() {
        String sourceCode = """
            def outer() -> i32:
                var x: i32 = 10
                if true:
                    var x: i32 = 20  # Shadows outer x
                    return x
                return x
                            
            def main() -> i32:
                return outer()
            """;
        String ir = compileAndExpectSuccess(sourceCode, "variable_shadowing");

        String outerFunc = extractFunction(ir, "outer");
        assertNotNull(outerFunc, "Should find outer function in IR");

        assertIrContains(outerFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("x1", "i32")
        );

        assertIrContainsInOrder(outerFunc,
            IrPatterns.store("10", "i32", "x"),
            IrPatterns.store("20", "i32", "x1")
        );
    }

    @Test
    public void testVariableInDifferentBlocks() {
        String sourceCode = """
            def test() -> i32:
                var result: i32 = 0
                if true:
                    var temp: i32 = 10
                    result = temp
                else:
                    var temp: i32 = 20  # Different temp variable
                    result = temp
                return result
                            
            def main() -> i32:
                return test()
            """;
        String ir = compileAndExpectSuccess(sourceCode, "variables_in_blocks");

        String testFunc = extractFunction(ir, "test");
        assertNotNull(testFunc, "Should find test function in IR");

        assertIrContains(testFunc,
            IrPatterns.alloca("temp", "i32"),
            IrPatterns.alloca("temp1", "i32")
        );

        assertIrContainsInOrder(testFunc,
            IrPatterns.store("10", "i32", "temp"),
            IrPatterns.store("20", "i32", "temp1")
        );
    }

    @Test
    public void testVariablesWithUnderscores() {
        String sourceCode = wrapInMainFunction("""
            var _private: i32 = 1
            var __dunder: i32 = 2
            var my_var: i32 = 3
            var var_: i32 = 4
            var _: i32 = 5        # Single underscore
            """);
        String ir = compileAndExpectSuccess(sourceCode, "underscore_names");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("_private", "i32"),
            IrPatterns.alloca("__dunder", "i32"),
            IrPatterns.alloca("my_var", "i32"),
            IrPatterns.alloca("var_", "i32"),
            IrPatterns.alloca("_", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("1", "i32", "_private"),
            IrPatterns.store("2", "i32", "__dunder"),
            IrPatterns.store("3", "i32", "my_var"),
            IrPatterns.store("4", "i32", "var_"),
            IrPatterns.store("5", "i32", "_")
        );
    }

    @Test
    public void testVariablesWithNumbers() {
        String sourceCode = wrapInMainFunction("""
            var var1: i32 = 1
            var var2: i32 = 2
            var var123: i32 = 123
            var x1y2z3: i32 = 456
            """);
        String ir = compileAndExpectSuccess(sourceCode, "numeric_names");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("var1", "i32"),
            IrPatterns.alloca("var2", "i32"),
            IrPatterns.alloca("var123", "i32"),
            IrPatterns.alloca("x1y2z3", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("1", "i32", "var1"),
            IrPatterns.store("2", "i32", "var2"),
            IrPatterns.store("123", "i32", "var123"),
            IrPatterns.store("456", "i32", "x1y2z3")
        );
    }
}