package com.reso.compiler.types;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Type-safe handle for ResoTypes. Provides compile-time safety for type lookups.
 *
 * @param <T> The specific ResoType subclass this handle represents
 */
public final class TypeHandle<T extends ResoType> {
    private final String name;
    private final Class<T> typeClass;
    private final int hashCode;

    private TypeHandle(@Nonnull String name, @Nonnull Class<T> typeClass) {
        this.name = Objects.requireNonNull(name, "Type name cannot be null");
        this.typeClass = Objects.requireNonNull(typeClass, "Type class cannot be null");
        this.hashCode = Objects.hash(name, typeClass);
    }

    /**
     * Creates a new TypeHandle for the specified type.
     */
    public static <T extends ResoType> TypeHandle<T> of(@Nonnull String name,
                                                        @Nonnull Class<T> typeClass) {
        return new TypeHandle<>(name, typeClass);
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public Class<T> getTypeClass() {
        return typeClass;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TypeHandle<?> that = (TypeHandle<?>) obj;
        return name.equals(that.name) && typeClass.equals(that.typeClass);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "TypeHandle{" + name + ":" + typeClass.getSimpleName() + "}";
    }
}