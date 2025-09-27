package com.reso.compiler.codegen.resources;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.symbols.resources.PathSegment;
import com.reso.compiler.symbols.resources.ResourceSymbol;
import com.reso.compiler.types.ResoType;
import com.reso.grammar.ResoParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Main coordinator for resource code generation.
 * This class coordinates the generation of LLVM IR code for resource definitions,
 * including methods, and any other resource-specific code.
 */
public class ResourceGenerator {
    private final CodeGenerationContext context;
    private final ResourceMethodGenerator methodGenerator;

    /**
     * Creates a new resource generator.
     *
     * @param context The code generation context
     */
    public ResourceGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");

        this.methodGenerator = new ResourceMethodGenerator(context);
    }

    /**
     * Generates code for a resource definition.
     * This method coordinates the generation of all components of a resource:
     * constructors, methods, and any other resource-specific code.
     *
     * @param resourceDef The resource definition context
     */
    public void generateResource(@Nonnull ResoParser.ResourceDefContext resourceDef) {
        Objects.requireNonNull(resourceDef, "Resource definition cannot be null");

        String resourceName = resourceDef.Identifier().getText();
        int line = resourceDef.getStart().getLine();
        int column = resourceDef.getStart().getCharPositionInLine();

        // Find the resource symbol (created during signature registration)
        ResourceSymbol resourceSymbol = context.getSymbolTable().findResource(resourceName);
        if (resourceSymbol == null) {
            context.getErrorReporter().error(
                "Failed to find resource " + resourceName, line, column);
            return;
        }

        // Generate all components of the resource
        ResoParser.ResourceBodyContext resourceBody = resourceDef.resourceBody();

        if (resourceBody != null) {
            // Generate methods
            generateMethods(resourceBody, resourceSymbol);
        }
    }

    /**
     * Generates all methods for a resource.
     *
     * @param resourceBody   The resource body context
     * @param resourceSymbol The resource symbol
     */
    private void generateMethods(
        @Nonnull ResoParser.ResourceBodyContext resourceBody,
        @Nonnull ResourceSymbol resourceSymbol) {

        // Generate methods in resource paths
        if (resourceBody.resourcePath() != null) {
            for (ResoParser.ResourcePathContext pathCtx : resourceBody.resourcePath()) {
                if (pathCtx.resourceMethod() != null) {
                    for (ResoParser.ResourceMethodContext methodCtx : pathCtx.resourceMethod()) {
                        methodGenerator.generateMethod(methodCtx, pathCtx, resourceSymbol);
                    }
                }
            }
        }
    }

    /**
     * Parses path segments from the path context.
     *
     * @param context     The code generation context
     * @param pathContext The path context
     * @return List of path segments, or null if parsing failed
     */
    @Nullable
    public static List<PathSegment> parsePathSegments(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.ResourcePathContext pathContext) {

        List<PathSegment> segments = new ArrayList<>();

        if (pathContext.resourcePathSegment() == null
            || pathContext.resourcePathSegment().isEmpty()) {
            return segments; // Empty list represents root path
        }

        for (ResoParser.ResourcePathSegmentContext segmentCtx : pathContext.resourcePathSegment()) {
            PathSegment segment = parsePathSegment(context, segmentCtx);
            if (segment == null) {
                return null; // Error already reported
            }
            segments.add(segment);
        }

        return segments;
    }

    /**
     * Parses a single path segment.
     * A path segment can be either a simple identifier or an indexer
     * (parameter in braces).
     *
     * @param context    The code generation context
     * @param segmentCtx The segment context
     * @return The path segment, or null if parsing failed
     */
    @Nullable
    private static PathSegment parsePathSegment(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.ResourcePathSegmentContext segmentCtx) {

        if (segmentCtx.Identifier() != null) {
            return parseIdentifierSegment(segmentCtx.Identifier());
        } else if (segmentCtx.resourceIndexer() != null) {
            return parseResourceIndexer(context, segmentCtx.resourceIndexer());
        } else {
            context.getErrorReporter().error(
                "Invalid path segment - expected identifier or indexer",
                segmentCtx.getStart().getLine(),
                segmentCtx.getStart().getCharPositionInLine());
            return null;
        }
    }

    /**
     * Parses an identifier segment from a terminal node.
     *
     * @param identifier The identifier terminal node
     * @return The path segment, or null if parsing failed
     */
    @Nullable
    private static PathSegment parseIdentifierSegment(@Nonnull TerminalNode identifier) {
        Objects.requireNonNull(identifier, "Identifier cannot be null");

        String identifierText = identifier.getText();
        if (identifierText == null || identifierText.trim().isEmpty()) {
            return null;
        }

        return new PathSegment(identifierText.trim());
    }

    /**
     * Parses a resource indexer from the grammar.
     *
     * @param context    The code generation context
     * @param indexerCtx The indexer context
     * @return The path segment for the indexer, or null if parsing failed
     */
    @Nullable
    private static PathSegment parseResourceIndexer(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.ResourceIndexerContext indexerCtx) {

        final ErrorReporter errorReporter = context.getErrorReporter();

        // Extract parameter name and type from {paramName: Type}
        String paramName = indexerCtx.Identifier().getText();
        ResoType paramType = context.getTypeSystem().resolveType(indexerCtx.type(), errorReporter);

        if (paramType == null) {
            return null; // Error already reported
        }

        return new PathSegment(paramName, paramType);
    }
}