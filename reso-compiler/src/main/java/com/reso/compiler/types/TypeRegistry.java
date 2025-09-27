package com.reso.compiler.types;

import com.reso.compiler.types.literals.FloatingPointLiteralType;
import com.reso.compiler.types.literals.IntegerLiteralType;
import com.reso.compiler.types.primary.BooleanType;
import com.reso.compiler.types.primary.CharType;
import com.reso.compiler.types.primary.FloatingPointType;
import com.reso.compiler.types.primary.IntegerType;
import com.reso.compiler.types.primary.NullType;
import com.reso.compiler.types.primary.UnitType;
import com.reso.compiler.types.primary.UnsignedIntegerType;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrContext;
import com.reso.llvm.api.IrModule;
import com.reso.llvm.api.IrType;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Thread-safe registry for mapping TypeHandles to ResoType instances.
 * This is the core of the type system, providing fast, type-safe access to types.
 */
public final class TypeRegistry {
    private final ConcurrentMap<TypeHandle<?>, ResoType> typeMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ResourceType> resourceTypes = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResoType, ResourceType> vectorTypes = new ConcurrentHashMap<>();

    /**
     * Registers a type with its handle.
     *
     * @param handle The type handle
     * @param type   The type instance
     * @throws IllegalArgumentException if handle is already registered
     */
    public <T extends ResoType> void registerType(@Nonnull TypeHandle<T> handle, @Nonnull T type) {
        Objects.requireNonNull(handle, "TypeHandle cannot be null");
        Objects.requireNonNull(type, "ResoType cannot be null");

        // Verify type class matches handle
        if (!handle.getTypeClass().isInstance(type)) {
            throw new IllegalArgumentException(
                "Type " + type.getClass().getSimpleName()
                    + " does not match handle type " + handle.getTypeClass().getSimpleName());
        }

        // Verify name matches
        if (!handle.getName().equals(type.getName())) {
            throw new IllegalArgumentException(
                "Type name '" + type.getName()
                    + "' does not match handle name '" + handle.getName() + "'");
        }

        ResoType existing = typeMap.putIfAbsent(handle, type);
        if (existing != null) {
            throw new IllegalArgumentException("TypeHandle already registered: " + handle);
        }
    }

    /**
     * Registers a resource type by its name.
     *
     * @param type The resource type instance
     * @throws IllegalArgumentException if the resource type is already registered
     */
    public void registerResourceType(@Nonnull ResourceType type) {
        Objects.requireNonNull(type, "ResourceType cannot be null");

        ResourceType existing = resourceTypes.putIfAbsent(type.getName(), type);
        if (existing != null) {
            throw new IllegalArgumentException(
                "Resource type already registered: " + type.getName());
        }
    }

    /**
     * Gets a resource type by its name.
     *
     * @param resourceName The resource name
     * @return The resource type instance
     */
    @Nullable
    public ResourceType getResourceType(@Nonnull String resourceName) {
        Objects.requireNonNull(resourceName, "Resource name cannot be null");
        return resourceTypes.get(resourceName);
    }

    /**
     * Checks if a resource type is registered by its name.
     *
     * @param resourceName The resource name
     * @return true if the resource type is registered, false otherwise
     */
    public boolean hasResourceType(@Nonnull String resourceName) {
        Objects.requireNonNull(resourceName, "Resource name cannot be null");
        return resourceTypes.containsKey(resourceName);
    }

    @Nonnull
    public ResourceType getOrCreateVectorType(@Nonnull IrContext irContext,
                                              @Nonnull ResoType elementType) {
        Objects.requireNonNull(elementType, "Element type cannot be null");

        return vectorTypes.computeIfAbsent(elementType, et -> createVectorType(irContext, et));
    }

    @Nullable
    public ResourceType getVectorType(@Nonnull ResoType elementType) {
        Objects.requireNonNull(elementType, "Element type cannot be null");
        return vectorTypes.get(elementType);
    }

    /**
     * Creates a new vector type for the given element type.
     *
     * @param irContext   The IR context
     * @param elementType The element type
     * @return The new vector type
     */
    @Nonnull
    private ResourceType createVectorType(@Nonnull IrContext irContext,
                                          @Nonnull ResoType elementType) {
        String vectorTypeName = "Vector<" + elementType.getName() + ">";

        IrType elementIrType = elementType.getType();
        IrType elementPointerType = IrFactory.createPointerType(elementIrType, 0);  // T*
        IrType usizeType = getType(StandardTypeHandles.USIZE).getType();

        // Create struct type: { T* elements, usize size, usize capacity }
        IrType[] structFields = {elementPointerType, usizeType, usizeType};
        IrType vectorStructType = IrFactory.createStructType(irContext, vectorTypeName);
        IrFactory.setStructBody(vectorStructType, structFields);

        // Vectors are passed as pointers to the struct
        IrType vectorPointerType = IrFactory.createPointerType(vectorStructType, 0);

        // Create the type instance
        return new ResourceType("Vector", vectorPointerType, vectorStructType,
            List.of(elementType));
    }

