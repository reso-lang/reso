package com.reso.llvm.core;

import com.reso.llvm.api.IrType;
import com.reso.llvm.api.IrValue;
import com.reso.llvm.exception.IrException;
import java.math.BigInteger;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

/**
 * Implementation of ResoValue that wraps an LLVMValueRef.
 */
@Immutable
public class IrValueImpl implements IrValue {
    private final LLVMValueRef valueRef;
    private final IrTypeImpl type;

    /**
     * Creates a constant integer value.
     *
     * @param type       The integer type
     * @param value      The integer value
     * @param signExtend Whether to sign extend the value
     * @return A new constant integer value
     * @throws IllegalArgumentException if the type is not an integer type
     */
    @Nonnull
    public static IrValueImpl createConstantInt(
        @Nonnull IrTypeImpl type,
        long value,
        boolean signExtend) {

        Objects.requireNonNull(type, "Type cannot be null");

        if (!type.isIntegerType()) {
            throw new IllegalArgumentException("Type must be an integer type");
        }

        LLVMValueRef constInt = LLVM.LLVMConstInt(type.getLlvmType(), value, signExtend ? 1 : 0);
        return new IrValueImpl(constInt, type);
    }

    /**
     * Creates a constant integer value.
     *
     * @param type  The integer type
     * @param value The integer value
     * @return A new constant integer value
     * @throws IllegalArgumentException if the type is not an integer type
     */
    @Nonnull
    public static IrValueImpl createConstantInt(
        @Nonnull IrTypeImpl type,
        BigInteger value) {

        Objects.requireNonNull(type, "Type cannot be null");

        if (!type.isIntegerType()) {
            throw new IllegalArgumentException("Type must be an integer type");
        }

        LLVMValueRef constInt =
            LLVM.LLVMConstIntOfString(type.getLlvmType(), value.toString(), (byte) 10);
        return new IrValueImpl(constInt, type);
    }

    /**
     * Creates a constant boolean value.
     *
     * @param context The context to create the value in
     * @param value   The boolean value
     * @return A new constant boolean value
     */
    @Nonnull
    public static IrValueImpl createConstantBool(@Nonnull IrContextImpl context, boolean value) {
        Objects.requireNonNull(context, "Context cannot be null");

        IrTypeImpl boolType = IrTypeImpl.createBoolType(context);
        return createConstantInt(boolType, value ? 1 : 0, false);
    }

    /**
     * Creates a constant floating-point value.
     *
     * @param type  The floating-point type
     * @param value The floating-point value
     * @return A new constant floating-point value
     * @throws IllegalArgumentException if the type is not a floating-point type
     */
    @Nonnull
    public static IrValueImpl createConstantFloat(
        @Nonnull IrTypeImpl type,
        double value) {

        Objects.requireNonNull(type, "Type cannot be null");

        if (!type.isFloatingPointType()) {
            throw new IllegalArgumentException("Type must be a floating-point type");
        }

        LLVMValueRef constFloat = LLVM.LLVMConstReal(type.getLlvmType(), value);
        return new IrValueImpl(constFloat, type);
    }

    /**
     * Creates a constant null value.
     *
     * @param type The type
     * @return A new constant null value
     */
    @Nonnull
    public static IrValueImpl createConstantNull(@Nonnull IrTypeImpl type) {
        Objects.requireNonNull(type, "Type cannot be null");

        LLVMValueRef constNull = LLVM.LLVMConstNull(type.getLlvmType());
        return new IrValueImpl(constNull, type);
    }

    /**
     * Creates a constant string value.
     *
     * @param context        The context to create the value in
     * @param text           The string text
     * @param nullTerminated Whether the string should be null-terminated
     * @return A new constant string value
     */
    @Nonnull
    public static IrValueImpl createConstantString(
        @Nonnull IrContextImpl context,
        @Nonnull String text,
        boolean nullTerminated) {

        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(text, "Text cannot be null");

        byte[] textBytes = text.getBytes();
        int length = textBytes.length;

        // Create an array type for the string
        IrTypeImpl charType = IrTypeImpl.createi8Type(context);
        IrTypeImpl arrayType =
            IrTypeImpl.createArrayType(charType, length + (nullTerminated ? 1 : 0));

        LLVMValueRef constString = LLVM.LLVMConstStringInContext(
            context.getLlvmContext(),
            text,
            length,
            nullTerminated ? 0 : 1
        );

        return new IrValueImpl(constString, arrayType);
    }

