package com.reso.compiler.types;

import com.reso.llvm.api.IrType;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Implementation of resource type for user-defined resources.
 */
public class ResourceType extends AbstractResoType {
    private final String resourceName;
    private final IrType structType;
    private final List<ResoType> genericTypes;

    /**
     * Creates a new resource type.
     *
     * @param resourceName The name of the resource
     * @param irType       The IR type (pointer to resource struct)
     * @param structType   The IR struct type
     */
    public ResourceType(@Nonnull String resourceName, @Nullable IrType irType,
                        @Nullable IrType structType) {
        this(resourceName, irType, structType, List.of());
    }

    /**
     * Creates a new resource type with generics.
     *
     * @param resourceName The name of the resource
     * @param irType       The IR type (pointer to resource struct)
     * @param structType   The IR struct type
     * @param genericTypes The list of generic type parameters
     */
    ResourceType(@Nonnull String resourceName, @Nullable IrType irType, @Nullable IrType structType,
                 @Nonnull List<ResoType> genericTypes) {
        super(resourceName, irType);
        this.resourceName = Objects.requireNonNull(resourceName, "Resource name cannot be null");
        this.structType = structType;
        this.genericTypes = Objects.requireNonNull(genericTypes, "Generic types cannot be null");
    }

    @Nonnull
    public IrType getStructType() {
        if (structType == null) {
            throw new IllegalStateException("Struct type is not set");
        }
        return structType;
    }

    /**
     * Resource types are not numeric.
     *
     * @return false
     */
    @Override
    public boolean isNumeric() {
        return false;
    }

    /**
     * Resource types are reference types.
     *
     * @return true
     */
    @Override
    public boolean isReference() {
        return true;
    }

    @Override
    public boolean isUntyped() {
        return this.irType == null;
    }

    @Override
    @Nonnull
    public List<ResoType> getGenericTypes() {
        return genericTypes;
    }

    @Override
    public String toString() {
        return "ResourceType{" + resourceName + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ResourceType that = (ResourceType) o;
        return Objects.equals(resourceName, that.resourceName)
            && Objects.equals(structType, that.structType)
            && Objects.equals(genericTypes, that.genericTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), resourceName, structType, genericTypes);
    }
}