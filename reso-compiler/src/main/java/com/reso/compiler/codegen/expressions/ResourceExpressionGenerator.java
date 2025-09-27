package com.reso.compiler.codegen.expressions;

import com.reso.compiler.codegen.common.CallGeneratorUtils;
import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.symbols.Parameter;
import com.reso.compiler.symbols.TypeSymbol;
import com.reso.compiler.symbols.resources.FieldSymbol;
import com.reso.compiler.symbols.resources.MethodSymbol;
import com.reso.compiler.symbols.resources.ResourceSymbol;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for resource-related expressions (field access and method calls).
 * This class handles the code generation for accessing fields and calling methods
 * on resource types, including validation of visibility and type safety.
 */
public class ResourceExpressionGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new resource expression generator.
     *
     * @param context The code generation context
     */
    public ResourceExpressionGenerator(
        @Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a field access expression.
     *
     * @param expr The field access expression
     * @return The result of the field access
     */
    @Nullable
    public ResoValue generateFieldAccess(@Nonnull ResoParser.FieldAccessExprContext expr,
                                         @Nonnull ResoValue objectValue) {
        int line = expr.getStart().getLine();
        int column = expr.getStart().getCharPositionInLine();

        // Validate object
        if (!validateObjectExpression(objectValue, line, column)) {
            return null;
        }

        ConcreteResoValue concreteObjectValue =
            objectValue.concretizeToDefault(context.getErrorReporter());
        if (concreteObjectValue == null) {
            context.getErrorReporter()
                .error("Cannot concretize object to default type for field access", line, column);
            return null;
        }

        String fieldName = expr.Identifier().getText();

        // Find and validate the resource
        ResourceSymbol resourceSymbol =
            findAndValidateResourceSymbol(concreteObjectValue.getType(), line, column);
        if (resourceSymbol == null) {
            return null;
        }

        // Find and validate the field
        FieldSymbol field = findAndValidateField(resourceSymbol, fieldName, line, column);
        if (field == null) {
            return null;
        }

        // Check field visibility
        if (!checkFieldVisibility(field, resourceSymbol, line, column)) {
            return null;
        }

        return generateFieldAccess(concreteObjectValue, resourceSymbol, field, fieldName, line,
            column);
    }

    /**
     * Generates the actual field access code.
     */
    @Nonnull
    private ResoValue generateFieldAccess(@Nonnull ConcreteResoValue objectValue,
                                          @Nonnull ResourceSymbol resourceSymbol,
                                          @Nonnull FieldSymbol field,
                                          @Nonnull String fieldName,
                                          int line, int column) {
        int fieldIndex = resourceSymbol.getFieldIndex(fieldName);
        if (fieldIndex == -1) {
            throw new IllegalStateException(
                "Field '" + fieldName + "' not found in resource " + resourceSymbol.getName());
        }

        IrBuilder builder = context.getIrBuilder();

        // Create struct GEP to access the field
        IrValue fieldPtr = IrFactory.createStructGEP(
            builder,
            resourceSymbol.getType().getStructType(),
            objectValue.getValue(),
            fieldIndex,
            fieldName + "_ptr"
        );

        // Load the field value
        IrValue loadedValue = IrFactory.createLoad(
            builder,
            fieldPtr,
            field.getType().getType(),
            fieldName + "_value"
        );

        return new ResoValue(field.getType(), loadedValue, line, column);
    }

    /**
     * Generates code for a method call expression.
     *
     * @param expr The resource method call expression
     * @return The result of the method call
     */
    @Nullable
    public ResoValue generateMethodCall(@Nonnull ResoParser.MethodCallExprContext expr,
                                        @Nonnull ResoValue objectValue) {
        int line = expr.getStart().getLine();
        int column = expr.getStart().getCharPositionInLine();

        // Validate object
        if (!validateObjectExpression(objectValue, line, column)) {
            return null;
        }

        ConcreteResoValue concreteObjectValue =
            objectValue.concretizeToDefault(context.getErrorReporter());
        if (concreteObjectValue == null) {
            context.getErrorReporter()
                .error("Cannot concretize object to default type for method call", line, column);
            return null;
        }

        // Find and validate the type
        TypeSymbol typeSymbol =
            findAndValidateTypeSymbol(concreteObjectValue.getType(), line, column);
        if (typeSymbol == null) {
            return null;
        }

        PathInfo pathInfo = extractPathInfo(expr.pathAccess());
        String methodName = expr.Identifier().getText();

        // Find and validate the method
        MethodSymbol method =
            findAndValidateMethod(typeSymbol, pathInfo.pathString, methodName, line, column);
        if (method == null) {
            return null;
        }

        // Check method visibility
        if (!checkMethodVisibility(method, typeSymbol, line, column)) {
            return null;
        }

        // Process arguments
        List<ResoParser.ExpressionContext> argExpr = new ArrayList<>();
        argExpr.addAll(pathInfo.pathArguments);
        argExpr.addAll(extractArgumentExpressions(expr));

        IrValue[] arguments = processArguments(method, argExpr, typeSymbol.getName(),
            concreteObjectValue.getType().getGenericTypes(), line, column);
        if (arguments == null) {
            return null;
        }

        // Combine all arguments and make the call
        IrValue[] allArguments = combineArgumentValues(concreteObjectValue.getValue(), arguments);

        return method.buildCall(context, concreteObjectValue.getType(), allArguments, line, column);
    }

    private boolean validateObjectExpression(@Nonnull ResoValue objectValue, int line, int column) {
        if (objectValue.getDefaultType() == null) {
            reportError("Cannot access resource on untyped value", line, column);
            return false;
        }

        return true;
    }

    /**
     * Finds and validates a resource symbol for the given type.
     */
    @Nullable
    private ResourceSymbol findAndValidateResourceSymbol(@Nonnull ResoType objectType,
                                                         int line, int column) {
        ResourceSymbol resourceSymbol = context.getSymbolTable().findResource(objectType.getName());
        if (resourceSymbol == null) {
            reportError("Symbol not found for type " + objectType.getName(), line, column);
            return null;
        }
        return resourceSymbol;
    }

    /**
     * Finds and validates a type symbol by name.
     */
    @Nullable
    private TypeSymbol findAndValidateTypeSymbol(@Nonnull ResoType type,
                                                 int line, int column) {
        TypeSymbol typeSymbol = context.getSymbolTable().findType(type.getName());
        if (typeSymbol == null) {
            reportError("Type symbol '" + type.getName() + "' not found", line, column);
            return null;
        }
        return typeSymbol;
    }

    /**
     * Finds and validates a field in the given resource.
     */
    @Nullable
    private FieldSymbol findAndValidateField(@Nonnull ResourceSymbol resourceSymbol,
                                             @Nonnull String fieldName,
                                             int line, int column) {
        FieldSymbol field = resourceSymbol.findField(fieldName);
        if (field == null) {
            reportError("Field '" + fieldName + "' not found in " + resourceSymbol.getName(), line,
                column);
            return null;
        }
        return field;
    }

    /**
     * Checks visibility for field access.
     */
    private boolean checkFieldVisibility(@Nonnull FieldSymbol field,
                                         @Nonnull ResourceSymbol resourceSymbol,
                                         int line, int column) {
        boolean accessAllowed = context.getCurrentAccessContext()
            .canAccess(field.getVisibility(), resourceSymbol.getFileIdentifier());

        if (!accessAllowed) {
            context.getErrorReporter().error(
                "Field '" + field.getName() + "' with "
                    + field.getVisibility().name().toLowerCase()
                    + " visibility is not accessible from current context",
                line,
                column);
        }

        return accessAllowed;
    }

    /**
     * Extracts path information from resource path access context.
     */
    @Nonnull
    private PathInfo extractPathInfo(@Nullable ResoParser.PathAccessContext pathAccess) {
        if (pathAccess == null) {
            return new PathInfo("", Collections.emptyList());
        }

        List<String> pathSegments = new ArrayList<>();
        List<ResoParser.ExpressionContext> pathArguments = new ArrayList<>();

        for (ResoParser.PathSegmentContext segmentCtx : pathAccess.pathSegment()) {
            if (segmentCtx.Identifier() != null) {
                pathSegments.add(segmentCtx.Identifier().getText());
            } else if (segmentCtx.expression() != null) {
                pathArguments.add(segmentCtx.expression());
                pathSegments.add("{Indexer}");
            }
        }

        String pathString = String.join(".", pathSegments);
        return new PathInfo(pathString, pathArguments);
    }

    /**
     * Finds and validates a method in the given resource and path.
     */
    @Nullable
    private MethodSymbol findAndValidateMethod(@Nonnull TypeSymbol typeSymbol,
                                               @Nonnull String pathString,
                                               @Nonnull String methodName,
                                               int line, int column) {
        MethodSymbol method = typeSymbol.findMethod(pathString, methodName);
        if (method == null) {
            String errorMessage = pathString.isEmpty()
                ? "Method '" + methodName + "' not found in " + typeSymbol.getName()
                : "Method '" + methodName + "' not found in path '" + pathString + "' of "
                + typeSymbol.getName();

            reportError(errorMessage, line, column);
            return null;
        }
        return method;
    }

    /**
     * Checks visibility for method access.
     */
    private boolean checkMethodVisibility(@Nonnull MethodSymbol method,
                                          @Nonnull TypeSymbol typeSymbol,
                                          int line, int column) {
        boolean accessAllowed = context.getCurrentAccessContext()
            .canAccess(method.getVisibility(), typeSymbol.getFileIdentifier());

        if (!accessAllowed) {
            context.getErrorReporter().error(
                "Method '" + method.getName() + "' with "
                    + method.getVisibility().name().toLowerCase()
                    + " visibility is not accessible from current context",
                line,
                column);
        }
        return accessAllowed;
    }

    /**
     * Processes arguments and validates them.
     */
    @Nullable
    private IrValue[] processArguments(@Nonnull MethodSymbol method,
                                       @Nonnull List<ResoParser.ExpressionContext> argExpr,
                                       @Nonnull String resourceName,
                                       @Nonnull List<ResoType> genericTypes,
                                       int line, int column) {
        // Exclude the first parameter (the resource instance itself)
        List<Parameter> parameters =
            method.getParameters().subList(0, method.getParameters().size());

        if (!CallGeneratorUtils.validateArgumentCount(parameters, argExpr,
            resourceName, context.getErrorReporter(), line, column)) {
            return null;
        }

        return CallGeneratorUtils.processArguments(parameters, argExpr,
            resourceName, genericTypes, context.getExpressionGenerator(),
            context.getErrorReporter(),
            line, column);
    }

    /**
     * Extracts argument expressions from the method call context.
     */
    @Nonnull
    private List<ResoParser.ExpressionContext> extractArgumentExpressions(
        @Nonnull ResoParser.MethodCallExprContext expr) {
        if (expr.expressionList() == null) {
            return Collections.emptyList();
        }
        return expr.expressionList().expression();
    }

    /**
     * Combines object value and method arguments into a single array.
     */
    @Nonnull
    private IrValue[] combineArgumentValues(@Nonnull IrValue thisValue,
                                            @Nonnull IrValue[] arguments) {
        IrValue[] result = new IrValue[1 + arguments.length];
        result[0] = thisValue;
        System.arraycopy(arguments, 0, result, 1, arguments.length);
        return result;
    }

    /**
     * Reports an error with consistent formatting.
     */
    private void reportError(@Nonnull String message, int line, int column) {
        context.getErrorReporter().error(message, line, column);
    }

    /**
     * Holds path information extracted from resource path access.
     */
    private record PathInfo(String pathString, List<ResoParser.ExpressionContext> pathArguments) {
    }
}