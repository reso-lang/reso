package com.reso.compiler.codegen.expressions;

import static com.reso.compiler.types.StandardTypeHandles.CHAR;
import static com.reso.compiler.types.StandardTypeHandles.F32;
import static com.reso.compiler.types.StandardTypeHandles.F64;
import static com.reso.compiler.types.StandardTypeHandles.I16;
import static com.reso.compiler.types.StandardTypeHandles.I32;
import static com.reso.compiler.types.StandardTypeHandles.I64;
import static com.reso.compiler.types.StandardTypeHandles.I8;
import static com.reso.compiler.types.StandardTypeHandles.ISIZE;
import static com.reso.compiler.types.StandardTypeHandles.U16;
import static com.reso.compiler.types.StandardTypeHandles.U32;
import static com.reso.compiler.types.StandardTypeHandles.U64;
import static com.reso.compiler.types.StandardTypeHandles.U8;
import static com.reso.compiler.types.StandardTypeHandles.USIZE;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.symbols.VariableSymbol;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for expressions.
 */
public class ExpressionGenerator {
    private static final String THIS_PARAMETER_NAME = "this";

    private final CodeGenerationContext context;
    private final LiteralGenerator literalGenerator;
    private final ArithmeticExpressionGenerator arithmeticGenerator;
    private final ComparisonExpressionGenerator comparisonGenerator;
    private final LogicalExpressionGenerator logicalGenerator;
    private final BitwiseExpressionGenerator bitwiseGenerator;
    private final UnaryExpressionGenerator unaryGenerator;
    private final FunctionCallGenerator functionCallGenerator;
    private final TernaryExpressionGenerator ternaryGenerator;
    private final ResourceExpressionGenerator resourceGenerator;
    private final ResourceInitializerGenerator resourceInitializerGenerator;

    /**
     * Creates a new expression generator.
     *
     * @param context The code generation context
     */
    public ExpressionGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");

