package com.reso.compiler.symbols.resources;

import com.reso.compiler.symbols.SymbolKind;
import com.reso.compiler.symbols.TypeSymbol;
import com.reso.compiler.symbols.Visibility;
import com.reso.compiler.types.ResourceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resource symbol implementation for representing resource types.
 */
public class ResourceSymbol extends TypeSymbol {
    private final List<FieldSymbol> fields;
    private final Visibility initializerVisibility;

    /**
     * Creates a new resource symbol.
     *
     * @param name           The resource name
     * @param resourceType   The resource type
     * @param fields         The resource fields
     * @param methods        The resource methods
     * @param fileIdentifier The source file name where this resource is defined
     */
    public ResourceSymbol(
        @Nonnull String name,
        @Nonnull ResourceType resourceType,
        @Nonnull List<FieldSymbol> fields,
        @Nonnull List<MethodSymbol> methods,
        @Nonnull String fileIdentifier) {
        super(name, resourceType, methods, fileIdentifier);
        this.fields = new ArrayList<>(Objects.requireNonNull(fields, "Fields cannot be null"));
        this.initializerVisibility = computeInitializerVisibility(fields);
    }

    @Override
    @Nonnull
    public ResourceType getType() {
        return (ResourceType) type;
    }

    @Override
    @Nonnull
    public SymbolKind getKind() {
        return SymbolKind.RESOURCE;
    }

    /**
     * Gets the fields of this resource.
     *
     * @return The resource fields
     */
    @Nonnull
    public List<FieldSymbol> getFields() {
        return Collections.unmodifiableList(fields);
    }

    /**
     * Gets the visibility of the resource initializer.
     * This is determined by the most restrictive visibility of all fields.
     *
     * @return The resource initializer visibility
     */
    @Nonnull
    public Visibility getInitializerVisibility() {
        return initializerVisibility;
    }

    /**
     * Finds a field by name.
     *
     * @param fieldName The field name to find
     * @return The field symbol, or null if not found
     */
    @Nullable
    public FieldSymbol findField(@Nonnull String fieldName) {
        Objects.requireNonNull(fieldName, "Field name cannot be null");
        return fields.stream()
            .filter(field -> field.getName().equals(fieldName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets the index of a field by name.
     *
     * @param fieldName The field name
     * @return The field index, or -1 if not found
     */
    public int getFieldIndex(@Nonnull String fieldName) {
        Objects.requireNonNull(fieldName, "Field name cannot be null");
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).getName().equals(fieldName)) {
                return i;
            }
        }
        return -1; // Not found
    }

    /**
     * Determines the resource initializer visibility based on field visibilities.
     * If at least one field is fileprivate, the initializer is fileprivate.
     * Otherwise, the initializer is global.
     *
     * @param fields The list of fields
     * @return The computed initializer visibility
     */
    @Nonnull
    private static Visibility computeInitializerVisibility(@Nonnull List<FieldSymbol> fields) {
        Objects.requireNonNull(fields, "Fields cannot be null");

        for (FieldSymbol field : fields) {
            if (field.getVisibility() == Visibility.FILEPRIVATE) {
                return Visibility.FILEPRIVATE;
            }
        }
        return Visibility.GLOBAL;
    }
}