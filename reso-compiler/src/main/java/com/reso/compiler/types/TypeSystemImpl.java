package com.reso.compiler.types;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrType;
import com.reso.llvm.api.IrValue;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Implementation of the TypeSystem interface.
 */
public final class TypeSystemImpl implements TypeSystem {

    // Core components
    private final TypeRegistry typeRegistry;
    private final IrBuilder irBuilder;

    /**
     * Creates a TypeSystem with the provided registry and IR context.
     *
     * @param typeRegistry The type registry for managing types
     * @throws IllegalArgumentException if any parameter is null
     */
    public TypeSystemImpl(@Nonnull IrBuilder irBuilder, @Nonnull TypeRegistry typeRegistry) {
        this.typeRegistry = Objects.requireNonNull(typeRegistry, "TypeRegistry cannot be null");
        this.irBuilder = Objects.requireNonNull(irBuilder, "IRBuilder cannot be null");
    }

    // ============================================================================
    // Core Type Access Methods
    // ============================================================================

    @Override
    @Nonnull
    public <T extends ResoType> T getType(@Nonnull TypeHandle<T> handle) {
        try {
            return typeRegistry.getType(handle);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Critical type system error: " + e.getMessage(), e);
        }
    }

    // ============================================================================
    // Parser Integration
    // ============================================================================

    @Override
    @Nullable
    public ResoType resolveType(@Nonnull ResoParser.TypeContext typeContext,
                                @Nonnull ErrorReporter errorReporter) {
        Objects.requireNonNull(typeContext, "Type context cannot be null");
        Objects.requireNonNull(errorReporter, "Error reporter cannot be null");

        int line = typeContext.getStart().getLine();
        int column = typeContext.getStart().getCharPositionInLine();

        try {
            if (typeContext.primitiveType() != null) {
                String typeName = typeContext.primitiveType().getText();
                return resolveTypeByName(typeName, errorReporter, line, column);
            } else if (typeContext.referenceType() != null) {
                String typeName = typeContext.referenceType().Identifier().getText();
                return resolveTypeByName(typeName, errorReporter, line, column);
            } else if (typeContext.genericType() != null) {
                return resolveGenericType(typeContext.genericType(), errorReporter);
            } else if (typeContext.tupleType() != null) {
                String typeName = typeContext.tupleType().getText();
                return resolveTypeByName(typeName, errorReporter, line, column);
            } else {
                errorReporter.error("Invalid type syntax",
                    line, column);
                return null;
            }
        } catch (Exception e) {
            errorReporter.error("Internal error resolving type: " + e.getMessage(),
                line, column);
            return null;
        }
    }

    /**
     * Maps string type names to TypeHandles for parser integration.
     *
     * @param typeName      The string type name from the parser
     * @param errorReporter The error reporter
     * @param line          The line number for error reporting
     * @param column        The column number for error reporting
     * @return The resolved type or null if not found
     */
    @Nullable
    @Override
    public ResoType resolveTypeByName(@Nonnull String typeName,
                                      @Nonnull ErrorReporter errorReporter,
                                      int line, int column) {
        try {
            return switch (typeName) {
                // Integer types
                case "i8" -> getType(StandardTypeHandles.I8);
                case "i16" -> getType(StandardTypeHandles.I16);
                case "i32" -> getType(StandardTypeHandles.I32);
                case "i64" -> getType(StandardTypeHandles.I64);
                case "isize" -> getType(StandardTypeHandles.ISIZE);

                // Unsigned integer types
                case "u8" -> getType(StandardTypeHandles.U8);
                case "u16" -> getType(StandardTypeHandles.U16);
                case "u32" -> getType(StandardTypeHandles.U32);
                case "u64" -> getType(StandardTypeHandles.U64);
                case "usize" -> getType(StandardTypeHandles.USIZE);

                // Character type
                case "char" -> getType(StandardTypeHandles.CHAR);

                // Floating point types
                case "f32" -> getType(StandardTypeHandles.F32);
                case "f64" -> getType(StandardTypeHandles.F64);

                // Basic types
                case "bool" -> getType(StandardTypeHandles.BOOL);
                case "()" -> getType(StandardTypeHandles.UNIT);

                default -> {
                    ResoType resourceType = typeRegistry.getResourceType(typeName);

                    if (resourceType != null) {
                        yield resourceType;
                    }

                    errorReporter.error("Unknown type: " + typeName,
                        line,
                        column);
                    yield null;
                }
            };
        } catch (IllegalStateException e) {
            errorReporter.error("Type system error: " + e.getMessage(),
                line,
                column);
            return null;
        }
    }

