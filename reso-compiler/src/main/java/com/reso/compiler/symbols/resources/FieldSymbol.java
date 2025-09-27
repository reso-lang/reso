package com.reso.compiler.symbols.resources;

import com.reso.compiler.symbols.Symbol;
import com.reso.compiler.symbols.SymbolKind;
import com.reso.compiler.symbols.Visibility;
import com.reso.compiler.types.ResoType;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Symbol for resource fields (data components).
 */
public class FieldSymbol implements Symbol {
    private final String name;
    private final ResoType fieldType;
    private final boolean isConstant;
    private final Visibility visibility;

    /**
     * Creates a new resource field symbol.
     *
     * @param name       The field name
     * @param fieldType  The field type
     * @param isConstant Whether the field is constant (const vs var)
     * @param visibility The visibility of the field
     */
    public FieldSymbol(
        @Nonnull String name,
        @Nonnull ResoType fieldType,
        boolean isConstant,
        @Nonnull Visibility visibility) {
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.fieldType = Objects.requireNonNull(fieldType, "Field type cannot be null");
        this.isConstant = isConstant;
        this.visibility = Objects.requireNonNull(visibility, "Visibility cannot be null");
    }

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public ResoType getType() {
        return fieldType;
    }

    @Override
    @Nonnull
    public SymbolKind getKind() {
        return SymbolKind.FIELD;
    }

    /**
     * Checks if this field is constant.
     *
     * @return true if the field is constant (const), false if variable (var)
     */
    public boolean isConstant() {
        return isConstant;
    }

    /**
     * Gets the visibility of this field.
     *
     * @return The visibility
     */
    @Nonnull
    public Visibility getVisibility() {
        return visibility;
    }
}