package com.reso.compiler.codegen.expressions;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.symbols.resources.FieldSymbol;
import com.reso.compiler.symbols.resources.ResourceSymbol;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.types.ResourceType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrType;
import com.reso.llvm.api.IrValue;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for resource initializer expressions (ResourceName{...} syntax).
 * This class handles the validation and generation of LLVM IR code for resource
 * initializer expressions. Resource initializers can be used anywhere expressions
 * are allowed and create new instances of resources with field initialization
 * using heap allocation (malloc).
 */
public class ResourceInitializerGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new resource initializer generator.
     *
     * @param context The code generation context
     */
    public ResourceInitializerGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a resource initializer expression.
     *
     * @param initCtx The resource initializer expression context
     * @return The created resource value, or null if an error occurred
     */
    @Nullable
    public ResoValue generateResourceInitializer(
        @Nonnull ResoParser.ResourceInitializerExprContext initCtx) {
        Objects.requireNonNull(initCtx, "Resource initializer context cannot be null");

        int line = initCtx.getStart().getLine();
        int column = initCtx.getStart().getCharPositionInLine();

        // Check if we're in a function context
        if (context.getCurrentFunction() == null) {
            context.getErrorReporter().error(
                "Resource initializer must be used within a function",
                line,
                column);
            return null;
        }

        // Extract resource name
        String resourceName = initCtx.Identifier().getText();

        // Find the resource symbol
        ResourceSymbol resourceSymbol = context.getSymbolTable().findResource(resourceName);
        if (resourceSymbol == null) {
            context.getErrorReporter().error(
                "Resource '" + resourceName + "' not found",
                line,
                column);
            return null;
        }

        // Check visibility access
        if (!checkInitializerVisibility(resourceSymbol, line, column)) {
            return null;
        }

        // Extract initialization expressions
        List<ResoParser.ExpressionContext> initExpressions = extractInitializerExpressions(initCtx);
        List<FieldSymbol> fields = resourceSymbol.getFields();

        // Validate field count
        if (!validateFieldCount(initCtx, initExpressions, fields, resourceName)) {
            return null;
        }

        // Create resource instance
        return createResourceInstance(resourceSymbol, initExpressions, line, column);
    }

    /**
     * Checks if the resource initializer is accessible from the current context.
     */
    private boolean checkInitializerVisibility(@Nonnull ResourceSymbol resourceSymbol, int line,
                                               int column) {
        boolean accessAllowed = context.getCurrentAccessContext().canAccess(
            resourceSymbol.getInitializerVisibility(),
            resourceSymbol.getFileIdentifier());

        if (!accessAllowed) {
            context.getErrorReporter().error(
                "Resource initializer for '" + resourceSymbol.getName() + "' with "
                    + resourceSymbol.getInitializerVisibility().name().toLowerCase()
                    + " visibility is not accessible from current context",
                line,
                column);
        }

        return accessAllowed;
    }

    /**
     * Extracts initialization expressions from the resource initializer.
     */
    @Nonnull
    private List<ResoParser.ExpressionContext> extractInitializerExpressions(
        @Nonnull ResoParser.ResourceInitializerExprContext initCtx) {

        if (initCtx.expressionList() == null) {
            return List.of();
        }

        return initCtx.expressionList().expression();
    }

    /**
     * Validates that the number of initialization expressions matches the number of fields.
     */
    private boolean validateFieldCount(
        @Nonnull ResoParser.ResourceInitializerExprContext initCtx,
        @Nonnull List<ResoParser.ExpressionContext> initExpressions,
        @Nonnull List<FieldSymbol> fields,
        @Nonnull String resourceName) {

        if (initExpressions.size() != fields.size()) {
            context.getErrorReporter().error(
                "Resource initializer for '" + resourceName + "' must provide values for all "
                    + fields.size() + " fields, but got " + initExpressions.size() + " values",
                initCtx.getStart().getLine(),
                initCtx.getStart().getCharPositionInLine());
            return false;
        }

        return true;
    }

    /**
     * Creates a new resource instance using heap allocation and initializes all fields.
     */
    @Nullable
    private ResoValue createResourceInstance(
        @Nonnull ResourceSymbol resourceSymbol,
        @Nonnull List<ResoParser.ExpressionContext> initExpressions,
        int line, int column) {

        ResourceType resourceType = resourceSymbol.getType();

        // Allocate memory for the resource instance on the heap using malloc
        IrValue resourceInstance = allocateResourceOnHeap(resourceSymbol, line, column);
        if (resourceInstance == null) {
            return null;
        }

        // Initialize each field
        List<FieldSymbol> fields = resourceSymbol.getFields();
        for (int i = 0; i < fields.size(); i++) {
            FieldSymbol field = fields.get(i);
            ResoParser.ExpressionContext initExpr = initExpressions.get(i);

            if (!initializeField(resourceSymbol, field, initExpr, resourceInstance, i)) {
                return null;
            }
        }

        // Create and return the resource value
        return new ResoValue(resourceType, resourceInstance, line, column);
    }

    /**
     * Allocates memory for a resource instance on the heap using malloc.
     */
    @Nullable
    private IrValue allocateResourceOnHeap(@Nonnull ResourceSymbol resourceSymbol, int line,
                                           int column) {
        try {
            // Get the underlying struct type
            ResourceType resourceType = resourceSymbol.getType();
            IrType structType = resourceType.getStructType();

            if (isAtomic(resourceSymbol)) {
                return IrFactory.createGCMallocAtomic(
                    context.getIrBuilder(),
                    context.getIrModule(),
                    structType,
                    IrFactory.declareGCMallocAtomic(context.getIrModule()),
                    resourceSymbol.getName() + "_instance"
                );
            } else {
                return IrFactory.createGCMalloc(
                    context.getIrBuilder(),
                    context.getIrModule(),
                    structType,
                    IrFactory.declareGCMalloc(context.getIrModule()),
                    resourceSymbol.getName() + "_instance"
                );
            }
        } catch (Exception e) {
            context.getErrorReporter().error(
                "Failed to allocate memory for resource instance: " + e.getMessage(),
                line,
                column);
            return null;
        }
    }

    /**
     * Determines if a resource contains only atomic (pointer-free) data.
     */
    private boolean isAtomic(@Nonnull ResourceSymbol resourceSymbol) {
        // Check if all fields are primitive types (no pointers/references)
        return resourceSymbol.getFields().stream()
            .allMatch(field -> isPrimitiveType(field.getType()));
    }

    /**
     * Checks if a type is primitive (contains no pointers).
     */
    private boolean isPrimitiveType(@Nonnull ResoType type) {
        // All numeric types, bool, char are atomic
        return !type.isReference();
    }

    /**
     * Initializes a single field with the given expression using proper LLVM struct GEP.
     */
    private boolean initializeField(
        @Nonnull ResourceSymbol resourceSymbol,
        @Nonnull FieldSymbol field,
        @Nonnull ResoParser.ExpressionContext initExpr,
        @Nonnull IrValue resourceInstance,
        int fieldIndex) {

        int line = initExpr.getStart().getLine();
        int column = initExpr.getStart().getCharPositionInLine();

        // Generate the initialization value
        ResoValue initValue = context.getExpressionGenerator().visit(initExpr);
        if (initValue == null) {
            context.getErrorReporter().error(
                "Failed to generate initialization expression for field: " + field.getName(),
                line,
                column);
            return false;
        }

        // Type check and concretize the value
        ConcreteResoValue concretized =
            initValue.concretize(field.getType(), context.getErrorReporter());
        if (concretized == null) {
            context.getErrorReporter().error(
                "Cannot initialize field '" + field.getName() + "' of type "
                    + field.getType().getName() + " with value of type "
                    + initValue.getType().getName(),
                line,
                column);
            return false;
        }

        // Create struct GEP to access the field
        IrValue fieldPtr = IrFactory.createStructGEP(
            context.getIrBuilder(),
            resourceSymbol.getType().getStructType(),
            resourceInstance,
            fieldIndex,
            field.getName() + "_ptr"
        );

        // Store the initialization value into the field
        IrFactory.createStore(context.getIrBuilder(), concretized.getValue(), fieldPtr);

        return true;
    }
}