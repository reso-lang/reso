package com.reso.compiler.codegen.resources;

import static com.reso.compiler.codegen.resources.ResourceGenerator.parsePathSegments;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.symbols.Parameter;
import com.reso.compiler.symbols.Visibility;
import com.reso.compiler.symbols.resources.FieldSymbol;
import com.reso.compiler.symbols.resources.MethodSymbol;
import com.reso.compiler.symbols.resources.PathSegment;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.types.ResourceType;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrType;
import com.reso.llvm.api.IrValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility for registering resource signatures in the first compiler pass.
 */
public final class ResourceSignatureRegister {

    private ResourceSignatureRegister() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers all resource type declarations (empty structs) in a program.
     *
     * @param context The code generation context
     * @param program The program context
     */
    public static void registerResourceTypeDeclarations(@Nonnull CodeGenerationContext context,
                                                        @Nonnull
                                                        ResoParser.ProgramContext program) {
        validateInputs(context, program);

        for (int i = 0; i < program.getChildCount(); i++) {
            if (program.getChild(i) instanceof ResoParser.ResourceDefContext resourceDef) {
                // Register struct types
                registerResourceTypeDeclaration(context, resourceDef);
            }
        }
    }

    /**
     * Registers all resource declarations in a program.
     *
     * @param context The code generation context
     * @param program The program context
     */
    public static void registerResourceDeclarations(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.ProgramContext program,
        @Nonnull String fileIdentifier) {

        validateInputs(context, program);

        for (int i = 0; i < program.getChildCount(); i++) {
            if (program.getChild(i) instanceof ResoParser.ResourceDefContext resourceDef) {
                // Register resource signature
                registerResourceDeclaration(context, resourceDef, fileIdentifier);
            }
        }
    }

    /**
     * Registers a resource type declaration (empty struct).
     *
     * @param context     The code generation context
     * @param resourceDef The resource definition context
     */
    private static void registerResourceTypeDeclaration(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.ResourceDefContext resourceDef) {

        final String resourceName = resourceDef.Identifier().getText();
        final int line = resourceDef.getStart().getLine();
        final int column = resourceDef.getStart().getCharPositionInLine();
        final ErrorReporter errorReporter = context.getErrorReporter();

        // Create empty struct type as forward declaration
        IrType emptyStructType = createResourceStructType(
            context,
            resourceName
        );

        IrType pointerResourceType = IrFactory.createPointerType(emptyStructType, 0);

        // Create and register the resource type in the type system
        ResoType resourceType = context.getTypeSystem()
            .createResourceType(resourceName, pointerResourceType, emptyStructType);
        if (resourceType == null) {
            errorReporter.error("Failed to create resource type declaration for: " + resourceName,
                line, column);
        }
    }

    /**
     * Registers a resource signature.
     *
     * @param context     The code generation context
     * @param resourceDef The resource definition context
     */
    private static void registerResourceDeclaration(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.ResourceDefContext resourceDef,
        @Nonnull String fileIdentifier) {

        final String resourceName = resourceDef.Identifier().getText();
        final int line = resourceDef.getStart().getLine();
        final int column = resourceDef.getStart().getCharPositionInLine();
        final ErrorReporter errorReporter = context.getErrorReporter();

        if (context.getSymbolTable().findResource(resourceName) != null) {
            errorReporter.error("Resource already defined: " + resourceName, line, column);
            return;
        }

        ResourceType resourceType = context.getTypeSystem().getResourceType(resourceName);
        if (resourceType == null) {
            throw new IllegalStateException(
                "Resource type not found in type system: " + resourceName);
        }

        ResoParser.ResourceFieldsContext resourceFields = resourceDef.resourceFields();
        List<FieldSymbol> fields = Collections.emptyList();
        if (resourceFields != null) {
            // Process resource fields
            fields = processResourceFields(context, resourceFields.resourceField());
            setResourceTypeBody(resourceType, fields);
        }

        ResoParser.ResourceBodyContext resourceBody = resourceDef.resourceBody();

        List<MethodSymbol> methods = Collections.emptyList();

        if (resourceBody != null) {
            // Process resource paths
            methods = processResourceMethods(
                context, resourceBody.resourcePath(), resourceType);
        }

        context.getSymbolTable().defineResource(
            resourceName,
            resourceType,
            fields,
            methods,
            fileIdentifier,
            errorReporter,
            line,
            column); // Error already reported
    }