    /**
     * Creates a global string constant.
     *
     * @param builder The builder to use
     * @param text    The string text
     * @return A new global string constant
     */
    @Nonnull
    public static IrValueImpl createGlobalString(
        @Nonnull IrBuilderImpl builder,
        @Nonnull String text) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(text, "Text cannot be null");

        LLVMValueRef globalStr = LLVM.LLVMBuildGlobalStringPtr(
            builder.getLlvmBuilder(),
            text,
            ""
        );

        IrContextImpl context = builder.getContext();
        IrTypeImpl int8Type = IrTypeImpl.createi8Type(context);
        IrTypeImpl stringType = IrTypeImpl.createPointerType(int8Type, 0);

        return new IrValueImpl(globalStr, stringType);
    }

    /**
     * Creates a constant all-ones value.
     *
     * @param type The type
     * @return A new constant all-ones value
     */
    @Nonnull
    public static IrValue createAllOnes(
        @Nonnull IrTypeImpl type) {
        Objects.requireNonNull(type, "Type cannot be null");

        LLVMValueRef allOnes = LLVM.LLVMConstAllOnes(type.getLlvmType());
        return new IrValueImpl(allOnes, type);
    }

    /**
     * Creates a constant struct value.
     * /**
     * Creates a constant struct value.
     *
     * @param type   The struct type
     * @param values The field values
     * @return A new constant struct value
     */
    @Nonnull
    public static IrValueImpl createConstantNamedStruct(
        @Nonnull IrTypeImpl type,
        @Nonnull IrValue[] values) {
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(values, "Values cannot be null");

        // Convert IRValue[] to LLVMValueRef[]
        LLVMValueRef[] valueRefs = new LLVMValueRef[values.length];
        for (int i = 0; i < values.length; i++) {
            Objects.requireNonNull(values[i], "Field value cannot be null");
            valueRefs[i] = ((IrValueImpl) values[i]).getLlvmValue();
        }

        LLVMValueRef constStruct;
        if (values.length == 0) {
            // For empty struct, use empty PointerPointer
            try (PointerPointer<LLVMValueRef> emptyVals = new PointerPointer<>(0)) {
                constStruct = LLVM.LLVMConstNamedStruct(type.getLlvmType(), emptyVals, 0);
            }
        } else {
            // Use PointerPointer for non-empty struct
            try (PointerPointer<LLVMValueRef> vals = new PointerPointer<>(valueRefs)) {
                constStruct = LLVM.LLVMConstNamedStruct(type.getLlvmType(), vals, values.length);
            }
        }

        return new IrValueImpl(constStruct, type);
    }

    /**
     * Wraps an existing LLVMValueRef with an explicitly provided type.
     *
     * @param valueRef The LLVM value reference to wrap
     * @param type     The type of this value
     */
    public IrValueImpl(@Nonnull LLVMValueRef valueRef, @Nonnull IrTypeImpl type) {
        this.valueRef = Objects.requireNonNull(valueRef, "Value reference cannot be null");
        this.type = Objects.requireNonNull(type, "Type cannot be null");
    }

    /**
     * Wraps an existing LLVMValueRef, inferring the type from the value.
     *
     * @param valueRef The LLVM value reference to wrap
     * @param context  The context this value belongs to
     */
    public IrValueImpl(@Nonnull LLVMValueRef valueRef, @Nonnull IrContextImpl context) {
        this.valueRef = Objects.requireNonNull(valueRef, "Value reference cannot be null");
        Objects.requireNonNull(context, "Context cannot be null");

        // Infer the type from the value
        LLVMTypeRef typeRef = LLVM.LLVMTypeOf(valueRef);
        this.type = new IrTypeImpl(typeRef, context);
    }

    /**
     * Gets the underlying LLVM value reference.
     *
     * @return The underlying LLVM value reference
     * @throws IllegalStateException if the context has been disposed
     */
    @Nonnull
    public LLVMValueRef getLlvmValue() {
        // Check if context is disposed
        if (type.getContext().isDisposed()) {
            throw new IllegalStateException("Context has been disposed");
        }
        return valueRef;
    }

    @Override
    @Nonnull
    public IrType getType() {
        return type;
    }

    @Override
    @Nonnull
    public IrContextImpl getContext() {
        return type.getContext();
    }

    @Override
    public boolean isConstant() {
        return LLVM.LLVMIsConstant(getLlvmValue()) != 0;
    }

    @Override
    public boolean isNull() {
        return LLVM.LLVMIsNull(getLlvmValue()) != 0;
    }

    @Override
    public BigInteger getConstantIntValue() {
        if (!isConstant() || !type.isIntegerType()) {
            throw new IllegalStateException("Not a constant integer");
        }
        BytePointer str = LLVM.LLVMPrintValueToString(getLlvmValue());
        try {
            String s = str.getString(); // e.g. "i128 12345..."
            int idx = s.indexOf(' ');
            String numPart = (idx != -1 ? s.substring(idx + 1) : s);
            return new BigInteger(numPart);
        } finally {
            LLVM.LLVMDisposeMessage(str);
        }
    }

    @Override
    public double getConstantFloatValue() {
        if (!isConstant() || !type.isFloatingPointType()) {
            throw new IllegalStateException("Not a constant float");
        }

        IntPointer losesInfo = new IntPointer(1);
        double value = LLVM.LLVMConstRealGetDouble(getLlvmValue(), losesInfo);

        if (losesInfo.get() != 0) {
            throw new IrException("Lost precision when converting constant float to double");
        }

        return value;
    }

    @Override
    public boolean isNumeric() {
        return type.isIntegerType() || type.isFloatingPointType();
    }

    @Override
    public boolean isFloatingPoint() {
        return type.isFloatingPointType();
    }

    @Override
    public boolean isInteger() {
        return type.isIntegerType();
    }

    @Override
    public boolean isBoolean() {
        return type.isIntegerType() && type.getIntBitWidth() == 1;
    }

    @Override
    public boolean isFunction() {
        return LLVM.LLVMGetValueKind(getLlvmValue()) == LLVM.LLVMFunctionValueKind;
    }

    @Override
    @Nonnull
    public String getName() {
        if (LLVM.LLVMGetValueKind(getLlvmValue()) == LLVM.LLVMFunctionValueKind) {
            return LLVM.LLVMGetValueName(getLlvmValue()).getString();
        }
        return "";
    }

    @Override
    @Nonnull
    public IrValue getParam(int index) {
        if (!isFunction()) {
            throw new IllegalStateException("Not a function: " + this);
        }

        LLVMValueRef paramValue = LLVM.LLVMGetParam(getLlvmValue(), index);
        if (paramValue == null) {
            throw new IndexOutOfBoundsException("Parameter index out of bounds: " + index);
        }

        return new IrValueImpl(paramValue, getContext());
    }

    @Override
    @Nonnull
    public String toString() {
        int kind = LLVM.LLVMGetValueKind(getLlvmValue());
        String kindStr;

        switch (kind) {
            case LLVM.LLVMArgumentValueKind:
                kindStr = "argument";
                break;
            case LLVM.LLVMBasicBlockValueKind:
                kindStr = "basic_block";
                break;
            case LLVM.LLVMFunctionValueKind:
                return "ResoValue[function=" + getName() + "]";
            case LLVM.LLVMGlobalAliasValueKind:
                kindStr = "global_alias";
                break;
            case LLVM.LLVMGlobalVariableValueKind:
                kindStr = "global_variable";
                break;
            case LLVM.LLVMConstantExprValueKind:
                kindStr = "constant_expr";
                break;
            case LLVM.LLVMConstantPointerNullValueKind:
                kindStr = "null";
                break;
            case LLVM.LLVMConstantIntValueKind:
                if (isConstant() && isInteger()) {
                    return "ResoValue[constant_int=" + getConstantIntValue() + "]";
                }
                kindStr = "constant_int";
                break;
            case LLVM.LLVMConstantFPValueKind:
                if (isConstant() && isFloatingPoint()) {
                    return "ResoValue[constant_float=" + getConstantFloatValue() + "]";
                }
                kindStr = "constant_float";
                break;
            default:
                kindStr = "value";
        }

        return "ResoValue[" + kindStr + ", type=" + type + "]";
    }
}