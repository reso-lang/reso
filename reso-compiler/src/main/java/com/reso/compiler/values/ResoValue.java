package com.reso.compiler.values;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.llvm.api.IrValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents a value in the Reso language.
 */
public class ResoValue {

    protected final ResoType type;
    protected final ResoType defaultType;
    protected final IrValue value;
    protected int line;
    protected int column;

    public ResoValue(@Nonnull ResoType type, @Nullable IrValue value, int line, int column) {
        this(type, type, value, line, column);
    }

    public ResoValue(@Nonnull ResoType type, @Nullable ResoType defaultType, @Nullable IrValue value, int line, int column) {
        this.type = requireNonNull(type, "Type cannot be null");
        this.defaultType = defaultType;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    /**
     * Gets the type of this value.
     *
     * @return The ResoType of this value
     */
    @Nonnull
    public ResoType getType() {
        return type;
    }

    /**
     * Gets the default type of this value.
     *
     * @return The default ResoType of this value
     */
    @Nullable
    public ResoType getDefaultType() {
        return defaultType;
    }

    /**
     * Gets the name of the type as a string.
     *
     * @return The type name
     */
    @Nonnull
    public String getTypeName() {
        return type.getName();
    }

    /**
     * Checks if this value is untyped.
     *
     * @return true if this is an untyped value, false otherwise
     */
    public boolean isUntyped() {
        return type.isUntyped();
    }

    /**
     * Attempts to concretize this value to the target type.
     *
     * @param targetType    The concrete type to convert to
     * @param errorReporter For reporting conversion errors
     * @return A concrete ResoValue of the target type, or null if conversion failed
     */
    @Nullable
    public ConcreteResoValue concretize(@Nonnull ResoType targetType,
                                        @Nonnull ErrorReporter errorReporter) {
        if (canConcretizeTo(targetType)) {
            return new ConcreteResoValue(type, defaultType, value, line, column);
        } else {
            errorReporter.error(
                    "Cannot convert value of type " + type.getName() + " to type " + targetType.getName(),
                    line, column
            );
            return null;
        }
    }

    /**
     * Checks if this untyped value can be converted to the target type without creating IR.
     *
     * @param targetType The target type to check compatibility with
     * @return true if conversion is possible, false otherwise
     */
    public boolean canConcretizeTo(@Nonnull ResoType targetType) {
        if (targetType.equals(type)) {
            return true;
        }

        return type.isNull() && targetType.isReference();
    }

    /**
     * Concretizes this value to its default type.
     *
     * @param errorReporter For reporting conversion errors
     * @return A concrete ResoValue of the default type, or null if conversion failed
     */
    public ConcreteResoValue concretizeToDefault(@Nonnull ErrorReporter errorReporter) {
        return concretize(defaultType, errorReporter);
    }

    /**
     * Checks if this value has an integer type.
     *
     * @return true if the type is an integer type
     */
    public boolean isInteger() {
        return type.isInteger();
    }

    /**
     * Checks if this value has a floating-point type.
     *
     * @return true if the type is a floating-point type
     */
    public boolean isFloatingPoint() {
        return type.isFloatingPoint();
    }

    /**
     * Checks if this value has a numeric type (integer or floating-point).
     *
     * @return true if the type is numeric
     */
    public boolean isNumeric() {
        return type.isNumeric();
    }

    /**
     * Checks if this value has a signed integer type.
     *
     * @return true if the type is a signed integer
     */
    public boolean isSignedInteger() {
        return type.isSignedInteger();
    }

    /**
     * Checks if this value has an unsigned integer type.
     *
     * @return true if the type is an unsigned integer
     */
    public boolean isUnsignedInteger() {
        return type.isUnsignedInteger();
    }

    /**
     * Checks if this value has a boolean type.
     *
     * @return true if the type is boolean
     */
    public boolean isBool() {
        return type.isBool();
    }

    /**
     * Checks if this value has a character type.
     *
     * @return true if the type is a character
     */
    public boolean isChar() {
        return type.isChar();
    }

    /**
     * Checks if this value has a string type.
     *
     * @return true if the type is a string
     */
    public boolean isString() {
        return type.isString();
    }

    /**
     * Checks if this is a null value.
     *
     * @return true if the type is null
     */
    public boolean isNull() {
        return type.isNull();
    }

    /**
     * Checks if this value is a reference type.
     *
     * @return true if the type is a reference
     */
    public boolean isReference() {
        return type.isReference();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResoValue resoValue = (ResoValue) o;
        return line == resoValue.line && column == resoValue.column && Objects.equals(type, resoValue.type) && Objects.equals(value, resoValue.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, line, column);
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}