    @Nullable
    public ResoType resolveGenericType(@Nonnull ResoParser.GenericTypeContext genericTypeContext,
                                       @Nonnull ErrorReporter errorReporter) {
        Objects.requireNonNull(genericTypeContext, "Generic type context cannot be null");
        Objects.requireNonNull(errorReporter, "Error reporter cannot be null");

        int line = genericTypeContext.getStart().getLine();
        int column = genericTypeContext.getStart().getCharPositionInLine();

        try {
            String baseTypeName = genericTypeContext.Identifier().getText();
            List<ResoParser.TypeContext> typeParams = genericTypeContext.type();

            if ("Vector".equals(baseTypeName)) {
                if (typeParams.size() != 1) {
                    errorReporter.error(
                        "Vector requires exactly one type parameter, got " + typeParams.size(),
                        line, column);
                    return null;
                }

                ResoType elementType = resolveType(typeParams.getFirst(), errorReporter);
                if (elementType == null) {
                    return null; // Error already reported
                }

                return typeRegistry.getOrCreateVectorType(irBuilder.getContext(), elementType);
            }

            errorReporter.error("Unknown generic type: " + baseTypeName, line, column);
            return null;

        } catch (Exception e) {
            errorReporter.error("Internal error resolving generic type: " + e.getMessage(),
                line, column);
            return null;
        }
    }

    // ============================================================================
    // Type Conversion Methods
    // ============================================================================

    @Override
    @Nullable
    public ConcreteResoValue createConversion(@Nonnull ConcreteResoValue source,
                                              @Nonnull ResoType targetType,
                                              @Nonnull ErrorReporter errorReporter,
                                              int errorLine,
                                              int errorColumn) {
        Objects.requireNonNull(source, "Source value cannot be null");
        Objects.requireNonNull(targetType, "Target type cannot be null");
        Objects.requireNonNull(errorReporter, "Error reporter cannot be null");

        ResoType sourceType = source.getType();

        // No conversion needed if types are the same
        if (sourceType.equals(targetType)) {
            return source;
        }

        try {
            // Handle numeric conversions (with potential data loss)
            if ((sourceType.isNumeric() || sourceType.isChar())
                && (targetType.isNumeric() || targetType.isChar())) {
                return handleNumericConversion(source, targetType, errorReporter, errorLine,
                    errorColumn);
            }

            // Handle other conversions (e.g., pointer casts, etc.)
            // Add more conversion types as needed

            errorReporter.error(
                "Cannot convert from " + sourceType.getName() + " to " + targetType.getName(),
                errorLine, errorColumn);
            return null;

        } catch (Exception e) {
            errorReporter.error("Internal error during conversion: " + e.getMessage(),
                errorLine, errorColumn);
            return null;
        }
    }

    @Nullable
    @Override
    public ResourceType createResourceType(@Nonnull String resourceName,
                                           @Nullable IrType resourcePointerType,
                                           @Nullable IrType resourceStructType) {
        return this.createResourceType(resourceName, resourcePointerType, resourceStructType,
            List.of());
    }

    @Nullable
    @Override
    public ResourceType createResourceType(@Nonnull String resourceName,
                                           @Nullable IrType resourcePointerType,
                                           @Nullable IrType resourceStructType,
                                           @Nonnull List<ResoType> genericTypes) {
        Objects.requireNonNull(resourceName, "Resource name cannot be null");
        Objects.requireNonNull(genericTypes, "Generic types cannot be null");

        // Check if the resource type already exists
        if (typeRegistry.hasResourceType(resourceName)) {
            return typeRegistry.getResourceType(resourceName);
        }

        // Create a new resource type
        ResourceType resourceType =
            new ResourceType(resourceName, resourcePointerType, resourceStructType, genericTypes);
        typeRegistry.registerResourceType(resourceType);
        return resourceType;
    }

    @Nullable
    @Override
    public ResourceType getResourceType(@Nonnull String resourceName) {
        Objects.requireNonNull(resourceName, "Resource name cannot be null");
        return typeRegistry.getResourceType(resourceName);
    }

    @Nonnull
    @Override
    public ResourceType getOrCreateVectorType(@Nonnull ResoType elementType) {
        Objects.requireNonNull(elementType, "Element type cannot be null");
        return typeRegistry.getOrCreateVectorType(irBuilder.getContext(), elementType);
    }

    @Nullable
    @Override
    public ResourceType getVectorType(@Nonnull ResoType elementType) {
        Objects.requireNonNull(elementType, "Element type cannot be null");
        return typeRegistry.getVectorType(elementType);
    }

