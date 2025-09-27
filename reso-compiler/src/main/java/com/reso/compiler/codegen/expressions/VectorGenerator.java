package com.reso.compiler.codegen.expressions;

import static com.reso.compiler.types.StandardTypeHandles.USIZE;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.types.ResourceType;
import com.reso.compiler.values.ResoValue;
import com.reso.compiler.values.expressions.VectorConstructorValue;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import com.reso.llvm.enums.IrIntPredicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for built-in Vector resource operations.
 * Handles Vector constructor calls and method implementations.
 */
public class VectorGenerator {

    private VectorGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Generates code for a vector constructor call: vector().
     *
     * @param context The code generation context
     * @param line    Line number for error reporting
     * @param column  Column number for error reporting
     * @return The constructed vector value
     */
    @Nonnull
    public static ResoValue generateVectorConstructor(@Nonnull ResoType vectorType,
                                                      @Nonnull CodeGenerationContext context,
                                                      int line, int column) {
        return new VectorConstructorValue((ResourceType) vectorType, context, line, column);
    }

    /**
     * Generates code for reading an element from a vector: vec/{index}.get().
     *
     * @param type           The resource type (ResourceType)
     * @param argumentValues The arguments
     * @param context        The code generation context
     * @param line           Line number for error reporting
     * @param column         Column number for error reporting
     * @return The read element value
     */
    @Nullable
    public static ResoValue generateVectorReadElement(@Nonnull ResoType type,
                                                      @Nonnull IrValue[] argumentValues,
                                                      @Nonnull CodeGenerationContext context,
                                                      int line, int column) {
        ResourceType resourceType = (ResourceType) type;

        // Generate the actual read operation
        return generateVectorRead(context, argumentValues[0], argumentValues[1], resourceType, line,
            column);
    }