    private static List<FieldSymbol> processResourceFields(
        @Nonnull CodeGenerationContext context,
        @Nonnull List<ResoParser.ResourceFieldContext> fieldContexts) {

        List<FieldSymbol> fields = new ArrayList<>();
        Set<String> fieldNames = new HashSet<>();
        for (ResoParser.ResourceFieldContext fieldCtx : fieldContexts) {
            FieldSymbol fieldSymbol = registerResourceField(context, fieldCtx);

            if (fieldSymbol == null) {
                continue;
            }

            if (fieldNames.contains(fieldSymbol.getName())) {
                context.getErrorReporter().error(
                    "Ambiguous field name in resource: " + fieldSymbol.getName(),
                    fieldCtx.getStart().getLine(),
                    fieldCtx.getStart().getCharPositionInLine());
                continue; // Skip this field
            }

            fields.add(fieldSymbol);
            fieldNames.add(fieldSymbol.getName());
        }
        return fields;
    }

    /**
     * Registers a resource field signature.
     *
     * @param context      The code generation context
     * @param fieldContext The resource field context
     * @return The resource field symbol, or null if registration failed
     */
    @Nullable
    private static FieldSymbol registerResourceField(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.ResourceFieldContext fieldContext) {

        final String fieldName = fieldContext.Identifier().getText();
        final int line = fieldContext.getStart().getLine();
        final int column = fieldContext.getStart().getCharPositionInLine();
        final ErrorReporter errorReporter = context.getErrorReporter();

        // Check visibility
        final Visibility visibility =
            fieldContext.visibility() != null ? Visibility.GLOBAL : Visibility.FILEPRIVATE;

        // Check if it's const or var
        final boolean isConstant = fieldContext.CONST() != null;

        // Resolve field type
        if (fieldContext.type() == null) {
            errorReporter.error("Resource field must have explicit type: " + fieldName, line,
                column);
            return null;
        }

        ResoType fieldType =
            context.getTypeSystem().resolveType(fieldContext.type(), errorReporter);
        if (fieldType == null) {
            return null; // Error already reported
        }

        return new FieldSymbol(
            fieldName,
            fieldType,
            isConstant,
            visibility
        );
    }

    /**
     * Processes resource methods.
     *
     * @param context      The code generation context
     * @param pathContexts The list of resource path contexts
     * @param resourceType The resource type
     * @return List of processed method symbols
     */
    private static List<MethodSymbol> processResourceMethods(
        @Nonnull CodeGenerationContext context,
        @Nonnull List<ResoParser.ResourcePathContext> pathContexts,
        @Nonnull ResoType resourceType) {

        List<MethodSymbol> methods = new ArrayList<>();

        // Track method names by path name
        Map<String, Set<String>> pathMethodNames = new HashMap<>();

        // Process each path context
        for (ResoParser.ResourcePathContext pathCtx : pathContexts) {
            List<MethodSymbol> pathMethods = processMethodsInPath(context, pathCtx, resourceType);

            // Check for method name conflicts
            for (MethodSymbol method : pathMethods) {
                String pathName = method.getPathString();
                Set<String> existingMethods =
                    pathMethodNames.computeIfAbsent(pathName, k -> new HashSet<>());

                if (existingMethods.contains(method.getName())) {
                    context.getErrorReporter().error(
                        "Method '" + method.getName() + "' is already defined in path '"
                            + pathName + "'",
                        pathCtx.getStart().getLine(),
                        pathCtx.getStart().getCharPositionInLine());
                    continue; // Skip this method due to conflict
                }

                existingMethods.add(method.getName());
                methods.add(method);
            }
        }

        return methods;
    }

