package com.reso.llvm.core;

import static com.reso.llvm.core.IrTypeImpl.getTargetPointerSize;

import com.reso.llvm.api.IrBasicBlock;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import com.reso.llvm.enums.IrIntPredicate;
import com.reso.llvm.enums.IrRealPredicate;
import com.reso.llvm.exception.IrException;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

/**
 * Implementation of ResoBuilder that wraps an LLVMBuilderRef.
 */
@NotThreadSafe
public class IrBuilderImpl implements IrBuilder {
    private final LLVMBuilderRef builderRef;
    private final IrContextImpl context;
    private boolean disposed = false;

    /**
     * Creates a new builder in the given context.
     *
     * @param context The context to create the builder in
     * @return A new builder
     */
    @Nonnull
    public static IrBuilderImpl create(@Nonnull IrContextImpl context) {
        Objects.requireNonNull(context, "Context cannot be null");
        return new IrBuilderImpl(
            LLVM.LLVMCreateBuilderInContext(context.getLlvmContext()),
            context
        );
    }

    /**
     * Wraps an existing LLVMBuilderRef.
     *
     * @param builderRef The LLVM builder reference to wrap
     * @param context    The context this builder belongs to
     */
    public IrBuilderImpl(@Nonnull LLVMBuilderRef builderRef, @Nonnull IrContextImpl context) {
        this.builderRef = Objects.requireNonNull(builderRef, "Builder reference cannot be null");
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Gets the underlying LLVM builder reference.
     *
     * @return The underlying LLVM builder reference
     * @throws IllegalStateException if the builder has been disposed
     */
    @Nonnull
    public LLVMBuilderRef getLlvmBuilder() {
        if (disposed) {
            throw new IllegalStateException("Builder has been disposed");
        }
        if (context.isDisposed()) {
            throw new IllegalStateException("Context has been disposed");
        }
        return builderRef;
    }

    @Override
    @Nonnull
    public IrContextImpl getContext() {
        return context;
    }

    /**
     * Positions the builder at the end of the given basic block.
     *
     * @param builder The builder to position
     * @param block   The block to position at
     */
    public static void positionAtEnd(@Nonnull IrBuilderImpl builder,
                                     @Nonnull IrBasicBlockImpl block) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(block, "Block cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        LLVM.LLVMPositionBuilderAtEnd(builder.getLlvmBuilder(), block.getLlvmBasicBlock());
    }

    /**
     * Positions the builder before the given instruction.
     *
     * @param builder     The builder to position
     * @param instruction The instruction to position before
     */
    public static void positionBefore(@Nonnull IrBuilderImpl builder,
                                      @Nonnull IrValueImpl instruction) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(instruction, "Instruction cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        LLVM.LLVMPositionBuilderBefore(builder.getLlvmBuilder(), instruction.getLlvmValue());
    }

    @Override
    @Nullable
    public IrBasicBlock getCurrentBlock() {
        if (disposed) {
            throw new IllegalStateException("Builder has been disposed");
        }

        LLVMBuilderRef builder = getLlvmBuilder();
        LLVMBasicBlockRef blockRef = LLVM.LLVMGetInsertBlock(builder);
        if (blockRef == null) {
            return null;
        }

        return new IrBasicBlockImpl(blockRef, context);
    }

    /**
     * Creates a return instruction.
     *
     * @param builder The builder to use
     * @param value   The value to return, or null for void return
     * @return The created return instruction
     */
    @Nonnull
    public static IrValue createReturn(@Nonnull IrBuilderImpl builder,
                                       @Nullable IrValueImpl value) {
        Objects.requireNonNull(builder, "Builder cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            IrTypeImpl voidType = IrTypeImpl.createVoidType(builder.getContext());
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef returnInst;
            if (value == null) {
                // Return void
                returnInst = LLVM.LLVMBuildRetVoid(builderRef);
            } else {
                // Return a value
                returnInst = LLVM.LLVMBuildRet(builderRef, value.getLlvmValue());
            }

            return new IrValueImpl(returnInst, voidType);
        } catch (Exception e) {
            throw new IrException("Failed to create return instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an allocation instruction.
     *
     * @param builder The builder to use
     * @param type    The type to allocate
     * @param name    The name of the allocation
     * @return The created allocation instruction
     */
    @Nonnull
    public static IrValue createAlloca(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrTypeImpl type,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef allocaInst = LLVM.LLVMBuildAlloca(
                builderRef,
                type.getLlvmType(),
                name
            );

            return new IrValueImpl(allocaInst, type);
        } catch (Exception e) {
            throw new IrException("Failed to create alloca instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a load instruction.
     *
     * @param builder The builder to use
     * @param value   The value to load from
     * @param type    The type of the value being loaded
     * @param name    The name of the load
     * @return The created load instruction
     */
    @Nonnull
    public static IrValue createLoad(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull IrTypeImpl type,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef loadInst = LLVM.LLVMBuildLoad2(
                builderRef,
                type.getLlvmType(),
                value.getLlvmValue(),
                name
            );

            return new IrValueImpl(loadInst, type);
        } catch (Exception e) {
            throw new IrException("Failed to create load instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a store instruction.
     *
     * @param builder The builder to use
     * @param store   The value to store
     * @param value   The pointer to store to
     * @return The created store instruction
     */
    @Nonnull
    public static IrValue createStore(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl store,
        @Nonnull IrValueImpl value) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(store, "Store cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef storeInst = LLVM.LLVMBuildStore(
                builderRef,
                store.getLlvmValue(),
                value.getLlvmValue()
            );

            return new IrValueImpl(storeInst, (IrTypeImpl) value.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create store instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an add instruction for integers.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created add instruction
     */
    @Nonnull
    public static IrValue createAdd(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isInteger() || !right.isInteger()) {
            throw new IllegalArgumentException("Operands must be integers");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef addInst = LLVM.LLVMBuildAdd(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(addInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create add instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a subtraction instruction for integers.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created subtraction instruction
     */
    @Nonnull
    public static IrValue createSub(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isInteger() || !right.isInteger()) {
            throw new IllegalArgumentException("Operands must be integers");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef subInst = LLVM.LLVMBuildSub(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(subInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create sub instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a multiplication instruction for integers.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created multiplication instruction
     */
    @Nonnull
    public static IrValue createMul(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isInteger() || !right.isInteger()) {
            throw new IllegalArgumentException("Operands must be integers");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef mulInst = LLVM.LLVMBuildMul(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(mulInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create mul instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a signed division instruction for integers.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created division instruction
     */
    @Nonnull
    public static IrValue createSDiv(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isInteger() || !right.isInteger()) {
            throw new IllegalArgumentException("Operands must be integers");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef divInst = LLVM.LLVMBuildSDiv(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(divInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create sdiv instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a signed remainder instruction for integers.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created remainder instruction
     */
    @Nonnull
    public static IrValue createSRem(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isInteger() || !right.isInteger()) {
            throw new IllegalArgumentException("Operands must be integers");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef remInst = LLVM.LLVMBuildSRem(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(remInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create srem instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an unsigned division instruction for integers.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created division instruction
     */
    @Nonnull
    public static IrValue createUDiv(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isInteger() || !right.isInteger()) {
            throw new IllegalArgumentException("Operands must be integers");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef divInst = LLVM.LLVMBuildUDiv(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(divInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create udiv instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an unsigned remainder instruction for integers.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created remainder instruction
     **/
    @Nonnull
    public static IrValue createURem(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isInteger() || !right.isInteger()) {
            throw new IllegalArgumentException("Operands must be integers");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef remInst = LLVM.LLVMBuildURem(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(remInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create urem instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a floating-point addition instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created addition instruction
     */
    @Nonnull
    public static IrValue createFAdd(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isFloatingPoint() || !right.isFloatingPoint()) {
            throw new IllegalArgumentException("Operands must be floating-point values");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef addInst = LLVM.LLVMBuildFAdd(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(addInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create fadd instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a floating-point subtraction instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created subtraction instruction
     */
    @Nonnull
    public static IrValue createFSub(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isFloatingPoint() || !right.isFloatingPoint()) {
            throw new IllegalArgumentException("Operands must be floating-point values");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef subInst = LLVM.LLVMBuildFSub(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(subInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create fsub instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a floating-point multiplication instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created multiplication instruction
     */
    @Nonnull
    public static IrValue createFMul(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isFloatingPoint() || !right.isFloatingPoint()) {
            throw new IllegalArgumentException("Operands must be floating-point values");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef mulInst = LLVM.LLVMBuildFMul(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(mulInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create fmul instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a floating-point division instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created division instruction
     */
    @Nonnull
    public static IrValue createFDiv(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isFloatingPoint() || !right.isFloatingPoint()) {
            throw new IllegalArgumentException("Operands must be floating-point values");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef divInst = LLVM.LLVMBuildFDiv(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(divInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create fdiv instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a floating-point remainder instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created remainder instruction
     */
    @Nonnull
    public static IrValue createFRem(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isFloatingPoint() || !right.isFloatingPoint()) {
            throw new IllegalArgumentException("Operands must be floating-point values");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef remInst = LLVM.LLVMBuildFRem(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(remInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create frem instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an integer comparison instruction.
     *
     * @param builder   The builder to use
     * @param predicate The comparison predicate
     * @param left      The left operand
     * @param right     The right operand
     * @param name      The name of the result
     * @return The created comparison instruction
     */
    @Nonnull
    public static IrValue createICmp(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrIntPredicate predicate,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();
            IrTypeImpl boolType = IrTypeImpl.createBoolType(builder.getContext());

            LLVMValueRef cmpInst = LLVM.LLVMBuildICmp(
                builderRef,
                predicate.getPredicate(),
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(cmpInst, boolType);
        } catch (Exception e) {
            throw new IrException("Failed to create icmp instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a floating-point comparison instruction.
     *
     * @param builder   The builder to use
     * @param predicate The comparison predicate
     * @param left      The left operand
     * @param right     The right operand
     * @param name      The name of the result
     * @return The created comparison instruction
     */
    @Nonnull
    public static IrValue createFCmp(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrRealPredicate predicate,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isFloatingPoint() || !right.isFloatingPoint()) {
            throw new IllegalArgumentException("Operands must be floating-point values");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();
            IrTypeImpl boolType = IrTypeImpl.createBoolType(builder.getContext());

            LLVMValueRef cmpInst = LLVM.LLVMBuildFCmp(
                builderRef,
                predicate.getPredicate(),
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(cmpInst, boolType);
        } catch (Exception e) {
            throw new IrException("Failed to create fcmp instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an unconditional branch instruction.
     *
     * @param builder The builder to use
     * @param dest    The destination basic block
     * @return The created branch instruction
     */
    @Nonnull
    public static IrValue createBr(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrBasicBlockImpl dest) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(dest, "Destination block cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();
            IrTypeImpl voidType = IrTypeImpl.createVoidType(builder.getContext());

            LLVMValueRef brInst = LLVM.LLVMBuildBr(
                builderRef,
                dest.getLlvmBasicBlock()
            );

            return new IrValueImpl(brInst, voidType);
        } catch (Exception e) {
            throw new IrException("Failed to create br instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a conditional branch instruction.
     *
     * @param builder   The builder to use
     * @param condition The condition value (must be a boolean)
     * @param thenBlock The block to branch to if the condition is true
     * @param elseBlock The block to branch to if the condition is false
     * @return The created branch instruction
     */
    @Nonnull
    public static IrValue createCondBr(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl condition,
        @Nonnull IrBasicBlockImpl thenBlock,
        @Nonnull IrBasicBlockImpl elseBlock) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(condition, "Condition cannot be null");
        Objects.requireNonNull(thenBlock, "Then block cannot be null");
        Objects.requireNonNull(elseBlock, "Else block cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!condition.isBoolean()) {
            throw new IllegalArgumentException("Condition must be a boolean value");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();
            IrTypeImpl voidType = IrTypeImpl.createVoidType(builder.getContext());

            LLVMValueRef brInst = LLVM.LLVMBuildCondBr(
                builderRef,
                condition.getLlvmValue(),
                thenBlock.getLlvmBasicBlock(),
                elseBlock.getLlvmBasicBlock()
            );

            return new IrValueImpl(brInst, voidType);
        } catch (Exception e) {
            throw new IrException("Failed to create condbr instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a select instruction.
     *
     * @param builder    The builder to use
     * @param condition  The condition value
     * @param trueValue  The true value
     * @param falseValue The false value
     * @param name       The name of the result
     * @return The created select instruction
     */
    @Nonnull
    public static IrValue createSelect(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl condition,
        @Nonnull IrValueImpl trueValue,
        @Nonnull IrValueImpl falseValue,
        @Nonnull String name) {

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef selectInst = LLVM.LLVMBuildSelect(
                builderRef,
                condition.getLlvmValue(),
                trueValue.getLlvmValue(),
                falseValue.getLlvmValue(),
                name
            );

            return new IrValueImpl(selectInst, (IrTypeImpl) trueValue.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create select instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the current basic block where this builder is positioned.
     *
     * @param builder The builder implementation
     * @return The current basic block, or null if not positioned in any block
     */
    @Nullable
    public static IrBasicBlock getCurrentBasicBlock(@Nonnull IrBuilderImpl builder) {
        Objects.requireNonNull(builder, "Builder cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();
            LLVMBasicBlockRef currentBlock = LLVM.LLVMGetInsertBlock(builderRef);

            if (currentBlock == null) {
                return null;
            }

            return new IrBasicBlockImpl(currentBlock, builder.getContext());
        } catch (Exception e) {
            throw new IrException("Failed to get current basic block: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a PHI node instruction.
     *
     * @param builder The builder implementation
     * @param type    The type of the PHI node
     * @param name    The name of the PHI node
     * @return The created PHI node
     */
    @Nonnull
    public static IrValue createPhi(@Nonnull IrBuilderImpl builder, @Nonnull IrTypeImpl type,
                                    @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            // Create PHI node with specified type
            LLVMValueRef phiInst = LLVM.LLVMBuildPhi(
                builderRef,
                type.getLlvmType(),
                name
            );

            return new IrValueImpl(phiInst, type);
        } catch (Exception e) {
            throw new IrException("Failed to create phi instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Adds an incoming value to a PHI node.
     *
     * @param phi   The PHI node
     * @param value The incoming value
     * @param block The basic block where this value comes from
     */
    public static void addIncoming(@Nonnull IrValueImpl phi, @Nonnull IrValueImpl value,
                                   @Nonnull IrBasicBlockImpl block) {
        Objects.requireNonNull(phi, "PHI node cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(block, "Block cannot be null");

        try {
            PointerPointer<LLVMValueRef> valuesPtr = new PointerPointer<>(1);
            valuesPtr.put(0, value.getLlvmValue());

            PointerPointer<LLVMBasicBlockRef> blocksPtr = new PointerPointer<>(1);
            blocksPtr.put(0, block.getLlvmBasicBlock());

            LLVM.LLVMAddIncoming(
                phi.getLlvmValue(),
                valuesPtr,
                blocksPtr,
                1  // Number of incoming values being added
            );

            // Clean up pointers
            valuesPtr.deallocate();
            blocksPtr.deallocate();
        } catch (Exception e) {
            throw new IrException("Failed to add incoming value to PHI node: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a function call instruction.
     *
     * @param builder  The builder to use
     * @param function The function to call
     * @param args     The arguments to pass to the function
     * @param name     The name of the result
     * @return The created call instruction
     */
    @Nonnull
    public static IrValue createCall(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl function,
        @Nonnull IrValueImpl[] args,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(function, "Function cannot be null");
        Objects.requireNonNull(args, "Arguments cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!function.isFunction()) {
            throw new IllegalArgumentException("Not a function: " + function);
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            // Get function type
            IrTypeImpl functionType = (IrTypeImpl) function.getType();

            // Get return type
            IrTypeImpl returnType = (IrTypeImpl) functionType.getReturnType();
            if (returnType == null) {
                returnType = IrTypeImpl.createVoidType(builder.getContext());
            }

            // Build the call
            LLVMValueRef callInst;

            if (args.length == 0) {
                // No arguments
                callInst = LLVM.LLVMBuildCall2(
                    builderRef,
                    functionType.getLlvmType(),
                    function.getLlvmValue(),
                    null,
                    0,
                    name
                );
            } else {
                // Use the arguments
                LLVMValueRef[] argValues = new LLVMValueRef[args.length];
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null) {
                        throw new IllegalArgumentException("Argument " + i + " cannot be null");
                    }
                    argValues[i] = args[i].getLlvmValue();
                }

                try (PointerPointer<LLVMValueRef> argPointer = new PointerPointer<>(argValues)) {
                    callInst = LLVM.LLVMBuildCall2(
                        builderRef,
                        functionType.getLlvmType(),
                        function.getLlvmValue(),
                        argPointer,
                        args.length,
                        name
                    );
                }
            }

            return new IrValueImpl(callInst, returnType);
        } catch (Exception e) {
            throw new IrException("Failed to create call instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a floating-point to signed integer conversion instruction.
     *
     * @param builder  The builder to use
     * @param value    The floating-point value to convert
     * @param destType The destination signed integer type
     * @param name     The name of the result
     * @return The created conversion instruction
     */
    @Nonnull
    public static IrValue createSIToFP(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull IrTypeImpl destType,
        @Nonnull String name) {

        LLVMBuilderRef builderRef = builder.getLlvmBuilder();
        LLVMValueRef result = LLVM.LLVMBuildSIToFP(
            builderRef,
            value.getLlvmValue(),
            destType.getLlvmType(),
            name
        );

        return new IrValueImpl(result, destType);
    }

    /**
     * Creates a floating-point to signed integer conversion instruction.
     *
     * @param builder  The builder to use
     * @param value    The floating-point value to convert
     * @param destType The destination signed integer type
     * @param name     The name of the result
     * @return The created conversion instruction
     */
    @Nonnull
    public static IrValue createFPToSI(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull IrTypeImpl destType,
        @Nonnull String name) {

        LLVMBuilderRef builderRef = builder.getLlvmBuilder();
        LLVMValueRef result = LLVM.LLVMBuildFPToSI(
            builderRef,
            value.getLlvmValue(),
            destType.getLlvmType(),
            name
        );

        return new IrValueImpl(result, destType);
    }

    /**
     * Creates a sign extension instruction.
     *
     * @param builder  The builder to use
     * @param value    The integer value to extend
     * @param destType The destination integer type
     * @param name     The name of the result
     * @return The sign extension instruction
     */
    @Nonnull
    public static IrValue createSExt(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull IrTypeImpl destType,
        @Nonnull String name) {

        LLVMBuilderRef builderRef = builder.getLlvmBuilder();
        LLVMValueRef result = LLVM.LLVMBuildSExt(
            builderRef,
            value.getLlvmValue(),
            destType.getLlvmType(),
            name
        );

        return new IrValueImpl(result, destType);
    }

    /**
     * Creates a floating-point to unsigned integer conversion instruction.
     *
     * @param builder  The builder to use
     * @param value    The floating-point value to convert
     * @param destType The destination unsigned integer type
     * @param name     The name of the result
     * @return The created conversion instruction
     */
    @Nonnull
    public static IrValue createUIToFP(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull IrTypeImpl destType,
        @Nonnull String name) {

        LLVMBuilderRef builderRef = builder.getLlvmBuilder();
        LLVMValueRef result = LLVM.LLVMBuildUIToFP(
            builderRef,
            value.getLlvmValue(),
            destType.getLlvmType(),
            name
        );

        return new IrValueImpl(result, destType);
    }

    /**
     * Creates a floating-point to unsigned integer conversion instruction.
     *
     * @param builder  The builder to use
     * @param value    The floating-point value to convert
     * @param destType The destination unsigned integer type
     * @param name     The name of the result
     * @return The created conversion instruction
     */
    @Nonnull
    public static IrValue createFPToUI(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull IrTypeImpl destType,
        @Nonnull String name) {

        LLVMBuilderRef builderRef = builder.getLlvmBuilder();
        LLVMValueRef result = LLVM.LLVMBuildFPToUI(
            builderRef,
            value.getLlvmValue(),
            destType.getLlvmType(),
            name
        );

        return new IrValueImpl(result, destType);
    }

    /**
     * Creates an unsigned extension instruction.
     *
     * @param builder  The builder to use
     * @param value    The integer value to extend
     * @param destType The destination integer type
     * @param name     The name of the result
     * @return The sign extension instruction
     */
    @Nonnull
    public static IrValue createZExt(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull IrTypeImpl destType,
        @Nonnull String name) {

        LLVMBuilderRef builderRef = builder.getLlvmBuilder();
        LLVMValueRef result = LLVM.LLVMBuildZExt(
            builderRef,
            value.getLlvmValue(),
            destType.getLlvmType(),
            name
        );

        return new IrValueImpl(result, destType);
    }

    /**
     * Creates a truncation instruction.
     *
     * @param builder  The builder to use
     * @param value    The integer value to truncate
     * @param destType The destination integer type
     * @param name     The name of the result
     * @return The truncation instruction
     */
    @Nonnull
    public static IrValue createTrunc(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull IrTypeImpl destType,
        @Nonnull String name) {

        LLVMBuilderRef builderRef = builder.getLlvmBuilder();
        LLVMValueRef result = LLVM.LLVMBuildTrunc(
            builderRef,
            value.getLlvmValue(),
            destType.getLlvmType(),
            name
        );

        return new IrValueImpl(result, destType);
    }

    /**
     * Creates a floating-point extension instruction.
     *
     * @param builder  The builder to use
     * @param value    The floating-point value to extend
     * @param destType The destination floating-point type
     * @param name     The name of the result
     * @return The floating-point extension instruction
     */
    @Nonnull
    public static IrValue createFPExt(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull IrTypeImpl destType,
        @Nonnull String name) {

        LLVMBuilderRef builderRef = builder.getLlvmBuilder();
        LLVMValueRef result = LLVM.LLVMBuildFPExt(
            builderRef,
            value.getLlvmValue(),
            destType.getLlvmType(),
            name
        );

        return new IrValueImpl(result, destType);
    }

    /**
     * Creates a floating-point truncation instruction.
     *
     * @param builder  The builder to use
     * @param value    The floating-point value to truncate
     * @param destType The destination floating-point type
     * @param name     The name of the result
     * @return The floating-point truncation instruction
     */
    @Nonnull
    public static IrValue createFPTrunc(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull IrTypeImpl destType,
        @Nonnull String name) {

        LLVMBuilderRef builderRef = builder.getLlvmBuilder();
        LLVMValueRef result = LLVM.LLVMBuildFPTrunc(
            builderRef,
            value.getLlvmValue(),
            destType.getLlvmType(),
            name
        );

        return new IrValueImpl(result, destType);
    }

    /**
     * Creates a bitwise AND instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created AND instruction
     */
    @Nonnull
    public static IrValue createAnd(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef andInst = LLVM.LLVMBuildAnd(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(andInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create and instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a bitwise OR instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created OR instruction
     */
    @Nonnull
    public static IrValue createOr(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef orInst = LLVM.LLVMBuildOr(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(orInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create or instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a bitwise XOR instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created XOR instruction
     */
    @Nonnull
    public static IrValue createXor(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef xorInst = LLVM.LLVMBuildXor(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(xorInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create xor instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a left shift instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created left shift instruction
     */
    @Nonnull
    public static IrValue createShl(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isInteger() || !right.isInteger()) {
            throw new IllegalArgumentException("Operands must be integers");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef shlInst = LLVM.LLVMBuildShl(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(shlInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create shl instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an arithmetic right shift instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created arithmetic right shift instruction
     */
    @Nonnull
    public static IrValue createAShr(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isInteger() || !right.isInteger()) {
            throw new IllegalArgumentException("Operands must be integers");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef ashrInst = LLVM.LLVMBuildAShr(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(ashrInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create ashr instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a logical right shift instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created logical right shift instruction
     */
    @Nonnull
    public static IrValue createLShr(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl left,
        @Nonnull IrValueImpl right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!left.isInteger() || !right.isInteger()) {
            throw new IllegalArgumentException("Operands must be integers");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef lshrInst = LLVM.LLVMBuildLShr(
                builderRef,
                left.getLlvmValue(),
                right.getLlvmValue(),
                name
            );

            return new IrValueImpl(lshrInst, (IrTypeImpl) left.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create lshr instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a floating-point negation instruction.
     *
     * @param builder The builder to use
     * @param value   The floating-point value to negate
     * @param name    The name of the result
     * @return The created negation instruction
     */
    public static IrValue createFNeg(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!value.isFloatingPoint()) {
            throw new IllegalArgumentException("Value must be a floating-point value");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef negInst = LLVM.LLVMBuildFNeg(
                builderRef,
                value.getLlvmValue(),
                name
            );

            return new IrValueImpl(negInst, (IrTypeImpl) value.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create fneg instruction: " + e.getMessage(), e);
        }
    }


    /**
     *  Creates an integer negation instruction.
     *
     * @param builder The builder to use
     * @param value   The integer value to negate
     * @param name    The name of the result
     * @return The created negation instruction
     */
    @Nonnull
    public static IrValue createNeg(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        if (!value.isInteger()) {
            throw new IllegalArgumentException("Value must be an integer");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            LLVMValueRef negInst = LLVM.LLVMBuildNeg(
                builderRef,
                value.getLlvmValue(),
                name
            );

            return new IrValueImpl(negInst, (IrTypeImpl) value.getType());
        } catch (Exception e) {
            throw new IrException("Failed to create neg instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a struct GEP (GetElementPtr) instruction to access struct fields.
     *
     * @param builder    The builder to use
     * @param structType Type The type of the struct
     * @param structPtr  The pointer to the struct
     * @param fieldIndex The index of the field to access
     * @param name       The name of the result
     * @return The field pointer
     */
    @Nonnull
    public static IrValue createStructGEP(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrTypeImpl structType,
        @Nonnull IrValueImpl structPtr,
        int fieldIndex,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(structPtr, "Struct pointer cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (fieldIndex < 0) {
            throw new IllegalArgumentException("Field index cannot be negative");
        }

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            // Get the struct type that the pointer points to

            LLVMValueRef gepResult = LLVM.LLVMBuildStructGEP2(
                builderRef,
                structType.getLlvmType(),
                structPtr.getLlvmValue(),
                fieldIndex,
                name
            );

            // The result type is a pointer to the field type
            LLVMTypeRef fieldPtrType = LLVM.LLVMTypeOf(gepResult);
            IrTypeImpl resultType = new IrTypeImpl(fieldPtrType, structType.getContext());

            return new IrValueImpl(gepResult, resultType);

        } catch (Exception e) {
            throw new IrException("Failed to create struct GEP instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a GC_malloc call for garbage-collected memory allocation.
     */
    @Nonnull
    public static IrValue createGCMalloc(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrModuleImpl module,
        @Nonnull IrTypeImpl type,
        @Nonnull IrValueImpl gcFunc,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(gcFunc, "GC function cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();
            LLVMContextRef contextRef = ((IrContextImpl) module.getContext()).getLlvmContext();

            // Calculate the size of the type
            LLVMValueRef sizeValue = convertSizeValue(builderRef,
                LLVM.LLVMSizeOf(type.getLlvmType()),
                LLVM.LLVMIntTypeInContext(contextRef, getTargetPointerSize(module)));

            LLVMValueRef[] args = {sizeValue};

            // Call GC_malloc(size)
            LLVMValueRef gcMallocResult = LLVM.LLVMBuildCall2(
                builderRef,
                ((IrTypeImpl) gcFunc.getType()).getLlvmType(),
                gcFunc.getLlvmValue(),
                new PointerPointer<>(args),
                1,
                name
            );

            // Cast the result to the correct type
            LLVMTypeRef targetPtrType = LLVM.LLVMPointerType(type.getLlvmType(), 0);
            LLVMValueRef castResult = LLVM.LLVMBuildBitCast(
                builderRef,
                gcMallocResult,
                targetPtrType,
                name
            );

            IrTypeImpl resultType = new IrTypeImpl(targetPtrType, type.getContext());
            return new IrValueImpl(castResult, resultType);

        } catch (Exception e) {
            throw new IrException("Failed to create GC malloc instruction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a GC_malloc_atomic call for atomic (pointer-free) data.
     */
    @Nonnull
    public static IrValue createGCMallocAtomic(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrModuleImpl module,
        @Nonnull IrTypeImpl type,
        @Nonnull IrValueImpl gcAtomicFunc,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(gcAtomicFunc, "GC atomic function cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();
            LLVMContextRef contextRef = ((IrContextImpl) module.getContext()).getLlvmContext();

            // Calculate the size
            LLVMValueRef sizeValue = convertSizeValue(builderRef,
                LLVM.LLVMSizeOf(type.getLlvmType()),
                LLVM.LLVMIntTypeInContext(contextRef, getTargetPointerSize(module)));
            LLVMValueRef[] args = {sizeValue};

            // Call GC_malloc_atomic(size)
            LLVMValueRef gcResult = LLVM.LLVMBuildCall2(
                builderRef,
                ((IrTypeImpl) gcAtomicFunc.getType()).getLlvmType(),
                gcAtomicFunc.getLlvmValue(),
                new PointerPointer<>(args),
                1,
                name
            );

            // Cast to correct type
            LLVMTypeRef targetPtrType = LLVM.LLVMPointerType(type.getLlvmType(), 0);
            LLVMValueRef castResult = LLVM.LLVMBuildBitCast(
                builderRef,
                gcResult,
                targetPtrType,
                name
            );

            IrTypeImpl resultType = new IrTypeImpl(targetPtrType, type.getContext());
            return new IrValueImpl(castResult, resultType);

        } catch (Exception e) {
            throw new IrException("Failed to create GC atomic malloc: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a GC array allocation by calling GC_malloc with calculated array size.
     */
    @Nonnull
    public static IrValue createGCArrayMalloc(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrModuleImpl module,
        @Nonnull IrTypeImpl elementType,
        @Nonnull IrValueImpl count,
        @Nonnull IrValueImpl gcFunc,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(elementType, "Element type cannot be null");
        Objects.requireNonNull(count, "Count cannot be null");
        Objects.requireNonNull(gcFunc, "GC array function cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();
            LLVMContextRef contextRef = ((IrContextImpl) module.getContext()).getLlvmContext();

            // Calculate total size: sizeof(elementType) * count
            LLVMValueRef elementSize = convertSizeValue(builderRef,
                LLVM.LLVMSizeOf(elementType.getLlvmType()),
                LLVM.LLVMIntTypeInContext(contextRef, getTargetPointerSize(module)));

            LLVMValueRef totalSize = LLVM.LLVMBuildMul(
                builderRef,
                elementSize,
                count.getLlvmValue(),
                name + "_total_size"
            );

            LLVMValueRef[] args = {totalSize};

            // Call GC_malloc(totalSize)
            LLVMValueRef gcResult = LLVM.LLVMBuildCall2(
                builderRef,
                ((IrTypeImpl) gcFunc.getType()).getLlvmType(),
                gcFunc.getLlvmValue(),
                new PointerPointer<>(args),
                1,
                name
            );

            // Cast to element pointer type
            LLVMTypeRef targetPtrType = LLVM.LLVMPointerType(elementType.getLlvmType(), 0);
            LLVMValueRef castResult = LLVM.LLVMBuildBitCast(
                builderRef,
                gcResult,
                targetPtrType,
                name
            );

            IrTypeImpl resultType = new IrTypeImpl(targetPtrType, elementType.getContext());
            return new IrValueImpl(castResult, resultType);

        } catch (Exception e) {
            throw new IrException("Failed to create GC array malloc: " + e.getMessage(), e);
        }
    }

    private static LLVMValueRef convertSizeValue(@Nonnull LLVMBuilderRef builder,
                                                 @Nonnull LLVMValueRef sizeValue,
                                                 @Nonnull LLVMTypeRef sizeType) {
        int sizeValueBits = LLVM.LLVMGetIntTypeWidth(LLVM.LLVMTypeOf(sizeValue));
        int targetPointerBits = LLVM.LLVMGetIntTypeWidth(sizeType);
        if (sizeValueBits < targetPointerBits) {
            // Zero extend element size to match current size type
            return LLVM.LLVMBuildZExt(
                builder,
                sizeValue,
                sizeType,
                "element_size_zext"
            );
        } else if (sizeValueBits > targetPointerBits) {
            // Truncate element size to match current size type
            return LLVM.LLVMBuildTrunc(
                builder,
                sizeValue,
                sizeType,
                "element_size_trunc"
            );
        }
        return sizeValue;
    }

    /**
     * Creates an in-bounds GEP (GetElementPtr) instruction.
     * This is the preferred method for array access as it enables additional optimizations.
     *
     * @param builder     The builder implementation
     * @param elementType The element type
     * @param ptr         The base pointer
     * @param indices     The indices for the GEP
     * @param name        The name of the result
     * @return The created GEP instruction
     */
    @Nonnull
    public static IrValue createInBoundsGEP(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrTypeImpl elementType,
        @Nonnull IrValueImpl ptr,
        @Nonnull IrValueImpl[] indices,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(elementType, "Element type cannot be null");
        Objects.requireNonNull(ptr, "Pointer cannot be null");
        Objects.requireNonNull(indices, "Indices cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();

            // Convert indices to LLVM value references
            LLVMValueRef[] llvmIndices = new LLVMValueRef[indices.length];
            for (int i = 0; i < indices.length; i++) {
                llvmIndices[i] = indices[i].getLlvmValue();
            }

            // Create LLVM GEP instruction
            LLVMValueRef gepResult;

            // Use PointerPointer to pass the indices array to LLVM
            try (PointerPointer<LLVMValueRef> indicesPtr = new PointerPointer<>(llvmIndices)) {
                gepResult = LLVM.LLVMBuildInBoundsGEP2(
                    builderRef,
                    elementType.getLlvmType(),
                    ptr.getLlvmValue(),
                    indicesPtr,
                    indices.length,
                    name
                );
            }

            // The result type is a pointer to the element type
            IrTypeImpl resultType = IrTypeImpl.createPointerType(elementType, 0);

            return new IrValueImpl(gepResult, resultType);

        } catch (Exception e) {
            throw new IrException("Failed to create in-bounds GEP instruction: " + e.getMessage(),
                e);
        }
    }

    /**
     * Creates a memcpy intrinsic call using LLVM's memory copy function.
     *
     * @param builder  The IRBuilder instance
     * @param dst      Destination pointer
     * @param src      Source pointer
     * @param size     Number of bytes to copy
     * @param dstAlign Destination alignment
     * @param srcAlign Source alignment
     * @return The memcpy call instruction
     */
    @Nonnull
    public static IrValueImpl createMemCpy(@Nonnull IrBuilderImpl builder,
                                           @Nonnull IrValueImpl dst,
                                           @Nonnull IrValueImpl src,
                                           @Nonnull IrValueImpl size,
                                           int dstAlign,
                                           int srcAlign) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(dst, "Destination cannot be null");
        Objects.requireNonNull(src, "Source cannot be null");
        Objects.requireNonNull(size, "Size cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("IRBuilder has been disposed");
        }

        try {
            // Call LLVM's memcpy intrinsic
            LLVMValueRef memcpyCall = LLVM.LLVMBuildMemCpy(
                builder.getLlvmBuilder(),
                dst.getLlvmValue(),
                dstAlign,
                src.getLlvmValue(),
                srcAlign,
                size.getLlvmValue()
            );

            if (memcpyCall == null) {
                throw new IrException("Failed to create memcpy call");
            }

            return new IrValueImpl(memcpyCall, builder.getContext());

        } catch (Exception e) {
            throw new IrException("Failed to create memcpy: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a memmove intrinsic call using LLVM's memory move function.
     *
     * @param builder  The IRBuilder instance
     * @param dst      Destination pointer
     * @param src      Source pointer
     * @param size     Number of bytes to move
     * @param dstAlign Destination alignment
     * @param srcAlign Source alignment
     * @return The memmove call instruction
     */
    @Nonnull
    public static IrValueImpl createMemMove(@Nonnull IrBuilderImpl builder,
                                            @Nonnull IrValueImpl dst,
                                            @Nonnull IrValueImpl src,
                                            @Nonnull IrValueImpl size,
                                            int dstAlign,
                                            int srcAlign) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(dst, "Destination cannot be null");
        Objects.requireNonNull(src, "Source cannot be null");
        Objects.requireNonNull(size, "Size cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("IRBuilder has been disposed");
        }

        try {
            // Call LLVM's memmove intrinsic
            LLVMValueRef memmoveCall = LLVM.LLVMBuildMemMove(
                builder.getLlvmBuilder(),
                dst.getLlvmValue(),
                dstAlign,
                src.getLlvmValue(),
                srcAlign,
                size.getLlvmValue()
            );

            if (memmoveCall == null) {
                throw new IrException("Failed to create memmove call");
            }

            return new IrValueImpl(memmoveCall, builder.getContext());

        } catch (Exception e) {
            throw new IrException("Failed to create memmove: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a memset intrinsic call using LLVM's memory set function.
     *
     * @param builder The IRBuilder instance
     * @param ptr     Pointer to memory to set
     * @param value   Value to set (i8)
     * @param size    Number of bytes to set
     * @param align   Memory alignment
     * @param name    Instruction name
     * @return The memset call instruction
     */
    @Nonnull
    public static IrValueImpl createMemSet(@Nonnull IrBuilderImpl builder,
                                           @Nonnull IrValueImpl ptr,
                                           @Nonnull IrValueImpl value,
                                           @Nonnull IrValueImpl size,
                                           int align,
                                           @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(ptr, "Pointer cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(size, "Size cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("IRBuilder has been disposed");
        }

        try {
            // Call LLVM's memset intrinsic
            LLVMValueRef memsetCall = LLVM.LLVMBuildMemSet(
                builder.getLlvmBuilder(),
                ptr.getLlvmValue(),
                value.getLlvmValue(),
                size.getLlvmValue(),
                align
            );

            if (memsetCall == null) {
                throw new IrException("Failed to create memset call");
            }

            // Set name if provided
            if (!name.isEmpty()) {
                LLVM.LLVMSetValueName2(memsetCall, name, name.length());
            }

            return new IrValueImpl(memsetCall, builder.getContext());

        } catch (Exception e) {
            throw new IrException("Failed to create memset: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a bitcast instruction.
     * Bitcast reinterprets a value as a different type without changing the bit pattern.
     * The source and destination types must have the same size.
     *
     * @param builder  The builder to use
     * @param value    The value to cast
     * @param destType The destination type
     * @param name     The name of the result
     * @return The bitcast instruction
     */
    @Nonnull
    public static IrValue createBitCast(
        @Nonnull IrBuilderImpl builder,
        @Nonnull IrValueImpl value,
        @Nonnull IrTypeImpl destType,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(destType, "Destination type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (builder.isDisposed()) {
            throw new IllegalStateException("Builder has been disposed");
        }

        try {
            LLVMBuilderRef builderRef = builder.getLlvmBuilder();
            LLVMValueRef result = LLVM.LLVMBuildBitCast(
                builderRef,
                value.getLlvmValue(),
                destType.getLlvmType(),
                name
            );

            return new IrValueImpl(result, destType);
        } catch (Exception e) {
            throw new IrException("Failed to create bitcast instruction: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (!disposed) {
            LLVM.LLVMDisposeBuilder(builderRef);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}