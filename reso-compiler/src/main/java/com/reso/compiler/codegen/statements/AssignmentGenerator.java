package com.reso.compiler.codegen.statements;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.symbols.VariableSymbol;
import com.reso.compiler.symbols.resources.FieldSymbol;
import com.reso.compiler.symbols.resources.ResourceSymbol;
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
 * Generator for assignment statements.
 * This class handles both simple assignments (=) and compound assignments (+=, -=, etc.).
 * It ensures proper type checking, constant validation, and generates appropriate LLVM IR.
 */
public class AssignmentGenerator {

    /**
     * Enumeration of supported assignment operators with their string representations
     * and corresponding binary operators for compound assignments.
     */
    public enum AssignmentOperator {
        ASSIGN("=", null),
        PLUS_ASSIGN("+=", "+"),
        MINUS_ASSIGN("-=", "-"),
        MULT_ASSIGN("*=", "*"),
        DIV_ASSIGN("div=", "div"),
        REM_ASSIGN("rem=", "rem"),
        MOD_ASSIGN("mod=", "mod"),
        AND_ASSIGN("&=", "&"),
        OR_ASSIGN("|=", "|"),
        XOR_ASSIGN("^=", "^"),
        LSHIFT_ASSIGN("<<=", "<<"),
        RSHIFT_ASSIGN(">>=", ">>");

        private final String operator;
        private final String binaryOperator;

        AssignmentOperator(String operator, String binaryOperator) {
            this.operator = operator;
            this.binaryOperator = binaryOperator;
        }

        public String getOperator() {
            return operator;
        }

        public String getBinaryOperator() {
            return binaryOperator;
        }

        public boolean isSimpleAssignment() {
            return this == ASSIGN;
        }

        public boolean isCompoundAssignment() {
            return !isSimpleAssignment();
        }

        public boolean isArithmetic() {
            return this == PLUS_ASSIGN || this == MINUS_ASSIGN || this == MULT_ASSIGN
                || this == DIV_ASSIGN || this == REM_ASSIGN || this == MOD_ASSIGN;
        }

        public boolean isBitwise() {
            return this == AND_ASSIGN || this == OR_ASSIGN || this == XOR_ASSIGN
                || this == LSHIFT_ASSIGN || this == RSHIFT_ASSIGN;
        }
    }

    /**
     * Base interface for assignment targets (lvalues).
     */
    private interface AssignmentTarget {
        /**
         * Gets the type of the assignment target.
         */
        ResoType getType();

        /**
         * Gets the current value of the assignment target (for compound assignments).
         */
        @Nullable
        ResoValue getCurrentValue();

        /**
         * Stores a value to this assignment target.
         */
        void store(@Nonnull IrValue value);

        /**
         * Validates that this target can be assigned to.
         */
        boolean validateAssignment(@Nonnull AssignmentOperator operator, int line, int column);
    }

    /**
     * Assignment target for variables.
     */
    private class VariableTarget implements AssignmentTarget {
        private final VariableSymbol symbol;
        private final String varName;
        private final int line;
        private final int column;

        public VariableTarget(@Nonnull VariableSymbol symbol, @Nonnull String varName, int line,
                              int column) {
            this.symbol = Objects.requireNonNull(symbol);
            this.varName = Objects.requireNonNull(varName);
            this.line = line;
            this.column = column;
        }

        @Override
        public ResoType getType() {
            return symbol.getType();
        }

        @Override
        @Nullable
        public ResoValue getCurrentValue() {
            if (!symbol.isInitialized()) {
                return null;
            }

            IrValue loadedValue = IrFactory.createLoad(
                context.getIrBuilder(),
                symbol.getLlvmValue(),
                symbol.getType().getType(),
                varName + "_load"
            );

            return new ResoValue(symbol.getType(), loadedValue, line, column);
        }

