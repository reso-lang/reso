package com.reso.llvm;

import com.reso.llvm.api.IrBasicBlock;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrContext;
import com.reso.llvm.api.IrModule;
import com.reso.llvm.api.IrPassBuilderOptions;
import com.reso.llvm.api.IrTarget;
import com.reso.llvm.api.IrTargetMachine;
import com.reso.llvm.api.IrType;
import com.reso.llvm.api.IrValue;
import com.reso.llvm.core.IrBasicBlockImpl;
import com.reso.llvm.core.IrBuilderImpl;
import com.reso.llvm.core.IrContextImpl;
import com.reso.llvm.core.IrModuleImpl;
import com.reso.llvm.core.IrPassBuilderOptionsImpl;
import com.reso.llvm.core.IrTargetImpl;
import com.reso.llvm.core.IrTargetMachineImpl;
import com.reso.llvm.core.IrTypeImpl;
import com.reso.llvm.core.IrValueImpl;
import com.reso.llvm.enums.IrIntPredicate;
import com.reso.llvm.enums.IrRealPredicate;
import com.reso.llvm.util.IrInitializer;
import java.math.BigInteger;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

/**
 * Central factory class for creating and managing LLVM wrapper objects.
 * This is the main entry point for the LLVM wrapper API.
 */
public final class IrFactory {

    private IrFactory() {
        throw new UnsupportedOperationException("Utility class, do not instantiate");
    }

    /**
     * Initializes the LLVM environment.
     * This must be called before any LLVM operations.
     */
    public static void initialize() {
        IrInitializer.initializeLlvm();
    }

    /**
     * Initializes the LLVM target machine components.
     * This is required for code generation and optimization.
     */
    public static void initializeTargetMachine() {
        IrInitializer.initializeTargetMachine();
    }

    /**
     * Creates a new LLVM context.
     *
     * @return A new context
     */
    @Nonnull
    public static IrContext createContext() {
        initialize();
        return IrContextImpl.create();
    }

