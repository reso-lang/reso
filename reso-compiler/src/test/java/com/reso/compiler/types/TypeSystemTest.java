package com.reso.compiler.types;

import static com.reso.compiler.types.StandardTypeHandles.BOOL;
import static com.reso.compiler.types.StandardTypeHandles.CHAR;
import static com.reso.compiler.types.StandardTypeHandles.F32;
import static com.reso.compiler.types.StandardTypeHandles.F64;
import static com.reso.compiler.types.StandardTypeHandles.FLOATING_POINT_LITERAL;
import static com.reso.compiler.types.StandardTypeHandles.I16;
import static com.reso.compiler.types.StandardTypeHandles.I32;
import static com.reso.compiler.types.StandardTypeHandles.I64;
import static com.reso.compiler.types.StandardTypeHandles.I8;
import static com.reso.compiler.types.StandardTypeHandles.INTEGER_LITERAL;
import static com.reso.compiler.types.StandardTypeHandles.ISIZE;
import static com.reso.compiler.types.StandardTypeHandles.NULL;
import static com.reso.compiler.types.StandardTypeHandles.U16;
import static com.reso.compiler.types.StandardTypeHandles.U32;
import static com.reso.compiler.types.StandardTypeHandles.U64;
import static com.reso.compiler.types.StandardTypeHandles.U8;
import static com.reso.compiler.types.StandardTypeHandles.UNIT;
import static com.reso.compiler.types.StandardTypeHandles.USIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.literals.FloatingPointLiteralType;
import com.reso.compiler.types.literals.IntegerLiteralType;
import com.reso.compiler.types.primary.BooleanType;
import com.reso.compiler.types.primary.CharType;
import com.reso.compiler.types.primary.FloatingPointType;
import com.reso.compiler.types.primary.IntegerType;
import com.reso.compiler.types.primary.NullType;
import com.reso.compiler.types.primary.UnitType;
import com.reso.compiler.types.primary.UnsignedIntegerType;
import com.reso.compiler.values.literals.FloatingPointLiteral;
import com.reso.compiler.values.literals.IntegerLiteral;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrContext;
import com.reso.llvm.api.IrModule;
import java.util.stream.Stream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for the TypeHandle-based TypeSystem.
 */
public class TypeSystemTest {

    private TypeSystemImpl typeSystem;
    private IrModule irModule;

    @Mock
    private ErrorReporter errorReporter;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        // Initialize LLVM
        IrFactory.initialize();

        // Create IR context for type system
        IrContext irContext = IrFactory.createContext();
        irModule = IrFactory.createModule(irContext, "reso_test_module");

