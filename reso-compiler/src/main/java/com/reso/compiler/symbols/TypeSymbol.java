package com.reso.compiler.symbols;

import com.reso.compiler.symbols.resources.MethodSymbol;
import com.reso.compiler.types.ResoType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Type symbol implementation for representing types.
 */
public class TypeSymbol implements Symbol {
    private final String name;
    protected final ResoType type;
    protected final List<MethodSymbol> methods;
    protected final String fileIdentifier;

    public TypeSymbol(
        @Nonnull String name,
        @Nonnull ResoType type,
        @Nonnull List<MethodSymbol> methods,
        @Nonnull String fileIdentifier) {
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.methods = new ArrayList<>(Objects.requireNonNull(methods, "Methods cannot be null"));
        this.fileIdentifier =
            Objects.requireNonNull(fileIdentifier, "Source file name cannot be null");
    }

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public ResoType getType() {
        return type;
    }

    @Override
    @Nonnull
    public SymbolKind getKind() {
        return SymbolKind.TYPE;
    }

    /**
     * Gets the methods of this type.
     *
     * @return The methods
     */
    @Nonnull
    public List<MethodSymbol> getMethods() {
        return Collections.unmodifiableList(methods);
    }

    /**
     * Finds a method in a specific path.
     *
     * @param pathString The path string
     * @param methodName The method name
     * @return The method symbol, or null if not found
     */
    @Nullable
    public MethodSymbol findMethod(@Nonnull String pathString, @Nonnull String methodName) {
        Objects.requireNonNull(pathString, "Path string cannot be null");
        Objects.requireNonNull(methodName, "Method name cannot be null");

        return methods.stream()
            .filter(method -> method.getPathString().equals(pathString)
                && method.getName().equals(methodName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets the source file name where this type is defined.
     *
     * @return The source file name
     */
    @Nonnull
    public String getFileIdentifier() {
        return fileIdentifier;
    }
}