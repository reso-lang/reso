package com.reso.compiler.symbols;

import com.reso.compiler.codegen.common.CallGeneratorUtils;
import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ResoValue;
import com.reso.llvm.api.IrValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Function symbol implementation.
 */
public class FunctionSymbol implements Symbol {
    private final String functionName;
    private final ResoType returnType;
    private final List<Parameter> parameters;
    private final IrValue llvmValue;
    private final Visibility visibility;
    private final String fileIdentifier;
    private final BuildFunctionCallFunction callFunction;

    /**
     * Creates a new function symbol.
     *
     * @param functionName   The function name
     * @param type           The return type
     * @param parameters     The parameters
     * @param llvmValue      The LLVM value
     * @param visibility     The visibility of the function
     * @param fileIdentifier The source file name where this function is defined
     */
    public FunctionSymbol(
        @Nonnull String functionName,
        @Nonnull ResoType type,
        @Nonnull List<Parameter> parameters,
        @Nullable IrValue llvmValue,
        @Nonnull Visibility visibility,
        @Nonnull String fileIdentifier) {
        this(functionName, type, parameters, llvmValue, visibility, fileIdentifier,
            (ResoType returnType,
             IrValue value,
             IrValue[] argumentValues,
             String name,
             CodeGenerationContext context,
             int line,
             int column) -> CallGeneratorUtils.buildCall(returnType, value, argumentValues, name,
                context.getIrBuilder(), line, column));
    }

    /**
     * Creates a new function symbol.
     *
     * @param functionName   The function name
     * @param type           The return type
     * @param parameters     The parameters
     * @param llvmValue      The LLVM value
     * @param visibility     The visibility of the function
     * @param fileIdentifier The source file name where this function is defined
     * @param callFunction   The function to use for building calls to this function
     */
    public FunctionSymbol(
        @Nonnull String functionName,
        @Nonnull ResoType type,
        @Nonnull List<Parameter> parameters,
        @Nullable IrValue llvmValue,
        @Nonnull Visibility visibility,
        @Nonnull String fileIdentifier,
        @Nonnull BuildFunctionCallFunction callFunction) {
        this.functionName = Objects.requireNonNull(functionName, "Name cannot be null");
        this.returnType = Objects.requireNonNull(type, "Return type cannot be null");
        this.parameters =
            new ArrayList<>(Objects.requireNonNull(parameters, "Parameter cannot be null"));
        this.llvmValue = llvmValue;
        this.visibility = Objects.requireNonNull(visibility, "Visibility cannot be null");
        this.fileIdentifier =
            Objects.requireNonNull(fileIdentifier, "Source file name cannot be null");
        this.callFunction = Objects.requireNonNull(callFunction, "Call function cannot be null");
    }

    @Override
    @Nonnull
    public String getName() {
        return functionName;
    }

    @Nonnull
    public ResoType getType() {
        return returnType;
    }

    @Nonnull
    public IrValue getLlvmValue() {
        if (llvmValue == null) {
            throw new IllegalStateException("LLVM value is not set for function: " + functionName);
        }
        return llvmValue;
    }

    @Override
    @Nonnull
    public SymbolKind getKind() {
        return SymbolKind.FUNCTION;
    }

    /**
     * Gets the return type of this function.
     *
     * @return The return type
     */
    @Nonnull
    public ResoType getReturnType() {
        return returnType;
    }

    /**
     * Gets the parameters of this function.
     *
     * @return The parameters
     */
    @Nonnull
    public List<Parameter> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    /**
     * Gets the visibility of this function.
     *
     * @return The visibility
     */
    @Nonnull
    public Visibility getVisibility() {
        return visibility;
    }

    /**
     * Gets the source file name where this function is defined.
     *
     * @return The source file name
     */
    @Nonnull
    public String getFileIdentifier() {
        return fileIdentifier;
    }

    @Nullable
    public ResoValue buildCall(@Nonnull CodeGenerationContext context,
                               @Nonnull IrValue[] argumentValues,
                               int line,
                               int column) {
        return callFunction.buildCall(
            returnType,
            llvmValue,
            argumentValues,
            functionName,
            context,
            line,
            column
        );
    }
}