package com.reso.compiler.types;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.literals.FloatingPointLiteralType;
import com.reso.compiler.types.literals.IntegerLiteralType;
import com.reso.compiler.types.primary.BooleanType;
import com.reso.compiler.types.primary.CharType;
import com.reso.compiler.types.primary.FloatingPointType;
import com.reso.compiler.types.primary.IntegerType;
import com.reso.compiler.types.primary.NullType;
import com.reso.compiler.types.primary.UnitType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.api.IrType;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface for the Reso type system.
 */
public interface TypeSystem {

    /**
     * Gets a type by its type-safe handle.
     *
     * @param handle The type handle
     * @return The type instance with correct compile-time type
     * @throws IllegalStateException if type is not registered
     */
    @Nonnull
    <T extends ResoType> T getType(@Nonnull TypeHandle<T> handle);

    /**
     * Gets the default integer type (i32).
     */
    @Nonnull
    default IntegerType getDefaultIntType() {
        return getType(StandardTypeHandles.I32);
    }

    /**
     * Gets the default floating-point type (f64).
     */
    @Nonnull
    default FloatingPointType getDefaultFloatType() {
        return getType(StandardTypeHandles.F64);
    }

    /**
     * Gets the boolean type.
     */
    @Nonnull
    default BooleanType getBoolType() {
        return getType(StandardTypeHandles.BOOL);
    }

    /**
     * Gets the char type.
     */
    @Nonnull
    default CharType getCharType() {
        return getType(StandardTypeHandles.CHAR);
    }

    /**
     * Gets the unit type.
     */
    @Nonnull
    default UnitType getUnitType() {
        return getType(StandardTypeHandles.UNIT);
    }

    /**
     * Gets the null type.
     */
    @Nonnull
    default NullType getNullType() {
        return getType(StandardTypeHandles.NULL);
    }

    /**
     * Gets the integer literal type.
     */
    @Nonnull
    default IntegerLiteralType getIntegerLiteralType() {
        return getType(StandardTypeHandles.INTEGER_LITERAL);
    }

    /**
     * Gets the floating-point literal type.
     */
    @Nonnull
    default FloatingPointLiteralType getFloatingPointLiteralType() {
        return getType(StandardTypeHandles.FLOATING_POINT_LITERAL);
    }

    /**
     * Resolves a type from a parser type context.
     */
    @Nullable
    ResoType resolveType(@Nonnull ResoParser.TypeContext typeContext,
                         @Nonnull ErrorReporter errorReporter);

    /**
     * Resolves a type from a parser type context.
     */
    @Nullable
    ResoType resolveTypeByName(@Nonnull String typeName, @Nonnull ErrorReporter errorReporter,
                               int line, int column);

    /**
     * Creates an explicit conversion.
     */
    @Nullable
    ConcreteResoValue createConversion(
        @Nonnull ConcreteResoValue source,
        @Nonnull ResoType targetType,
        @Nonnull ErrorReporter errorReporter,
        int errorLine,
        int errorColumn);

    /**
     * Creates or gets a resource type.
     *
     * @param resourceName        The name of the resource
     * @param resourcePointerType The LLVM pointer type for the resource
     * @param resourceStructType  The LLVM struct type for the resource
     * @return The resource type
     */
    @Nullable
    ResourceType createResourceType(@Nonnull String resourceName,
                                    @Nullable IrType resourcePointerType,
                                    @Nullable IrType resourceStructType);

    /**
     * Creates or gets a resource type with generics.
     *
     * @param resourceName        The name of the resource
     * @param resourcePointerType The LLVM pointer type for the resource
     * @param resourceStructType  The LLVM struct type for the resource
     * @param genericTypes        The list of generic type parameters
     * @return The resource type
     */
    ResourceType createResourceType(@Nonnull String resourceName,
                                    @Nullable IrType resourcePointerType,
                                    @Nullable IrType resourceStructType,
                                    @Nonnull List<ResoType> genericTypes);

    /**
     * Gets a resource type by name.
     *
     * @param resourceName The name of the resource
     * @return The resource type, or null if not found
     */
    @Nullable
    ResourceType getResourceType(@Nonnull String resourceName);

    /**
     * Creates or gets a vector type for the given element type.
     *
     * @param elementType The element type
     * @return The vector type, or null if element type is invalid
     */
    @Nonnull
    ResourceType getOrCreateVectorType(@Nonnull ResoType elementType);

    /**
     * Gets a vector type for the given element type if it exists.
     *
     * @param elementType The element type
     * @return The vector type, or null if not found
     */
    @Nullable
    ResourceType getVectorType(@Nonnull ResoType elementType);
}