    /**
     * Gets a type by its handle with compile-time type safety.
     *
     * @param handle The type handle
     * @return The type instance
     * @throws IllegalStateException if type is not registered
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public <T extends ResoType> T getType(@Nonnull TypeHandle<T> handle) {
        Objects.requireNonNull(handle, "TypeHandle cannot be null");

        ResoType type = typeMap.get(handle);
        if (type == null) {
            throw new IllegalStateException("Type not registered: " + handle);
        }

        // This cast is safe because registerType ensures type correctness
        return (T) type;
    }

    /**
     * Creates a TypeRegistry with all standard types pre-registered.
     */
    @Nonnull
    public static TypeRegistry createWithStandardTypes(@Nonnull IrModule irModule) {
        Objects.requireNonNull(irModule, "IRModule cannot be null");

        IrContext irContext = irModule.getContext();
        TypeRegistry registry = new TypeRegistry();

        try {
            // Integer types
            IrType int8Type = IrFactory.createi8Type(irContext);
            registry.registerType(StandardTypeHandles.I8,
                new IntegerType("i8", int8Type, 8));

            IrType int16Type = IrFactory.createi16Type(irContext);
            registry.registerType(StandardTypeHandles.I16,
                new IntegerType("i16", int16Type, 16));

            IrType int32Type = IrFactory.createi32Type(irContext);
            registry.registerType(StandardTypeHandles.I32,
                new IntegerType("i32", int32Type, 32));

            IrType int64Type = IrFactory.createi64Type(irContext);
            registry.registerType(StandardTypeHandles.I64,
                new IntegerType("i64", int64Type, 64));

            IrType isizeType = IrFactory.createisizeType(irModule);
            registry.registerType(StandardTypeHandles.ISIZE,
                new IntegerType("isize", isizeType, IrFactory.getTargetPointerSize(irModule)));

            IrType uint8Type = IrFactory.createi8Type(irContext);
            registry.registerType(StandardTypeHandles.U8,
                new UnsignedIntegerType("u8", uint8Type, 8));

            IrType uint16Type = IrFactory.createi16Type(irContext);
            registry.registerType(StandardTypeHandles.U16,
                new UnsignedIntegerType("u16", uint16Type, 16));

            IrType uint32Type = IrFactory.createi32Type(irContext);
            registry.registerType(StandardTypeHandles.U32,
                new UnsignedIntegerType("u32", uint32Type, 32));

            IrType uint64Type = IrFactory.createi64Type(irContext);
            registry.registerType(StandardTypeHandles.U64,
                new UnsignedIntegerType("u64", uint64Type, 64));

            IrType usizeType = IrFactory.createusizeType(irModule);
            registry.registerType(StandardTypeHandles.USIZE,
                new UnsignedIntegerType("usize", usizeType,
                    IrFactory.getTargetPointerSize(irModule)));

            // Floating point types
            IrType float32Type = IrFactory.createf32Type(irContext);
            registry.registerType(StandardTypeHandles.F32,
                new FloatingPointType("f32", float32Type, 32));

            IrType float64Type = IrFactory.createf64Type(irContext);
            registry.registerType(StandardTypeHandles.F64,
                new FloatingPointType("f64", float64Type, 64));

            // Basic types
            IrType boolType = IrFactory.createBoolType(irContext);
            registry.registerType(StandardTypeHandles.BOOL,
                new BooleanType(boolType));

            IrType charType = IrFactory.createi32Type(irContext);
            registry.registerType(StandardTypeHandles.CHAR,
                new CharType(charType));

            // Unit type is represented as an empty struct {} in LLVM
            IrType unitType = IrFactory.createStructType(irContext, "unit");
            IrFactory.setStructBody(unitType, new IrType[0]); // Empty struct
            registry.registerType(StandardTypeHandles.UNIT,
                new UnitType(unitType));

            IrType int8ForPtr = IrFactory.createi8Type(irContext);
            IrType nullType = IrFactory.createPointerType(int8ForPtr, 0);
            registry.registerType(StandardTypeHandles.NULL,
                new NullType(nullType));

            // Untyped literal types
            registry.registerType(StandardTypeHandles.INTEGER_LITERAL,
                new IntegerLiteralType("IntegerLiteral"));
            registry.registerType(StandardTypeHandles.FLOATING_POINT_LITERAL,
                new FloatingPointLiteralType("FloatingPointLiteral"));

        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize standard types", e);
        }

        return registry;
    }
}