    /**
     * Processes all methods within a path context.
     *
     * @param context      The code generation context
     * @param pathContext  The resource path context
     * @param resourceType The resource type
     * @return List of method symbols for this path
     */
    private static List<MethodSymbol> processMethodsInPath(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.ResourcePathContext pathContext,
        @Nonnull ResoType resourceType) {

        final int line = pathContext.getStart().getLine();
        final int column = pathContext.getStart().getCharPositionInLine();
        final ErrorReporter errorReporter = context.getErrorReporter();

        // Parse path segments
        List<PathSegment> segments = parsePathSegments(context, pathContext);
        if (segments == null) {
            errorReporter.error("Invalid resource path definition", line, column);
            return Collections.emptyList();
        }

        List<MethodSymbol> methods = new ArrayList<>();

        // Process each method in this path
        if (pathContext.resourceMethod() != null) {
            for (ResoParser.ResourceMethodContext methodCtx : pathContext.resourceMethod()) {
                MethodSymbol methodSymbol = registerResourceMethod(
                    context, methodCtx, resourceType, segments);

                if (methodSymbol == null) {
                    continue; // Error already reported
                }
                methods.add(methodSymbol);
            }
        }

        if (methods.isEmpty()) {
            errorReporter.error("Resource path must contain at least one method", line, column);
        }

        return methods;
    }

    /**
     * Registers a single resource method.
     *
     * @param context       The code generation context
     * @param methodContext The resource method context
     * @param resourceType  The type of the containing resource
     * @param pathSegments  The path segments for this method
     * @return The method symbol, or null if registration failed
     */
    @Nullable
    private static MethodSymbol registerResourceMethod(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.ResourceMethodContext methodContext,
        @Nonnull ResoType resourceType,
        @Nonnull List<PathSegment> pathSegments) {

        final String methodName = methodContext.Identifier().getText();
        final ErrorReporter errorReporter = context.getErrorReporter();

        // Check visibility
        final Visibility visibility = methodContext.visibility() != null
            ? Visibility.GLOBAL :
            Visibility.FILEPRIVATE;

        // Process method parameters
        ParameterProcessingResult paramResult = processParameterList(
            context, methodContext.parameterList(), errorReporter);
        if (paramResult == null) {
            return null; // Error already reported
        }

        // Build all parameter types (this + path parameters + method parameters)
        List<IrType> allParams = new ArrayList<>();
        allParams.add(resourceType.getType()); // 'this' parameter type

        // Add path parameters
        List<IrType> pathParameters = extractPathParameters(pathSegments);
        allParams.addAll(pathParameters);

        // Add method parameters
        allParams.addAll(paramResult.irTypes);

        // Determine return type
        ResoType returnType = methodContext.type() != null
            ? context.getTypeSystem().resolveType(methodContext.type(), errorReporter) :
            context.getTypeSystem().getUnitType();

        if (returnType == null) {
            errorReporter.error("Invalid return type in method: " + methodName,
                methodContext.getStart().getLine(),
                methodContext.getStart().getCharPositionInLine());
            return null;
        }

        // Create function type
        IrType functionType = IrFactory.createFunctionType(
            returnType.getType(),
            allParams.toArray(new IrType[0]),
            false
        );

        // Create LLVM function declaration
        String llvmMethodName =
            buildMethodLlvmName(resourceType.getName(), pathSegments, methodName);
        IrValue methodValue = IrFactory.addFunction(
            context.getIrModule(),
            llvmMethodName,
            functionType
        );

        // Build complete parameter list including 'this' and path parameters
        List<Parameter> allParameters = new ArrayList<>();

        // Add path parameters
        for (PathSegment segment : pathSegments) {
            if (segment.isIndexer()) {
                allParameters.add(new Parameter(segment.getName(), segment.getIndexerType()));
            }
        }

        // Add method parameters
        allParameters.addAll(paramResult.params);

        return new MethodSymbol(
            methodName,
            returnType,
            allParameters,
            methodValue,
            visibility,
            pathSegments
        );
    }

