package com.reso.llvm.api;

import java.math.BigInteger;
import javax.annotation.Nonnull;

/**
 * Public interface for LLVM value operations.
 * A value represents an LLVM instruction, constant, or global variable.
 */
public interface IrValue {

    /**
     * Gets the type of this value.
     *
     * @return The type
     */
    @Nonnull
    IrType getType();

    /**
     * Gets the context this value belongs to.
     *
     * @return The context
     */
    @Nonnull
    IrContext getContext();

    /**
     * Checks if this value is a constant.
     *
     * @return true if this value is a constant
     */
    boolean isConstant();

    /**
     * Checks if this value is a null constant.
     *
     * @return true if this value is a null constant
     */
    boolean isNull();

    /**
     * Gets the integer value of a constant integer.
     *
     * @return The integer value
     * @throws IllegalStateException if this is not a constant integer
     */
    BigInteger getConstantIntValue();

    /**
     * Gets the floating-point value of a constant float.
     *
     * @return The floating-point value
     * @throws IllegalStateException if this is not a constant float
     */
    double getConstantFloatValue();

    /**
     * Checks if this value has a numeric type.
     *
     * @return true if the type is numeric
     */
    boolean isNumeric();

    /**
     * Checks if this value has a floating-point type.
     *
     * @return true if the type is floating-point
     */
    boolean isFloatingPoint();

    /**
     * Checks if this value has an integer type.
     *
     * @return true if the type is an integer
     */
    boolean isInteger();

    /**
     * Checks if this value has a boolean type (i1).
     *
     * @return true if the type is boolean
     */
    boolean isBoolean();

    /**
     * Checks if this value is a function.
     *
     * @return true if this value is a function
     */
    boolean isFunction();

    /**
     * Gets the name of this value if it has one.
     *
     * @return The name, or an empty string if it doesn't have a name
     */
    @Nonnull
    String getName();

    /**
     * Gets a parameter from a function.
     *
     * @param index The parameter index
     * @return The parameter value
     * @throws IllegalStateException     if this value is not a function
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    @Nonnull
    IrValue getParam(int index);
}