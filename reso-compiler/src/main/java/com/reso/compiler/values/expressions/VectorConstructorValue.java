package com.reso.compiler.values.expressions;

import static com.reso.compiler.types.StandardTypeHandles.USIZE;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.types.ResourceType;
import com.reso.compiler.values.ConcreteResoValue;
import com.reso.compiler.values.ResoValue;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import com.reso.llvm.enums.IrIntPredicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class VectorConstructorValue extends ResoValue {
    private final CodeGenerationContext context;

    public VectorConstructorValue(@Nonnull ResourceType vectorType,
                                  @Nonnull CodeGenerationContext context,
                                  int line,
                                  int column) {
        super(vectorType, null, null, line, column);
        this.context = context;
    }

    @Nullable
    @Override
    public ConcreteResoValue concretize(@Nonnull ResoType targetType,
                                        @Nonnull ErrorReporter errorReporter) {
        if (!canConcretizeTo(targetType)) {
            errorReporter.error(
                "Cannot convert vector constructor to type " + targetType.getName(),
                line, column
            );
            return null;
        }

        ResourceType vectorType = (ResourceType) targetType;
        return generateVectorAllocation(vectorType.getGenericTypes().getFirst(), line, column);
    }

    @Override
    public boolean canConcretizeTo(@Nonnull ResoType targetType) {
        return targetType.isReference() && type.getName().equals(targetType.getName());
    }

    /**
     * Generates the actual vector allocation.
     *
     * @param elementType The element type of the vector
     * @param line        Line number for error reporting
     * @param column      Column number for error reporting
     * @return The allocated vector
     */
    @Nullable
    private ConcreteResoValue generateVectorAllocation(ResoType elementType,
                                                       int line, int column) {
        try {
            IrValue currentFunction = context.getCurrentFunction();
            if (currentFunction == null) {
                context.getErrorReporter()
                    .error("Vector constructor must be called within a function", line, column);
                return null;
            }

            ResourceType vectorType = context.getTypeSystem().getOrCreateVectorType(elementType);
            ResoType usizeType = context.getTypeSystem().getType(USIZE);

            IrValue initialCapacity = IrFactory.createConstantInt(usizeType.getType(), 8, false);
            IrValue initialSize = IrFactory.createConstantInt(usizeType.getType(), 0, false);

            // Allocate memory for the vector struct
            IrValue vectorStruct = IrFactory.createGCMalloc(
                context.getIrBuilder(),
                context.getIrModule(),
                vectorType.getStructType(),
                IrFactory.declareGCMalloc(context.getIrModule()),
                "vector_struct"
            );

            // Allocate memory for the elements array using the calculated capacity
            IrValue elementsArray = IrFactory.createGCArrayMalloc(
                context.getIrBuilder(),
                context.getIrModule(),
                vectorType.getGenericTypes().getFirst().getType(),
                initialCapacity,
                IrFactory.declareGCMalloc(context.getIrModule()),
                "elements_array"
            );

            // Initialize vector struct fields
            // struct { T* elements, usize size, usize capacity }

            // Set elements pointer (field 0)
            IrValue elementsPtr =
                IrFactory.createStructGEP(context.getIrBuilder(), vectorType.getStructType(),
                    vectorStruct, 0, "elements_ptr");
            IrFactory.createStore(context.getIrBuilder(), elementsArray, elementsPtr);

            // Set size (field 1)
            IrValue sizePtr =
                IrFactory.createStructGEP(context.getIrBuilder(), vectorType.getStructType(),
                    vectorStruct, 1, "size_ptr");
            IrFactory.createStore(context.getIrBuilder(), initialSize, sizePtr);

            // Set capacity (field 2) - this is the allocated capacity (may be larger than size)
            IrValue capacityPtr =
                IrFactory.createStructGEP(context.getIrBuilder(), vectorType.getStructType(),
                    vectorStruct, 2, "capacity_ptr");
            IrFactory.createStore(context.getIrBuilder(), initialCapacity, capacityPtr);

            return new ConcreteResoValue(vectorType, vectorStruct, line, column);

        } catch (Exception e) {
            context.getErrorReporter()
                .error("Failed to generate vector allocation: " + e.getMessage(),
                    line, column);
            return null;
        }
    }

    /**
     * Creates a for-loop that initializes vector elements with the default value.
     * Only initializes up to the requested size, not the full capacity.
     * LLVM Structure:
     * entry:
     * br label %loop_header
     * loop_header:
     * %i = phi i64 [ 0, %entry ], [ %next_i, %loop_body ]
     * %condition = icmp ult i64 %i, %size
     * br i1 %condition, label %loop_body, label %loop_exit
     * loop_body:
     * %element_ptr = getelementptr inbounds T, T* %elements, i64 %i
     * store T %defaultValue, T* %element_ptr
     * %next_i = add i64 %i, 1
     * br label %loop_header
     * loop_exit:
     * ; continue with rest of function...
     */
    private void generateVectorInitialization(@Nonnull IrValue elementsArray,
                                              @Nonnull ConcreteResoValue sizeValue,
                                              @Nonnull ConcreteResoValue defaultValue,
                                              @Nonnull ResourceType vectorType,
                                              int line, int column) {
        try {
            IrValue currentFunction = context.getCurrentFunction();
            IrBuilder irBuilder = context.getIrBuilder();

            // Remember current block for PHI incoming value
            IrBasicBlock entryBlock = IrFactory.getCurrentBasicBlock(irBuilder);

            // Create basic blocks for the initialization loop
            IrBasicBlock loopHeader = IrFactory.createBasicBlock(currentFunction,
                "vector_init_header");
            IrBasicBlock loopBody = IrFactory.createBasicBlock(currentFunction,
                "vector_init_body");
            IrBasicBlock loopExit = IrFactory.createBasicBlock(currentFunction,
                "vector_init_exit");

            // Jump from current block to loop header
            IrFactory.createBr(irBuilder, loopHeader);

            // Loop header: PHI node and condition check
            IrFactory.positionAtEnd(irBuilder, loopHeader);

            // Create loop counter PHI node: i = phi [0, entry], [next_i, loop_body]
            IrValue zero = IrFactory.createConstantInt(sizeValue.getType().getType(), 0, false);
            IrValue counter = IrFactory.createPhi(irBuilder, sizeValue.getType().getType(), "i");

            // Compare: i < size (only initialize up to requested size, not full capacity)
            IrValue condition = IrFactory.createICmp(irBuilder,
                IrIntPredicate.ULT, counter, sizeValue.getValue(), "loop_condition");

            // Conditional branch: if (i < size) goto loop_body else goto loop_exit
            IrFactory.createCondBr(irBuilder, condition, loopBody, loopExit);

            // Loop body: initialize current element and increment counter
            IrFactory.positionAtEnd(irBuilder, loopBody);

            // Get pointer to current element: elements[i]
            IrValue elementPtr = IrFactory.createInBoundsGEP(irBuilder,
                vectorType.getGenericTypes().getFirst().getType(),   // Element type
                elementsArray,                          // Base pointer (elements array)
                new IrValue[] {counter},                 // Indices (just the loop counter)
                "element_ptr"                           // Name
            );

            // Store default value: *element_ptr = defaultValue
            IrFactory.createStore(irBuilder, defaultValue.getValue(), elementPtr);

            // Increment counter: next_i = i + 1
            IrValue one = IrFactory.createConstantInt(sizeValue.getType().getType(), 1, false);
            IrValue nextCounter = IrFactory.createAdd(irBuilder, counter, one, "next_i");

            // Jump back to header
            IrFactory.createBr(irBuilder, loopHeader);

            // Set up PHI node incoming values
            IrFactory.addIncoming(counter, zero, entryBlock);        // Initial: i = 0 from entry
            IrFactory.addIncoming(counter, nextCounter,
                loopBody);   // Updated: i = next_i from loop_body

            // Loop exit: continue with rest of function
            IrFactory.positionAtEnd(irBuilder, loopExit);

        } catch (Exception e) {
            context.getErrorReporter()
                .error("Failed to generate vector initialization: " + e.getMessage(),
                    line, column);
        }
    }
}
