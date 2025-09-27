package com.reso.compiler.symbols.resources;

import com.reso.compiler.codegen.common.CallGeneratorUtils;
import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.symbols.Parameter;
import com.reso.compiler.symbols.Symbol;
import com.reso.compiler.symbols.SymbolKind;
import com.reso.compiler.symbols.Visibility;
import com.reso.compiler.types.GenericType;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ResoValue;
import com.reso.llvm.api.IrValue;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Symbol for resource methods.
 */
public class MethodSymbol implements Symbol {
    private final String methodName;
    private final ResoType returnType;
    private final List<Parameter> parameters;
    private final IrValue llvmValue;
    private final Visibility visibility;
    private final List<PathSegment> pathSegments;
    private final String pathString;
    private final BuildMethodCallFunction callFunction;

    /**
     * Creates a new resource method symbol.
     *
     * @param methodName   The method name
     * @param type         The return type
     * @param parameters   The parameters (including 'this' and path parameters)
     * @param llvmValue    The LLVM value
     * @param visibility   The visibility of the method
     * @param pathSegments The path segments for this method
     */
    public MethodSymbol(
        @Nonnull String methodName,
        @Nonnull ResoType type,
        @Nonnull List<Parameter> parameters,
        @Nullable IrValue llvmValue,
        @Nonnull Visibility visibility,
        @Nonnull List<PathSegment> pathSegments) {
        this(methodName, type, parameters, llvmValue, visibility, pathSegments,
            (ResoType resoType,
             ResoType returnType,
             IrValue value,
             IrValue[] argumentValues,
             String name,
             CodeGenerationContext context,
             int line,
             int column) -> {
                if (returnType.isGeneric()) {
                    returnType =
                        resoType.getGenericTypes().get(((GenericType) returnType).getIndex());
                }
                return CallGeneratorUtils.buildCall(returnType, value, argumentValues, name,
                    context.getIrBuilder(), line, column);
            });
    }

    /**
     * Creates a new resource method symbol.
     *
     * @param methodName   The method name
     * @param type         The return type
     * @param parameters   The parameters (including 'this' and path parameters)
     * @param llvmValue    The LLVM value
     * @param visibility   The visibility of the method
     * @param pathSegments The path segments for this method
     * @param callFunction The function to use for building calls to this method
     */
    public MethodSymbol(
        @Nonnull String methodName,
        @Nonnull ResoType type,
        @Nonnull List<Parameter> parameters,
        @Nullable IrValue llvmValue,
        @Nonnull Visibility visibility,
        @Nonnull List<PathSegment> pathSegments,
        @Nonnull BuildMethodCallFunction callFunction) {
        this.methodName = Objects.requireNonNull(methodName, "MethodeName cannot be null");
        this.returnType = Objects.requireNonNull(type, "ReturnType cannot be null");
        this.parameters = Objects.requireNonNull(parameters, "Parameters cannot be null");
        this.llvmValue = llvmValue;
        this.visibility = Objects.requireNonNull(visibility, "Visibility cannot be null");
        this.pathSegments = Objects.requireNonNull(pathSegments, "PathSegments cannot be null");
        this.pathString = pathSegments.stream()
            .map(PathSegment::getDisplayName)
            .collect(Collectors.joining("."));
        this.callFunction = Objects.requireNonNull(callFunction, "CallFunction cannot be null");
    }


    @Nonnull
    @Override
    public String getName() {
        return methodName;
    }

    @Nonnull
    @Override
    public SymbolKind getKind() {
        return SymbolKind.METHOD;
    }

    @Nonnull
    public ResoType getType() {
        return returnType;
    }

    @Nonnull
    public IrValue getLlvmValue() {
        if (llvmValue == null) {
            throw new IllegalStateException("LLVMValue is not set for method: " + methodName);
        }
        return llvmValue;
    }

    @Nonnull
    public ResoType getReturnType() {
        return returnType;
    }

    @Nonnull
    public List<Parameter> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    @Nonnull
    public Visibility getVisibility() {
        return visibility;
    }

    @Nonnull
    public List<PathSegment> getPathSegments() {
        return Collections.unmodifiableList(pathSegments);
    }

    @Nonnull
    public String getPathString() {
        return pathString;
    }

    @Nullable
    public ResoValue buildCall(@Nonnull CodeGenerationContext context,
                               @Nonnull ResoType type,
                               @Nonnull IrValue[] argumentValues,
                               int line,
                               int column) {
        return callFunction.buildCall(
            type,
            returnType,
            llvmValue,
            argumentValues,
            methodName,
            context,
            line,
            column
        );
    }
}