        // Create type system with all standard types pre-registered
        typeSystem = new TypeSystemImpl(IrFactory.createBuilder(irContext),
            TypeRegistry.createWithStandardTypes(irModule));
    }

    // ============================================================================
    // Type Existence Tests
    // ============================================================================

    /**
     * Tests that all signed integer types exist and have correct basic properties.
     */
    @ParameterizedTest
    @MethodSource("signedIntegerTypeData")
    public void testSignedIntegerTypeExists(TypeHandle<IntegerType> handle, String expectedName,
                                            int expectedBitWidth) {
        IntegerType type = typeSystem.getType(handle);

        assertNotNull(type, expectedName + " type should exist");
        assertEquals(expectedName, type.getName(), "Type should have correct name");
        assertEquals(expectedBitWidth, type.getBitWidth(), "Type should have correct bit width");
        assertTrue(type.isInteger(), expectedName + " should be an integer type");
        assertTrue(type.isSignedInteger(), expectedName + " should be signed");
        assertFalse(type.isUnsignedInteger(), expectedName + " should not be unsigned");
        assertTrue(type.isNumeric(), expectedName + " should be numeric");
        assertFalse(type.isUntyped(), expectedName + " should not be untyped");
        assertNotNull(type.getType(), expectedName + " should have valid LLVM type");
    }

    @Test
    public void testISizeTypeExists() {
        IntegerType isizeType = typeSystem.getType(ISIZE);

        assertNotNull(isizeType, "isize type should exist");
        assertEquals("isize", isizeType.getName(), "isize should have correct name");
        assertEquals(IrFactory.getTargetPointerSize(irModule), isizeType.getBitWidth(),
            "isize should have correct bit width");
        assertTrue(isizeType.isInteger(), "isize should be an integer type");
        assertTrue(isizeType.isSignedInteger(), "isize should be signed");
        assertFalse(isizeType.isUnsignedInteger(), "isize should not be unsigned");
        assertTrue(isizeType.isNumeric(), "isize should be numeric");
        assertFalse(isizeType.isUntyped(), "isize should not be untyped");
        assertNotNull(isizeType.getType(), "isize should have valid LLVM type");
    }

    /**
     * Tests that all unsigned integer types exist and have correct basic properties.
     */
    @ParameterizedTest
    @MethodSource("unsignedIntegerTypeData")
    public void testUnsignedIntegerTypeExists(TypeHandle<UnsignedIntegerType> handle,
                                              String expectedName, int expectedBitWidth) {
        UnsignedIntegerType type = typeSystem.getType(handle);

        assertNotNull(type, expectedName + " type should exist");
        assertEquals(expectedName, type.getName(), "Type should have correct name");
        assertEquals(expectedBitWidth, type.getBitWidth(), "Type should have correct bit width");
        assertTrue(type.isInteger(), expectedName + " should be an integer type");
        assertFalse(type.isSignedInteger(), expectedName + " should not be signed");
        assertTrue(type.isUnsignedInteger(), expectedName + " should be unsigned");
        assertTrue(type.isNumeric(), expectedName + " should be numeric");
        assertFalse(type.isUntyped(), expectedName + " should not be untyped");
        assertNotNull(type.getType(), expectedName + " should have valid LLVM type");
    }

    @Test
    public void testUSizeTypeExists() {
        UnsignedIntegerType usizeType = typeSystem.getType(USIZE);

        assertNotNull(usizeType, "usize type should exist");
        assertEquals("usize", usizeType.getName(), "usize should have correct name");
        assertEquals(IrFactory.getTargetPointerSize(irModule), usizeType.getBitWidth(),
            "usize should have correct bit width");
        assertTrue(usizeType.isInteger(), "usize should be an integer type");
        assertFalse(usizeType.isSignedInteger(), "usize should not be signed");
        assertTrue(usizeType.isUnsignedInteger(), "usize should be unsigned");
        assertTrue(usizeType.isNumeric(), "usize should be numeric");
        assertFalse(usizeType.isUntyped(), "usize should not be untyped");
        assertNotNull(usizeType.getType(), "usize should have valid LLVM type");
    }

    /**
     * Tests that all floating-point types exist and have correct basic properties.
     */
    @ParameterizedTest
    @MethodSource("floatingPointTypeData")
    public void testFloatingPointTypeExists(TypeHandle<FloatingPointType> handle,
                                            String expectedName, int expectedBitWidth) {
        FloatingPointType type = typeSystem.getType(handle);

        assertNotNull(type, expectedName + " type should exist");
        assertEquals(expectedName, type.getName(), "Type should have correct name");
        assertEquals(expectedBitWidth, type.getBitWidth(), "Type should have correct bit width");
        assertTrue(type.isFloatingPoint(), expectedName + " should be a floating-point type");
        assertFalse(type.isInteger(), expectedName + " should not be an integer type");
        assertTrue(type.isNumeric(), expectedName + " should be numeric");
        assertNotNull(type.getType(), expectedName + " should have valid LLVM type");
    }

    /**
     * Tests that all non-numeric types exist and have correct basic properties.
     */
    @ParameterizedTest
    @MethodSource("nonNumericTypeData")
    public void testNonNumericTypeExists(TypeHandle<?> handle, String expectedName,
                                         Class<?> expectedClass) {
        ResoType type = typeSystem.getType(handle);

        assertNotNull(type, expectedName + " type should exist");
        assertEquals(expectedName, type.getName(), "Type should have correct name");
        assertEquals(expectedClass, type.getClass(), "Type should have correct class");
        assertFalse(type.isNumeric(), expectedName + " should not be numeric");
        assertFalse(type.isInteger(), expectedName + " should not be an integer type");
        assertFalse(type.isFloatingPoint(), expectedName + " should not be a floating-point type");
        assertNotNull(type.getType(), expectedName + " should have valid LLVM type");
    }

    // ============================================================================
    // Range Validation Tests
    // ============================================================================

    /**
     * Tests range validation for signed integer types.
     */
    @ParameterizedTest
    @MethodSource("signedIntegerRangeData")
    public void testSignedIntegerRangeValidation(TypeHandle<IntegerType> handle, long minValue,
                                                 long maxValue,
                                                 long validValue) {
        IntegerLiteral minValueLiteral = IntegerLiteral.fromLong(minValue, irModule);
        IntegerLiteral maxValueLiteral = IntegerLiteral.fromLong(maxValue, irModule);
        IntegerLiteral validValueLiteral = IntegerLiteral.fromLong(validValue, irModule);
        ResoType type = typeSystem.getType(handle);
        String typeName = type.getName();

        // Test boundary values
        assertTrue(minValueLiteral.isInRange(typeName),
            typeName + " should accept min value: " + minValue);
        assertTrue(maxValueLiteral.isInRange(typeName),
            typeName + " should accept max value: " + maxValue);

        // Test valid value
        assertTrue(validValueLiteral.isInRange(typeName),
            typeName + " should accept valid value: " + validValue);
    }

    @Test
    public void testISizeRangeValidation() {
        IntegerType isizeType = typeSystem.getType(ISIZE);
        String typeName = isizeType.getName();
        long minValue = -(1L << (IrFactory.getTargetPointerSize(irModule) - 1));
        long maxValue = (1L << (IrFactory.getTargetPointerSize(irModule) - 1)) - 1;

        IntegerLiteral minValueLiteral = IntegerLiteral.fromLong(minValue, irModule);
        IntegerLiteral maxValueLiteral = IntegerLiteral.fromLong(maxValue, irModule);
        IntegerLiteral validValueLiteral = IntegerLiteral.fromLong(0, irModule);

        // Test boundary values
        assertTrue(minValueLiteral.isInRange(typeName),
            typeName + " should accept min value: " + minValue);
        assertTrue(maxValueLiteral.isInRange(typeName),
            typeName + " should accept max value: " + maxValue);

        // Test valid value
        assertTrue(validValueLiteral.isInRange(typeName),
            typeName + " should accept valid value: 0");
    }

    /**
     * Tests range validation for unsigned integer types.
     */
    @ParameterizedTest
    @MethodSource("unsignedIntegerRangeData")
    public void testUnsignedIntegerRangeValidation(TypeHandle<UnsignedIntegerType> handle,
                                                   long minValue, long maxValue,
                                                   long validValue) {
        IntegerLiteral minValueLiteral = IntegerLiteral.fromUnsignedLong(minValue, irModule);
        IntegerLiteral maxValueLiteral = IntegerLiteral.fromUnsignedLong(maxValue, irModule);
        IntegerLiteral validValueLiteral = IntegerLiteral.fromUnsignedLong(validValue, irModule);
        ResoType type = typeSystem.getType(handle);
        String typeName = type.getName();

        // Test boundary values
        assertTrue(minValueLiteral.isInRange(typeName),
            typeName + " should accept min value: " + minValue);
        assertTrue(maxValueLiteral.isInRange(typeName),
            typeName + " should accept max value: " + maxValue);

        // Test valid value
        assertTrue(validValueLiteral.isInRange(typeName),
            typeName + " should accept valid value: " + validValue);
    }

    @Test
    public void testUSizeRangeValidation() {
        UnsignedIntegerType usizeType = typeSystem.getType(USIZE);
        String typeName = usizeType.getName();
        long minValue = 0;
        long maxValue = (1L << IrFactory.getTargetPointerSize(irModule)) - 1;

        IntegerLiteral minValueLiteral = IntegerLiteral.fromUnsignedLong(minValue, irModule);
        IntegerLiteral maxValueLiteral = IntegerLiteral.fromUnsignedLong(maxValue, irModule);
        IntegerLiteral validValueLiteral = IntegerLiteral.fromUnsignedLong(0, irModule);

        // Test boundary values
        assertTrue(minValueLiteral.isInRange(typeName),
            typeName + " should accept min value: " + minValue);
        assertTrue(maxValueLiteral.isInRange(typeName),
            typeName + " should accept max value: " + maxValue);

        // Test valid value
        assertTrue(validValueLiteral.isInRange(typeName),
            typeName + " should accept valid value: 0");
    }

    /**
     * Tests range validation for floating-point types.
     */
    @ParameterizedTest
    @MethodSource("floatingPointRangeData")
    public void testFloatingPointRangeValidation(TypeHandle<FloatingPointType> handle,
                                                 double validValue) {
        ResoType type = typeSystem.getType(handle);
        String typeName = type.getName();
        FloatingPointLiteral floatLiteral = FloatingPointLiteral.fromDouble(validValue);

        // Test valid values
        assertTrue(floatLiteral.isInRange(typeName),
            typeName + " should accept valid value: " + validValue);
    }

    @ParameterizedTest
    @MethodSource("invalidFloatingPointRangeData")
    public void testInvalidFloatingPointRangeValidation(TypeHandle<FloatingPointType> handle,
                                                        double invalidValue) {
        ResoType type = typeSystem.getType(handle);
        String typeName = type.getName();
        FloatingPointLiteral floatLiteral = FloatingPointLiteral.fromDouble(invalidValue);

        // Test special values
        assertFalse(floatLiteral.isInRange(typeName),
            typeName + " should not accept invalid value: " + invalidValue);
    }

    // ============================================================================
    // Type Resolution Tests
    // ============================================================================

    /**
     * Tests type resolution from string names.
     */
    @ParameterizedTest
    @ValueSource(strings = {"i8", "i16", "i32", "i64", "isize", "u8", "u16", "u32", "u64", "usize",
        "f32", "f64", "bool", "char"})
    public void testValidTypeResolution(String typeName) {
        ResoParser.TypeContext typeContext = createTypeContext(typeName);

        ResoType resolvedType = typeSystem.resolveType(typeContext, errorReporter);

        assertNotNull(resolvedType, "Type should be resolved: " + typeName);
        assertEquals(typeName, resolvedType.getName(), "Resolved type should have correct name");
        verify(errorReporter, never()).error(anyString(), anyInt(), anyInt());
    }

    /**
     * Tests type resolution failure for unknown types.
     */
    @ParameterizedTest
    @ValueSource(strings = {"UnknownType", "InvalidType", "Foo", "Bar", "Int128", "UInt128"})
    public void testInvalidTypeResolution(String typeName) {
        ResoParser.TypeContext typeContext = createTypeContext(typeName);

        ResoType resolvedType = typeSystem.resolveType(typeContext, errorReporter);

        assertNull(resolvedType, "Unknown type should not be resolved: " + typeName);
        verify(errorReporter).error(anyString(), anyInt(), anyInt());
    }

    // ============================================================================
    // Signed vs Unsigned Property Tests
    // ============================================================================

    /**
     * Tests signed integer properties.
     */
    @ParameterizedTest
    @MethodSource("signedIntegerTypeHandles")
    public void testSignedIntegerProperties(TypeHandle<IntegerType> handle) {
        IntegerType type = typeSystem.getType(handle);

        assertTrue(type.isInteger(), handle.getName() + " should be integer");
        assertTrue(type.isSignedInteger(), handle.getName() + " should be signed");
        assertFalse(type.isUnsignedInteger(), handle.getName() + " should not be unsigned");
        assertTrue(type.isNumeric(), handle.getName() + " should be numeric");
        assertFalse(type.isFloatingPoint(), handle.getName() + " should not be floating-point");
        assertFalse(type.isUntyped(), handle.getName() + " should not be untyped");
    }

    /**
     * Tests unsigned integer properties.
     */
    @ParameterizedTest
    @MethodSource("unsignedIntegerTypeHandles")
    public void testUnsignedIntegerProperties(TypeHandle<UnsignedIntegerType> handle) {
        UnsignedIntegerType type = typeSystem.getType(handle);

        assertTrue(type.isInteger(), handle.getName() + " should be integer");
        assertFalse(type.isSignedInteger(), handle.getName() + " should not be signed");
        assertTrue(type.isUnsignedInteger(), handle.getName() + " should be unsigned");
        assertTrue(type.isNumeric(), handle.getName() + " should be numeric");
        assertFalse(type.isFloatingPoint(), handle.getName() + " should not be floating-point");
        assertFalse(type.isUntyped(), handle.getName() + " should not be untyped");
    }

    // ============================================================================
    // Convenience Method Tests
    // ============================================================================

    /**
     * Tests getDefaultIntType() method.
     */
    @Test
    public void testGetDefaultIntType() {
        IntegerType defaultInt = typeSystem.getDefaultIntType();
        IntegerType int32 = typeSystem.getType(I32);

        assertNotNull(defaultInt, "Default int type should exist");
        assertEquals("i32", defaultInt.getName(), "Default int should be i32");
        assertEquals(32, defaultInt.getBitWidth(), "Default int should be 32-bit");
        assertSame(defaultInt, int32, "Default int should be same instance as I32");
    }

    /**
     * Tests getDefaultFloatType() method.
     */
    @Test
    public void testGetDefaultFloatType() {
        FloatingPointType defaultFloat = typeSystem.getDefaultFloatType();
        FloatingPointType float64 = typeSystem.getType(F64);

        assertNotNull(defaultFloat, "Default float type should exist");
        assertEquals("f64", defaultFloat.getName(), "Default float should be f64");
        assertEquals(64, defaultFloat.getBitWidth(), "Default float should be 64-bit");
        assertSame(defaultFloat, float64, "Default float should be same instance as F64");
    }

    /**
     * Tests getBoolType() method.
     */
    @Test
    public void testGetBoolType() {
        BooleanType boolType = typeSystem.getBoolType();
        BooleanType boolHandle = typeSystem.getType(BOOL);

        assertNotNull(boolType, "bool type should exist");
        assertEquals("bool", boolType.getName(), "bool should have correct name");
        assertTrue(boolType.isBool(), "bool should be boolean type");
        assertSame(boolType, boolHandle, "bool should be same instance as BOOL handle");
    }

    /**
     * Tests getUnitType() method.
     */
    @Test
    public void testGetUnitType() {
        UnitType unitType = typeSystem.getUnitType();
        UnitType unitHandle = typeSystem.getType(UNIT);

        assertNotNull(unitType, "Unit type should exist");
        assertEquals("()", unitType.getName(), "Should have correct name");
        assertSame(unitType, unitHandle, "Should be same instance as UNIT handle");
    }

    /**
     * Tests getNullType() method.
     */
    @Test
    public void testGetNullType() {
        NullType nullType = typeSystem.getNullType();
        NullType nullHandle = typeSystem.getType(NULL);

        assertNotNull(nullType, "Null type should exist");
        assertEquals("Null", nullType.getName(), "Null should have correct name");
        assertSame(nullType, nullHandle, "Null should be same instance as NULL handle");
    }

    /**
     * Tests getUntypedIntType() method.
     */
    @Test
    public void testGetUntypedIntType() {
        IntegerLiteralType integerLiteralType = typeSystem.getIntegerLiteralType();
        IntegerLiteralType integerLiteralHandle = typeSystem.getType(INTEGER_LITERAL);

        assertNotNull(integerLiteralType, "Integer Literal should exist");
        assertEquals("IntegerLiteral", integerLiteralType.getName(),
            "Integer Literal should have correct name");
        assertTrue(integerLiteralType.isUntyped(), "Integer Literal int should be untyped");
        assertTrue(integerLiteralType.isInteger(), "Integer Literal int should be integer");
        assertSame(integerLiteralType, integerLiteralHandle,
            "Integer Literal int should be same instance as INTEGER_LITERAL handle");
    }

    /**
     * Tests getUntypedFloatType() method.
     */
    @Test
    public void testGetUntypedFloatType() {
        FloatingPointLiteralType floatLiteralType = typeSystem.getFloatingPointLiteralType();
        FloatingPointLiteralType floatLiteralHandle = typeSystem.getType(FLOATING_POINT_LITERAL);

        assertNotNull(floatLiteralType, "Floating Point Literal should exist");
        assertEquals("FloatingPointLiteral", floatLiteralType.getName(),
            "Floating Point Literal should have correct name");
        assertTrue(floatLiteralType.isUntyped(), "Floating Point Literal should be untyped");
        assertTrue(floatLiteralType.isFloatingPoint(),
            "Floating Point Literal should be floating-point");
        assertSame(floatLiteralType, floatLiteralHandle,
            "Floating Point Literal should be same instance as FLOATING_POINT_LITERAL handle");
    }

    // ============================================================================
    // Type Equality and Identity Tests
    // ============================================================================

    /**
     * Tests type equality and identity consistency.
     */
    @ParameterizedTest
    @MethodSource("allTypeHandles")
    public void testTypeIdentityConsistency(TypeHandle<?> handle) {
        ResoType type1 = typeSystem.getType(handle);
        ResoType type2 = typeSystem.getType(handle);

        assertSame(type1, type2,
            handle.getName() + " should return same instance on multiple calls");
        assertEquals(type1, type2, handle.getName() + " should be equal to itself");
        assertEquals(type1.hashCode(), type2.hashCode(),
            handle.getName() + " should have consistent hash code");
    }

    /**
     * Tests that different types are not equal.
     */
    @Test
    public void testDifferentTypesNotEqual() {
        IntegerType int32 = typeSystem.getType(I32);
        IntegerType int64 = typeSystem.getType(I64);
        UnsignedIntegerType uint32 = typeSystem.getType(U32);
        FloatingPointType float32 = typeSystem.getType(F32);

        assertNotEquals(int32, int64, "Different integer types should not be equal");
        assertNotEquals(int32, uint32, "Signed and unsigned of same width should not be equal");
        assertNotEquals(int32, float32, "Integer and float types should not be equal");
        assertNotSame(int32, int64, "Different types should not be same instance");
    }

    // ============================================================================
    // Character Type Special Tests
    // ============================================================================

    /**
     * Tests character type properties and range.
     */
    @Test
    public void testCharacterTypeProperties() {
        CharType charType = typeSystem.getType(CHAR);

        assertNotNull(charType, "char type should exist");
        assertEquals("char", charType.getName(), "char should have correct name");
        assertTrue(charType.isChar(), "char should be char type");
        assertFalse(charType.isNumeric(), "char should not be numeric");
        assertFalse(charType.isInteger(), "char should not be integer type");
        assertFalse(charType.isFloatingPoint(), "char should not be floating-point");
        assertEquals(32, charType.getBitWidth(), "char should be 32-bit");

        assertFalse(charType.isUnsignedInteger(), "char should not be unsigned");
        assertFalse(charType.isSignedInteger(), "char should not be signed");
    }

    // ============================================================================
    // Data Providers
    // ============================================================================

    static Stream<Arguments> signedIntegerTypeData() {
        return Stream.of(
            Arguments.of(I8, "i8", 8),
            Arguments.of(I16, "i16", 16),
            Arguments.of(I32, "i32", 32),
            Arguments.of(I64, "i64", 64)
        );
    }

    static Stream<Arguments> unsignedIntegerTypeData() {
        return Stream.of(
            Arguments.of(U8, "u8", 8),
            Arguments.of(U16, "u16", 16),
            Arguments.of(U32, "u32", 32),
            Arguments.of(U64, "u64", 64)
        );
    }

    static Stream<Arguments> floatingPointTypeData() {
        return Stream.of(
            Arguments.of(F32, "f32", 32),
            Arguments.of(F64, "f64", 64)
        );
    }

    static Stream<Arguments> nonNumericTypeData() {
        return Stream.of(
            Arguments.of(BOOL, "bool", BooleanType.class),
            Arguments.of(UNIT, "()", UnitType.class),
            Arguments.of(NULL, "Null", NullType.class),
            Arguments.of(CHAR, "char", CharType.class)
        );
    }

    static Stream<Arguments> signedIntegerRangeData() {
        return Stream.of(
            Arguments.of(I8, -128L, 127L, 42L),
            Arguments.of(I16, -32768L, 32767L, 1000L),
            Arguments.of(I32, -2147483648L, 2147483647L, 1000000L),
            Arguments.of(I64, Long.MIN_VALUE, Long.MAX_VALUE, 1000000000000L)
        );
    }

    static Stream<Arguments> unsignedIntegerRangeData() {
        return Stream.of(
            Arguments.of(U8, 0L, 255L, 100L),
            Arguments.of(U16, 0L, 65535L, 1000L),
            Arguments.of(U32, 0L, 4294967295L, 1000000L),
            Arguments.of(U64, 0L, Long.parseUnsignedLong("18446744073709551615"), 1000000000000L)
        );
    }

    static Stream<Arguments> floatingPointRangeData() {
        return Stream.of(
            Arguments.of(F32, 3.14),
            Arguments.of(F64, 3.14159),
            Arguments.of(FLOATING_POINT_LITERAL, 3.14159)
        );
    }

    static Stream<Arguments> invalidFloatingPointRangeData() {
        return Stream.of(
            Arguments.of(F32, Double.NaN),
            Arguments.of(F64, Double.NEGATIVE_INFINITY),
            Arguments.of(FLOATING_POINT_LITERAL, Double.POSITIVE_INFINITY)
        );
    }

    static Stream<TypeHandle<IntegerType>> signedIntegerTypeHandles() {
        return Stream.of(I8, I16, I32, I64, ISIZE);
    }

    static Stream<TypeHandle<UnsignedIntegerType>> unsignedIntegerTypeHandles() {
        return Stream.of(U8, U16, U32, U64, USIZE);
    }

    static Stream<TypeHandle<?>> allTypeHandles() {
        return Stream.of(
            I8, I16, I32, I64, ISIZE,
            U8, U16, U32, U64, USIZE,
            F32, F64,
            BOOL, CHAR, UNIT, NULL,
            INTEGER_LITERAL, FLOATING_POINT_LITERAL
        );
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Creates a TypeContext for the given type name.
     */
    private ResoParser.TypeContext createTypeContext(String typeName) {
        com.reso.grammar.ResoLexer lexer =
            new com.reso.grammar.ResoLexer(CharStreams.fromString(typeName));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ResoParser parser = new ResoParser(tokens);
        return parser.type();
    }
}