    /**
     * Generates the vector read operation with bounds checking.
     * Algorithm:
     * 1. Load vector size and elements pointer
     * 2. Check bounds: index < size
     * 3. If valid: load element at index and return it
     * 4. If invalid: generate runtime error
     *
     * @param vectorValue  The concrete vector value
     * @param indexValue   The concrete index value
     * @param resourceType The vector type
     * @param line         Line number for error reporting
     * @param column       Column number for error reporting
     * @return The element value at the given index
     */
    @Nullable
    private static ResoValue generateVectorRead(@Nonnull CodeGenerationContext context,
                                                @Nonnull IrValue vectorValue,
                                                @Nonnull IrValue indexValue,
                                                @Nonnull ResourceType resourceType, int line,
                                                int column) {
        IrValue currentFunction = context.getCurrentFunction();
        if (currentFunction == null) {
            context.getErrorReporter()
                .error("Vector read must be called within a function", line, column);
            return null;
        }

        IrBuilder irBuilder = context.getIrBuilder();

        // Get struct field pointers
        IrValue elementsPtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 0,
                "elements_ptr");
        IrValue sizePtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 1,
                "size_ptr");

        // Load current values
        ResoType usizeType = context.getTypeSystem().getType(USIZE);
        IrValue currentElements = IrFactory.createLoad(irBuilder, elementsPtr,
            IrFactory.createPointerType(resourceType.getGenericTypes().getFirst().getType(), 0),
            "current_elements");
        IrValue currentSize =
            IrFactory.createLoad(irBuilder, sizePtr, usizeType.getType(), "current_size");

        // Bounds check: index < size
        IrValue validIndex =
            IrFactory.createICmp(irBuilder, IrIntPredicate.ULT, indexValue, currentSize,
                "valid_index");

        // Create blocks for bounds check
        IrBasicBlock boundsOkBlock = IrFactory.createBasicBlock(currentFunction, "bounds_ok");
        IrBasicBlock boundsErrorBlock = IrFactory.createBasicBlock(currentFunction, "bounds_error");
        IrBasicBlock continueBlock = IrFactory.createBasicBlock(currentFunction, "continue");

        IrFactory.createCondBr(irBuilder, validIndex, boundsOkBlock, boundsErrorBlock);

        // Bounds error block: generate runtime error
        IrFactory.positionAtEnd(irBuilder, boundsErrorBlock);
        // For now, just return unreachable - in the future this would call a runtime error function
        // TODO: Add runtime bounds checking error with proper error message
        IrFactory.createUnreachable(irBuilder);

        // Bounds OK block: proceed with reading
        IrFactory.positionAtEnd(irBuilder, boundsOkBlock);

        // Get pointer to element: elements[index]
        IrValue elementPtr = IrFactory.createInBoundsGEP(irBuilder,
            resourceType.getGenericTypes().getFirst().getType(), currentElements,
            new IrValue[] {indexValue}, "element_ptr");

        // Load the element value
        IrValue elementValue = IrFactory.createLoad(irBuilder, elementPtr,
            resourceType.getGenericTypes().getFirst().getType(), "element_value");

        // Create the return value
        ResoValue resultValue =
            new ResoValue(resourceType.getGenericTypes().getFirst(), elementValue, line, column);

        IrFactory.createBr(irBuilder, continueBlock);

        // Continue block
        IrFactory.positionAtEnd(irBuilder, continueBlock);

        return resultValue;
    }

    /**
     * Generates code for writing an element to a vector: vec/{index}.set(value).
     *
     * @param type           The resource type
     * @param argumentValues The arguments
     * @param context        The code generation context
     * @param line           Line number for error reporting
     * @param column         Column number for error reporting
     * @return The unit value (since set returns nothing)
     */
    @Nonnull
    public static ResoValue generateVectorWriteElement(@Nonnull ResoType type,
                                                       @Nonnull IrValue[] argumentValues,
                                                       @Nonnull CodeGenerationContext context,
                                                       int line, int column) {
        ResourceType resourceType = (ResourceType) type;

        // Generate the actual write operation
        generateVectorWrite(context, argumentValues[0], argumentValues[1], argumentValues[2],
            resourceType, line, column);

        // Return Unit value
        ResoType unitType = context.getTypeSystem().getUnitType();
        IrValue unitValue = IrFactory.createConstantNamedStruct(unitType.getType(), new IrValue[0]);
        return new ResoValue(unitType, unitValue, line, column);
    }

    /**
     * Generates the vector write operation with bounds checking.
     * Algorithm:
     * 1. Load vector size and elements pointer
     * 2. Check bounds: index < size
     * 3. If valid: store value at index
     * 4. If invalid: generate runtime error
     *
     * @param vectorValue  The concrete vector value
     * @param indexValue   The concrete index value
     * @param elementValue The concrete element value to store
     * @param resourceType The vector type
     * @param line         Line number for error reporting
     * @param column       Column number for error reporting
     */
    private static void generateVectorWrite(@Nonnull CodeGenerationContext context,
                                            @Nonnull IrValue vectorValue,
                                            @Nonnull IrValue indexValue,
                                            @Nonnull IrValue elementValue,
                                            @Nonnull ResourceType resourceType, int line,
                                            int column) {
        IrValue currentFunction = context.getCurrentFunction();
        if (currentFunction == null) {
            context.getErrorReporter()
                .error("Vector write must be called within a function", line, column);
            return;
        }

        IrBuilder irBuilder = context.getIrBuilder();

        // Get struct field pointers
        IrValue elementsPtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 0,
                "elements_ptr");
        IrValue sizePtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 1,
                "size_ptr");

        // Load current values
        ResoType usizeType = context.getTypeSystem().getType(USIZE);
        IrValue currentElements = IrFactory.createLoad(irBuilder, elementsPtr,
            IrFactory.createPointerType(resourceType.getGenericTypes().getFirst().getType(), 0),
            "current_elements");
        IrValue currentSize =
            IrFactory.createLoad(irBuilder, sizePtr, usizeType.getType(), "current_size");

        // Bounds check: index < size
        IrValue validIndex =
            IrFactory.createICmp(irBuilder, IrIntPredicate.ULT, indexValue, currentSize,
                "valid_index");

        // Create blocks for bounds check
        IrBasicBlock boundsOkBlock = IrFactory.createBasicBlock(currentFunction, "bounds_ok");
        IrBasicBlock boundsErrorBlock = IrFactory.createBasicBlock(currentFunction, "bounds_error");
        IrBasicBlock continueBlock = IrFactory.createBasicBlock(currentFunction, "continue");

        IrFactory.createCondBr(irBuilder, validIndex, boundsOkBlock, boundsErrorBlock);

        // Bounds error block: generate runtime error
        IrFactory.positionAtEnd(irBuilder, boundsErrorBlock);
        // For now, just return unreachable - in the future this would call a runtime error function
        // TODO: Add runtime bounds checking error with proper error message
        IrFactory.createUnreachable(irBuilder);

        // Bounds OK block: proceed with writing
        IrFactory.positionAtEnd(irBuilder, boundsOkBlock);

        // Get pointer to element: elements[index]
        IrValue elementPtr = IrFactory.createInBoundsGEP(irBuilder,
            resourceType.getGenericTypes().getFirst().getType(), currentElements,
            new IrValue[] {indexValue}, "element_ptr");

        // Store the element value
        IrFactory.createStore(irBuilder, elementValue, elementPtr);

        IrFactory.createBr(irBuilder, continueBlock);

        // Continue block
        IrFactory.positionAtEnd(irBuilder, continueBlock);
    }

    /**
     * Generates code for inserting an element into an vector: vec.insert(index, element)
     * Inserts an element at the specified index, shifting all elements after it to the right.
     *
     * @param type   The resource type (ResourceType)
     * @param argumentValues The arguments
     * @param context        The code generation context
     * @param line           Line number for error reporting
     * @param column         Column number for error reporting
     * @return The unit value (since insert returns nothing)
     */
    @Nonnull
    public static ResoValue generateVectorInsertElement(@Nonnull ResoType type,
                                                        @Nonnull IrValue[] argumentValues,
                                                        @Nonnull CodeGenerationContext context,
                                                        int line, int column) {
        ResourceType resourceType = (ResourceType) type;


        // Generate the actual insert operation
        generateVectorInsert(context, argumentValues[0], argumentValues[1], argumentValues[2],
            resourceType, line, column);

        // Return Unit value
        ResoType unitType = context.getTypeSystem().getUnitType();
        IrValue unitValue = IrFactory.createConstantNamedStruct(unitType.getType(), new IrValue[0]);
        return new ResoValue(unitType, unitValue, line, column);
    }

    /**
     * Generates the vector insert operation with bounds checking and element shifting.
     * Algorithm:
     * 1. Check bounds: index <= size (can insert at end)
     * 2. Check if resize is needed: size >= capacity
     * 3. If resize needed: resize vector (double capacity)
     * 4. Shift elements [index..size) to [index+1..size+1)
     * 5. Store element at index
     * 6. Increment size
     *
     * @param vectorValue  The concrete vector value
     * @param indexValue   The concrete index value
     * @param elementValue The concrete element value to insert
     * @param resourceType The vector type
     * @param line         Line number for error reporting
     * @param column       Column number for error reporting
     */
    private static void generateVectorInsert(@Nonnull CodeGenerationContext context,
                                             @Nonnull IrValue vectorValue,
                                             @Nonnull IrValue indexValue,
                                             @Nonnull IrValue elementValue,
                                             @Nonnull ResourceType resourceType, int line,
                                             int column) {
        IrValue currentFunction = context.getCurrentFunction();
        if (currentFunction == null) {
            context.getErrorReporter()
                .error("Vector insert must be called within a function", line, column);
            return;
        }

        IrBuilder irBuilder = context.getIrBuilder();

        // Get struct field pointers
        IrValue elementsPtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 0,
                "elements_ptr");
        IrValue sizePtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 1,
                "size_ptr");
        IrValue capacityPtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 2,
                "capacity_ptr");

        // Load current values
        ResoType usizeType = context.getTypeSystem().getType(USIZE);
        IrValue currentElements = IrFactory.createLoad(irBuilder, elementsPtr,
            IrFactory.createPointerType(resourceType.getGenericTypes().getFirst().getType(), 0),
            "current_elements");
        IrValue currentSize =
            IrFactory.createLoad(irBuilder, sizePtr, usizeType.getType(), "current_size");
        IrValue currentCapacity =
            IrFactory.createLoad(irBuilder, capacityPtr, usizeType.getType(), "current_capacity");

        // Bounds check: index <= size (can insert at end)
        IrValue validIndex =
            IrFactory.createICmp(irBuilder, IrIntPredicate.ULE, indexValue, currentSize,
                "valid_index");

        // Create blocks for bounds check
        IrBasicBlock boundsOkBlock = IrFactory.createBasicBlock(currentFunction, "bounds_ok");
        IrBasicBlock boundsErrorBlock = IrFactory.createBasicBlock(currentFunction, "bounds_error");

        IrFactory.createCondBr(irBuilder, validIndex, boundsOkBlock, boundsErrorBlock);

        // Bounds error block: generate runtime error
        IrFactory.positionAtEnd(irBuilder, boundsErrorBlock);
        // For now, just return unreachable - in the future this would call a runtime error function
        // TODO: Add runtime bounds checking error with proper error message
        IrFactory.createUnreachable(irBuilder);

        // Bounds OK block: proceed with insertion
        IrFactory.positionAtEnd(irBuilder, boundsOkBlock);

        // Check if resize is needed: size >= capacity
        IrValue needsResize =
            IrFactory.createICmp(irBuilder, IrIntPredicate.UGE, currentSize, currentCapacity,
                "needs_resize");

        // Create basic blocks for resize logic
        IrBasicBlock resizeBlock = IrFactory.createBasicBlock(currentFunction, "resize_vector");
        IrBasicBlock shiftBlock = IrFactory.createBasicBlock(currentFunction, "shift_elements");
        IrBasicBlock insertBlock = IrFactory.createBasicBlock(currentFunction, "insert_element");
        IrBasicBlock continueBlock = IrFactory.createBasicBlock(currentFunction, "continue");

        // Conditional branch: if needs_resize goto resize_vector else goto shift_elements
        IrFactory.createCondBr(irBuilder, needsResize, resizeBlock, shiftBlock);

        // Resize block: double capacity and reallocate
        IrFactory.positionAtEnd(irBuilder, resizeBlock);

        // Calculate new capacity: capacity * 2 (or 1 if capacity was 0)
        IrValue zero = IrFactory.createConstantInt(usizeType.getType(), 0, false);
        IrValue one = IrFactory.createConstantInt(usizeType.getType(), 1, false);
        IrValue two = IrFactory.createConstantInt(usizeType.getType(), 2, false);

        IrValue isCapacityZero =
            IrFactory.createICmp(irBuilder, IrIntPredicate.EQ, currentCapacity, zero,
                "is_capacity_zero");
        IrValue doubledCapacity =
            IrFactory.createMul(irBuilder, currentCapacity, two, "doubled_capacity");
        IrValue newCapacity =
            IrFactory.createSelect(irBuilder, isCapacityZero, one, doubledCapacity, "new_capacity");

        // Allocate new vector with doubled capacity
        IrValue newElements = IrFactory.createGCArrayMalloc(irBuilder, context.getIrModule(),
            resourceType.getGenericTypes().getFirst().getType(), newCapacity,
            IrFactory.declareGCMalloc(context.getIrModule()), "new_elements");

        // Copy existing elements if any
        IrValue hasElements =
            IrFactory.createICmp(irBuilder, IrIntPredicate.NE, currentSize, zero, "has_elements");

        IrBasicBlock copyBlock = IrFactory.createBasicBlock(currentFunction, "copy_elements");
        IrBasicBlock updatePointersBlock =
            IrFactory.createBasicBlock(currentFunction, "update_pointers");

        IrFactory.createCondBr(irBuilder, hasElements, copyBlock, updatePointersBlock);

        // Copy block: copy old elements to new vector
        IrFactory.positionAtEnd(irBuilder, copyBlock);

        // Calculate size in bytes
        IrValue elementSize = getElementSize(irBuilder, resourceType, currentSize);
        IrValue copySize = IrFactory.createMul(irBuilder, currentSize, elementSize, "copy_size");

        // Use memcpy to copy elements
        IrFactory.createMemCpy(irBuilder, newElements, currentElements, copySize, 1, 1);

        IrFactory.createBr(irBuilder, updatePointersBlock);

        // Update pointers block
        IrFactory.positionAtEnd(irBuilder, updatePointersBlock);

        // Update vector struct with new elements and capacity
        IrFactory.createStore(irBuilder, newElements, elementsPtr);
        IrFactory.createStore(irBuilder, newCapacity, capacityPtr);

        IrFactory.createBr(irBuilder, shiftBlock);

        // Shift block: move elements [index..size) to [index+1..size+1)
        IrFactory.positionAtEnd(irBuilder, shiftBlock);

        // Load potentially updated values
        IrValue finalElements = IrFactory.createLoad(irBuilder, elementsPtr,
            IrFactory.createPointerType(resourceType.getGenericTypes().getFirst().getType(), 0),
            "final_elements");
        IrValue finalSize =
            IrFactory.createLoad(irBuilder, sizePtr, usizeType.getType(), "final_size");

        // Check if we need to shift elements: index < size
        IrValue needsShift =
            IrFactory.createICmp(irBuilder, IrIntPredicate.ULT, indexValue, finalSize,
                "needs_shift");

        IrBasicBlock doShiftBlock = IrFactory.createBasicBlock(currentFunction, "do_shift");

        IrFactory.createCondBr(irBuilder, needsShift, doShiftBlock, insertBlock);

        // Do shift block: actually shift the elements
        IrFactory.positionAtEnd(irBuilder, doShiftBlock);

        // Calculate number of elements to shift: size - index
        IrValue elementsToShift =
            IrFactory.createSub(irBuilder, finalSize, indexValue, "elements_to_shift");

        // Calculate shift size in bytes
        IrValue shiftSize =
            IrFactory.createMul(irBuilder, elementsToShift, elementSize, "shift_size");

        // Source: elements + index
        IrValue srcPtr = IrFactory.createInBoundsGEP(irBuilder,
            resourceType.getGenericTypes().getFirst().getType(), finalElements,
            new IrValue[] {indexValue}, "src_ptr");

        // Destination: elements + index + 1
        IrValue indexPlus1 = IrFactory.createAdd(irBuilder, indexValue, one, "index_plus_1");
        IrValue dstPtr = IrFactory.createInBoundsGEP(irBuilder,
            resourceType.getGenericTypes().getFirst().getType(), finalElements,
            new IrValue[] {indexPlus1}, "dst_ptr");

        // Use memmove to shift elements right (memmove handles overlapping memory)
        IrFactory.createMemMove(irBuilder, dstPtr, srcPtr, shiftSize, 1, 1);

        IrFactory.createBr(irBuilder, insertBlock);

        // Insert block: store the new element at the insertion point
        IrFactory.positionAtEnd(irBuilder, insertBlock);

        // Get pointer to insertion position: elements[index]
        IrValue insertPtr = IrFactory.createInBoundsGEP(irBuilder,
            resourceType.getGenericTypes().getFirst().getType(), finalElements,
            new IrValue[] {indexValue}, "insert_ptr");

        // Store the new element
        IrFactory.createStore(irBuilder, elementValue, insertPtr);

        // Increment size
        IrValue newSize = IrFactory.createAdd(irBuilder, finalSize, one, "new_size");
        IrFactory.createStore(irBuilder, newSize, sizePtr);

        IrFactory.createBr(irBuilder, continueBlock);

        // Continue block
        IrFactory.positionAtEnd(irBuilder, continueBlock);
    }

    /**
     * Generates code for removing an element from an vector: vec.remove(index)
     * Removes and returns the element at the specified index,
     * shifting all elements after it to the left.
     *
     * @param type   The resource type (ResourceType)
     * @param argumentValues The arguments
     * @param context        The code generation context
     * @param line           Line number for error reporting
     * @param column         Column number for error reporting
     * @return The removed element value
     */
    @Nullable
    public static ResoValue generateVectorRemoveElement(@Nonnull ResoType type,
                                                        @Nonnull IrValue[] argumentValues,
                                                        @Nonnull CodeGenerationContext context,
                                                        int line, int column) {
        ResourceType resourceType = (ResourceType) type;

        // Generate the actual remove operation
        return generateVectorRemove(context, argumentValues[0], argumentValues[1], resourceType,
            line, column);
    }

    /**
     * Generates the vector remove operation with bounds checking and element shifting.
     * Algorithm:
     * 1. Check bounds: index < size
     * 2. Load element at index to return it
     * 3. Check if shift is needed: index < size-1
     * 4. If shift needed: move elements [index+1..size) to [index..size-1)
     * 5. Decrement size
     * 6. Return the removed element
     *
     * @param vectorValue  The concrete vector value
     * @param indexValue   The concrete index value
     * @param resourceType The vector type
     * @param line         Line number for error reporting
     * @param column       Column number for error reporting
     * @return The removed element value
     */
    @Nullable
    private static ResoValue generateVectorRemove(@Nonnull CodeGenerationContext context,
                                                  @Nonnull IrValue vectorValue,
                                                  @Nonnull IrValue indexValue,
                                                  @Nonnull ResourceType resourceType,
                                                  int line,
                                                  int column) {
        IrValue currentFunction = context.getCurrentFunction();
        if (currentFunction == null) {
            context.getErrorReporter()
                .error("Vector remove must be called within a function", line, column);
            return null;
        }

        IrBuilder irBuilder = context.getIrBuilder();

        // Get struct field pointers
        IrValue elementsPtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 0,
                "elements_ptr");
        IrValue sizePtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 1,
                "size_ptr");

        // Load current values
        ResoType usizeType = context.getTypeSystem().getType(USIZE);
        IrValue currentElements = IrFactory.createLoad(irBuilder, elementsPtr,
            IrFactory.createPointerType(resourceType.getGenericTypes().getFirst().getType(), 0),
            "current_elements");
        IrValue currentSize =
            IrFactory.createLoad(irBuilder, sizePtr, usizeType.getType(), "current_size");

        // Bounds check: index < size
        IrValue validIndex =
            IrFactory.createICmp(irBuilder, IrIntPredicate.ULT, indexValue, currentSize,
                "valid_index");

        // Create blocks for bounds check
        IrBasicBlock boundsOkBlock = IrFactory.createBasicBlock(currentFunction, "bounds_ok");
        IrBasicBlock boundsErrorBlock = IrFactory.createBasicBlock(currentFunction, "bounds_error");

        IrFactory.createCondBr(irBuilder, validIndex, boundsOkBlock, boundsErrorBlock);

        // Bounds error block: generate runtime error
        IrFactory.positionAtEnd(irBuilder, boundsErrorBlock);
        // For now, just return unreachable - in the future this would call a runtime error function
        // TODO: Add runtime bounds checking error with proper error message
        IrFactory.createUnreachable(irBuilder);

        // Bounds OK block: proceed with removal
        IrFactory.positionAtEnd(irBuilder, boundsOkBlock);

        // Get pointer to element to remove: elements[index]
        IrValue elementPtr = IrFactory.createInBoundsGEP(irBuilder,
            resourceType.getGenericTypes().getFirst().getType(), currentElements,
            new IrValue[] {indexValue}, "element_ptr");

        // Load the element to return it
        IrValue removedElement = IrFactory.createLoad(irBuilder, elementPtr,
            resourceType.getGenericTypes().getFirst().getType(), "removed_element");

        // Check if we need to shift elements: index < size - 1
        IrValue one = IrFactory.createConstantInt(usizeType.getType(), 1, false);
        IrValue sizeMinus1 = IrFactory.createSub(irBuilder, currentSize, one, "size_minus_1");
        IrValue needsShift =
            IrFactory.createICmp(irBuilder, IrIntPredicate.ULT, indexValue, sizeMinus1,
                "needs_shift");

        // Create blocks for shift logic
        IrBasicBlock shiftBlock = IrFactory.createBasicBlock(currentFunction, "shift_elements");
        IrBasicBlock updateSizeBlock = IrFactory.createBasicBlock(currentFunction, "update_size");
        IrBasicBlock continueBlock = IrFactory.createBasicBlock(currentFunction, "continue");

        IrFactory.createCondBr(irBuilder, needsShift, shiftBlock, updateSizeBlock);

        // Shift block: move elements [index+1..size) to [index..size-1)
        IrFactory.positionAtEnd(irBuilder, shiftBlock);

        // Calculate number of elements to shift: size - index - 1
        IrValue indexPlus1 = IrFactory.createAdd(irBuilder, indexValue, one, "index_plus_1");
        IrValue elementsToShift =
            IrFactory.createSub(irBuilder, currentSize, indexPlus1, "elements_to_shift");

        // Calculate shift size in bytes
        IrValue elementSize = IrFactory.createConstantInt(usizeType.getType(),
            resourceType.getGenericTypes().getFirst().getBitWidth() / 8, false);
        IrValue shiftSize =
            IrFactory.createMul(irBuilder, elementsToShift, elementSize, "shift_size");

        // Source: elements + index + 1
        IrValue srcPtr = IrFactory.createInBoundsGEP(irBuilder,
            resourceType.getGenericTypes().getFirst().getType(), currentElements,
            new IrValue[] {indexPlus1}, "src_ptr");

        // Destination: elements + index
        IrValue dstPtr = IrFactory.createInBoundsGEP(irBuilder,
            resourceType.getGenericTypes().getFirst().getType(), currentElements,
            new IrValue[] {indexValue}, "dst_ptr");

        // Use memmove to shift elements left
        IrFactory.createMemMove(irBuilder, dstPtr, srcPtr, shiftSize, 1, 1);

        IrFactory.createBr(irBuilder, updateSizeBlock);

        // Update size block: decrement size
        IrFactory.positionAtEnd(irBuilder, updateSizeBlock);

        IrValue newSize = IrFactory.createSub(irBuilder, currentSize, one, "new_size");
        IrFactory.createStore(irBuilder, newSize, sizePtr);

        IrFactory.createBr(irBuilder, continueBlock);

        // Continue block: return the removed element
        IrFactory.positionAtEnd(irBuilder, continueBlock);

        return new ResoValue(resourceType.getGenericTypes().getFirst(), removedElement, line,
            column);
    }

    /**
     * Generates code for adding an element to an vector: vec.add(element).
     *
     * @param type   The resource type (ResourceType)
     * @param argumentValues The arguments
     * @param context        The code generation context
     * @param line           Line number for error reporting
     * @param column         Column number for error reporting
     * @return The unit value
     */
    @Nonnull
    public static ResoValue generateVectorAddElement(@Nonnull ResoType type,
                                                     @Nonnull IrValue[] argumentValues,
                                                     @Nonnull CodeGenerationContext context,
                                                     int line, int column) {
        ResourceType resourceType = (ResourceType) type;

        generateVectorAdd(context, argumentValues[0], argumentValues[1], resourceType, line,
            column);

        // Return Unit value
        ResoType unitType = context.getTypeSystem().getUnitType();
        IrValue unitValue = IrFactory.createConstantNamedStruct(unitType.getType(), new IrValue[0]);
        return new ResoValue(unitType, unitValue, line, column);
    }

    /**
     * Generates the vector add operation with automatic resizing.
     * Algorithm:
     * 1. Check if size < capacity
     * 2. If yes: simply add element at size position and increment size
     * 3. If no: resize vector (double capacity), then add element
     *
     * @param vectorValue  The concrete vector value
     * @param elementValue The concrete element value
     * @param resourceType The vector type
     * @param line         Line number for error reporting
     * @param column       Column number for error reporting
     */
    private static void generateVectorAdd(@Nonnull CodeGenerationContext context,
                                          @Nonnull IrValue vectorValue,
                                          @Nonnull IrValue elementValue,
                                          @Nonnull ResourceType resourceType, int line,
                                          int column) {
        IrValue currentFunction = context.getCurrentFunction();
        if (currentFunction == null) {
            context.getErrorReporter()
                .error("Vector add must be called within a function", line, column);
            return;
        }

        IrBuilder irBuilder = context.getIrBuilder();

        // Get struct field pointers
        IrValue elementsPtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 0,
                "elements_ptr");
        IrValue sizePtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 1,
                "size_ptr");
        IrValue capacityPtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), vectorValue, 2,
                "capacity_ptr");

        // Load current values
        ResoType usizeType = context.getTypeSystem().getType(USIZE);
        IrValue currentElements = IrFactory.createLoad(irBuilder, elementsPtr,
            IrFactory.createPointerType(resourceType.getGenericTypes().getFirst().getType(), 0),
            "current_elements");
        IrValue currentSize =
            IrFactory.createLoad(irBuilder, sizePtr, usizeType.getType(), "current_size");
        IrValue currentCapacity =
            IrFactory.createLoad(irBuilder, capacityPtr, usizeType.getType(), "current_capacity");

        // Check if resize is needed: size >= capacity
        IrValue needsResize =
            IrFactory.createICmp(irBuilder, IrIntPredicate.UGE, currentSize, currentCapacity,
                "needs_resize");

        // Create basic blocks
        IrBasicBlock resizeBlock = IrFactory.createBasicBlock(currentFunction, "resize_vector");
        IrBasicBlock addElementBlock = IrFactory.createBasicBlock(currentFunction, "add_element");
        IrBasicBlock continueBlock = IrFactory.createBasicBlock(currentFunction, "continue");

        // Conditional branch: if needs_resize goto resize_vector else goto add_element
        IrFactory.createCondBr(irBuilder, needsResize, resizeBlock, addElementBlock);

        // Resize block: double capacity and reallocate
        IrFactory.positionAtEnd(irBuilder, resizeBlock);

        // Calculate new capacity: capacity * 2 (or 1 if capacity was 0)
        IrValue zero = IrFactory.createConstantInt(usizeType.getType(), 0, false);
        IrValue one = IrFactory.createConstantInt(usizeType.getType(), 1, false);
        IrValue two = IrFactory.createConstantInt(usizeType.getType(), 2, false);

        IrValue isCapacityZero =
            IrFactory.createICmp(irBuilder, IrIntPredicate.EQ, currentCapacity, zero,
                "is_capacity_zero");
        IrValue doubledCapacity =
            IrFactory.createMul(irBuilder, currentCapacity, two, "doubled_capacity");
        IrValue newCapacity =
            IrFactory.createSelect(irBuilder, isCapacityZero, one, doubledCapacity, "new_capacity");

        // Allocate new vector with doubled capacity
        IrValue newElements = IrFactory.createGCArrayMalloc(irBuilder, context.getIrModule(),
            resourceType.getGenericTypes().getFirst().getType(), newCapacity,
            IrFactory.declareGCMalloc(context.getIrModule()), "new_elements");

        // Copy existing elements if any
        IrValue hasElements =
            IrFactory.createICmp(irBuilder, IrIntPredicate.NE, currentSize, zero, "has_elements");

        IrBasicBlock copyBlock = IrFactory.createBasicBlock(currentFunction, "copy_elements");
        IrBasicBlock updatePointersBlock =
            IrFactory.createBasicBlock(currentFunction, "update_pointers");

        IrFactory.createCondBr(irBuilder, hasElements, copyBlock, updatePointersBlock);

        // Copy block: copy old elements to new vector
        IrFactory.positionAtEnd(irBuilder, copyBlock);

        // Calculate size in bytes:
        IrValue elementSize = getElementSize(irBuilder, resourceType, currentSize);
        IrValue copySize = IrFactory.createMul(irBuilder, currentSize, elementSize, "copy_size");

        // Use memcpy to copy elements
        IrFactory.createMemCpy(irBuilder, newElements, currentElements, copySize, 1, 1);

        IrFactory.createBr(irBuilder, updatePointersBlock);

        // Update pointers block
        IrFactory.positionAtEnd(irBuilder, updatePointersBlock);

        // Update vector struct with new elements and capacity
        IrFactory.createStore(irBuilder, newElements, elementsPtr);
        IrFactory.createStore(irBuilder, newCapacity, capacityPtr);

        IrFactory.createBr(irBuilder, addElementBlock);

        // Add element block: add the element at the end
        IrFactory.positionAtEnd(irBuilder, addElementBlock);

        // Load potentially updated values
        IrValue finalElements = IrFactory.createLoad(irBuilder, elementsPtr,
            IrFactory.createPointerType(resourceType.getGenericTypes().getFirst().getType(), 0),
            "final_elements");
        IrValue finalSize =
            IrFactory.createLoad(irBuilder, sizePtr, usizeType.getType(), "final_size");

        // Get pointer to new element position: elements[size]
        IrValue elementPtr = IrFactory.createInBoundsGEP(irBuilder,
            resourceType.getGenericTypes().getFirst().getType(), finalElements,
            new IrValue[] {finalSize}, "element_ptr");

        // Store the new element
        IrFactory.createStore(irBuilder, elementValue, elementPtr);

        // Increment size
        IrValue newSize = IrFactory.createAdd(irBuilder, finalSize, one, "new_size");
        IrFactory.createStore(irBuilder, newSize, sizePtr);

        IrFactory.createBr(irBuilder, continueBlock);

        // Continue block
        IrFactory.positionAtEnd(irBuilder, continueBlock);
    }

    /**
     * Generates vector size access: vec/size.get().
     *
     * @param type   The resource type (ResourceType)
     * @param argumentValues The arguments
     * @param context        The code generation context
     * @param line           Line number for error reporting
     * @param column         Column number for error reporting
     * @return The size value (usize)
     */
    @Nonnull
    public static ResoValue generateVectorSizeAccess(@Nonnull ResoType type,
                                                     @Nonnull IrValue[] argumentValues,
                                                     @Nonnull CodeGenerationContext context,
                                                     int line, int column) {
        ResourceType resourceType = (ResourceType) type;

        IrBuilder irBuilder = context.getIrBuilder();

        // Get size from vector struct (field 1)
        IrValue sizePtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), argumentValues[0], 1,
                "size_ptr");

        ResoType usizeType = context.getTypeSystem().getType(USIZE);
        IrValue sizeValue = IrFactory.createLoad(irBuilder, sizePtr, usizeType.getType(), "size");

        return new ResoValue(usizeType, sizeValue, line, column);
    }

    /**
     * Generates vector capacity access: vec/size.capacity().
     *
     * @param type   The resource type (ResourceType)
     * @param argumentValues The arguments
     * @param context        The code generation context
     * @param line           Line number for error reporting
     * @param column         Column number for error reporting
     * @return The capacity value (usize)
     */
    @Nonnull
    public static ResoValue generateVectorCapacityAccess(@Nonnull ResoType type,
                                                         @Nonnull IrValue[] argumentValues,
                                                         @Nonnull CodeGenerationContext context,
                                                         int line, int column) {
        ResourceType resourceType = (ResourceType) type;

        IrBuilder irBuilder = context.getIrBuilder();

        // Get capacity from vector struct (field 2)
        IrValue capacityPtr =
            IrFactory.createStructGEP(irBuilder, resourceType.getStructType(), argumentValues[0], 2,
                "capacity_ptr");

        ResoType usizeType = context.getTypeSystem().getType(USIZE);
        IrValue capacityValue =
            IrFactory.createLoad(irBuilder, capacityPtr, usizeType.getType(), "capacity");

        return new ResoValue(usizeType, capacityValue, line, column);
    }

    private static IrValue getElementSize(@Nonnull IrBuilder irBuilder,
                                          @Nonnull ResourceType resourceType,
                                          @Nonnull IrValue currentSize) {
        IrValue elementSize = IrFactory.createSizeOf(irBuilder.getContext(),
            resourceType.getGenericTypes().getFirst().getType());
        int elementSizeBits = elementSize.getType().getIntBitWidth();
        int currentSizeBits = currentSize.getType().getIntBitWidth();
        if (elementSizeBits < currentSizeBits) {
            // Zero extend element size to match current size type
            elementSize = IrFactory.createZExt(irBuilder, elementSize, currentSize.getType(),
                "element_size_zext");
        } else if (elementSizeBits > currentSizeBits) {
            // Truncate element size to match current size type
            elementSize = IrFactory.createTrunc(irBuilder, elementSize, currentSize.getType(),
                "element_size_trunc");
        }
        return elementSize;
    }
}