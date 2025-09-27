package com.reso.compiler.types;

import com.reso.llvm.api.IrType;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Abstract base class for Reso types with common functionality.
 */
public abstract class AbstractResoType implements ResoType {
    private final String name;
    protected final IrType irType;

    /**
     * Creates a new abstract Reso type.
     *
     * @param name   The type name
     * @param irType The IR type
     */
    protected AbstractResoType(@Nonnull String name, @Nullable IrType irType) {
        this.name = Objects.requireNonNull(name, "Type name cannot be null");
        this.irType = irType;
    }

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Override
    @Nonnull
    public IrType getType() {
        if (irType == null) {
            throw new IllegalStateException("IR type is not set for type: " + name);
        }
        return irType;
    }

    // Default implementations that return false
    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isFloatingPoint() {
        return false;
    }

    @Override
    public boolean isInteger() {
        return false;
    }

    @Override
    public boolean isUnsignedInteger() {
        return false;
    }

    @Override
    public boolean isSignedInteger() {
        return isInteger() && !isUnsignedInteger();
    }

    @Override
    public boolean isBool() {
        return false;
    }

    @Override
    public boolean isString() {
        return false;
    }

    @Override
    public boolean isChar() {
        return false;
    }

    @Override
    public boolean isUntyped() {
        return false;
    }

    @Override
    public int getBitWidth() {
        return -1; // Default for non-integer types
    }

    @Override
    public boolean isReference() {
        return false; // Default for non-reference types
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isGeneric() {
        return false;
    }

    @Override
    public boolean isUnit() {
        return false;
    }

    @Override
    @Nonnull
    public List<ResoType> getGenericTypes() {
        return List.of();
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ResoType other)) {
            return false;
        }
        return getName().equals(other.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}