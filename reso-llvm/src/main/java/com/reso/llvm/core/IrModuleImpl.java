package com.reso.llvm.core;

import static com.reso.llvm.core.IrTypeImpl.getTargetPointerSize;

import com.reso.llvm.api.IrContext;
import com.reso.llvm.api.IrModule;
import com.reso.llvm.api.IrType;
import com.reso.llvm.api.IrValue;
import com.reso.llvm.exception.IrException;
import com.reso.llvm.util.IrInitializer;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMErrorRef;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

/**
 * Implementation of ResoModule that wraps an LLVMModuleRef.
 */
@NotThreadSafe
public class IrModuleImpl implements IrModule {
    private final LLVMModuleRef moduleRef;
    private final IrContextImpl context;
    private boolean disposed = false;

    /**
     * Creates a new module in the given context.
     *
     * @param context The context to create the module in
     * @param name    The name of the module
     * @return A new module
     */
    @Nonnull
    public static IrModuleImpl create(@Nonnull IrContextImpl context, @Nonnull String name) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        try {
            return new IrModuleImpl(
                LLVM.LLVMModuleCreateWithNameInContext(name, context.getLlvmContext()),
                context
            );
        } catch (Exception e) {
            throw new IrException("Failed to create module: " + e.getMessage(), e);
        }
    }

    /**
     * Wraps an existing LLVMModuleRef.
     *
     * @param moduleRef The LLVM module reference to wrap
     * @param context   The context this module belongs to
     */
    public IrModuleImpl(@Nonnull LLVMModuleRef moduleRef, @Nonnull IrContextImpl context) {
        this.moduleRef = Objects.requireNonNull(moduleRef, "Module reference cannot be null");
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Gets the underlying LLVM module reference.
     *
     * @return The underlying LLVM module reference
     * @throws IllegalStateException if the module has been disposed
     */
    @Nonnull
    public LLVMModuleRef getLlvmModule() {
        if (disposed) {
            throw new IllegalStateException("Module has been disposed");
        }
        if (context.isDisposed()) {
            throw new IllegalStateException("Context has been disposed");
        }
        return moduleRef;
    }

    @Override
    @Nonnull
    public IrContext getContext() {
        return context;
    }

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
        @Nonnull IrModuleImpl module,
        @Nonnull String name,
        @Nonnull IrTypeImpl functionType) {

        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(name, "Function name cannot be null");
        Objects.requireNonNull(functionType, "Function type cannot be null");

        if (!functionType.isFunctionType()) {
            throw new IllegalArgumentException("Not a function type: " + functionType);
        }

        if (module.isDisposed()) {
            throw new IllegalStateException("Module has been disposed");
        }

        try {
            LLVMValueRef function = LLVM.LLVMAddFunction(
                module.getLlvmModule(), name, functionType.getLlvmType());
            return new IrValueImpl(function, functionType);
        } catch (Exception e) {
            throw new IrException("Failed to add function: " + e.getMessage(), e);
        }
    }

    @Override
    @Nullable
    public IrValue getFunction(@Nonnull String name, @Nonnull IrType functionType) {
        Objects.requireNonNull(name, "Function name cannot be null");
        Objects.requireNonNull(functionType, "Function type cannot be null");

        if (disposed) {
            throw new IllegalStateException("Module has been disposed");
        }

        LLVMModuleRef llvmModule = getLlvmModule();

        // Get the function
        LLVMValueRef funcRef = LLVM.LLVMGetNamedFunction(llvmModule, name);
        if (funcRef == null) {
            return null;
        }

        // Create a value with the inferred type
        return new IrValueImpl(funcRef, (IrTypeImpl) functionType);
    }

    @Override
    public boolean hasFunction(@Nonnull String name) {
        Objects.requireNonNull(name, "Function name cannot be null");

        if (disposed) {
            throw new IllegalStateException("Module has been disposed");
        }

        LLVMModuleRef llvmModule = getLlvmModule();
        return LLVM.LLVMGetNamedFunction(llvmModule, name) != null;
    }

    @Override
    public void setDataLayout(@Nonnull String dataLayout) {
        Objects.requireNonNull(dataLayout, "Data layout cannot be null");

        if (disposed) {
            throw new IllegalStateException("Module has been disposed");
        }

        LLVM.LLVMSetDataLayout(getLlvmModule(), dataLayout);
    }

    @Override
    @Nonnull
    public String getDataLayout() {
        if (disposed) {
            throw new IllegalStateException("Module has been disposed");
        }

        return LLVM.LLVMGetDataLayout(getLlvmModule()).getString();
    }

    @Override
    public void setTargetTriple(@Nonnull String triple) {
        Objects.requireNonNull(triple, "Target triple cannot be null");

        if (disposed) {
            throw new IllegalStateException("Module has been disposed");
        }

        LLVM.LLVMSetTarget(getLlvmModule(), triple);
    }

    @Override
    @Nonnull
    public String getTargetTriple() {
        if (disposed) {
            throw new IllegalStateException("Module has been disposed");
        }

        BytePointer triplePtr = LLVM.LLVMGetTarget(getLlvmModule());
        return triplePtr == null ? "" : triplePtr.getString();
    }

    /**
     * Generates LLVM IR from a module.
     *
     * @param module The module to generate IR from
     * @return The generated LLVM IR
     */
    @Nonnull
    public static String generateIr(@Nonnull IrModuleImpl module) {
        Objects.requireNonNull(module, "Module cannot be null");

        if (module.isDisposed()) {
            throw new IllegalStateException("Module has been disposed");
        }

        try {
            BytePointer irString = LLVM.LLVMPrintModuleToString(module.getLlvmModule());
            if (irString == null) {
                throw new IrException("Failed to generate IR");
            }

            String result = irString.getString();
            LLVM.LLVMDisposeMessage(irString);

            return result;
        } catch (Exception e) {
            throw new IrException("Failed to generate IR: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies a module for correctness.
     *
     * @param module      The module to verify
     * @param printErrors Whether to print errors to stderr
     * @return true if the module is valid
     * @throws IrException if the module is invalid and printErrors is false
     */
    public static boolean verify(@Nonnull IrModuleImpl module, boolean printErrors) {
        Objects.requireNonNull(module, "Module cannot be null");

        if (module.isDisposed()) {
            throw new IllegalStateException("Module has been disposed");
        }

        BytePointer errorMessage = new BytePointer();
        int action = printErrors ? LLVM.LLVMPrintMessageAction : LLVM.LLVMReturnStatusAction;

        try {
            int result = LLVM.LLVMVerifyModule(module.getLlvmModule(), action, errorMessage);

            if (result != 0) {
                String error = errorMessage.getString();
                if (!printErrors) {
                    throw new IrException("Module verification failed: " + error);
                }
                return false;
            }

            return true;
        } finally {
            LLVM.LLVMDisposeMessage(errorMessage);
        }
    }

    /**
     * Runs optimization passes on a module.
     *
     * @param module             The module to optimize
     * @param targetMachine      The target machine to optimize for
     * @param options            The pass builder options
     * @param optimizationPasses The optimization pipeline to run
     * @return true if optimization was successful
     * @throws IrException if an error occurs during optimization
     */
    public static boolean optimizeModule(
        @Nonnull IrModuleImpl module,
        @Nonnull IrTargetMachineImpl targetMachine,
        @Nonnull IrPassBuilderOptionsImpl options,
        @Nonnull String optimizationPasses) {

        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(targetMachine, "Target machine cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");
        Objects.requireNonNull(optimizationPasses, "Optimization passes cannot be null");

        // Ensure LLVM target is initialized
        IrInitializer.initializeTargetMachine();

        if (module.isDisposed()) {
            throw new IllegalStateException("Module has been disposed");
        }

        if (targetMachine.isDisposed()) {
            throw new IllegalStateException("Target machine has been disposed");
        }

        if (options.isDisposed()) {
            throw new IllegalStateException("Pass builder options have been disposed");
        }

        // Run optimization passes
        LLVMErrorRef error = LLVM.LLVMRunPasses(
            module.getLlvmModule(),
            optimizationPasses,
            targetMachine.getLlvmTargetMachine(),
            options.getLlvmPassBuilderOptions()
        );

        // Check for errors
        if (error != null) {
            try {
                BytePointer errorMessage = LLVM.LLVMGetErrorMessage(error);
                String errorMsg = errorMessage.getString();

                LLVM.LLVMDisposeErrorMessage(errorMessage);
                LLVM.LLVMConsumeError(error);

                throw new IrException("Optimization error: " + errorMsg);
            } finally {
                if (error != null) {
                    LLVM.LLVMConsumeError(error);
                }
            }
        }

        return true;
    }

    /**
     * Optimizes a module using default options.
     *
     * @param module            The module to optimize
     * @param targetMachine     The target machine to optimize for
     * @param optimizationLevel The optimization level (0-3)
     * @return true if optimization was successful
     * @throws IrException if an error occurs during optimization
     */
    public static boolean optimizeModule(
        @Nonnull IrModuleImpl module,
        @Nonnull IrTargetMachineImpl targetMachine,
        int optimizationLevel) {

        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(targetMachine, "Target machine cannot be null");

        if (optimizationLevel < 0 || optimizationLevel > 3) {
            throw new IllegalArgumentException("Optimization level must be between 0 and 3");
        }

        try (IrPassBuilderOptionsImpl options = IrPassBuilderOptionsImpl.create()) {
            // Configure default options
            options.setLoopVectorization(true)
                .setSlpVectorization(true)
                .setLoopUnrolling(true)
                .setLoopInterleaving(true)
                .setVerifyEach(false);

            // Get the optimization pipeline
            String optimizationPasses = "default<O" + optimizationLevel + ">";

            return optimizeModule(module, targetMachine, options, optimizationPasses);
        }
    }

    /**
     * Declares the GC_init function that must be called at program startup.
     *
     * @param module The IR module
     * @return The GC_init function declaration
     */
    @Nonnull
    public static IrValue declareGcInit(@Nonnull IrModuleImpl module) {
        Objects.requireNonNull(module, "Module cannot be null");

        if (module.isDisposed()) {
            throw new IllegalStateException("Module has been disposed");
        }

        try {
            LLVMModuleRef moduleRef = module.getLlvmModule();
            LLVMContextRef contextRef = ((IrContextImpl) module.getContext()).getLlvmContext();

            // Check if already declared
            LLVMValueRef existing = LLVM.LLVMGetNamedFunction(moduleRef, "GC_init");
            if (existing != null) {
                LLVMTypeRef existingType = LLVM.LLVMTypeOf(existing);
                IrTypeImpl irType =
                    new IrTypeImpl(existingType, (IrContextImpl) module.getContext());
                return new IrValueImpl(existing, irType);
            }

            // Create function type: void GC_init(void)
            LLVMTypeRef voidType = LLVM.LLVMVoidTypeInContext(contextRef);
            LLVMTypeRef funcType;
            try (PointerPointer<LLVMTypeRef> emptyParams = new PointerPointer<>(0)) {
                funcType = LLVM.LLVMFunctionType(
                    voidType,
                    emptyParams,  // Empty pointer instead of null
                    0,
                    0     // Not varargs
                );
            }

            // Add function to module
            LLVMValueRef gcInitFunc = LLVM.LLVMAddFunction(moduleRef, "GC_init", funcType);

            IrTypeImpl irType = new IrTypeImpl(funcType, (IrContextImpl) module.getContext());
            return new IrValueImpl(gcInitFunc, irType);

        } catch (Exception e) {
            throw new IrException("Failed to declare GC_init function: " + e.getMessage(), e);
        }
    }

    /**
     * Declares the GC_malloc function.
     *
     * @param module The IR module
     * @return The GC_malloc function declaration
     */
    @Nonnull
    public static IrValue declareGcMalloc(@Nonnull IrModuleImpl module) {
        Objects.requireNonNull(module, "Module cannot be null");

        if (module.isDisposed()) {
            throw new IllegalStateException("Module has been disposed");
        }

        try {
            LLVMModuleRef moduleRef = module.getLlvmModule();
            LLVMContextRef contextRef = ((IrContextImpl) module.getContext()).getLlvmContext();

            // Create function type: void* GC_malloc(size_t size)
            try (LLVMTypeRef int8Type = LLVM.LLVMInt8TypeInContext(contextRef);
                 LLVMTypeRef voidPtrType = LLVM.LLVMPointerType(int8Type, 0);
                 LLVMTypeRef sizeTType = LLVM.LLVMIntTypeInContext(contextRef,
                     getTargetPointerSize(module))) {

                LLVMTypeRef[] paramTypes = {sizeTType};

                try (LLVMTypeRef funcType = LLVM.LLVMFunctionType(
                    voidPtrType,
                    new PointerPointer<>(paramTypes),
                    1,
                    0
                )) {
                    // Check if already declared
                    LLVMValueRef existing = LLVM.LLVMGetNamedFunction(moduleRef, "GC_malloc");
                    if (existing != null) {
                        IrTypeImpl irType =
                            new IrTypeImpl(funcType, (IrContextImpl) module.getContext());
                        return new IrValueImpl(existing, irType);
                    }

                    // Add function to module
                    LLVMValueRef gcMallocFunc =
                        LLVM.LLVMAddFunction(moduleRef, "GC_malloc", funcType);

                    IrTypeImpl irType =
                        new IrTypeImpl(funcType, (IrContextImpl) module.getContext());
                    return new IrValueImpl(gcMallocFunc, irType);
                }
            }

        } catch (Exception e) {
            throw new IrException("Failed to declare GC_malloc function: " + e.getMessage(), e);
        }
    }

    /**
     * Declares the GC_malloc_atomic function for atomic (pointer-free) data.
     *
     * @param module The IR module
     * @return The GC_malloc_atomic function declaration
     */
    @Nonnull
    public static IrValue declareGcMallocAtomic(@Nonnull IrModuleImpl module) {
        Objects.requireNonNull(module, "Module cannot be null");

        if (module.isDisposed()) {
            throw new IllegalStateException("Module has been disposed");
        }

        try {
            LLVMModuleRef moduleRef = module.getLlvmModule();
            LLVMContextRef contextRef = ((IrContextImpl) module.getContext()).getLlvmContext();

            // Create function type: void* GC_malloc_atomic(size_t size)
            try (LLVMTypeRef int8Type = LLVM.LLVMInt8TypeInContext(contextRef);
                 LLVMTypeRef voidPtrType = LLVM.LLVMPointerType(int8Type, 0);
                 LLVMTypeRef sizeTType = LLVM.LLVMIntTypeInContext(contextRef,
                     getTargetPointerSize(module))) {

                LLVMTypeRef[] paramTypes = {sizeTType};

                LLVMTypeRef funcType = LLVM.LLVMFunctionType(
                    voidPtrType,
                    new PointerPointer<>(paramTypes),
                    1,
                    0
                );

                // Check if already declared
                LLVMValueRef existing = LLVM.LLVMGetNamedFunction(moduleRef, "GC_malloc_atomic");
                if (existing != null) {
                    IrTypeImpl irType =
                        new IrTypeImpl(funcType, (IrContextImpl) module.getContext());
                    return new IrValueImpl(existing, irType);
                }

                // Add function to module
                LLVMValueRef gcMallocAtomicFunc =
                    LLVM.LLVMAddFunction(moduleRef, "GC_malloc_atomic", funcType);

                IrTypeImpl irType = new IrTypeImpl(funcType, (IrContextImpl) module.getContext());
                return new IrValueImpl(gcMallocAtomicFunc, irType);
            }

        } catch (Exception e) {
            throw new IrException("Failed to declare GC_malloc_atomic function: " + e.getMessage(),
                e);
        }
    }

    @Override
    public void close() {
        if (!disposed) {
            LLVM.LLVMDisposeModule(moduleRef);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}