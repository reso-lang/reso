package com.reso.compiler.codegen.expressions;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for logical expressions with short-circuit evaluation.
 * This class handles the generation of LLVM IR code for logical AND and OR
 * expressions, implementing proper short-circuit evaluation semantics.
 */
public class LogicalExpressionGenerator {

    private final CodeGenerationContext context;

    public LogicalExpressionGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates code for a logical AND expression with short-circuit evaluation.
     *
     * @param expr The logical AND expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateLogicalAndExpr(@Nullable ResoParser.LogicalAndExprContext expr) {
        return expr == null ? null : generateLogicalExpression(
            expr.expression(0),
            expr.expression(1),
            expr.getStart().getLine(),
            expr.getStart().getCharPositionInLine(),
            true
        );
    }

    /**
     * Generates code for a logical OR expression with short-circuit evaluation.
     *
     * @param expr The logical OR expression
     * @return The result of the expression
     */
    @Nullable
    public ResoValue generateLogicalOrExpr(@Nullable ResoParser.LogicalOrExprContext expr) {
        return expr == null ? null : generateLogicalExpression(expr.expression(0),
            expr.expression(1),
            expr.getStart().getLine(),
            expr.getStart().getCharPositionInLine(),
            false
        );
    }

    /**
     * Generates logical expressions using PHI nodes.
     */
    @Nullable
    private ResoValue generateLogicalExpression(
        @Nonnull ResoParser.ExpressionContext leftExpr,
        @Nonnull ResoParser.ExpressionContext rightExpr,
        int line,
        int column,
        boolean isAnd) {
        Objects.requireNonNull(leftExpr, "Left expression cannot be null");
        Objects.requireNonNull(rightExpr, "Right expression cannot be null");

        IrValue currentFunction = context.getCurrentFunction();
        if (currentFunction == null) {
            context.getErrorReporter().error(
                "Logical expressions must be inside a function",
                line, column);
            return null;
        }

        IrBuilder builder = context.getIrBuilder();

        // Evaluate left operand
        ConcreteResoValue leftValue = evaluateOperand(leftExpr, line, column);
        if (leftValue == null) {
            return null;
        }

        IrBasicBlock originalBlock = IrFactory.getCurrentBasicBlock(builder);

        // Create basic blocks
        IrBasicBlock evalRightBlock = IrFactory.createBasicBlock(currentFunction, "eval_right");
        IrBasicBlock mergeBlock = IrFactory.createBasicBlock(currentFunction, "merge");

        IrValue shortCircuitValue = IrFactory.createConstantBool(context.getIrContext(), !isAnd);

        // Conditional branch based on left operand
        if (isAnd) {
            // AND: if left is true, evaluate right; else short-circuit to false
            IrFactory.createCondBr(builder, leftValue.getValue(), evalRightBlock, mergeBlock);
        } else {
            // OR: if left is false, evaluate right; else short-circuit to true
            IrFactory.createCondBr(builder, leftValue.getValue(), mergeBlock, evalRightBlock);
        }

        // Evaluate right operand block
        IrFactory.positionAtEnd(builder, evalRightBlock);
        ConcreteResoValue rightValue = evaluateOperand(rightExpr, line, column);
        if (rightValue == null) {
            return null;
        }

        IrFactory.createBr(builder, mergeBlock);
        IrBasicBlock rightEvalEndBlock = IrFactory.getCurrentBasicBlock(builder);

        // Merge block with PHI node
        IrFactory.positionAtEnd(builder, mergeBlock);

        // Create PHI node to merge the two possible values
        ResoType boolType = context.getTypeSystem().getBoolType();
        IrValue phi = IrFactory.createPhi(builder, boolType.getType(), "logical_result");

        // Add incoming values to PHI:
        // - From original block: short-circuit value
        // - From right eval block: right operand result
        IrFactory.addIncoming(phi, shortCircuitValue, originalBlock);
        IrFactory.addIncoming(phi, rightValue.getValue(), rightEvalEndBlock);

        return new ResoValue(boolType, phi, line,
            column);
    }

    @Nullable
    private ConcreteResoValue evaluateOperand(@Nonnull ResoParser.ExpressionContext operandExpr,
                                              int line, int column) {
        ResoValue operand = context.getExpressionGenerator().visit(operandExpr);
        if (operand == null) {
            return null;
        }

        ResoType boolType = context.getTypeSystem().getBoolType();
        ConcreteResoValue concreteOperand =
            operand.concretize(boolType, context.getErrorReporter());

        if (concreteOperand == null) {
            context.getErrorReporter().error(
                "Logical operation requires boolean operands, got " + operand.getTypeName(),
                line,
                column
            );
        }

        return concreteOperand;
    }
}