package com.reso.llvm.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public interface for LLVM type operations.
 */
public interface IrType {

    /**
     * Gets the context this type belongs to.
     *
     * @return The context
     */
    @Nonnull
    IrContext getContext();

    /**
     * Gets the bit width of an integer type.
     *
     * @return The bit width, or -1 if this is not an integer type
     */
    int getIntBitWidth();

    /**
     * Gets the element type of this type if it's a pointer, array, or vector type.
     *
     * @return The element type, or null if this is not a pointer, array, or vector type
     */
    @Nullable
    IrType getElementType();

    /**
     * Gets the return type of this type if it's a function type.
     *
     * @return The return type, or null if this is not a function type
     */
    @Nullable
    IrType getReturnType();

    /**
     * Gets the number of parameters of this type if it's a function type.
     *
     * @return The number of parameters, or -1 if this is not a function type
     */
    int getParamCount();

    /**
     * Gets the parameter types of this type if it's a function type.
     *
     * @return The parameter types, or null if this is not a function type
     */
    @Nullable
    IrType[] getParamTypes();

    /**
     * Checks if this is an integer type.
     *
     * @return true if this is an integer type
     */
    boolean isIntegerType();

    /**
     * Checks if this is a floating-point type.
     *
     * @return true if this is a floating-point type
     */
    boolean isFloatingPointType();

    /**
     * Checks if this is a pointer type.
     *
     * @return true if this is a pointer type
     */
    boolean isPointerType();

    /**
     * Checks if this is an array type.
     *
     * @return true if this is an array type
     */
    boolean isArrayType();

    /**
     * Checks if this is a vector type.
     *
     * @return true if this is a vector type
     */
    boolean isVectorType();

    /**
     * Checks if this is a function type.
     *
     * @return true if this is a function type
     */
    boolean isFunctionType();

    /**
     * Checks if this is a struct type.
     *
     * @return true if this is a struct type
     */
    boolean isStructType();
}