        @Override
        public void store(@Nonnull IrValue value) {
            IrFactory.createStore(context.getIrBuilder(), value, symbol.getLlvmValue());
            context.getSymbolTable().initializeVariable(varName, errorReporter, line, column);
        }

        @Override
        public boolean validateAssignment(@Nonnull AssignmentOperator operator, int line,
                                          int column) {
            // Check if reassigning to a constant
            if (symbol.isConstant() && symbol.isInitialized()) {
                errorReporter.error("Cannot reassign constant variable: " + varName, line, column);
                return false;
            }

            // For compound assignments, ensure the variable is initialized
            if (operator.isCompoundAssignment() && !symbol.isInitialized()) {
                errorReporter.error(
                    "Cannot use compound assignment on uninitialized variable: " + varName, line,
                    column);
                return false;
            }

            return true;
        }
    }

    /**
     * Assignment target for resource field access.
     */
    private class ResourceFieldTarget implements AssignmentTarget {
        private final ConcreteResoValue objectValue;
        private final ResourceSymbol resourceSymbol;
        private final FieldSymbol field;
        private final String fieldName;
        private final int line;
        private final int column;

        public ResourceFieldTarget(@Nonnull ConcreteResoValue objectValue,
                                   @Nonnull ResourceSymbol resourceSymbol,
                                   @Nonnull FieldSymbol field,
                                   @Nonnull String fieldName,
                                   int line, int column) {
            this.objectValue = Objects.requireNonNull(objectValue);
            this.resourceSymbol = Objects.requireNonNull(resourceSymbol);
            this.field = Objects.requireNonNull(field);
            this.fieldName = Objects.requireNonNull(fieldName);
            this.line = line;
            this.column = column;
        }

        @Override
        public ResoType getType() {
            return field.getType();
        }

