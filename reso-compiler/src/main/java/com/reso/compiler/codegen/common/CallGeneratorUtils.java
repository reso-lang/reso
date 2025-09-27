package com.reso.compiler.codegen.common;

import com.reso.compiler.codegen.expressions.ExpressionGenerator;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.symbols.Parameter;
import com.reso.compiler.types.GenericType;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class for common function/method call generation logic.
 */
public class CallGeneratorUtils {

    private CallGeneratorUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Builds the actual function call IR instruction.
     *
     * @param returnType     The return type of the function
     * @param value          The function value to call
     * @param argumentValues The processed argument values
     * @param name           The name to use for the result value
     * @param irBuilder      The IR builder to use for creating the call
     * @param line           Source line number for the result value
     * @param column         Source column number for the result value
     * @return The result value of the function call
     */
    @Nonnull
    public static ResoValue buildCall(@Nonnull ResoType returnType,
                                      @Nonnull IrValue value,
                                      @Nonnull IrValue[] argumentValues,
                                      @Nonnull String name,
                                      @Nonnull IrBuilder irBuilder,
                                      int line,
                                      int column) {
        final String resultName = returnType.getName().equals("Void") ? "" : name + "_result";

        final IrValue callResult = IrFactory.createCall(
            irBuilder,
            value,
            argumentValues,
            resultName
        );

        return new ResoValue(returnType, callResult, line, column);
    }

    /**
     * Converts an argument value to match the expected parameter type.
     *
     * @param argument      The evaluated argument value
     * @param parameterType The expected parameter type
     * @param name          The name for error reporting
     * @param errorReporter The error reporter to use for reporting errors
     * @param argumentIndex The 1-based argument index for error reporting
     * @param line          Source line number for error reporting
     * @param column        Source column number for error reporting
     * @return The converted IR value, or null if conversion failed
     */
    @Nullable
    private static IrValue convertArgumentToParameterType(@Nonnull ResoValue argument,
                                                          @Nonnull ResoType parameterType,
                                                          @Nonnull String name,
                                                          @Nonnull ErrorReporter errorReporter,
                                                          int argumentIndex,
                                                          int line,
                                                          int column) {
        // Try to concretize the argument to the expected parameter type
        ConcreteResoValue convertedArgument = argument.concretize(parameterType, errorReporter);

        if (convertedArgument != null) {
            return convertedArgument.getValue();
        }

        // Report error with appropriate message
        errorReporter.error(String.format("Cannot convert argument %d from %s to %s in function %s",
            argumentIndex, argument.getTypeName(), parameterType.getName(), name), line, column);
        return null;
    }

    /**
     * Processes and evaluates all function arguments, performing type checking and conversion.
     *
     * @param parameters          The parameter list
     * @param argumentExpressions The argument expressions to evaluate
     * @param name                The name for error reporting
     * @param genericTypes        The generic types for the function, if any
     * @param expressionGenerator The expression generator for evaluating arguments
     * @param errorReporter       The error reporter to use for reporting errors
     * @param line                Source line number for error reporting
     * @param column              Source column number for error reporting
     * @return Array of IR values for the arguments, or null if an error occurred
     */
    @Nullable
    public static IrValue[] processArguments(@Nonnull List<Parameter> parameters,
                                             @Nonnull
                                             List<ResoParser.ExpressionContext> argumentExpressions,
                                             @Nonnull String name,
                                             @Nonnull List<ResoType> genericTypes,
                                             @Nonnull ExpressionGenerator expressionGenerator,
                                             @Nonnull ErrorReporter errorReporter,
                                             int line,
                                             int column) {
        final IrValue[] argumentValues = new IrValue[argumentExpressions.size()];

        for (int i = 0; i < argumentExpressions.size(); i++) {
            final ResoValue evaluatedArgument =
                expressionGenerator.visit(argumentExpressions.get(i));

            if (evaluatedArgument == null) {
                return null; // Error already reported during evaluation
            }

            ResoType expectedParameterType = parameters.get(i).type();
            if (expectedParameterType.isGeneric()) {
                expectedParameterType =
                    genericTypes.get(((GenericType) expectedParameterType).getIndex());
            }

            final IrValue convertedValue = convertArgumentToParameterType(
                evaluatedArgument, expectedParameterType, name, errorReporter, i + 1, line, column);

            if (convertedValue == null) {
                return null;
            }

            argumentValues[i] = convertedValue;
        }

        return argumentValues;
    }

    /**
     * Validates that the number of arguments matches the function's parameter count.
     *
     * @param parameters    The parameter list
     * @param arguments     The list of argument expressions
     * @param name          The name for error reporting
     * @param errorReporter The error reporter to use for reporting errors
     * @param line          Source line number for error reporting
     * @param column        Source column number for error reporting
     * @return true if the argument count is valid, false otherwise
     */
    public static boolean validateArgumentCount(@Nonnull List<Parameter> parameters,
                                                @Nonnull
                                                List<ResoParser.ExpressionContext> arguments,
                                                @Nonnull String name,
                                                @Nonnull ErrorReporter errorReporter,
                                                int line,
                                                int column) {
        final int expectedCount = parameters.size();
        final int actualCount = arguments.size();

        if (actualCount != expectedCount) {
            errorReporter.error(String.format("%s requires %d arguments, but got %d",
                name, expectedCount, actualCount), line, column);
            return false;
        }

        return true;
    }
}