    /**
     * Builds the LLVM function name for a resource method.
     *
     * @param resourceName The resource name
     * @param pathSegments The path segments
     * @param methodName   The method name
     * @return The LLVM function name
     */
    @Nonnull
    private static String buildMethodLlvmName(@Nonnull String resourceName,
                                              @Nonnull List<PathSegment> pathSegments,
                                              @Nonnull String methodName) {
        StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append(resourceName);

        if (!pathSegments.isEmpty()) {
            nameBuilder.append("_");
            nameBuilder.append(String.join("_", pathSegments.stream()
                .map(PathSegment::toString)
                .toList()));
        }

        nameBuilder.append("_").append(methodName);
        return nameBuilder.toString();
    }

    /**
     * Extracts path parameters from path segments.
     *
     * @param segments The path segments
     * @return List of path parameters
     */
    private static List<IrType> extractPathParameters(
        @Nonnull List<PathSegment> segments) {

        List<IrType> pathParameters = new ArrayList<>();

        for (PathSegment segment : segments) {
            if (segment.isIndexer()) {
                ResoType paramType = segment.getIndexerType();
                if (paramType != null) {
                    pathParameters.add(paramType.getType());
                }
            }
        }

        return pathParameters;
    }


    /**
     * Processes a parameter list and returns both Parameter and IRType lists.
     *
     * @param context       The code generation context
     * @param parameterList The parameter list context (can be null)
     * @param errorReporter The error reporter
     * @return Parameter processing result, or null if an error occurred
     */
    @Nullable
    private static ParameterProcessingResult processParameterList(
        @Nonnull CodeGenerationContext context,
        @Nullable ResoParser.ParameterListContext parameterList,
        @Nonnull ErrorReporter errorReporter) {

        final List<Parameter> params = new ArrayList<>();
        final List<IrType> irTypes = new ArrayList<>();

        if (parameterList != null) {
            List<ResoParser.ParameterContext> parameters = parameterList.parameter();

            for (ResoParser.ParameterContext paramCtx : parameters) {
                ResoType paramType =
                    context.getTypeSystem().resolveType(paramCtx.type(), errorReporter);
                if (paramType == null) {
                    return null; // Error already reported
                }

                params.add(new Parameter(paramCtx.Identifier().getText(), paramType));
                irTypes.add(paramType.getType());
            }
        }

        return new ParameterProcessingResult(params, irTypes);
    }

    private static IrType createResourceStructType(
        @Nonnull CodeGenerationContext context,
        @Nonnull String resourceName) {

        // Create struct type using IRFactory
        return IrFactory.createStructType(
            context.getIrContext(),
            resourceName + "_struct"
        );
    }

    private static void setResourceTypeBody(
        @Nonnull ResourceType resourceType,
        @Nonnull List<FieldSymbol> fields) {

        List<IrType> fieldTypes = new ArrayList<>();
        for (FieldSymbol field : fields) {
            fieldTypes.add(field.getType().getType());
        }

        IrFactory.setStructBody(resourceType.getStructType(),
            fieldTypes.toArray(new IrType[0]));
    }

    /**
     * Validates input parameters.
     *
     * @param context The code generation context
     * @param program The program context
     */
    private static void validateInputs(@Nonnull CodeGenerationContext context,
                                       @Nonnull ResoParser.ProgramContext program) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(program, "Program cannot be null");
    }

    /**
     * Helper class to hold parameter processing results.
     */
    private record ParameterProcessingResult(List<Parameter> params, List<IrType> irTypes) {
    }
}