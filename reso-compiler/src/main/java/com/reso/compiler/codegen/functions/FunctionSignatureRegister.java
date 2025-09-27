package com.reso.compiler.codegen.functions;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.symbols.Parameter;
import com.reso.compiler.symbols.Visibility;
import com.reso.compiler.types.ResoType;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrType;
import com.reso.llvm.api.IrValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Utility for registering function signatures in the first compiler pass.
 */
public final class FunctionSignatureRegister {

    // Constants for magic strings
    private static final String MAIN_FUNCTION_NAME = "main";
    private static final String I32_TYPE_NAME = "i32";

    private FunctionSignatureRegister() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers all function declarations in a program.
     *
     * @param context        The code generation context
     * @param program        The program context
     * @param fileIdentifier The source file identifier
     */
    public static void registerFunctionDeclarations(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.ProgramContext program,
        @Nonnull String fileIdentifier) {

        validateInputs(context, program);

        for (int i = 0; i < program.getChildCount(); i++) {
            if (program.getChild(i) instanceof ResoParser.FunctionDefContext funcDef) {
                // Register function signature without generating body
                registerFunctionSignature(context, funcDef, fileIdentifier);
            }
        }
    }

    /**
     * Registers a function signature.
     *
     * @param context        The code generation context
     * @param funcDef        The function definition context
     * @param fileIdentifier The source file identifier
     */
    private static void registerFunctionSignature(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.FunctionDefContext funcDef,
        @Nonnull String fileIdentifier) {

        final String functionName = funcDef.Identifier().getText();
        final int line = funcDef.getStart().getLine();
        final int column = funcDef.getStart().getCharPositionInLine();
        final boolean isMainFunction = MAIN_FUNCTION_NAME.equals(functionName);

        // Get visibility, defaulting to FILEPRIVATE
        Visibility visibility = extractVisibility(funcDef);

        // Resolve return type
        ResoType returnType = resolveReturnType(context, funcDef, isMainFunction, line, column);
        if (returnType == null) {
            return; // Error already reported
        }

        // Process parameters
        List<Parameter> params = new ArrayList<>();
        List<IrType> paramIrTypes = new ArrayList<>();

        if (!processParameters(context, funcDef, isMainFunction, params, paramIrTypes,
            context.getErrorReporter(), line, column)) {
            return; // Error occurred
        }

        // Create and register function
        createAndRegisterFunction(context, functionName, returnType, params, paramIrTypes,
            visibility, fileIdentifier, context.getErrorReporter(), line, column);
    }

    /**
     * Extracts the visibility from the function definition.
     *
     * @param funcDef The function definition context
     * @return The extracted visibility
     */
    @Nonnull
    private static Visibility extractVisibility(@Nonnull ResoParser.FunctionDefContext funcDef) {
        // Check for explicit visibility modifier
        if (funcDef.visibility() != null) {
            return Visibility.GLOBAL; // pub keyword makes it global
        }

        // Default visibility is fileprivate
        return Visibility.FILEPRIVATE;
    }

    /**
     * Validates the input parameters.
     */
    private static void validateInputs(@Nonnull CodeGenerationContext context,
                                       @Nonnull ResoParser.ProgramContext program) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(program, "Program cannot be null");
    }

    /**
     * Resolves the return type of the function.
     */
    private static ResoType resolveReturnType(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.FunctionDefContext funcDef,
        boolean isMainFunction,
        int line,
        int column) {

        if (funcDef.type() != null) {
            // Explicit return type specified
            ResoType returnType =
                context.getTypeSystem().resolveType(funcDef.type(), context.getErrorReporter());

            // Validate main function return type
            if (isMainFunction && returnType != null) {
                if (!I32_TYPE_NAME.equals(returnType.getName())) {
                    context.getErrorReporter().error("Main function must return i32", line, column);
                    return null;
                }
            }

            return returnType;
        } else {
            // No return type specified - default behavior
            if (isMainFunction) {
                context.getErrorReporter()
                    .error("Main function must have explicit return type i32", line, column);
                return null;
            }

            // For non-main functions, default to Unit type if available
            return context.getTypeSystem().getUnitType();
        }
    }

    /**
     * Processes function parameters.
     */
    private static boolean processParameters(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResoParser.FunctionDefContext funcDef,
        boolean isMainFunction,
        @Nonnull List<Parameter> params,
        @Nonnull List<IrType> paramIrTypes,
        @Nonnull ErrorReporter errorReporter,
        int line,
        int column) {

        if (funcDef.parameterList() == null) {
            return true; // No parameters to process
        }

        List<ResoParser.ParameterContext> parameters = funcDef.parameterList().parameter();

        // Validate main function has no parameters
        if (isMainFunction && !parameters.isEmpty()) {
            errorReporter.error("Main function should not have any parameters", line, column);
            return false;
        }

        // Process each parameter
        for (ResoParser.ParameterContext paramCtx : parameters) {
            ResoType paramType =
                context.getTypeSystem().resolveType(paramCtx.type(), errorReporter);

            if (paramType == null) {
                return false; // Error already reported
            }

            params.add(new Parameter(paramCtx.Identifier().getText(), paramType));
            paramIrTypes.add(paramType.getType());
        }

        return true;
    }

    /**
     * Creates the function type and registers it in the symbol table.
     */
    private static boolean createAndRegisterFunction(
        @Nonnull CodeGenerationContext context,
        @Nonnull String functionName,
        @Nonnull ResoType returnType,
        @Nonnull List<Parameter> params,
        @Nonnull List<IrType> paramIrTypes,
        @Nonnull Visibility visibility,
        @Nonnull String fileIdentifier,
        @Nonnull ErrorReporter errorReporter,
        int line,
        int column) {

        // Create function type
        IrType[] irParamTypesArray = paramIrTypes.toArray(new IrType[0]);
        IrType functionType = IrFactory.createFunctionType(
            returnType.getType(), irParamTypesArray, false);

        // Add function to module
        IrValue function = IrFactory.addFunction(context.getIrModule(), functionName, functionType);

        // Register in symbol table with visibility
        return context.getSymbolTable().defineFunction(
            functionName, function, returnType, params, visibility, fileIdentifier,
            errorReporter, line, column);
    }
}