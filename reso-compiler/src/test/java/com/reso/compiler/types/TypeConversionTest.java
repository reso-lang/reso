package com.reso.compiler.types;

import static com.reso.compiler.types.StandardTypeHandles.BOOL;
import static com.reso.compiler.types.StandardTypeHandles.CHAR;
import static com.reso.compiler.types.StandardTypeHandles.F32;
import static com.reso.compiler.types.StandardTypeHandles.F64;
import static com.reso.compiler.types.StandardTypeHandles.I16;
import static com.reso.compiler.types.StandardTypeHandles.I32;
import static com.reso.compiler.types.StandardTypeHandles.I64;
import static com.reso.compiler.types.StandardTypeHandles.I8;
import static com.reso.compiler.types.StandardTypeHandles.ISIZE;
import static com.reso.compiler.types.StandardTypeHandles.NULL;
import static com.reso.compiler.types.StandardTypeHandles.U16;
import static com.reso.compiler.types.StandardTypeHandles.U32;
import static com.reso.compiler.types.StandardTypeHandles.U64;
import static com.reso.compiler.types.StandardTypeHandles.U8;
import static com.reso.compiler.types.StandardTypeHandles.UNIT;
import static com.reso.compiler.types.StandardTypeHandles.USIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.primary.FloatingPointType;
import com.reso.compiler.types.primary.IntegerType;
import com.reso.compiler.types.primary.UnsignedIntegerType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrContext;
import com.reso.llvm.api.IrModule;
import com.reso.llvm.api.IrType;
import com.reso.llvm.api.IrValue;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for type conversion functionality.
 */
public class TypeConversionTest {

    private TypeSystemImpl typeSystem;
    private IrContext irContext;

    @Mock
    private ErrorReporter errorReporter;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        // Initialize LLVM
        IrFactory.initialize();

        // Create IR context for type system
        irContext = IrFactory.createContext();
        IrModule irModule = IrFactory.createModule(irContext, "reso_test_module");