        @Override
        @Nullable
        public ResoValue getCurrentValue() {
            int fieldIndex = resourceSymbol.getFieldIndex(fieldName);
            if (fieldIndex == -1) {
                return null;
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

        @Override
        public void store(@Nonnull IrValue value) {
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

            // Store the value to the field
            IrFactory.createStore(builder, value, fieldPtr);
        }

        @Override
        public boolean validateAssignment(@Nonnull AssignmentOperator operator, int line,
                                          int column) {
            // Check if field is constant
            if (field.isConstant()) {
                errorReporter.error("Cannot assign to constant field: " + fieldName, line, column);
                return false;
            }

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
    }

    private final CodeGenerationContext context;
    private final ErrorReporter errorReporter;

    /**
     * Creates a new assignment generator.
     *
     * @param context The code generation context
     */
    public AssignmentGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
        this.errorReporter = context.getErrorReporter();
    }

    /**
     * Generates code for an assignment statement.
     *
     * @param stmt The assignment statement context
     * @return The result of the assignment, or null if an error occurred
     */
    @Nullable
    public ResoValue generateAssignment(@Nullable ResoParser.AssignmentStatementContext stmt) {
        if (stmt == null) {
            return null;
        }

        final AssignmentContext assignmentContext = createAssignmentContext(stmt);
        if (assignmentContext == null) {
            return null; // Error already reported
        }

        final AssignmentTarget target = createAssignmentTarget(assignmentContext);
        if (target == null) {
            return null; // Error already reported
        }

        if (!target.validateAssignment(assignmentContext.operator, assignmentContext.line,
            assignmentContext.column)) {
            return null; // Error already reported
        }

        final ResoValue rightValue = evaluateRightHandExpression(assignmentContext);
        if (rightValue == null) {
            return null; // Error already reported
        }

        final IrValue storeValue = generateStoreValue(target, rightValue, assignmentContext);
        if (storeValue == null) {
            return null; // Error already reported
        }

        // Perform the assignment
        target.store(storeValue);

        // Return the assigned value
        return new ResoValue(target.getType(), storeValue, assignmentContext.line,
            assignmentContext.column);
    }

    /**
     * Creates an assignment context from the parse tree node.
     */
    @Nullable
    private AssignmentContext createAssignmentContext(
        @Nonnull ResoParser.AssignmentStatementContext stmt) {
        final int line = stmt.getStart().getLine();
        final int column = stmt.getStart().getCharPositionInLine();
        final AssignmentOperator operator = determineAssignmentOperator(stmt);

        if (operator == null) {
            errorReporter.error("Unknown assignment operator", line, column);
            return null;
        }

        return new AssignmentContext(stmt, operator, line, column);
    }

    /**
     * Creates an assignment target from the left-hand side expression.
     */
    @Nullable
    private AssignmentTarget createAssignmentTarget(@Nonnull AssignmentContext ctx) {
        ResoParser.ExpressionContext lhsExpr = ctx.stmt.expression(0);

        if (lhsExpr instanceof ResoParser.PrimaryExprContext primaryExpr) {

            // Check if it's an identifier (variable)
            if (primaryExpr.primary().Identifier() != null) {
                String varName = primaryExpr.primary().Identifier().getText();
                VariableSymbol symbol = context.getSymbolTable().findReadableVariable(
                    varName, errorReporter, ctx.line, ctx.column);

                if (symbol == null) {
                    return null; // Error already reported
                }

                return new VariableTarget(symbol, varName, ctx.line, ctx.column);
            }

            // Check if it's 'this' keyword
            if (primaryExpr.primary().THIS() != null) {
                errorReporter.error("Cannot assign to 'this'", ctx.line, ctx.column);
                return null;
            }
        } else if (lhsExpr instanceof ResoParser.FieldAccessExprContext fieldAccessExpr) {

            // Evaluate the object expression
            ResoValue objectValue =
                context.getExpressionGenerator().visit(fieldAccessExpr.expression());
            if (objectValue == null) {
                return null; // Error already reported
            }

            // Check if the object is a reference type
            ResoType objectType = objectValue.getType();
            if (!objectType.isReference()) {
                errorReporter.error(
                    "Cannot access field on non-reference type " + objectType.getName(), ctx.line,
                    ctx.column);
                return null;
            }

            ConcreteResoValue concreteObjectValue = objectValue.concretizeToDefault(errorReporter);
            if (concreteObjectValue == null) {
                return null; // Error already reported
            }

            String fieldName = fieldAccessExpr.Identifier().getText();

            // Find the resource symbol
            ResourceSymbol resourceSymbol =
                context.getSymbolTable().findResource(objectType.getName());
            if (resourceSymbol == null) {
                errorReporter.error("Symbol not found for type " + objectType.getName(), ctx.line,
                    ctx.column);
                return null;
            }

            // Find the field
            FieldSymbol field = resourceSymbol.findField(fieldName);
            if (field == null) {
                errorReporter.error(
                    "Field '" + fieldName + "' not found in " + resourceSymbol.getName(), ctx.line,
                    ctx.column);
                return null;
            }

            return new ResourceFieldTarget(concreteObjectValue, resourceSymbol, field, fieldName,
                ctx.line, ctx.column);
        }

        // If we reach here, the left-hand side is not a valid lvalue
        errorReporter.error("Invalid assignment target", ctx.line, ctx.column);
        return null;
    }

    /**
     * Determines the assignment operator from the parse tree node.
     */
    @Nullable
    private AssignmentOperator determineAssignmentOperator(
        @Nonnull ResoParser.AssignmentStatementContext stmt) {
        if (stmt.ASSIGN() != null) {
            return AssignmentOperator.ASSIGN;
        }
        if (stmt.PLUS_ASSIGN() != null) {
            return AssignmentOperator.PLUS_ASSIGN;
        }
        if (stmt.MINUS_ASSIGN() != null) {
            return AssignmentOperator.MINUS_ASSIGN;
        }
        if (stmt.MULT_ASSIGN() != null) {
            return AssignmentOperator.MULT_ASSIGN;
        }
        if (stmt.DIV_ASSIGN() != null) {
            return AssignmentOperator.DIV_ASSIGN;
        }
        if (stmt.REM_ASSIGN() != null) {
            return AssignmentOperator.REM_ASSIGN;
        }
        if (stmt.MOD_ASSIGN() != null) {
            return AssignmentOperator.MOD_ASSIGN;
        }
        if (stmt.AND_ASSIGN() != null) {
            return AssignmentOperator.AND_ASSIGN;
        }
        if (stmt.OR_ASSIGN() != null) {
            return AssignmentOperator.OR_ASSIGN;
        }
        if (stmt.XOR_ASSIGN() != null) {
            return AssignmentOperator.XOR_ASSIGN;
        }
        if (stmt.LSHIFT_ASSIGN() != null) {
            return AssignmentOperator.LSHIFT_ASSIGN;
        }
        if (stmt.RSHIFT_ASSIGN() != null) {
            return AssignmentOperator.RSHIFT_ASSIGN;
        }
        return null;
    }

    /**
     * Evaluates the right-hand side expression.
     */
    @Nullable
    private ResoValue evaluateRightHandExpression(@Nonnull AssignmentContext ctx) {
        return context.getExpressionGenerator().visit(ctx.stmt.expression(1));
    }

    /**
     * Generates the value to be stored based on the assignment type.
     */
    @Nullable
    private IrValue generateStoreValue(@Nonnull AssignmentTarget target,
                                       @Nonnull ResoValue rightValue,
                                       @Nonnull AssignmentContext ctx) {
        if (ctx.operator.isSimpleAssignment()) {
            // Simple assignment: just concretize the right-hand value
            ConcreteResoValue concretizedValue =
                rightValue.concretize(target.getType(), errorReporter);
            return concretizedValue != null ? concretizedValue.getValue() : null;
        } else {
            // Compound assignment: perform binary operation
            ResoValue leftValue = target.getCurrentValue();
            if (leftValue == null) {
                errorReporter.error("Cannot perform compound assignment: current value unavailable",
                    ctx.line, ctx.column);
                return null;
            }

            ResoValue resultValue =
                performBinaryOperation(leftValue, rightValue, ctx.operator, ctx);
            if (resultValue == null) {
                return null; // Error already reported
            }

            ConcreteResoValue concretizedValue =
                resultValue.concretize(target.getType(), errorReporter);

            return concretizedValue != null ? concretizedValue.getValue() : null;
        }
    }

    /**
     * Performs the binary operation for compound assignments.
     */
    @Nullable
    private ResoValue performBinaryOperation(@Nonnull ResoValue leftValue,
                                             @Nonnull ResoValue rightValue,
                                             @Nonnull AssignmentOperator operator,
                                             @Nonnull AssignmentContext ctx) {
        final String binaryOp = operator.getBinaryOperator();

        if (operator.isArithmetic()) {
            return context.getExpressionGenerator().getArithmeticGenerator()
                .generateArithmeticExpr(leftValue, rightValue, binaryOp, ctx.line, ctx.column);
        } else if (operator.isBitwise()) {
            return context.getExpressionGenerator().getBitwiseGenerator()
                .generateBitwiseExpr(leftValue, rightValue, binaryOp, ctx.line, ctx.column);
        } else {
            errorReporter.error(
                "Unsupported compound assignment operator: " + operator.getOperator(),
                ctx.line, ctx.column);
            return null;
        }
    }

    /**
     * Context class to hold assignment-related information.
     */
    private record AssignmentContext(ResoParser.AssignmentStatementContext stmt,
                                     AssignmentOperator operator, int line, int column) {
        private AssignmentContext(@Nonnull ResoParser.AssignmentStatementContext stmt,
                                  @Nonnull AssignmentOperator operator,
                                  int line,
                                  int column) {
            this.stmt = stmt;
            this.operator = operator;
            this.line = line;
            this.column = column;
        }
    }
}