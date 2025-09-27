package com.reso.compiler.types;

import com.reso.llvm.api.IrType;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Base interface for all Reso types.
 */
public interface ResoType {
    /**
     * Gets the name of the type.
     *
     * @return The type name
     */
    @Nonnull
    String getName();

    /**
     * Gets the IR representation of this type.
     *
     * @return The IR type
     */
    @Nonnull
    IrType getType();

    /**
     * Gets the list of generic type parameters if this type is generic.
     * For non-generic types, this returns an empty list.
     *
     * @return List of generic type parameters
     */
    @Nonnull
    List<ResoType> getGenericTypes();

    /**
     * Checks if this type is numeric.
     *
     * @return true if this type is numeric (integer, char or floating-point)
     */
    boolean isNumeric();

    /**
     * Checks if this type is a floating-point type.
     *
     * @return true if this type is a floating-point type
     */
    boolean isFloatingPoint();

    /**
     * Checks if this type is an integer type.
     *
     * @return true if this type is an integer type
     */
    boolean isInteger();

    /**
     * Checks if this type is an unsigned integer type.
     *
     * @return true if this type is an unsigned integer type
     */
    boolean isUnsignedInteger();

    /**
     * Checks if this type is a signed integer type.
     *
     * @return true if this type is a signed integer type
     */
    boolean isSignedInteger();

    /**
     * Checks if this type is a boolean type.
     *
     * @return true if this type is a boolean type
     */
    boolean isBool();

    /**
     * Checks if this type is a string type.
     *
     * @return true if this type is a string type
     */
    boolean isString();

    /**
     * Checks if this type is a char type.
     *
     * @return true if this type is a char type
     */
    boolean isChar();

    /**
     * Checks if this type is untyped.
     *
     * @return true if this type is untyped
     */
    boolean isUntyped();

    /**
     * Gets the bit width for integer types.
     *
     * @return The bit width, or -1 if not an integer type
     */
    int getBitWidth();

    /**
     * Checks if this type is a reference type.
     *
     * @return true if this type is a reference type
     */
    boolean isReference();

    /**
     * Checks if this type is a null type.
     *
     * @return true if this type is a null type
     */
    boolean isNull();

    /**
     * Checks if this type is generic (e.g., Array<T>).
     *
     * @return true if this type is generic
     */
    boolean isGeneric();

    /**
     * Checks if this type is the unit type.
     *
     * @return true if this type is unit
     */
    boolean isUnit();
}