        // Initialize specialized generators
        this.literalGenerator = new LiteralGenerator(context);
        this.arithmeticGenerator = new ArithmeticExpressionGenerator(context);
        this.comparisonGenerator = new ComparisonExpressionGenerator(context);
        this.logicalGenerator = new LogicalExpressionGenerator(context);
        this.bitwiseGenerator = new BitwiseExpressionGenerator(context);
        this.unaryGenerator = new UnaryExpressionGenerator(context);
        this.functionCallGenerator = new FunctionCallGenerator(context);
        this.ternaryGenerator = new TernaryExpressionGenerator(context);
        this.resourceGenerator = new ResourceExpressionGenerator(context);
        this.resourceInitializerGenerator = new ResourceInitializerGenerator(context);
    }

    @Nonnull
    public ArithmeticExpressionGenerator getArithmeticGenerator() {
        return arithmeticGenerator;
    }

    @Nonnull
    public BitwiseExpressionGenerator getBitwiseGenerator() {
        return bitwiseGenerator;
    }

    @Nonnull
    public LiteralGenerator getLiteralGenerator() {
        return literalGenerator;
    }

    /**
     * Visits an expression context.
     *
     * @param expr The expression context
     * @return The result of the expression
     */
    @Nullable
    public ResoValue visit(@Nullable ResoParser.ExpressionContext expr) {
        if (expr == null) {
            return null;
        }

        switch (expr) {
            case ResoParser.PrimaryExprContext primaryExpr -> {
                return generatePrimary(primaryExpr.primary());
            }
            case ResoParser.TypeConversionExprContext typeConversionExpr -> {
                return generateTypeConversion(typeConversionExpr);
            }
            case ResoParser.FunctionCallExprContext functionCallExpr -> {
                return generateFunctionCall(functionCallExpr);
            }
            case ResoParser.MethodCallExprContext methodCallExpr -> {
                return generateMethodCall(methodCallExpr);
            }
            case ResoParser.FieldAccessExprContext fieldAccessExpr -> {
                return generateFieldAccess(fieldAccessExpr);
            }
            case ResoParser.ResourceInitializerExprContext resourceInitializerExpr -> {
                return generateResourceInitializer(resourceInitializerExpr);
            }
            case ResoParser.UnaryExprContext unaryExpr -> {
                return generateUnaryExpr(unaryExpr);
            }
            case ResoParser.MultiplicativeExprContext multiplicativeExpr -> {
                return generateMultiplicativeExpr(multiplicativeExpr);
            }
            case ResoParser.AdditiveExprContext additiveExpr -> {
                return generateAdditiveExpr(additiveExpr);
            }
            case ResoParser.ShiftExprContext shiftExpr -> {
                return generateShiftExpr(shiftExpr);
            }
            case ResoParser.RelationalExprContext relationalExpr -> {
                return generateRelationalExpr(relationalExpr);
            }
            case ResoParser.EqualityExprContext equalityExpr -> {
                return generateEqualityExpr(equalityExpr);
            }
            case ResoParser.BitwiseAndExprContext bitwiseAndExpr -> {
                return generateBitwiseAndExpr(bitwiseAndExpr);
            }
            case ResoParser.BitwiseXorExprContext bitwiseXorExpr -> {
                return generateBitwiseXorExpr(bitwiseXorExpr);
            }
            case ResoParser.BitwiseOrExprContext bitwiseOrExpr -> {
                return generateBitwiseOrExpr(bitwiseOrExpr);
            }
            case ResoParser.LogicalAndExprContext logicalAndExpr -> {
                return generateLogicalAndExpr(logicalAndExpr);
            }
            case ResoParser.LogicalOrExprContext logicalOrExpr -> {
                return generateLogicalOrExpr(logicalOrExpr);
            }
            case ResoParser.TernaryExprContext ternaryExpr -> {
                return generateTernaryExpr(ternaryExpr);
            }
            case ResoParser.TupleExprContext tupleExpr -> {
                return generateTupleExpr(tupleExpr);
            }
            default -> {
            }
        }

        context.getErrorReporter()
            .error("Unsupported expression type: " + expr.getClass().getSimpleName(),
                expr.getStart().getLine(), expr.getStart().getCharPositionInLine());
        return null;
    }

    /**
     * Generates code for a primary expression.
     *
     * @param primary The primary expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generatePrimary(@Nullable ResoParser.PrimaryContext primary) {
        if (primary == null) {
            return null;
        }

        if (primary.Identifier() != null) {
            return generateIdentifierRef(primary.Identifier().getText(),
                primary.getStart().getLine(),
                primary.getStart().getCharPositionInLine());
        } else if (primary.THIS() != null) {
            return generateIdentifierRef(THIS_PARAMETER_NAME,
                primary.getStart().getLine(),
                primary.getStart().getCharPositionInLine());
        } else if (primary.literal() != null) {
            return literalGenerator.generateLiteral(primary.literal());
        } else if (primary.expression() != null) {
            return visit(primary.expression());
        }

        context.getErrorReporter().error("Invalid primary expression",
            primary.getStart().getLine(),
            primary.getStart().getCharPositionInLine());
        return null;
    }

    /**
     * Generates code for an identifier reference.
     *
     * @param name   The identifier name
     * @param line   The line number
     * @param column The column number
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateIdentifierRef(@Nonnull String name, int line, int column) {
        Objects.requireNonNull(name, "Name cannot be null");

        // Find the variable
        VariableSymbol symbol = context.getSymbolTable().findReadableVariable(name,
            context.getErrorReporter(), line, column);

        if (symbol == null) {
            // Error already reported by findReadableVariable
            return null;
        }

        // Get type and LLVM value
        ResoType type = symbol.getType();
        IrValue pointerValue = symbol.getLlvmValue();

        // Load the value from the pointer
        IrBuilder builder = context.getIrBuilder();
        IrValue loadedValue =
            IrFactory.createLoad(builder, pointerValue, pointerValue.getType(), name + "_value");

        // Return the loaded value with its type
        return new ResoValue(type, loadedValue, line, column);
    }

    /**
     * Generates code for a type conversion expression.
     *
     * @param expr The type conversion expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateTypeConversion(@Nullable ResoParser.TypeConversionExprContext expr) {
        if (expr == null) {
            return null;
        }

        int line = expr.getStart().getLine();
        int column = expr.getStart().getCharPositionInLine();

        // Get the target type
        String targetTypeName = expr.numericType().getText();
        ResoType targetType = resolveNumericTypeByName(targetTypeName, line, column);

        if (targetType == null) {
            return null;
        }

        // Evaluate the expression to convert
        ResoValue source = visit(expr.expression());
        if (source == null) {
            // Error already reported
            return null;
        }

        ConcreteResoValue concretized;

        if (source.canConcretizeTo(targetType)) {
            concretized = source.concretize(targetType, context.getErrorReporter());
        } else {
            concretized = source.concretizeToDefault(context.getErrorReporter());
        }

        if (concretized != null) {
            // Perform the explicit conversion
            return context.getTypeSystem().createConversion(
                concretized, targetType, context.getErrorReporter(), line, column);
        }

        return null;
    }

    @Nullable
    private ResoType resolveNumericTypeByName(@Nonnull String typeName, int line, int column) {
        try {
            return switch (typeName) {
                // Signed integer types
                case "i8" -> context.getTypeSystem().getType(I8);
                case "i16" -> context.getTypeSystem().getType(I16);
                case "i32" -> context.getTypeSystem().getType(I32);
                case "i64" -> context.getTypeSystem().getType(I64);
                case "isize" -> context.getTypeSystem().getType(ISIZE);

                // Unsigned integer types
                case "u8" -> context.getTypeSystem().getType(U8);
                case "u16" -> context.getTypeSystem().getType(U16);
                case "u32" -> context.getTypeSystem().getType(U32);
                case "u64" -> context.getTypeSystem().getType(U64);
                case "usize" -> context.getTypeSystem().getType(USIZE);

                // character type
                case "char" -> context.getTypeSystem().getType(CHAR);

                // Floating point types
                case "f32" -> context.getTypeSystem().getType(F32);
                case "f64" -> context.getTypeSystem().getType(F64);

                // Unknown numeric type
                default -> {
                    context.getErrorReporter().error(
                        "Unknown numeric type: " + typeName, line, column);
                    yield null;
                }
            };
        } catch (IllegalStateException e) {
            context.getErrorReporter().error(
                "Type system error: " + e.getMessage(), line, column);
            return null;
        }
    }

    /**
     * Generates code for a function call.
     *
     * @param expr The function call expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateFunctionCall(@Nullable ResoParser.FunctionCallExprContext expr) {
        if (expr == null) {
            return null;
        }

        return functionCallGenerator.generateFunctionCall(expr);
    }

    /**
     * Generates code for a resource initializer expression.
     *
     * @param expr The resource initializer expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateResourceInitializer(
        @Nullable ResoParser.ResourceInitializerExprContext expr) {
        if (expr == null) {
            return null;
        }

        return resourceInitializerGenerator.generateResourceInitializer(expr);
    }

    /**
     * Generates code for a unary expression.
     *
     * @param expr The unary expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateUnaryExpr(@Nullable ResoParser.UnaryExprContext expr) {
        if (expr == null) {
            return null;
        }

        return unaryGenerator.generateUnaryExpr(expr);
    }

    /**
     * Generates code for a multiplicative expression.
     *
     * @param expr The multiplicative expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateMultiplicativeExpr(
        @Nullable ResoParser.MultiplicativeExprContext expr) {
        if (expr == null) {
            return null;
        }

        return arithmeticGenerator.generateMultiplicativeExpr(expr);
    }

    /**
     * Generates code for an additive expression.
     *
     * @param expr The additive expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateAdditiveExpr(@Nullable ResoParser.AdditiveExprContext expr) {
        if (expr == null) {
            return null;
        }

        return arithmeticGenerator.generateAdditiveExpr(expr);
    }

    /**
     * Generates code for a shift expression.
     *
     * @param expr The shift expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateShiftExpr(@Nullable ResoParser.ShiftExprContext expr) {
        if (expr == null) {
            return null;
        }

        return bitwiseGenerator.generateShiftExpr(expr);
    }

    /**
     * Generates code for a relational expression.
     *
     * @param expr The relational expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateRelationalExpr(@Nullable ResoParser.RelationalExprContext expr) {
        if (expr == null) {
            return null;
        }

        return comparisonGenerator.generateRelationalExpr(expr);
    }

    /**
     * Generates code for an equality expression.
     *
     * @param expr The equality expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateEqualityExpr(@Nullable ResoParser.EqualityExprContext expr) {
        if (expr == null) {
            return null;
        }

        return comparisonGenerator.generateEqualityExpr(expr);
    }

    /**
     * Generates code for a bitwise AND expression.
     *
     * @param expr The bitwise AND expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateBitwiseAndExpr(@Nullable ResoParser.BitwiseAndExprContext expr) {
        if (expr == null) {
            return null;
        }

        return bitwiseGenerator.generateBitwiseAndExpr(expr);
    }

    /**
     * Generates code for a bitwise XOR expression.
     *
     * @param expr The bitwise XOR expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateBitwiseXorExpr(@Nullable ResoParser.BitwiseXorExprContext expr) {
        if (expr == null) {
            return null;
        }

        return bitwiseGenerator.generateBitwiseXorExpr(expr);
    }

    /**
     * Generates code for a bitwise OR expression.
     *
     * @param expr The bitwise OR expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateBitwiseOrExpr(@Nullable ResoParser.BitwiseOrExprContext expr) {
        if (expr == null) {
            return null;
        }

        return bitwiseGenerator.generateBitwiseOrExpr(expr);
    }

    /**
     * Generates code for a logical AND expression.
     *
     * @param expr The logical AND expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateLogicalAndExpr(@Nullable ResoParser.LogicalAndExprContext expr) {
        if (expr == null) {
            return null;
        }

        return logicalGenerator.generateLogicalAndExpr(expr);
    }

    /**
     * Generates code for a logical OR expression.
     *
     * @param expr The logical OR expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateLogicalOrExpr(@Nullable ResoParser.LogicalOrExprContext expr) {
        if (expr == null) {
            return null;
        }

        return logicalGenerator.generateLogicalOrExpr(expr);
    }

    /**
     * Generates code for a ternary expression.
     *
     * @param expr The ternary expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateTernaryExpr(@Nullable ResoParser.TernaryExprContext expr) {
        if (expr == null) {
            return null;
        }

        return ternaryGenerator.generateTernaryExpr(expr);
    }

    /**
     * Generates code for a tuple expression.
     *
     * @param expr The tuple expression
     * @return The result of the expression
     */
    @Nullable
    private ResoValue generateTupleExpr(@Nullable ResoParser.TupleExprContext expr) {
        if (expr == null) {
            return null;
        }


        ResoType unitType = context.getTypeSystem().getUnitType();
        int line = expr.getStart().getLine();
        int column = expr.getStart().getCharPositionInLine();

        // Create a constant empty struct value for unit type
        IrValue unitValue = IrFactory.createConstantNamedStruct(unitType.getType(), new IrValue[0]);
        return new ResoValue(unitType, unitValue, line, column);
    }

    /**
     * Generates code for a field access expression.
     *
     * @param expr The field access expression
     * @return The result of the field access
     */
    @Nullable
    public ResoValue generateFieldAccess(@Nullable ResoParser.FieldAccessExprContext expr) {
        if (expr == null) {
            return null;
        }

        ResoValue baseValue = visit(expr.expression());
        if (baseValue == null) {
            return null;
        }

        return resourceGenerator.generateFieldAccess(expr, baseValue);
    }

    /**
     * Generates code for a resource method call expression.
     *
     * @param expr The resource method call expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateMethodCall(@Nullable ResoParser.MethodCallExprContext expr) {
        if (expr == null) {
            return null;
        }

        ResoValue baseValue = visit(expr.expression());
        if (baseValue == null) {
            return null;
        }

        return resourceGenerator.generateMethodCall(expr, baseValue);
    }
}