    /**
     * Creates a new module in the given context.
     *
     * @param context The context to create the module in
     * @param name    The name of the module
     * @return A new module
     */
    @Nonnull
    public static IrModule createModule(@Nonnull IrContext context, @Nonnull String name) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrModuleImpl.create((IrContextImpl) context, name);
    }

    /**
     * Creates a new instruction builder in the given context.
     *
     * @param context The context to create the builder in
     * @return A new builder
     */
    @Nonnull
    public static IrBuilder createBuilder(@Nonnull IrContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        return IrBuilderImpl.create((IrContextImpl) context);
    }

    /**
     * Creates a basic block in a function.
     *
     * @param function The function to create the block in
     * @param name     The name of the block
     * @return A new basic block
     */
    @Nonnull
    public static IrBasicBlock createBasicBlock(@Nonnull IrValue function, @Nonnull String name) {
        Objects.requireNonNull(function, "Function cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!function.isFunction()) {
            throw new IllegalArgumentException("Not a function: " + function);
        }

        return IrBasicBlockImpl.create((IrValueImpl) function, name);
    }

    /**
     * Gets the entry basic block of a function.
     *
     * @param function The function
     * @return The entry basic block
     * @throws IllegalArgumentException if the value is not a function
     */
    @Nonnull
    public static IrBasicBlock getEntryBasicBlock(@Nonnull IrValue function) {
        Objects.requireNonNull(function, "Function cannot be null");

        if (!function.isFunction()) {
            throw new IllegalArgumentException("Not a function: " + function);
        }

        IrValueImpl functionImpl = (IrValueImpl) function;
        LLVMBasicBlockRef entryBlockRef = LLVM.LLVMGetEntryBasicBlock(functionImpl.getLlvmValue());
        return new IrBasicBlockImpl(entryBlockRef, functionImpl.getContext());
    }

    /**
     * Creates a target for the given triple.
     *
     * @param triple The target triple
     * @return A target
     */
    @Nonnull
    public static IrTarget createTarget(@Nonnull String triple) {
        Objects.requireNonNull(triple, "Triple cannot be null");
        return IrTargetImpl.fromTriple(triple);
    }

    /**
     * Creates a target machine for the host platform.
     *
     * @return A target machine
     */
    @Nonnull
    public static IrTargetMachine createHostTargetMachine() {
        return IrTargetMachineImpl.createHostTargetMachine();
    }

    /**
     * Gets the default target triple for the host platform.
     *
     * @return The default target triple
     */
    @Nonnull
    public static String getDefaultTargetTriple() {
        return IrTargetImpl.getDefaultTargetTriple();
    }

    /**
     * Gets the host CPU name.
     *
     * @return The host CPU name
     */
    @Nonnull
    public static String getHostCpuName() {
        return IrTargetImpl.getHostCpuName();
    }

    /**
     * Gets the host CPU features.
     *
     * @return The host CPU features
     */
    @Nonnull
    public static String getHostCpuFeatures() {
        return IrTargetImpl.getHostCpuFeatures();
    }

    /**
     * Creates pass builder options for optimization.
     *
     * @return Pass builder options
     */
    @Nonnull
    public static IrPassBuilderOptions createPassBuilderOptions() {
        return IrPassBuilderOptionsImpl.create();
    }

    /**
     * Verifies a module for correctness.
     *
     * @param module      The module to verify
     * @param printErrors Whether to print errors to stderr
     * @return true if the module is valid
     */
    public static boolean verifyModule(@Nonnull IrModule module, boolean printErrors) {
        Objects.requireNonNull(module, "Module cannot be null");
        return IrModuleImpl.verify((IrModuleImpl) module, printErrors);
    }

    /**
     * Optimizes a module using default options.
     *
     * @param module            The module to optimize
     * @param targetMachine     The target machine
     * @param optimizationLevel The optimization level (0-3)
     * @return true if optimization was successful
     */
    public static boolean optimizeModule(
        @Nonnull IrModule module,
        @Nonnull IrTargetMachine targetMachine,
        int optimizationLevel) {
        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(targetMachine, "Target machine cannot be null");
        return IrModuleImpl.optimizeModule((IrModuleImpl) module,
            (IrTargetMachineImpl) targetMachine, optimizationLevel);
    }

    // Types

    /**
     * Creates a boolean type.
     *
     * @param context The context to create the type in
     * @return A boolean type (i1)
     */
    @Nonnull
    public static IrType createBoolType(@Nonnull IrContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        return IrTypeImpl.createBoolType((IrContextImpl) context);
    }

    /**
     * Creates an 8-bit integer type.
     *
     * @param context The context to create the type in
     * @return An 8-bit integer type (i8)
     */
    @Nonnull
    public static IrType createi8Type(@Nonnull IrContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        return IrTypeImpl.createi8Type((IrContextImpl) context);
    }

    /**
     * Creates a 16-bit integer type.
     *
     * @param context The context to create the type in
     * @return A 16-bit integer type (i16)
     */
    @Nonnull
    public static IrType createi16Type(@Nonnull IrContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        return IrTypeImpl.createi16Type((IrContextImpl) context);
    }

    /**
     * Creates a 32-bit integer type.
     *
     * @param context The context to create the type in
     * @return A 32-bit integer type (i32)
     */
    @Nonnull
    public static IrType createi32Type(@Nonnull IrContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        return IrTypeImpl.createi32Type((IrContextImpl) context);
    }

    /**
     * Creates a 64-bit integer type.
     *
     * @param context The context to create the type in
     * @return A 64-bit integer type (i64)
     */
    @Nonnull
    public static IrType createi64Type(@Nonnull IrContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        return IrTypeImpl.createi64Type((IrContextImpl) context);
    }

    /**
     * Creates a 32-bit floating-point type.
     *
     * @param context The context to create the type in
     * @return A 32-bit floating-point type (float)
     */
    @Nonnull
    public static IrType createf32Type(@Nonnull IrContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        return IrTypeImpl.createf32Type((IrContextImpl) context);
    }

    /**
     * Creates a 64-bit floating-point type.
     *
     * @param context The context to create the type in
     * @return A 64-bit floating-point type (double)
     */
    @Nonnull
    public static IrType createf64Type(@Nonnull IrContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        return IrTypeImpl.createf64Type((IrContextImpl) context);
    }

    /**
     * Creates a void type.
     *
     * @param context The context to create the type in
     * @return A void type
     */
    @Nonnull
    public static IrType createVoidType(@Nonnull IrContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        return IrTypeImpl.createVoidType((IrContextImpl) context);
    }

    /**
     * Creates a pointer type.
     *
     * @param elementType  The element type
     * @param addressSpace The address space (usually 0)
     * @return A pointer type
     */
    @Nonnull
    public static IrType createPointerType(@Nonnull IrType elementType, int addressSpace) {
        Objects.requireNonNull(elementType, "Element type cannot be null");
        return IrTypeImpl.createPointerType((IrTypeImpl) elementType, addressSpace);
    }

    /**
     * Creates a function type.
     *
     * @param returnType The return type
     * @param paramTypes The parameter types
     * @param isVarArgs  Whether the function is variadic
     * @return A function type
     */
    @Nonnull
    public static IrType createFunctionType(
        @Nonnull IrType returnType,
        @Nonnull IrType[] paramTypes,
        boolean isVarArgs) {
        Objects.requireNonNull(returnType, "Return type cannot be null");
        Objects.requireNonNull(paramTypes, "Parameter types cannot be null");

        IrTypeImpl[] params = new IrTypeImpl[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Objects.requireNonNull(paramTypes[i], "Parameter type cannot be null");
            params[i] = (IrTypeImpl) paramTypes[i];
        }

        return IrTypeImpl.createFunctionType((IrTypeImpl) returnType, params, isVarArgs);
    }

    // Values

    /**
     * Creates a constant integer value.
     *
     * @param type       The integer type
     * @param value      The integer value
     * @param signExtend Whether to sign-extend the value
     * @return A constant integer value
     */
    @Nonnull
    public static IrValue createConstantInt(
        @Nonnull IrType type,
        long value,
        boolean signExtend) {
        Objects.requireNonNull(type, "Type cannot be null");
        return IrValueImpl.createConstantInt((IrTypeImpl) type, value, signExtend);
    }

    /**
     * Creates a constant integer value.
     *
     * @param type  The integer type
     * @param value The integer value
     * @return A constant integer value
     */
    @Nonnull
    public static IrValue createConstantInt(
        @Nonnull IrType type,
        BigInteger value) {
        Objects.requireNonNull(type, "Type cannot be null");
        return IrValueImpl.createConstantInt((IrTypeImpl) type, value);
    }

    /**
     * Creates a constant boolean value.
     *
     * @param context The context to create the value in
     * @param value   The boolean value
     * @return A constant boolean value
     */
    @Nonnull
    public static IrValue createConstantBool(@Nonnull IrContext context, boolean value) {
        Objects.requireNonNull(context, "Context cannot be null");
        return IrValueImpl.createConstantBool((IrContextImpl) context, value);
    }

    /**
     * Creates a constant floating-point value.
     *
     * @param type  The floating-point type
     * @param value The floating-point value
     * @return A constant floating-point value
     */
    @Nonnull
    public static IrValue createConstantFloat(@Nonnull IrType type, double value) {
        Objects.requireNonNull(type, "Type cannot be null");
        return IrValueImpl.createConstantFloat((IrTypeImpl) type, value);
    }

    /**
     * Creates a constant null value.
     *
     * @param type The type
     * @return A constant null value
     */
    @Nonnull
    public static IrValue createConstantNull(@Nonnull IrType type) {
        Objects.requireNonNull(type, "Type cannot be null");
        return IrValueImpl.createConstantNull((IrTypeImpl) type);
    }

    /**
     * Creates a constant string value.
     *
     * @param context        The context to create the value in
     * @param text           The string text
     * @param nullTerminated Whether the string should be null-terminated
     * @return A constant string value
     */
    @Nonnull
    public static IrValue createConstantString(
        @Nonnull IrContext context,
        @Nonnull String text,
        boolean nullTerminated) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(text, "Text cannot be null");
        return IrValueImpl.createConstantString((IrContextImpl) context, text, nullTerminated);
    }

    /**
     * Creates a global string in the current module.
     *
     * @param builder The builder to use
     * @param text    The string text
     * @return A new global string
     */
    @Nonnull
    public static IrValue createGlobalString(
        @Nonnull IrBuilder builder,
        @Nonnull String text) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(text, "Text cannot be null");
        return IrValueImpl.createGlobalString((IrBuilderImpl) builder, text);
    }

    // Builder Methods

    /**
     * Creates a return instruction.
     *
     * @param builder The builder to use
     * @param value   The value to return
     * @return The created return instruction
     */
    @Nonnull
    public static IrValue createReturn(@Nonnull IrBuilder builder, @Nonnull IrValue value) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        return IrBuilderImpl.createReturn((IrBuilderImpl) builder, (IrValueImpl) value);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrType type,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createAlloca((IrBuilderImpl) builder, (IrTypeImpl) type, name);
    }

    /**
     * Creates a load instruction.
     *
     * @param builder The builder to use
     * @param value   The value to load from
     * @param type    The type of the loaded value
     * @param name    The name of the load
     * @return The created load instruction
     */
    @Nonnull
    public static IrValue createLoad(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrType type,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createLoad((IrBuilderImpl) builder, (IrValueImpl) value,
            (IrTypeImpl) type, name);
    }

    /**
     * Creates a store instruction.
     *
     * @param builder The builder to use
     * @param store   The value to store
     * @param value   The value to store into
     * @return The created store instruction
     */
    @Nonnull
    public static IrValue createStore(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue store,
        @Nonnull IrValue value) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(store, "Store cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        return IrBuilderImpl.createStore((IrBuilderImpl) builder, (IrValueImpl) store,
            (IrValueImpl) value);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createAdd((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createSub((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createMul((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createSDiv((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createSRem((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createUDiv((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
    }

    /**
     * Creates an unsigned remainder instruction for integers.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The created remainder instruction
     */
    @Nonnull
    public static IrValue createURem(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createURem((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
    }

    /**
     * Creates a zero extension instruction (for widening unsigned integers).
     *
     * @param builder  The builder to use
     * @param value    The integer value to extend
     * @param destType The destination integer type
     * @param name     The name of the result
     * @return The sign extension instruction
     */
    @Nonnull
    public static IrValue createZExt(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrType destType,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(destType, "Destination type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createZExt((IrBuilderImpl) builder, (IrValueImpl) value,
            (IrTypeImpl) destType, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createFAdd((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createFSub((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createFMul((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createFDiv((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createFRem((IrBuilderImpl) builder, (IrValueImpl) left,
            (IrValueImpl) right, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrIntPredicate predicate,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createICmp(
            (IrBuilderImpl) builder, predicate, (IrValueImpl) left, (IrValueImpl) right, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrRealPredicate predicate,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createFCmp(
            (IrBuilderImpl) builder, predicate, (IrValueImpl) left, (IrValueImpl) right, name);
    }

    /**
     * Creates an unconditional branch instruction.
     *
     * @param builder The builder to use
     * @param dest    The destination basic block
     * @return The created branch instruction
     */
    @Nonnull
    public static IrValue createBr(@Nonnull IrBuilder builder, @Nonnull IrBasicBlock dest) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(dest, "Destination block cannot be null");
        return IrBuilderImpl.createBr((IrBuilderImpl) builder, (IrBasicBlockImpl) dest);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue condition,
        @Nonnull IrBasicBlock thenBlock,
        @Nonnull IrBasicBlock elseBlock) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(condition, "Condition cannot be null");
        Objects.requireNonNull(thenBlock, "Then block cannot be null");
        Objects.requireNonNull(elseBlock, "Else block cannot be null");

        if (!condition.isBoolean()) {
            throw new IllegalArgumentException("Condition must be a boolean value");
        }

        return IrBuilderImpl.createCondBr(
            (IrBuilderImpl) builder,
            (IrValueImpl) condition,
            (IrBasicBlockImpl) thenBlock,
            (IrBasicBlockImpl) elseBlock);
    }

    /**
     * Creates a select instruction (ternary operator).
     *
     * @param builder    The builder to use
     * @param condition  The condition value (must be boolean)
     * @param trueValue  The value to select if condition is true
     * @param falseValue The value to select if condition is false
     * @param name       The name of the result
     * @return The created select instruction
     */
    @Nonnull
    public static IrValue createSelect(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue condition,
        @Nonnull IrValue trueValue,
        @Nonnull IrValue falseValue,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(condition, "Condition cannot be null");
        Objects.requireNonNull(trueValue, "True value cannot be null");
        Objects.requireNonNull(falseValue, "False value cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!condition.isBoolean()) {
            throw new IllegalArgumentException("Condition must be a boolean value");
        }

        return IrBuilderImpl.createSelect((IrBuilderImpl) builder,
            (IrValueImpl) condition,
            (IrValueImpl) trueValue,
            (IrValueImpl) falseValue,
            name);
    }

    /**
     * Gets the current basic block where the builder is positioned.
     *
     * @param builder The builder to get the current block from
     * @return The current basic block, or null if not positioned
     */
    @Nullable
    public static IrBasicBlock getCurrentBasicBlock(@Nonnull IrBuilder builder) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        return IrBuilderImpl.getCurrentBasicBlock((IrBuilderImpl) builder);
    }

    /**
     * Creates a PHI node instruction.
     * PHI nodes are used for SSA form to merge values from different basic blocks.
     *
     * @param builder The builder to use
     * @param type    The type of the PHI node (must match incoming value types)
     * @param name    The name of the PHI node
     * @return The created PHI node
     */
    @Nonnull
    public static IrValue createPhi(@Nonnull IrBuilder builder, @Nonnull IrType type,
                                    @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return IrBuilderImpl.createPhi((IrBuilderImpl) builder, (IrTypeImpl) type, name);
    }

    /**
     * Adds an incoming value to a PHI node.
     * Each incoming value represents a possible path through the control flow.
     *
     * @param phi   The PHI node to add the incoming value to
     * @param value The incoming value
     * @param block The basic block where this value comes from
     */
    public static void addIncoming(@Nonnull IrValue phi, @Nonnull IrValue value,
                                   @Nonnull IrBasicBlock block) {
        Objects.requireNonNull(phi, "PHI node cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(block, "Block cannot be null");
        IrBuilderImpl.addIncoming((IrValueImpl) phi, (IrValueImpl) value, (IrBasicBlockImpl) block);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue function,
        @Nonnull IrValue[] args,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(function, "Function cannot be null");
        Objects.requireNonNull(args, "Arguments cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!function.isFunction()) {
            throw new IllegalArgumentException("Not a function: " + function);
        }

        for (int i = 0; i < args.length; i++) {
            Objects.requireNonNull(args[i], "Argument " + i + " cannot be null");
        }

        IrValueImpl[] valueArgs = new IrValueImpl[args.length];
        for (int i = 0; i < args.length; i++) {
            valueArgs[i] = (IrValueImpl) args[i];
        }

        return IrBuilderImpl.createCall(
            (IrBuilderImpl) builder, (IrValueImpl) function, valueArgs, name);
    }

    // Module-related methods

    /**
     * Adds a function declaration to a module.
     *
     * @param module       The module to add the function to
     * @param name         The function name
     * @param functionType The function type (must be a function type)
     * @return A new function value
     */
    @Nonnull
    public static IrValue addFunction(
        @Nonnull IrModule module,
        @Nonnull String name,
        @Nonnull IrType functionType) {
        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(name, "Function name cannot be null");
        Objects.requireNonNull(functionType, "Function type cannot be null");

        if (!functionType.isFunctionType()) {
            throw new IllegalArgumentException("Not a function type: " + functionType);
        }

        return IrModuleImpl.addFunction((IrModuleImpl) module, name, (IrTypeImpl) functionType);
    }

    /**
     * Generates LLVM IR from a module.
     *
     * @param module The module to generate IR from
     * @return The generated LLVM IR
     */
    @Nonnull
    public static String generateIR(@Nonnull IrModule module) {
        Objects.requireNonNull(module, "Module cannot be null");
        return IrModuleImpl.generateIr((IrModuleImpl) module);
    }

    // Builder positioning methods

    /**
     * Positions a builder at the end of the given basic block.
     *
     * @param builder The builder to position
     * @param block   The block to position at
     */
    public static void positionAtEnd(@Nonnull IrBuilder builder, @Nonnull IrBasicBlock block) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(block, "Block cannot be null");
        IrBuilderImpl.positionAtEnd((IrBuilderImpl) builder, (IrBasicBlockImpl) block);
    }

    /**
     * Positions a builder before the given instruction.
     *
     * @param builder     The builder to position
     * @param instruction The instruction to position before
     */
    public static void positionBefore(@Nonnull IrBuilder builder, @Nonnull IrValue instruction) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(instruction, "Instruction cannot be null");
        IrBuilderImpl.positionBefore((IrBuilderImpl) builder, (IrValueImpl) instruction);
    }

    /**
     * Creates a signed-integer-to-floating-point conversion instruction.
     *
     * @param builder  The builder to use
     * @param value    The integer value to convert
     * @param destType The destination floating-point type
     * @param name     The name of the result
     * @return The conversion instruction
     */
    @Nonnull
    public static IrValue createSIToFP(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrType destType,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(destType, "Destination type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.getType().isIntegerType() || !destType.isFloatingPointType()) {
            throw new IllegalArgumentException(
                "SI to FP conversion requires integer source and floating-point destination");
        }

        return IrBuilderImpl.createSIToFP((IrBuilderImpl) builder, (IrValueImpl) value,
            (IrTypeImpl) destType, name);
    }

    /**
     * Creates a floating-point-to-signed-integer conversion instruction.
     *
     * @param builder  The builder to use
     * @param value    The floating-point value to convert
     * @param destType The destination integer type
     * @param name     The name of the result
     * @return The conversion instruction
     */
    @Nonnull
    public static IrValue createFPToSI(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrType destType,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(destType, "Destination type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.getType().isFloatingPointType() || !destType.isIntegerType()) {
            throw new IllegalArgumentException(
                "FP to SI conversion requires floating-point source and integer destination");
        }

        return IrBuilderImpl.createFPToSI((IrBuilderImpl) builder, (IrValueImpl) value,
            (IrTypeImpl) destType, name);
    }

    /**
     * Creates an unsigned-integer-to-floating-point conversion instruction.
     *
     * @param builder  The builder to use
     * @param value    The integer value to convert
     * @param destType The destination floating-point type
     * @param name     The name of the result
     * @return The conversion instruction
     */
    @Nonnull
    public static IrValue createUIToFP(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrType destType,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(destType, "Destination type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.getType().isIntegerType() || !destType.isFloatingPointType()) {
            throw new IllegalArgumentException(
                "UI to FP conversion requires integer source and floating-point destination");
        }

        return IrBuilderImpl.createUIToFP((IrBuilderImpl) builder, (IrValueImpl) value,
            (IrTypeImpl) destType, name);
    }

    /**
     * Creates a floating-point-to-unsigned-integer conversion instruction.
     *
     * @param builder  The builder to use
     * @param value    The floating-point value to convert
     * @param destType The destination integer type
     * @param name     The name of the result
     * @return The conversion instruction
     */
    @Nonnull
    public static IrValue createFPToUI(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrType destType,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(destType, "Destination type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.getType().isFloatingPointType() || !destType.isIntegerType()) {
            throw new IllegalArgumentException(
                "FP to UI conversion requires floating-point source and integer destination");
        }

        return IrBuilderImpl.createFPToUI((IrBuilderImpl) builder, (IrValueImpl) value,
            (IrTypeImpl) destType, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrType destType,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(destType, "Destination type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.getType().isIntegerType() || !destType.isIntegerType()) {
            throw new IllegalArgumentException("SExt requires integer source and destination");
        }

        return IrBuilderImpl.createSExt((IrBuilderImpl) builder, (IrValueImpl) value,
            (IrTypeImpl) destType, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrType destType,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(destType, "Destination type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.getType().isIntegerType() || !destType.isIntegerType()) {
            throw new IllegalArgumentException("Trunc requires integer source and destination");
        }

        return IrBuilderImpl.createTrunc((IrBuilderImpl) builder, (IrValueImpl) value,
            (IrTypeImpl) destType, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrType destType,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(destType, "Destination type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.getType().isFloatingPointType() || !destType.isFloatingPointType()) {
            throw new IllegalArgumentException(
                "FPExt requires floating-point source and destination");
        }

        return IrBuilderImpl.createFPExt((IrBuilderImpl) builder, (IrValueImpl) value,
            (IrTypeImpl) destType, name);
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
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrType destType,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(destType, "Destination type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.getType().isFloatingPointType() || !destType.isFloatingPointType()) {
            throw new IllegalArgumentException(
                "FPTrunc requires floating-point source and destination");
        }

        return IrBuilderImpl.createFPTrunc((IrBuilderImpl) builder, (IrValueImpl) value,
            (IrTypeImpl) destType, name);
    }

    /**
     * Creates a bitwise AND instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The AND instruction
     */
    @Nonnull
    public static IrValue createAnd(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        return IrBuilderImpl.createAnd((IrBuilderImpl) builder,
            (IrValueImpl) left, (IrValueImpl) right, name);
    }

    /**
     * Creates a bitwise OR instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The OR instruction
     */
    @Nonnull
    public static IrValue createOr(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        return IrBuilderImpl.createOr((IrBuilderImpl) builder,
            (IrValueImpl) left, (IrValueImpl) right, name);
    }

    /**
     * Creates a bitwise XOR instruction.
     *
     * @param builder The builder to use
     * @param left    The left operand
     * @param right   The right operand
     * @param name    The name of the result
     * @return The XOR instruction
     */
    @Nonnull
    public static IrValue createXor(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue left,
        @Nonnull IrValue right,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(left, "Left operand cannot be null");
        Objects.requireNonNull(right, "Right operand cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!left.isInteger() || !right.isInteger()) {
            throw new IllegalArgumentException("XOR requires integer operands");
        }

        return IrBuilderImpl.createXor((IrBuilderImpl) builder,
            (IrValueImpl) left, (IrValueImpl) right, name);
    }

    /**
     * Creates a logical shift left instruction.
     *
     * @param builder The builder to use
     * @param value   The value to shift
     * @param amount  The shift amount
     * @param name    The name of the result
     * @return The shift left instruction
     */
    @Nonnull
    public static IrValue createShl(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrValue amount,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.isInteger() || !amount.isInteger()) {
            throw new IllegalArgumentException("SHL requires integer operands");
        }

        return IrBuilderImpl.createShl((IrBuilderImpl) builder,
            (IrValueImpl) value, (IrValueImpl) amount, name);
    }

    /**
     * Creates an arithmetic shift right instruction.
     *
     * @param builder The builder to use
     * @param value   The value to shift
     * @param amount  The shift amount
     * @param name    The name of the result
     * @return The arithmetic shift right instruction
     */
    @Nonnull
    public static IrValue createAShr(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrValue amount,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.isInteger() || !amount.isInteger()) {
            throw new IllegalArgumentException("ASHR requires integer operands");
        }

        return IrBuilderImpl.createAShr((IrBuilderImpl) builder,
            (IrValueImpl) value, (IrValueImpl) amount, name);
    }

    /**
     * Creates a logical shift right instruction.
     *
     * @param builder The builder to use
     * @param value   The value to shift
     * @param amount  The shift amount
     * @param name    The name of the result
     * @return The logical shift right instruction
     */
    @Nonnull
    public static IrValue createLShr(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrValue amount,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.isInteger() || !amount.isInteger()) {
            throw new IllegalArgumentException("LSHR requires integer operands");
        }

        return IrBuilderImpl.createLShr((IrBuilderImpl) builder,
            (IrValueImpl) value, (IrValueImpl) amount, name);
    }

    /**
     * Creates a floating-point negation instruction.
     *
     * @param builder The builder to use
     * @param value   The value to negate
     * @param name    The name of the result
     * @return The negation instruction
     */
    @Nonnull
    public static IrValue createFNeg(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.isFloatingPoint()) {
            throw new IllegalArgumentException("FNEG requires floating-point operand");
        }

        return IrBuilderImpl.createFNeg((IrBuilderImpl) builder,
            (IrValueImpl) value, name);
    }

    /**
     * Creates an integer negation instruction.
     *
     * @param builder The builder to use
     * @param value   The value to negate
     * @param name    The name of the result
     * @return The negation instruction
     */
    @Nonnull
    public static IrValue createNeg(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!value.isInteger()) {
            throw new IllegalArgumentException("NEG requires integer operand");
        }

        return IrBuilderImpl.createNeg((IrBuilderImpl) builder,
            (IrValueImpl) value, name);
    }

    /**
     * Creates a constant with all bits set to 1.
     *
     * @param type The type of the constant
     * @return The constant value
     */
    @Nonnull
    public static IrValue createAllOnes(@Nonnull IrType type) {
        Objects.requireNonNull(type, "Type cannot be null");

        if (!type.isIntegerType()) {
            throw new IllegalArgumentException("ALL_ONES requires integer type");
        }

        return IrValueImpl.createAllOnes((IrTypeImpl) type);
    }

    /**
     * Creates a struct type with the given name.
     *
     * @param context The context to create the type in
     * @param name    The name of the struct
     * @return A struct type
     */
    @Nonnull
    public static IrType createStructType(
        @Nonnull IrContext context,
        @Nonnull String name) {
        Objects.requireNonNull(context, "Context cannot be null");

        return IrTypeImpl.createStructType((IrContextImpl) context, name);
    }

    /**
     * Sets the body of a struct type with the given field types.
     *
     * @param structType The struct type to modify
     * @param fieldTypes The field types of the struct
     */
    public static void setStructBody(
        @Nonnull IrType structType,
        @Nonnull IrType[] fieldTypes) {
        Objects.requireNonNull(structType, "Struct type cannot be null");
        Objects.requireNonNull(fieldTypes, "Field types cannot be null");

        IrTypeImpl[] fieldTypeImpls = new IrTypeImpl[fieldTypes.length];
        for (int i = 0; i < fieldTypes.length; i++) {
            Objects.requireNonNull(fieldTypes[i], "Field type cannot be null");
            fieldTypeImpls[i] = (IrTypeImpl) fieldTypes[i];
        }

        IrTypeImpl.setStructBody((IrTypeImpl) structType, fieldTypeImpls);
    }

    /**
     * Creates a struct GEP (GetElementPtr) instruction to access struct fields.
     *
     * @param builder    The builder to use
     * @param structType The type of the struct
     * @param structPtr  The pointer to the struct
     * @param fieldIndex The index of the field to access
     * @param name       The name of the result
     * @return The field pointer
     */
    @Nonnull
    public static IrValue createStructGEP(
        @Nonnull IrBuilder builder,
        @Nonnull IrType structType,
        @Nonnull IrValue structPtr,
        int fieldIndex,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(structPtr, "Struct pointer cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (fieldIndex < 0) {
            throw new IllegalArgumentException("Field index cannot be negative");
        }

        return IrBuilderImpl.createStructGEP((IrBuilderImpl) builder, (IrTypeImpl) structType,
            (IrValueImpl) structPtr, fieldIndex, name);
    }

    /**
     * Creates a GC-managed allocation instead of malloc.
     * This replaces the traditional malloc with GC_malloc from Boehm GC.
     *
     * @param builder The IR builder
     * @param module  The IR module
     * @param type    The type to allocate
     * @param gcFunc  The GC_malloc function (can be obtained via declareGCMalloc)
     * @param name    The name of the result
     * @return The allocated GC-managed pointer
     */
    @Nonnull
    public static IrValue createGCMalloc(
        @Nonnull IrBuilder builder,
        @Nonnull IrModule module,
        @Nonnull IrType type,
        @Nonnull IrValue gcFunc,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(gcFunc, "GC function cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        return IrBuilderImpl.createGCMalloc((IrBuilderImpl) builder, (IrModuleImpl) module,
            (IrTypeImpl) type, (IrValueImpl) gcFunc, name);
    }

    /**
     * Creates a GC-managed array allocation.
     * Uses GC_malloc for array allocation with automatic size calculation.
     *
     * @param builder     The IR builder
     * @param module      The IR module
     * @param elementType The element type
     * @param count       The number of elements
     * @param gcFunc      The GC_malloc function (can be obtained via declareGCMalloc)
     * @param name        The name of the result
     * @return The allocated GC-managed array pointer
     */
    @Nonnull
    public static IrValue createGCArrayMalloc(
        @Nonnull IrBuilder builder,
        @Nonnull IrModule module,
        @Nonnull IrType elementType,
        @Nonnull IrValue count,
        @Nonnull IrValue gcFunc,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(elementType, "Element type cannot be null");
        Objects.requireNonNull(count, "Count cannot be null");
        Objects.requireNonNull(gcFunc, "GC array function cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        return IrBuilderImpl.createGCArrayMalloc((IrBuilderImpl) builder, (IrModuleImpl) module,
            (IrTypeImpl) elementType, (IrValueImpl) count, (IrValueImpl) gcFunc, name);
    }

    /**
     * Creates a GC_malloc_atomic call for primitive data that contains no pointers.
     * This is more efficient for data like strings, numbers, etc.
     *
     * @param builder      The IR builder
     * @param module       The IR module
     * @param type         The type to allocate
     * @param gcAtomicFunc The GC_malloc_atomic function (can be obtained via declareGCMallocAtomic)
     * @param name         The name of the result
     * @return The allocated atomic GC-managed pointer
     */
    @Nonnull
    public static IrValue createGCMallocAtomic(
        @Nonnull IrBuilder builder,
        @Nonnull IrModule module,
        @Nonnull IrType type,
        @Nonnull IrValue gcAtomicFunc,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(gcAtomicFunc, "GC atomic function cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        return IrBuilderImpl.createGCMallocAtomic((IrBuilderImpl) builder, (IrModuleImpl) module,
            (IrTypeImpl) type, (IrValueImpl) gcAtomicFunc, name);
    }

    /**
     * Declares the GC initialization function that must be called at program startup.
     *
     * @param module The IR module
     * @return The GC_init function declaration
     */
    @Nonnull
    public static IrValue declareGCInit(@Nonnull IrModule module) {
        Objects.requireNonNull(module, "Module cannot be null");
        return IrModuleImpl.declareGcInit((IrModuleImpl) module);
    }

    /**
     * Declares the GC_malloc function.
     *
     * @param module The IR module
     * @return The GC_malloc function declaration
     */
    @Nonnull
    public static IrValue declareGCMalloc(@Nonnull IrModule module) {
        Objects.requireNonNull(module, "Module cannot be null");
        return IrModuleImpl.declareGcMalloc((IrModuleImpl) module);
    }

    /**
     * Declares the GC_malloc_atomic function.
     *
     * @param module The IR module
     * @return The GC_malloc_atomic function declaration
     */
    @Nonnull
    public static IrValue declareGCMallocAtomic(@Nonnull IrModule module) {
        Objects.requireNonNull(module, "Module cannot be null");
        return IrModuleImpl.declareGcMallocAtomic((IrModuleImpl) module);
    }

    /**
     * Creates an in-bounds GEP (GetElementPtr) instruction for array access.
     * This is used for accessing array elements: array[index]
     *
     * @param builder     The builder to use
     * @param elementType The type of elements in the array
     * @param arrayPtr    The pointer to the array (or first element)
     * @param indices     The indices to use for the GEP (usually just one for array[index])
     * @param name        The name of the result
     * @return The element pointer
     */
    @Nonnull
    public static IrValue createInBoundsGEP(
        @Nonnull IrBuilder builder,
        @Nonnull IrType elementType,
        @Nonnull IrValue arrayPtr,
        @Nonnull IrValue[] indices,
        @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(elementType, "Element type cannot be null");
        Objects.requireNonNull(arrayPtr, "Array pointer cannot be null");
        Objects.requireNonNull(indices, "Indices cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        for (int i = 0; i < indices.length; i++) {
            Objects.requireNonNull(indices[i], "Index " + i + " cannot be null");
        }

        return IrBuilderImpl.createInBoundsGEP((IrBuilderImpl) builder, (IrTypeImpl) elementType,
            (IrValueImpl) arrayPtr, castToImpl(indices), name);
    }

    /**
     * Creates a memcpy intrinsic call for copying memory between non-overlapping regions.
     *
     * @param builder  The IR builder
     * @param dst      Destination pointer
     * @param src      Source pointer
     * @param size     Number of bytes to copy
     * @param dstAlign Destination alignment (power of 2)
     * @param srcAlign Source alignment (power of 2)
     * @return The memcpy call instruction
     */
    @Nonnull
    public static IrValue createMemCpy(@Nonnull IrBuilder builder,
                                       @Nonnull IrValue dst,
                                       @Nonnull IrValue src,
                                       @Nonnull IrValue size,
                                       int dstAlign,
                                       int srcAlign) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(dst, "Destination cannot be null");
        Objects.requireNonNull(src, "Source cannot be null");
        Objects.requireNonNull(size, "Size cannot be null");

        return IrBuilderImpl.createMemCpy((IrBuilderImpl) builder, (IrValueImpl) dst,
            (IrValueImpl) src, (IrValueImpl) size,
            dstAlign, srcAlign);
    }

    /**
     * Creates a memmove intrinsic call for copying memory between potentially overlapping regions.
     *
     * @param builder  The IR builder
     * @param dst      Destination pointer
     * @param src      Source pointer
     * @param size     Number of bytes to move
     * @param dstAlign Destination alignment (power of 2)
     * @param srcAlign Source alignment (power of 2)
     * @return The memmove call instruction
     */
    @Nonnull
    public static IrValue createMemMove(@Nonnull IrBuilder builder,
                                        @Nonnull IrValue dst,
                                        @Nonnull IrValue src,
                                        @Nonnull IrValue size,
                                        int dstAlign,
                                        int srcAlign) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(dst, "Destination cannot be null");
        Objects.requireNonNull(src, "Source cannot be null");
        Objects.requireNonNull(size, "Size cannot be null");

        return IrBuilderImpl.createMemMove((IrBuilderImpl) builder, (IrValueImpl) dst,
            (IrValueImpl) src, (IrValueImpl) size,
            dstAlign, srcAlign);
    }

    /**
     * Creates a memset intrinsic call for setting memory to a specific value.
     *
     * @param builder The IR builder
     * @param ptr     Pointer to memory to set
     * @param value   Value to set (i8)
     * @param size    Number of bytes to set
     * @param align   Memory alignment (power of 2)
     * @param name    Name for the instruction
     * @return The memset call instruction
     */
    @Nonnull
    public static IrValue createMemSet(@Nonnull IrBuilder builder,
                                       @Nonnull IrValue ptr,
                                       @Nonnull IrValue value,
                                       @Nonnull IrValue size,
                                       int align,
                                       @Nonnull String name) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(ptr, "Pointer cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(size, "Size cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        return IrBuilderImpl.createMemSet((IrBuilderImpl) builder, (IrValueImpl) ptr,
            (IrValueImpl) value, (IrValueImpl) size,
            align, name);
    }

    /**
     * Creates a constant representing the size in bytes of the given type.
     * Works for any LLVM type (integers, floats, structs, pointers, etc.)
     *
     * @param context The IR context
     * @param type    The type to get size of
     * @return Constant integer representing size in bytes
     */
    @Nonnull
    public static IrValue createSizeOf(@Nonnull IrContext context, @Nonnull IrType type) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");

        // Use LLVM's built-in sizeof calculation
        LLVMValueRef sizeValue = LLVM.LLVMSizeOf(((IrTypeImpl) type).getLlvmType());
        return new IrValueImpl(sizeValue, (IrContextImpl) context);
    }

    /**
     * Creates an unreachable instruction at the current builder position.
     * This indicates that the code path should never be reached.
     *
     * @param builder The builder to use
     */
    public static void createUnreachable(@Nonnull IrBuilder builder) {
        Objects.requireNonNull(builder, "Builder cannot be null");
        LLVM.LLVMBuildUnreachable(((IrBuilderImpl) builder).getLlvmBuilder());
    }

    /**
     * Creates a constant struct value.
     *
     * @param type   The struct type
     * @param values The field values
     * @return A constant struct value
     */
    @Nonnull
    public static IrValue createConstantNamedStruct(@Nonnull IrType type,
                                                    @Nonnull IrValue[] values) {
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(values, "Values cannot be null");
        return IrValueImpl.createConstantNamedStruct((IrTypeImpl) type, values);
    }

    /**
     * Creates a bitcast instruction.
     * Bitcast reinterprets a value as a different type without changing the bit pattern.
     *
     * @param builder  The builder to use
     * @param value    The value to cast
     * @param destType The destination type
     * @param name     The name of the result
     * @return The bitcast instruction
     */
    @Nonnull
    public static IrValue createBitCast(
        @Nonnull IrBuilder builder,
        @Nonnull IrValue value,
        @Nonnull IrType destType,
        @Nonnull String name) {

        Objects.requireNonNull(builder, "Builder cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(destType, "Destination type cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        return IrBuilderImpl.createBitCast((IrBuilderImpl) builder, (IrValueImpl) value,
            (IrTypeImpl) destType, name);
    }

    /**
     * Creates an array type.
     *
     * @param elementType The element type of the array
     * @param numElements The number of elements in the array
     * @return An array type
     */
    @Nonnull
    public static IrType createArrayType(@Nonnull IrType elementType, int numElements) {
        Objects.requireNonNull(elementType, "Element type cannot be null");
        if (numElements < 0) {
            throw new IllegalArgumentException("Number of elements cannot be negative");
        }

        return IrTypeImpl.createArrayType((IrTypeImpl) elementType, numElements);
    }

    /**
     * Creates an architecture-dependent signed integer type (isize).
     * This type is 32-bit on 32-bit architectures and 64-bit on 64-bit architectures.
     *
     * @param module The module to create the type in
     * @return An architecture-dependent signed integer type (isize)
     */
    @Nonnull
    public static IrType createisizeType(@Nonnull IrModule module) {
        Objects.requireNonNull(module, "Module cannot be null");
        return IrTypeImpl.createisizeType((IrModuleImpl) module);
    }

    /**
     * Creates an architecture-dependent unsigned integer type (usize).
     * This type is 32-bit on 32-bit architectures and 64-bit on 64-bit architectures.
     *
     * @param module The module to create the type in
     * @return An architecture-dependent unsigned integer type (usize)
     */
    @Nonnull
    public static IrType createusizeType(@Nonnull IrModule module) {
        Objects.requireNonNull(module, "Module cannot be null");
        return IrTypeImpl.createusizeType((IrModuleImpl) module);
    }

    /**
     * Gets the pointer size in bits for the current target architecture.
     * This is used to determine the size of isize/usize types.
     *
     * @param module The IR module
     * @return The pointer size in bits (32 or 64)
     */
    public static int getTargetPointerSize(@Nonnull IrModule module) {
        return IrTypeImpl.getTargetPointerSize((IrModuleImpl) module);
    }

    /**
     * Helper method to cast IRValue[] to IRValueImpl[].
     */
    private static IrValueImpl[] castToImpl(@Nonnull IrValue[] values) {
        IrValueImpl[] result = new IrValueImpl[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (IrValueImpl) values[i];
        }
        return result;
    }
}