    // ============================================================================
    // Private Conversion Helper Methods
    // ============================================================================

    /**
     * Handles numeric conversions that may involve data loss.
     */
    @Nullable
    private ConcreteResoValue handleNumericConversion(@Nonnull ConcreteResoValue source,
                                                      @Nonnull ResoType targetType,
                                                      @Nonnull ErrorReporter errorReporter,
                                                      int errorLine, int errorColumn) {

        ResoType sourceType = source.getType();
        IrValue sourceValue = source.getValue();

        // Integer to integer conversion (including signed/unsigned and narrowing/widening)
        if (isIntegerOrCharType(sourceType) && isIntegerOrCharType(targetType)) {
            IrValue convertedValue = createIntegerConversion(sourceValue, sourceType, targetType);
            return new ConcreteResoValue(targetType, convertedValue, errorLine, errorColumn);
        }

        // Float to float conversion
        if (isFloatingPointType(sourceType) && isFloatingPointType(targetType)) {
            IrValue convertedValue = createFloatConversion(sourceValue, sourceType, targetType);
            return new ConcreteResoValue(targetType, convertedValue, errorLine, errorColumn);
        }

        // Integer to float conversion
        if (isIntegerOrCharType(sourceType) && isFloatingPointType(targetType)) {
            IrValue convertedValue;
            if (isUnsignedIntegerOrCharType(sourceType)) {
                convertedValue =
                    IrFactory.createUIToFP(irBuilder, sourceValue, targetType.getType(),
                        "uitofp_tmp");
            } else {
                convertedValue =
                    IrFactory.createSIToFP(irBuilder, sourceValue, targetType.getType(),
                        "sitofp_tmp");
            }
            return new ConcreteResoValue(targetType, convertedValue, errorLine, errorColumn);
        }

        // Float to integer conversion (truncation)
        if (isFloatingPointType(sourceType) && isIntegerOrCharType(targetType)) {
            IrValue convertedValue;
            if (isUnsignedIntegerOrCharType(targetType)) {
                convertedValue =
                    IrFactory.createFPToUI(irBuilder, sourceValue, targetType.getType(),
                        "fptoui_tmp");
            } else {
                convertedValue =
                    IrFactory.createFPToSI(irBuilder, sourceValue, targetType.getType(),
                        "fptosi_tmp");
            }
            return new ConcreteResoValue(targetType, convertedValue, errorLine, errorColumn);
        }

        errorReporter.error(
            "Cannot convert from " + sourceType.getName() + " to " + targetType.getName(),
            errorLine, errorColumn);

        return null;
    }

    // ============================================================================
    // Helper Methods for Type Classification
    // ============================================================================

    private boolean isFloatingPointType(ResoType type) {
        return type.isFloatingPoint();
    }

    private boolean isIntegerOrCharType(ResoType type) {
        return type.isInteger() || type.isChar();
    }

    private boolean isUnsignedIntegerOrCharType(ResoType type) {
        return type.isUnsignedInteger() || type.isChar();
    }

    // ============================================================================
    // IR Generation Helper Methods
    // ============================================================================

    private IrValue createIntegerConversion(IrValue source, ResoType sourceType,
                                            ResoType targetType) {
        int sourceBits = sourceType.getBitWidth();
        int targetBits = targetType.getBitWidth();

        boolean sourceUnsigned = isUnsignedIntegerOrCharType(sourceType);

        if (targetBits > sourceBits) {
            // Widening conversion - choose extension type based on source signedness
            if (sourceUnsigned) {
                return IrFactory.createZExt(irBuilder, source, targetType.getType(), "zext_tmp");
            } else {
                return IrFactory.createSExt(irBuilder, source, targetType.getType(), "sext_tmp");
            }
        } else if (targetBits < sourceBits) {
            // Narrowing conversion (truncation) - same for both signed and unsigned
            return IrFactory.createTrunc(irBuilder, source, targetType.getType(), "trunc_tmp");
        } else {
            // Same width - might be signed/unsigned reinterpretation
            return source;
        }
    }

    private IrValue createFloatConversion(IrValue source, ResoType sourceType,
                                          ResoType targetType) {
        int sourceBits = sourceType.getBitWidth();
        int targetBits = targetType.getBitWidth();

        if (targetBits > sourceBits) {
            // Widening conversion
            return IrFactory.createFPExt(irBuilder, source, targetType.getType(), "fpext_tmp");
        } else if (targetBits < sourceBits) {
            // Narrowing conversion
            return IrFactory.createFPTrunc(irBuilder, source, targetType.getType(), "fptrunc_tmp");
        } else {
            // Same precision
            return source;
        }
    }
}