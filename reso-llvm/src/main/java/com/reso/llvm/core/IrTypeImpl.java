package com.reso.llvm.core;

import com.reso.llvm.api.IrType;
import com.reso.llvm.exception.IrException;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMTargetDataRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.global.LLVM;

/**
 * Implementation of ResoType that wraps an LLVMTypeRef.
 */
@Immutable
public class IrTypeImpl implements IrType {
    private final LLVMTypeRef typeRef;
    private final IrContextImpl context;

    /**
     * Creates a new integer type with the specified bit width.
     *
     * @param context The context to create the type in
     * @param bits    The bit width of the integer type
     * @return A new integer type
     */
    @Nonnull
    public static IrTypeImpl createIntType(@Nonnull IrContextImpl context, int bits) {
        Objects.requireNonNull(context, "Context cannot be null");
        if (bits <= 0) {
            throw new IllegalArgumentException("Bit width must be positive");
        }

        LLVMTypeRef typeRef = LLVM.LLVMIntTypeInContext(context.getLlvmContext(), bits);
        return new IrTypeImpl(typeRef, context);
    }

    /**
     * Creates a boolean (i1) type.
     *
     * @param context The context to create the type in
     * @return A new boolean type
     */
    @Nonnull
    public static IrTypeImpl createBoolType(@Nonnull IrContextImpl context) {
        return createIntType(context, 1);
    }

    /**
     * Creates an 8-bit integer type.
     *
     * @param context The context to create the type in
     * @return A new 8-bit integer type
     */
    @Nonnull
    public static IrTypeImpl createi8Type(@Nonnull IrContextImpl context) {
        return createIntType(context, 8);
    }

    /**
     * Creates a 16-bit integer type.
     *
     * @param context The context to create the type in
     * @return A new 16-bit integer type
     */
    @Nonnull
    public static IrTypeImpl createi16Type(@Nonnull IrContextImpl context) {
        return createIntType(context, 16);
    }

    /**
     * Creates a 32-bit integer type.
     *
     * @param context The context to create the type in
     * @return A new 32-bit integer type
     */
    @Nonnull
    public static IrTypeImpl createi32Type(@Nonnull IrContextImpl context) {
        return createIntType(context, 32);
    }

    /**
     * Creates a 64-bit integer type.
     *
     * @param context The context to create the type in
     * @return A new 64-bit integer type
     */
    @Nonnull
    public static IrTypeImpl createi64Type(@Nonnull IrContextImpl context) {
        return createIntType(context, 64);
    }

    /**
     * Creates a 32-bit floating-point type.
     *
     * @param context The context to create the type in
     * @return A new 32-bit floating-point type
     */
    @Nonnull
    public static IrTypeImpl createf32Type(@Nonnull IrContextImpl context) {
        Objects.requireNonNull(context, "Context cannot be null");

        LLVMTypeRef typeRef = LLVM.LLVMFloatTypeInContext(context.getLlvmContext());
        return new IrTypeImpl(typeRef, context);
    }

    /**
     * Creates a 64-bit floating-point type.
     *
     * @param context The context to create the type in
     * @return A new 64-bit floating-point type
     */
    @Nonnull
    public static IrTypeImpl createf64Type(@Nonnull IrContextImpl context) {
        Objects.requireNonNull(context, "Context cannot be null");

        LLVMTypeRef typeRef = LLVM.LLVMDoubleTypeInContext(context.getLlvmContext());
        return new IrTypeImpl(typeRef, context);
    }

    /**
     * Creates a void type.
     *
     * @param context The context to create the type in
     * @return A new void type
     */
    @Nonnull
    public static IrTypeImpl createVoidType(@Nonnull IrContextImpl context) {
        Objects.requireNonNull(context, "Context cannot be null");

        LLVMTypeRef typeRef = LLVM.LLVMVoidTypeInContext(context.getLlvmContext());
        return new IrTypeImpl(typeRef, context);
    }