        // Create type system with all standard types pre-registered
        typeSystem = new TypeSystemImpl(IrFactory.createBuilder(irContext),
            TypeRegistry.createWithStandardTypes(irModule));
    }

    // ============================================================================
    // Conversion Tests - Integer to Integer (Signed)
    // ============================================================================

    /**
     * Tests conversion between signed integer types (widening).
     */
    @ParameterizedTest
    @MethodSource("signedIntegerWideningConversions")
    public void testSignedIntWideningConversion(TypeHandle<IntegerType> sourceHandle,
                                                TypeHandle<IntegerType> targetHandle,
                                                long testValue) {
        IntegerType sourceType = typeSystem.getType(sourceHandle);
        IntegerType targetType = typeSystem.getType(targetHandle);

        IrValue sourceValue = IrFactory.createConstantInt(sourceType.getType(), testValue, true);
        ConcreteResoValue sourceResoValue = new ConcreteResoValue(sourceType, sourceValue, 0, 0);

        ConcreteResoValue convertedValue = typeSystem.createConversion(
            sourceResoValue, targetType, errorReporter, 1, 1);

        assertNotNull(convertedValue,
            sourceHandle.getName() + " should convert to " + targetHandle.getName());
        assertEquals(targetHandle.getName(), convertedValue.getTypeName(),
            "Converted type should be " + targetHandle.getName());
        verify(errorReporter, never()).error(anyString(), anyInt(), anyInt());
    }

    /**
     * Tests conversion between signed integer types (narrowing).
     */
    @ParameterizedTest
    @MethodSource("signedIntegerNarrowingConversions")
    public void testSignedIntNarrowingConversion(TypeHandle<IntegerType> sourceHandle,
                                                 TypeHandle<IntegerType> targetHandle,
                                                 long testValue) {
        IntegerType sourceType = typeSystem.getType(sourceHandle);
        IntegerType targetType = typeSystem.getType(targetHandle);

        IrValue sourceValue = IrFactory.createConstantInt(sourceType.getType(), testValue, true);
        ConcreteResoValue sourceResoValue = new ConcreteResoValue(sourceType, sourceValue, 0, 0);

        ConcreteResoValue convertedValue = typeSystem.createConversion(
            sourceResoValue, targetType, errorReporter, 1, 1);

        assertNotNull(convertedValue,
            sourceHandle.getName() + " should convert to " + targetHandle.getName());
        assertEquals(targetHandle.getName(), convertedValue.getTypeName(),
            "Converted type should be " + targetHandle.getName());
        verify(errorReporter, never()).error(anyString(), anyInt(), anyInt());
    }

    // ============================================================================
    // Conversion Tests - Integer to Integer (Unsigned)
    // ============================================================================

    /**
     * Tests conversion between unsigned integer types.
     */
    @ParameterizedTest
    @MethodSource("unsignedIntegerConversions")
    public void testUnsignedIntConversion(TypeHandle<UnsignedIntegerType> sourceHandle,
                                          TypeHandle<UnsignedIntegerType> targetHandle,
                                          long testValue) {
        UnsignedIntegerType sourceType = typeSystem.getType(sourceHandle);
        UnsignedIntegerType targetType = typeSystem.getType(targetHandle);

        IrValue sourceValue = IrFactory.createConstantInt(sourceType.getType(), testValue, false);
        ConcreteResoValue sourceResoValue = new ConcreteResoValue(sourceType, sourceValue, 0, 0);

        ConcreteResoValue convertedValue = typeSystem.createConversion(
            sourceResoValue, targetType, errorReporter, 1, 1);

        assertNotNull(convertedValue,
            sourceHandle.getName() + " should convert to " + targetHandle.getName());
        assertEquals(targetHandle.getName(), convertedValue.getTypeName(),
            "Converted type should be " + targetHandle.getName());
        verify(errorReporter, never()).error(anyString(), anyInt(), anyInt());
    }

    /**
     * Tests conversion between signed and unsigned integer types.
     */
    @ParameterizedTest
    @MethodSource("signedUnsignedMixedConversions")
    public void testSignedUnsignedConversion(TypeHandle<?> sourceHandle,
                                             TypeHandle<?> targetHandle,
                                             long testValue,
                                             boolean sourceIsSigned) {
        ResoType sourceType = typeSystem.getType(sourceHandle);
        ResoType targetType = typeSystem.getType(targetHandle);

        IrValue sourceValue =
            IrFactory.createConstantInt(sourceType.getType(), testValue, sourceIsSigned);
        ConcreteResoValue sourceResoValue = new ConcreteResoValue(sourceType, sourceValue, 0, 0);

        ConcreteResoValue convertedValue = typeSystem.createConversion(
            sourceResoValue, targetType, errorReporter, 1, 1);

        assertNotNull(convertedValue,
            sourceHandle.getName() + " should convert to " + targetHandle.getName());
        assertEquals(targetHandle.getName(), convertedValue.getTypeName(),
            "Converted type should be " + targetHandle.getName());
        verify(errorReporter, never()).error(anyString(), anyInt(), anyInt());
    }

    // ============================================================================
    // Conversion Tests - Integer to Float
    // ============================================================================

    /**
     * Tests conversion from signed integers to floating-point types.
     */
    @ParameterizedTest
    @MethodSource("signedIntToFloatConversions")
    public void testSignedIntToFloatConversion(TypeHandle<IntegerType> intHandle,
                                               TypeHandle<FloatingPointType> floatHandle,
                                               long testValue) {
        IntegerType intType = typeSystem.getType(intHandle);
        FloatingPointType floatType = typeSystem.getType(floatHandle);

        IrValue intValue = IrFactory.createConstantInt(intType.getType(), testValue, true);
        ConcreteResoValue intResoValue = new ConcreteResoValue(intType, intValue, 0, 0);

        ConcreteResoValue convertedValue = typeSystem.createConversion(
            intResoValue, floatType, errorReporter, 1, 1);

        assertNotNull(convertedValue,
            intHandle.getName() + " should convert to " + floatHandle.getName());
        assertEquals(floatHandle.getName(), convertedValue.getTypeName(),
            "Converted type should be " + floatHandle.getName());
        verify(errorReporter, never()).error(anyString(), anyInt(), anyInt());
    }

    /**
     * Tests conversion from unsigned integers to floating-point types.
     */
    @ParameterizedTest
    @MethodSource("unsignedIntToFloatConversions")
    public void testUnsignedIntToFloatConversion(TypeHandle<UnsignedIntegerType> intHandle,
                                                 TypeHandle<FloatingPointType> floatHandle,
                                                 long testValue) {
        UnsignedIntegerType intType = typeSystem.getType(intHandle);
        FloatingPointType floatType = typeSystem.getType(floatHandle);

        IrValue intValue = IrFactory.createConstantInt(intType.getType(), testValue, false);
        ConcreteResoValue intResoValue = new ConcreteResoValue(intType, intValue, 0, 0);

        ConcreteResoValue convertedValue = typeSystem.createConversion(
            intResoValue, floatType, errorReporter, 1, 1);

        assertNotNull(convertedValue,
            intHandle.getName() + " should convert to " + floatHandle.getName());
        assertEquals(floatHandle.getName(), convertedValue.getTypeName(),
            "Converted type should be " + floatHandle.getName());
        verify(errorReporter, never()).error(anyString(), anyInt(), anyInt());
    }

    // ============================================================================
    // Conversion Tests - Float to Integer
    // ============================================================================

    /**
     * Tests conversion from floating-point to signed integer types.
     */
    @ParameterizedTest
    @MethodSource("floatToSignedIntConversions")
    public void testFloatToSignedIntConversion(TypeHandle<FloatingPointType> floatHandle,
                                               TypeHandle<IntegerType> intHandle,
                                               double testValue) {
        FloatingPointType floatType = typeSystem.getType(floatHandle);
        IntegerType intType = typeSystem.getType(intHandle);

        IrValue floatValue = IrFactory.createConstantFloat(floatType.getType(), testValue);
        ConcreteResoValue floatResoValue = new ConcreteResoValue(floatType, floatValue, 0, 0);

        ConcreteResoValue convertedValue = typeSystem.createConversion(
            floatResoValue, intType, errorReporter, 1, 1);

        assertNotNull(convertedValue,
            floatHandle.getName() + " should convert to " + intHandle.getName());
        assertEquals(intHandle.getName(), convertedValue.getTypeName(),
            "Converted type should be " + intHandle.getName());
        verify(errorReporter, never()).error(anyString(), anyInt(), anyInt());
    }

    /**
     * Tests conversion from floating-point to unsigned integer types.
     */
    @ParameterizedTest
    @MethodSource("floatToUnsignedIntConversions")
    public void testFloatToUnsignedIntConversion(TypeHandle<FloatingPointType> floatHandle,
                                                 TypeHandle<UnsignedIntegerType> intHandle,
                                                 double testValue) {
        FloatingPointType floatType = typeSystem.getType(floatHandle);
        UnsignedIntegerType intType = typeSystem.getType(intHandle);

        IrValue floatValue = IrFactory.createConstantFloat(floatType.getType(), testValue);
        ConcreteResoValue floatResoValue = new ConcreteResoValue(floatType, floatValue, 0, 0);

        ConcreteResoValue convertedValue = typeSystem.createConversion(
            floatResoValue, intType, errorReporter, 1, 1);

        assertNotNull(convertedValue,
            floatHandle.getName() + " should convert to " + intHandle.getName());
        assertEquals(intHandle.getName(), convertedValue.getTypeName(),
            "Converted type should be " + intHandle.getName());
        verify(errorReporter, never()).error(anyString(), anyInt(), anyInt());
    }

    // ============================================================================
    // Conversion Tests - Floating-Point to Floating-Point
    // ============================================================================

    /**
     * Tests conversion between floating-point types.
     */
    @ParameterizedTest
    @MethodSource("floatToFloatConversions")
    public void testFloatToFloatConversion(TypeHandle<FloatingPointType> sourceHandle,
                                           TypeHandle<FloatingPointType> targetHandle,
                                           double testValue) {
        FloatingPointType sourceType = typeSystem.getType(sourceHandle);
        FloatingPointType targetType = typeSystem.getType(targetHandle);

        IrValue sourceValue = IrFactory.createConstantFloat(sourceType.getType(), testValue);
        ConcreteResoValue sourceResoValue = new ConcreteResoValue(sourceType, sourceValue, 0, 0);

        ConcreteResoValue convertedValue = typeSystem.createConversion(
            sourceResoValue, targetType, errorReporter, 1, 1);

        assertNotNull(convertedValue,
            sourceHandle.getName() + " should convert to " + targetHandle.getName());
        assertEquals(targetHandle.getName(), convertedValue.getTypeName(),
            "Converted type should be " + targetHandle.getName());
        verify(errorReporter, never()).error(anyString(), anyInt(), anyInt());
    }

    // ============================================================================
    // Character Type Conversion Tests
    // ============================================================================

    /**
     * Tests conversion involving character type (as unsigned 16-bit).
     */
    @ParameterizedTest
    @MethodSource("charConversions")
    public void testCharConversion(TypeHandle<?> sourceHandle,
                                   TypeHandle<?> targetHandle,
                                   long testValue,
                                   boolean shouldSucceed) {
        ResoType sourceType = typeSystem.getType(sourceHandle);
        ResoType targetType = typeSystem.getType(targetHandle);

        IrValue sourceValue;
        if (sourceType.isChar()) {
            sourceValue = IrFactory.createConstantInt(sourceType.getType(), testValue,
                false); // char is unsigned
        } else if (sourceType.isInteger()) {
            boolean isSigned = sourceType.isSignedInteger();
            sourceValue = IrFactory.createConstantInt(sourceType.getType(), testValue, isSigned);
        } else if (sourceType.isFloatingPoint()) {
            sourceValue = IrFactory.createConstantFloat(sourceType.getType(), (double) testValue);
        } else {
            return; // Skip unsupported types
        }

        ConcreteResoValue sourceResoValue = new ConcreteResoValue(sourceType, sourceValue, 0, 0);

        ConcreteResoValue convertedValue = typeSystem.createConversion(
            sourceResoValue, targetType, errorReporter, 1, 1);

        if (shouldSucceed) {
            assertNotNull(convertedValue,
                sourceHandle.getName() + " should convert to " + targetHandle.getName());
            assertEquals(targetHandle.getName(), convertedValue.getTypeName(),
                "Converted type should be " + targetHandle.getName());
        } else {
            assertNull(convertedValue,
                sourceHandle.getName() + " should not convert to " + targetHandle.getName());
        }
    }

    // ============================================================================
    // Conversion Failure Tests
    // ============================================================================

    /**
     * Tests conversion failures for incompatible types.
     */
    @ParameterizedTest
    @MethodSource("incompatibleConversions")
    public void testIncompatibleConversion(TypeHandle<?> sourceHandle, TypeHandle<?> targetHandle) {
        ResoType sourceType = typeSystem.getType(sourceHandle);
        ResoType targetType = typeSystem.getType(targetHandle);

        IrValue sourceValue = createTestValue(sourceType);
        if (sourceValue == null) {
            return; // Skip if we can't create a test value
        }

        ConcreteResoValue sourceResoValue = new ConcreteResoValue(sourceType, sourceValue, 0, 0);

        ConcreteResoValue convertedValue = typeSystem.createConversion(
            sourceResoValue, targetType, errorReporter, 1, 1);

        assertNull(convertedValue,
            sourceHandle.getName() + " should not convert to " + targetHandle.getName());
        verify(errorReporter).error(anyString(), anyInt(), anyInt());
    }

    // ============================================================================
    // Data Providers
    // ============================================================================

    static Stream<Arguments> signedIntegerWideningConversions() {
        return Stream.of(
            Arguments.of(I8, I16, 42L),
            Arguments.of(I8, I32, -42L),
            Arguments.of(I8, I64, 127L),
            Arguments.of(I16, I32, 1000L),
            Arguments.of(I16, I64, -1000L),
            Arguments.of(I32, I64, 1000000L),
            Arguments.of(ISIZE, I64, -1000000L)
        );
    }

    static Stream<Arguments> signedIntegerNarrowingConversions() {
        return Stream.of(
            Arguments.of(ISIZE, I8, 100L),
            Arguments.of(I64, I32, 1000L),
            Arguments.of(I64, I16, 100L),
            Arguments.of(I64, I8, 42L),
            Arguments.of(I32, I16, 1000L),
            Arguments.of(I32, I8, 100L),
            Arguments.of(I16, I8, 42L)
        );
    }

    static Stream<Arguments> unsignedIntegerConversions() {
        return Stream.of(
            Arguments.of(U8, U16, 200L),
            Arguments.of(U8, U32, 255L),
            Arguments.of(U8, U64, 100L),
            Arguments.of(USIZE, U16, 1000L),
            Arguments.of(U16, U32, 50000L),
            Arguments.of(U16, U64, 65535L),
            Arguments.of(U32, U64, 4000000000L),
            Arguments.of(U64, U32, 1000L),
            Arguments.of(U32, U16, 1000L),
            Arguments.of(U8, USIZE, 100L),
            Arguments.of(U16, U8, 200L)
        );
    }

    static Stream<Arguments> signedUnsignedMixedConversions() {
        return Stream.of(
            Arguments.of(I8, U8, 42L, true),
            Arguments.of(I16, U16, 1000L, true),
            Arguments.of(I32, U32, 1000000L, true),
            Arguments.of(I64, U64, 1000000000L, true),
            Arguments.of(I16, USIZE, 100L, true),
            Arguments.of(U8, I8, 100L, false),
            Arguments.of(U16, I16, 1000L, false),
            Arguments.of(U32, I32, 1000000L, false),
            Arguments.of(U64, I64, 1000000000L, false),
            Arguments.of(U16, ISIZE, 100L, false)
        );
    }

    static Stream<Arguments> signedIntToFloatConversions() {
        return Stream.of(
            Arguments.of(I8, F32, 42L),
            Arguments.of(I8, F64, -42L),
            Arguments.of(I16, F32, 1000L),
            Arguments.of(I16, F64, -1000L),
            Arguments.of(I32, F32, 1000000L),
            Arguments.of(I32, F64, -1000000L),
            Arguments.of(I64, F32, 1000000000L),
            Arguments.of(I64, F64, -1000000000L),
            Arguments.of(ISIZE, F32, 1000000L)
        );
    }

    static Stream<Arguments> unsignedIntToFloatConversions() {
        return Stream.of(
            Arguments.of(U8, F32, 200L),
            Arguments.of(U8, F64, 255L),
            Arguments.of(U16, F32, 50000L),
            Arguments.of(U16, F64, 65535L),
            Arguments.of(U32, F32, 3000000000L),
            Arguments.of(U32, F64, 4294967295L),
            Arguments.of(U64, F32, 1000000000000L),
            Arguments.of(U64, F64, -1L), // u64::MAX as unsigned
            Arguments.of(USIZE, F32, 1000000L)
        );
    }

    static Stream<Arguments> floatToSignedIntConversions() {
        return Stream.of(
            Arguments.of(F32, I8, 42.5),
            Arguments.of(F32, I16, 1000.7),
            Arguments.of(F32, I32, 1000000.3),
            Arguments.of(F32, I64, 1000000000.9),
            Arguments.of(F64, I8, 42.5),
            Arguments.of(F64, I16, 1000.7),
            Arguments.of(F64, I32, 1000000.3),
            Arguments.of(F64, I64, 1000000000.9),
            Arguments.of(F64, ISIZE, 1000000.3)
        );
    }

    static Stream<Arguments> floatToUnsignedIntConversions() {
        return Stream.of(
            Arguments.of(F32, U8, 200.5),
            Arguments.of(F32, U16, 50000.7),
            Arguments.of(F32, U32, 3000000000.3),
            Arguments.of(F32, U64, 1000000000000.9),
            Arguments.of(F64, U8, 200.5),
            Arguments.of(F64, U16, 50000.7),
            Arguments.of(F64, U32, 3000000000.3),
            Arguments.of(F64, U64, 1000000000000.9),
            Arguments.of(F64, USIZE, 1000000.3)
        );
    }

    static Stream<Arguments> floatToFloatConversions() {
        return Stream.of(
            Arguments.of(F32, F64, 3.14f),
            Arguments.of(F64, F32, 3.141592653589793)
        );
    }

    static Stream<Arguments> charConversions() {
        return Stream.of(
            // char to integers (should succeed - char is unsigned 16-bit)
            Arguments.of(CHAR, I16, 65L, true),   // 'A'
            Arguments.of(CHAR, U16, 65L, true),
            Arguments.of(CHAR, I32, 32768L, true), // > i16::MAX
            Arguments.of(CHAR, U32, 65535L, true), // char::MAX
            Arguments.of(CHAR, ISIZE, 65535L, true),

            // Integers to char (should succeed)
            Arguments.of(I16, CHAR, 65L, true),
            Arguments.of(U16, CHAR, 65L, true),
            Arguments.of(I32, CHAR, 65L, true),
            Arguments.of(U32, CHAR, 65L, true),
            Arguments.of(USIZE, CHAR, 65L, true),

            // char to float (should succeed)
            Arguments.of(CHAR, F32, 65L, true),
            Arguments.of(CHAR, F64, 65L, true),

            // Float to char (should succeed)
            Arguments.of(F32, CHAR, 65L, true),
            Arguments.of(F64, CHAR, 65L, true)
        );
    }

    static Stream<Arguments> incompatibleConversions() {
        return Stream.of(
            Arguments.of(BOOL, I32),
            Arguments.of(I32, BOOL),
            Arguments.of(BOOL, U32),
            Arguments.of(U32, BOOL),
            Arguments.of(BOOL, F32),
            Arguments.of(F32, BOOL),
            Arguments.of(UNIT, I32),
            Arguments.of(I32, UNIT),
            Arguments.of(NULL, I32),
            Arguments.of(I32, NULL),
            Arguments.of(UNIT, BOOL),
            Arguments.of(BOOL, UNIT),
            Arguments.of(NULL, BOOL),
            Arguments.of(BOOL, NULL),
            Arguments.of(USIZE, UNIT),
            Arguments.of(UNIT, ISIZE)
        );
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Creates a test value for the given type.
     */
    private IrValue createTestValue(ResoType type) {
        if (type.isInteger() || type.isChar()) {
            boolean isSigned = type.isSignedInteger();
            return IrFactory.createConstantInt(type.getType(), 42, isSigned);
        } else if (type.isFloatingPoint()) {
            return IrFactory.createConstantFloat(type.getType(), 3.14);
        } else if (type.isBool()) {
            return IrFactory.createConstantBool(irContext, true);
        } else if (type.getName().equals("String")) {
            IntegerType int8Type = typeSystem.getType(I8);
            IrType stringLlvmType = IrFactory.createPointerType(int8Type.getType(), 0);
            return IrFactory.createConstantNull(stringLlvmType);
        }
        return null; // Unsupported type for test value creation
    }
}