    /**
     * Creates a pointer type.
     *
     * @param elementType  The element type of the pointer
     * @param addressSpace The address space (use 0 for default)
     * @return A new pointer type
     */
    @Nonnull
    public static IrTypeImpl createPointerType(@Nonnull IrTypeImpl elementType, int addressSpace) {
        Objects.requireNonNull(elementType, "Element type cannot be null");

        LLVMTypeRef typeRef = LLVM.LLVMPointerType(elementType.getLlvmType(), addressSpace);
        return new IrTypeImpl(typeRef, elementType.getContext());
    }

    /**
     * Creates a function type.
     *
     * @param returnType The return type of the function
     * @param paramTypes The parameter types of the function
     * @param isVarArg   Whether the function is variadic
     * @return A new function type
     */
    @Nonnull
    public static IrTypeImpl createFunctionType(
        @Nonnull IrTypeImpl returnType,
        @Nonnull IrTypeImpl[] paramTypes,
        boolean isVarArg) {

        Objects.requireNonNull(returnType, "Return type cannot be null");
        Objects.requireNonNull(paramTypes, "Parameter types cannot be null");

        IrContextImpl context = returnType.getContext();

        // Check all param types have the same context
        for (IrTypeImpl paramType : paramTypes) {
            if (paramType == null) {
                throw new IllegalArgumentException("Parameter type cannot be null");
            }
            if (paramType.getContext() != context) {
                throw new IllegalArgumentException("All types must belong to the same context");
            }
        }

        try {
            // Create LLVM type refs array for parameters
            LLVMTypeRef[] paramTypeRefs = new LLVMTypeRef[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                paramTypeRefs[i] = paramTypes[i].getLlvmType();
            }

            // Create function type
            LLVMTypeRef functionType;
            if (paramTypes.length == 0) {
                try (PointerPointer<LLVMTypeRef> emptyParams = new PointerPointer<>(0)) {
                    functionType = LLVM.LLVMFunctionType(
                        returnType.getLlvmType(),
                        emptyParams,  // Empty pointer instead of null
                        0,
                        0     // Not varargs
                    );
                }
            } else {
                // Use temporary native array
                PointerPointer<LLVMTypeRef> params = new PointerPointer<>(paramTypeRefs);
                try {
                    functionType = LLVM.LLVMFunctionType(
                        returnType.getLlvmType(),
                        params,
                        paramTypes.length,
                        isVarArg ? 1 : 0
                    );
                } finally {
                    params.deallocate();
                }
            }

            return new IrTypeImpl(functionType, context);
        } catch (Exception e) {
            throw new IrException("Failed to create function type: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an array type.
     *
     * @param elementType The element type of the array
     * @param numElements The number of elements in the array
     * @return A new array type
     */
    @Nonnull
    public static IrTypeImpl createArrayType(@Nonnull IrTypeImpl elementType, int numElements) {
        Objects.requireNonNull(elementType, "Element type cannot be null");
        if (numElements < 0) {
            throw new IllegalArgumentException("Number of elements cannot be negative");
        }

        LLVMTypeRef typeRef = LLVM.LLVMArrayType2(elementType.getLlvmType(), numElements);
        return new IrTypeImpl(typeRef, elementType.getContext());
    }

    /**
     * Creates a struct type.
     *
     * @param context The context to create the type in
     * @param name    The name of the struct
     * @return A new struct type
     */
    @Nonnull
    public static IrTypeImpl createStructType(
        @Nonnull IrContextImpl context,
        @Nonnull String name) {

        Objects.requireNonNull(context, "Context cannot be null");

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Struct name cannot be empty");
        }

        try {
            LLVMTypeRef structType;

            // Create named struct type
            structType = LLVM.LLVMStructCreateNamed(context.getLlvmContext(), name);

            return new IrTypeImpl(structType, context);

        } catch (Exception e) {
            throw new IrException("Failed to create struct type: " + name, e);
        }
    }

    /**
     * Sets the body of an existing struct type.
     *
     * @param structType The struct type to modify
     * @param fieldTypes The field types of the struct
     * @throws IllegalArgumentException if the struct type is not a valid struct
     *                                  or if field types are invalid
     */
    public static void setStructBody(
        @Nonnull IrTypeImpl structType,
        @Nonnull IrTypeImpl[] fieldTypes) {

        Objects.requireNonNull(structType, "Struct type cannot be null");
        Objects.requireNonNull(fieldTypes, "Field types cannot be null");

        if (!structType.isStructType()) {
            throw new IllegalArgumentException("Provided type is not a struct type");
        }

        // Check all field types have the same context
        for (IrTypeImpl fieldType : fieldTypes) {
            if (fieldType == null) {
                throw new IllegalArgumentException("Field type cannot be null");
            }
            if (fieldType.getContext() != structType.getContext()) {
                throw new IllegalArgumentException("All types must belong to the same context");
            }
        }

        try {
            LLVMTypeRef[] fieldTypeRefs = new LLVMTypeRef[fieldTypes.length];
            for (int i = 0; i < fieldTypes.length; i++) {
                fieldTypeRefs[i] = fieldTypes[i].getLlvmType();
            }

            try (PointerPointer<LLVMTypeRef> fields = new PointerPointer<>(fieldTypeRefs)) {
                LLVM.LLVMStructSetBody(structType.getLlvmType(), fields, fieldTypes.length, 0);
            }
        } catch (Exception e) {
            throw new IrException("Failed to set struct body: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an architecture-dependent signed integer type (isize).
     * This type is 32-bit on 32-bit architectures and 64-bit on 64-bit architectures.
     *
     * @param module The module to determine the target architecture
     * @return A new architecture-dependent signed integer type
     */
    @Nonnull
    public static IrTypeImpl createisizeType(@Nonnull IrModuleImpl module) {
        int pointerSize = getTargetPointerSize(module);
        return createIntType((IrContextImpl) module.getContext(), pointerSize);
    }

    /**
     * Creates an architecture-dependent unsigned integer type (usize).
     * This type is 32-bit on 32-bit architectures and 64-bit on 64-bit architectures.
     *
     * @param module The module to determine the target architecture
     * @return A new architecture-dependent unsigned integer type
     */
    @Nonnull
    public static IrTypeImpl createusizeType(@Nonnull IrModuleImpl module) {
        int pointerSize = getTargetPointerSize(module);
        return createIntType((IrContextImpl) module.getContext(), pointerSize);
    }

    public static int getTargetPointerSize(@Nonnull IrModuleImpl module) {
        try {
            LLVMTargetDataRef targetData = LLVM.LLVMGetModuleDataLayout(module.getLlvmModule());
            int pointerSizeBytes = LLVM.LLVMPointerSize(targetData);
            return pointerSizeBytes * 8;
        } catch (Exception e) {
            return 64; // Fallback
        }
    }

    /**
     * Wraps an existing LLVMTypeRef.
     *
     * @param typeRef The LLVM type reference to wrap
     * @param context The context this type belongs to
     */
    public IrTypeImpl(@Nonnull LLVMTypeRef typeRef, @Nonnull IrContextImpl context) {
        this.typeRef = Objects.requireNonNull(typeRef, "Type reference cannot be null");
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Gets the underlying LLVM type reference.
     *
     * @return The underlying LLVM type reference
     */
    @Nonnull
    public LLVMTypeRef getLlvmType() {
        if (context.isDisposed()) {
            throw new IllegalStateException("Context has been disposed");
        }
        return typeRef;
    }

    @Override
    @Nonnull
    public IrContextImpl getContext() {
        return context;
    }

    @Override
    public int getIntBitWidth() {
        if (isIntegerType()) {
            return LLVM.LLVMGetIntTypeWidth(getLlvmType());
        }
        return -1;
    }

    @Override
    @Nullable
    public IrType getElementType() {
        if (isPointerType() || isArrayType() || isVectorType()) {
            LLVMTypeRef elementTypeRef = LLVM.LLVMGetElementType(getLlvmType());
            return new IrTypeImpl(elementTypeRef, context);
        }
        return null;
    }

    @Override
    @Nullable
    public IrType getReturnType() {
        if (isFunctionType()) {
            LLVMTypeRef returnTypeRef = LLVM.LLVMGetReturnType(getLlvmType());
            return new IrTypeImpl(returnTypeRef, context);
        }
        return null;
    }

    @Override
    public int getParamCount() {
        if (isFunctionType()) {
            return LLVM.LLVMCountParamTypes(getLlvmType());
        }
        return -1;
    }

    @Override
    @Nullable
    public IrType[] getParamTypes() {
        if (!isFunctionType()) {
            return null;
        }

        int paramCount = getParamCount();
        if (paramCount == 0) {
            return new IrType[0];
        }

        IrType[] paramTypes = new IrType[paramCount];
        LLVMTypeRef[] paramTypeRefs = new LLVMTypeRef[paramCount];

        try (PointerPointer<LLVMTypeRef> paramTypesPointer = new PointerPointer<>(paramCount)) {
            LLVM.LLVMGetParamTypes(getLlvmType(), paramTypesPointer);

            for (int i = 0; i < paramCount; i++) {
                paramTypeRefs[i] = paramTypesPointer.get(LLVMTypeRef.class, i);
                paramTypes[i] = new IrTypeImpl(paramTypeRefs[i], context);
            }

            return paramTypes;
        }
    }

    @Override
    public boolean isIntegerType() {
        return LLVM.LLVMGetTypeKind(getLlvmType()) == LLVM.LLVMIntegerTypeKind;
    }

    @Override
    public boolean isFloatingPointType() {
        int kind = LLVM.LLVMGetTypeKind(getLlvmType());
        return kind == LLVM.LLVMFloatTypeKind
            || kind == LLVM.LLVMDoubleTypeKind
            || kind == LLVM.LLVMHalfTypeKind;
    }

    @Override
    public boolean isPointerType() {
        return LLVM.LLVMGetTypeKind(getLlvmType()) == LLVM.LLVMPointerTypeKind;
    }

    @Override
    public boolean isArrayType() {
        return LLVM.LLVMGetTypeKind(getLlvmType()) == LLVM.LLVMArrayTypeKind;
    }

    @Override
    public boolean isVectorType() {
        return LLVM.LLVMGetTypeKind(getLlvmType()) == LLVM.LLVMVectorTypeKind;
    }

    @Override
    public boolean isFunctionType() {
        return LLVM.LLVMGetTypeKind(getLlvmType()) == LLVM.LLVMFunctionTypeKind;
    }

    @Override
    public boolean isStructType() {
        return LLVM.LLVMGetTypeKind(getLlvmType()) == LLVM.LLVMStructTypeKind;
    }

    @Override
    @Nonnull
    public String toString() {
        int kind = LLVM.LLVMGetTypeKind(getLlvmType());
        String kindStr = switch (kind) {
            case LLVM.LLVMVoidTypeKind -> "void";
            case LLVM.LLVMHalfTypeKind -> "half";
            case LLVM.LLVMFloatTypeKind -> "float";
            case LLVM.LLVMDoubleTypeKind -> "double";
            case LLVM.LLVMIntegerTypeKind -> "i" + getIntBitWidth();
            case LLVM.LLVMFunctionTypeKind -> "function";
            case LLVM.LLVMPointerTypeKind -> "pointer";
            case LLVM.LLVMArrayTypeKind -> "array";
            case LLVM.LLVMStructTypeKind -> "struct";
            default -> "unknown";
        };

        return "ResoType[" + kindStr + "]";
    }
}