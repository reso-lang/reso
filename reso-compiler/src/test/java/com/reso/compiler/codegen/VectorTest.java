package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for Vector functionality in Reso.
 * Tests compilation from Reso source code to LLVM IR with verification of:
 * - Vector creation for various types (inline malloc operations)
 * - Vector size operations (inline struct field access)
 * - Element access (inline bounds checking and memory access)
 * - Vector manipulation (inline memory management)
 * - Vector usage in different contexts (if conditions, while loops, returns, etc.)
 * - Edge cases and error handling
 */
public class VectorTest extends BaseTest {

    private String usizeType;

    @BeforeEach
    public void setUp() {
        usizeType = "i" + POINTER_SIZE;
    }

    // ============================================================================
    // Basic Vector Creation Tests
    // ============================================================================

    @ParameterizedTest
    @MethodSource("vectorTypeTestData")
    public void testVectorCreationForDifferentTypes(String typeName, String structTypeName,
                                                    String elementType) {
        String sourceCode = wrapInMainFunction(String.format("""
            var vec: Vector<%s> = Vector()
            """, typeName));
        String ir =
            compileAndExpectSuccess(sourceCode, "vector_creation_" + typeName.toLowerCase());

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("vec", "ptr"),
            IrPatterns.malloc("vector_struct", "\"" + structTypeName + "\""),
            IrPatterns.arrayMalloc("elements_array", elementType),
            IrPatterns.fieldAccess("elements_ptr", structTypeName, "vector_struct", 0),
            IrPatterns.store("elements_array", "ptr", "elements_ptr"),
            IrPatterns.fieldAccess("size_ptr", structTypeName, "vector_struct", 1),
            IrPatterns.store("0", usizeType, "size_ptr"),
            IrPatterns.fieldAccess("capacity_ptr", structTypeName, "vector_struct", 2),
            IrPatterns.store("8", usizeType, "capacity_ptr"),
            IrPatterns.store("vector_struct", "ptr", "vec")
        );
    }

    // ============================================================================
    // Vector Size Operations Tests
    // ============================================================================

    @Test
    public void testVectorSizeAccess() {
        String sourceCode = wrapInMainFunction("""
            var numbers: Vector<i32> = Vector()
            var size: usize = numbers/size.get()
            var empty_check: bool = size == 0
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_size_access");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("numbers", "ptr"),
            IrPatterns.alloca("size", usizeType),
            IrPatterns.alloca("empty_check", "i1")
        );

        // Verify inline size access (no function call)
        assertIrContainsInOrder(mainFunc,
            IrPatterns.load("ptr", "numbers"),
            IrPatterns.fieldAccess("size_ptr", "Vector<i32>", "numbers_value", 1),
            IrPatterns.load(usizeType, "size_ptr"),
            IrPatterns.store("size", usizeType, "size"),
            IrPatterns.icmp("eq", usizeType, "size", "0")
        );
    }

    @Test
    public void testVectorSizeAfterOperations() {
        String sourceCode = wrapInMainFunction("""
            var vec: Vector<i32> = Vector()
            var initial_size: usize = vec/size.get()
            vec.add(42)
            vec.add(13)
            var after_size: usize = vec/size.get()
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_size_after_operations");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("vec", "ptr"),
            IrPatterns.alloca("initial_size", usizeType),
            IrPatterns.alloca("after_size", usizeType)
        );

        // Verify multiple size accesses (inline operations)
        assertIrContains(mainFunc,
            IrPatterns.load("ptr", "vec"),
            IrPatterns.fieldAccess("size_ptr", "Vector<i32>", "vec_value", 1),
            IrPatterns.load("ptr", "vec"),
            IrPatterns.fieldAccess("size_ptr", "Vector<i32>", "vec_value", 1)
        );
    }

    // ============================================================================
    // Vector Capacity Operations Tests
    // ============================================================================

    @Test
    public void testVectorCapacityAccess() {
        String sourceCode = wrapInMainFunction("""
            var chars: Vector<char> = Vector()
            var capacity: usize = chars/capacity.get()
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_capacity_access");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("chars", "ptr"),
            IrPatterns.alloca("capacity", usizeType)
        );

        // Verify inline capacity access (no function call)
        assertIrContainsInOrder(mainFunc,
            IrPatterns.load("ptr", "chars"),
            IrPatterns.fieldAccess("capacity_ptr", "Vector<char>", "chars_value", 2),
            IrPatterns.load(usizeType, "capacity_ptr"),
            IrPatterns.store("capacity", usizeType, "capacity")
        );
    }

    // ============================================================================
    // Element Access Tests (Get/Set)
    // ============================================================================

    @Test
    public void testBasicElementAccess() {
        String sourceCode = wrapInMainFunction("""
            var numbers: Vector<i32> = Vector()
            var first: i32 = numbers[0].get()
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_element_access");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        // Verify inline element access with bounds checking
        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("first", "i32"),
            IrPatterns.alloca("numbers", "ptr"),
            IrPatterns.load("ptr", "numbers"),
            IrPatterns.fieldAccess("elements_ptr", "Vector<i32>", "numbers_value", 0),
            IrPatterns.fieldAccess("size_ptr", "Vector<i32>", "numbers_value", 1),
            IrPatterns.load("ptr", "elements_ptr"),
            IrPatterns.load(usizeType, "size_ptr"),
            IrPatterns.icmp("ult", usizeType, "0", "current_size"), // bounds check: index < size
            IrPatterns.conditionalBranch("valid_index", "bounds_ok", "bounds_error"),
            IrPatterns.label("bounds_ok"),
            IrPatterns.arrayAccess("element_ptr", "i32", "current_elements", 0),
            IrPatterns.load("i32", "element_ptr"),
            IrPatterns.store("element", "i32", "first")
        );
    }

    @Test
    public void testElementModification() {
        String sourceCode = wrapInMainFunction("""
            var values: Vector<f64> = Vector()
            values[0].set(9.9)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "element_modification");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        // Verify inline element set operations with bounds checking
        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("values", "ptr"),
            IrPatterns.load("ptr", "values"),
            IrPatterns.fieldAccess("size_ptr", "Vector<f64>", "values_value", 1),
            IrPatterns.load(usizeType, "size_ptr"),
            IrPatterns.icmp("ult", usizeType, "0", "current_size"), // bounds check
            IrPatterns.conditionalBranch("valid_index", "bounds_ok", "bounds_error"),
            IrPatterns.label("bounds_ok"),
            IrPatterns.arrayAccess("element_ptr", "double", "current_elements", 0),
            IrPatterns.store("9.900000e\\+00", "double", "element_ptr")
        );
    }

    // ============================================================================
    // Vector Manipulation Tests (Add/Insert/Remove)
    // ============================================================================

    @Test
    public void testVectorAddOperation() {
        String sourceCode = wrapInMainFunction("""
            var chars: Vector<char> = Vector()
            chars.add(65 as char)  # 'A'
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_add_operation");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        // Verify inline add operations (capacity checks, reallocation, element storage)
        assertIrContainsInOrder(mainFunc,
            IrPatterns.load("ptr", "chars"),
            IrPatterns.fieldAccess("elements_ptr", "Vector<char>", "chars", 0),
            IrPatterns.fieldAccess("size_ptr", "Vector<char>", "chars", 1),
            IrPatterns.fieldAccess("capacity_ptr", "Vector<char>", "chars", 2),
            IrPatterns.load("ptr", "elements_ptr"),
            IrPatterns.load(usizeType, "size_ptr"),
            IrPatterns.load(usizeType, "capacity_ptr"),
            IrPatterns.icmp("uge", usizeType, "current_size", "current_capacity"),
            IrPatterns.conditionalBranch("needs_resize", "resize_vector", "add_element"),

            // Resize vector block
            IrPatterns.label("resize_vector"),
            IrPatterns.icmp("eq", usizeType, "current_capacity", "0"),
            IrPatterns.mul(usizeType, "current_capacity", "2"),
            IrPatterns.select("i1", "is_capacity_zero", usizeType, "1", "doubled_capacity"),
            "call ptr @GC_malloc\\(i" + POINTER_SIZE + " %new_elements_total_size\\)",
            IrPatterns.icmp("ne", usizeType, "current_size", "0"),
            IrPatterns.conditionalBranch("has_elements", "copy_elements", "update_pointers"),

            // Add element block
            IrPatterns.label("add_element"),
            IrPatterns.load("ptr", "elements_ptr"),
            IrPatterns.load(usizeType, "size_ptr"),
            IrPatterns.arrayAccess("element_ptr", "i32", "final_elements", "final_size"),
            IrPatterns.store("65", "i32", "element_ptr"),
            IrPatterns.add(usizeType, "final_size", "1"),
            IrPatterns.store("new_size", usizeType, "size_ptr"),

            // Copy elements block
            IrPatterns.label("copy_elements"),
            IrPatterns.mul(usizeType, "current_size",
                "ptrtoint \\(ptr getelementptr \\(i32, ptr null, i32 1\\)"),
            IrPatterns.functionCall("llvm.memcpy.p0.p0.i" + POINTER_SIZE, "void", List.of(/*...*/)),
            IrPatterns.unconditionalBranch("update_pointers"),

            // Update pointers block
            IrPatterns.label("update_pointers"),
            IrPatterns.store("new_elements", "ptr", "elements_ptr"),
            IrPatterns.store("new_capacity", usizeType, "capacity_ptr"),
            IrPatterns.unconditionalBranch("add_element")
        );
    }

    @Test
    public void testVectorInsertOperation() {
        String sourceCode = wrapInMainFunction("""
            var numbers: Vector<i32> = Vector()
            numbers.insert(1, 20)  # Insert 20 at position 1
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_insert_operation");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        // Verify inline insert operation (bounds check, capacity check, element shifting)
        assertIrContainsInOrder(mainFunc,
            IrPatterns.load("ptr", "numbers"),
            IrPatterns.fieldAccess("elements_ptr", "Vector<i32>", "numbers", 0),
            IrPatterns.fieldAccess("size_ptr", "Vector<i32>", "numbers", 1),
            IrPatterns.fieldAccess("capacity_ptr", "Vector<i32>", "numbers", 2),
            IrPatterns.load("ptr", "elements_ptr"),
            IrPatterns.load(usizeType, "size_ptr"),
            IrPatterns.load(usizeType, "capacity_ptr"),

            // Bounds check: index <= size (insert allows index == size)
            IrPatterns.icmp("ule", usizeType, "1", "current_size"),
            IrPatterns.conditionalBranch("valid_index", "bounds_ok", "bounds_error"),

            IrPatterns.label("bounds_ok"),
            // Capacity check
            IrPatterns.icmp("uge", usizeType, "current_size", "current_capacity"),
            IrPatterns.conditionalBranch("needs_resize", "resize_vector", "shift_elements"),

            // Resize vector
            IrPatterns.label("resize_vector"),
            IrPatterns.icmp("eq", usizeType, "current_capacity", "0"),
            IrPatterns.mul(usizeType, "current_capacity", "2"),
            IrPatterns.select("i1", "is_capacity_zero", usizeType, "1", "doubled_capacity"),

            // Shift elements block
            IrPatterns.label("shift_elements"),
            IrPatterns.load("ptr", "elements_ptr"),
            IrPatterns.load(usizeType, "size_ptr"),
            IrPatterns.icmp("ult", usizeType, "1", "final_size"),
            IrPatterns.conditionalBranch("needs_shift", "do_shift", "insert_element"),

            // Insert element at the specified position
            IrPatterns.label("insert_element"),
            IrPatterns.store("20", "i32", "insert_ptr"),
            IrPatterns.add(usizeType, "final_size", "1"),
            IrPatterns.store("new_size", usizeType, "size_ptr2"),

            // Copy elements block
            IrPatterns.label("copy_elements"),
            IrPatterns.mul(usizeType, "current_size",
                "ptrtoint \\(ptr getelementptr \\(i32, ptr null, i32 1\\)"),
            IrPatterns.functionCall("llvm.memcpy.p0.p0.i" + POINTER_SIZE, "void", List.of(/*...*/)),
            IrPatterns.unconditionalBranch("update_pointers"),

            // Do shift block - move elements to make space for insertion
            IrPatterns.label("do_shift"),
            IrPatterns.sub(usizeType, "final_size", "1"),
            IrPatterns.mul(usizeType, "elements_to_shift",
                "ptrtoint \\(ptr getelementptr \\(i32, ptr null, i32 1\\)"),
            IrPatterns.arrayAccess("src_ptr", "i32", "final_elements", "1"),
            IrPatterns.arrayAccess("dst_ptr", "i32", "final_elements", "2"),
            IrPatterns.functionCall("llvm.memmove.p0.p0.i" + POINTER_SIZE, "void",
                List.of(/*...*/)),
            IrPatterns.unconditionalBranch("insert_element")
        );
    }

    @Test
    public void testVectorRemoveOperation() {
        String sourceCode = wrapInMainFunction("""
            var items: Vector<bool> = Vector()
            var removed: bool = items.remove(1)  # Remove element at index 1
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_remove_operation");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        // Verify inline remove operation (bounds check, element retrieval, shifting)
        assertIrContainsInOrder(mainFunc,
            IrPatterns.load("ptr", "items"),
            IrPatterns.fieldAccess("elements_ptr", "Vector<bool>", "items", 0),
            IrPatterns.fieldAccess("size_ptr", "Vector<bool>", "items", 1),
            IrPatterns.load("ptr", "elements_ptr"),
            IrPatterns.load(usizeType, "size_ptr"),

            // Bounds check: index < size (remove requires valid existing index)
            IrPatterns.icmp("ult", usizeType, "1", "current_size"),
            IrPatterns.conditionalBranch("valid_index", "bounds_ok", "bounds_error"),

            IrPatterns.label("bounds_ok"),
            // Get the element that will be removed
            IrPatterns.arrayAccess("element_ptr", "i1", "current_elements", "1"),
            IrPatterns.load("i1", "element_ptr"), // removed_element

            // Check if shifting is needed
            IrPatterns.sub(usizeType, "current_size", "1"),
            IrPatterns.icmp("ult", usizeType, "1", "size_minus"),
            IrPatterns.conditionalBranch("needs_shift", "shift_elements", "update_size"),

            // Shift elements left to fill the gap
            IrPatterns.label("shift_elements"),
            IrPatterns.sub(usizeType, "current_size", "2"), // elements_to_shift
            IrPatterns.mul(usizeType, "elements_to_shift", "0"), // shift_size for bool (i1)
            IrPatterns.arrayAccess("src_ptr", "i1", "current_elements", "2"),
            IrPatterns.arrayAccess("dst_ptr", "i1", "current_elements", "1"),
            IrPatterns.functionCall("llvm.memmove.p0.p0.i" + POINTER_SIZE, "void",
                List.of(/*...*/)),
            IrPatterns.unconditionalBranch("update_size"),

            // Update vector size
            IrPatterns.label("update_size"),
            IrPatterns.sub(usizeType, "current_size", "1"),
            IrPatterns.store("new_size", usizeType, "size_ptr"),
            IrPatterns.unconditionalBranch("continue"),

            IrPatterns.label("continue"),
            // Store the removed element in the result variable
            IrPatterns.store("removed_element", "i1", "removed")
        );
    }

    // ============================================================================
    // Vectors in Control Flow Tests
    // ============================================================================

    @Test
    public void testVectorInIfCondition() {
        String sourceCode = wrapInMainFunction("""
            var numbers: Vector<i32> = Vector()
            numbers.add(5)
            if numbers/size.get() > 0:
                var first: i32 = numbers[0].get()
                numbers[1 + 3 * first as usize].set(first * 2)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_in_if_condition");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("first", "i32"),
            IrPatterns.alloca("numbers", "ptr"),
            IrPatterns.load("ptr", "numbers"),
            IrPatterns.fieldAccess("size_ptr", "Vector<i32>", "numbers_value", 1),
            IrPatterns.load(usizeType, "size_ptr"),
            IrPatterns.icmp("ugt", usizeType, "size", "0"),
            IrPatterns.conditionalBranch("icmp", "if_then", "if_end"),
            IrPatterns.label("if_then"),
            // Inside if block: element access and modification
            IrPatterns.load("ptr", "numbers"),
            IrPatterns.fieldAccess("elements_ptr", "Vector<i32>", "numbers_value", 0),
            IrPatterns.arrayAccess("element_ptr", "i32", "current_elements", 0),
            IrPatterns.load("i32", "element_ptr"),
            IrPatterns.mul(usizeType, "3", usizeType.equals("i32") ? "first" : "sext"),
            IrPatterns.add(usizeType, "1", "mul"),
            IrPatterns.mul("i32", "first", "2")
        );
    }

    @Test
    public void testVectorInWhileLoop() {
        String sourceCode = wrapInMainFunction("""
            var data: Vector<i32> = Vector()
            var i: usize = 0
            while i < 5:
                data.add((i * 10) as i32)
                i = i + 1
            var finalSize: usize = data/size.get()
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_in_while_loop");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("finalSize", usizeType),
            IrPatterns.alloca("i", usizeType),
            IrPatterns.alloca("data", "ptr"),
            IrPatterns.store("0", usizeType, "i"),
            IrPatterns.unconditionalBranch("while_cond"),
            IrPatterns.label("while_cond"),
            IrPatterns.icmp("ult", usizeType, "i", "5"),
            IrPatterns.conditionalBranch("icmp", "while_body", "while_end"),
            IrPatterns.label("while_body"),
            IrPatterns.mul(usizeType, "i", "10"),
            // Inline add operation in loop
            IrPatterns.load("ptr", "data"),
            IrPatterns.fieldAccess("size_ptr", "Vector<i32>", "data_value", 1),
            IrPatterns.add(usizeType, "i", "1")
        );
    }

    // ============================================================================
    // Vectors as Return Values and Function Arguments Tests
    // ============================================================================

    @Test
    public void testVectorAsReturnValue() {
        String sourceCode = """
            def create_vector() -> Vector<i32>:
                var vec: Vector<i32> = Vector()
                vec.add(1)
                vec.add(2)
                vec.add(3)
                return vec
            
            def main() -> i32:
                var result: Vector<i32> = create_vector()
                var size: usize = result/size.get()
                return size as i32
            """;
        String ir = compileAndExpectSuccess(sourceCode, "vector_as_return_value");

        assertIrContains(ir,
            IrPatterns.functionDefinition("create_vector", "ptr"),
            IrPatterns.functionDefinition("main", "i32")
        );

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("result", "ptr"),
            IrPatterns.functionCall("create_vector", "ptr", Collections.emptyList()),
            IrPatterns.store("create_vector_result", "ptr", "result"),
            // Inline size access on returned vector
            IrPatterns.load("ptr", "result"),
            IrPatterns.fieldAccess("size_ptr", "Vector<i32>", "result_value", 1)
        );

        String createVectorFunc = extractFunction(ir, "create_vector");
        assertNotNull(createVectorFunc, "create_vector function should be present in the IR");

        assertIrContainsInOrder(createVectorFunc,
            IrPatterns.alloca("vec", "ptr"),
            IrPatterns.malloc("vector_struct", "\"Vector<i32>\""),
            // Inline add operations
            IrPatterns.fieldAccess("size_ptr", "Vector<i32>", "vec_value", 1),
            IrPatterns.returnStatement("ptr", "vec")
        );
    }

    // ============================================================================
    // Vectors in Ternary Expressions Tests
    // ============================================================================

    @Test
    public void testVectorInTernaryExpression() {
        String sourceCode = wrapInMainFunction("""
            var vec1: Vector<i32> = Vector()
            var vec2: Vector<i32> = Vector()
            vec1.add(10)
            vec2.add(20)
            vec2.add(30)
            var condition: bool = true
            var chosen: Vector<i32> = vec1 if condition else vec2
            var chosenSize: usize = chosen/size.get()
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_in_ternary_expression");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "condition_value", "ptr", "vec1_value", "vec2_value"),
            IrPatterns.load("ptr", "chosen"),
            IrPatterns.fieldAccess("size_ptr", "Vector<i32>", "chosen_value", 1)

        );
    }

    @Test
    public void testUntypedVectorInTernaryExpression() {
        String sourceCode = wrapInMainFunction("""
            var vec1: Vector<i32> = Vector()
            var vec2: Vector<i32> = vec1 if true else Vector()
            """);
        String ir = compileAndExpectSuccess(sourceCode, "untyped_vector_in_ternary_expression");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "true", "ptr", "vec1_value", "vector_struct")
        );
    }

    @Test
    public void testUntypedVectorsInTernaryExpression() {
        String sourceCode = wrapInMainFunction("""
            var vec: Vector<i32> = Vector() if true else null if false else Vector()
            """);
        String ir = compileAndExpectSuccess(sourceCode, "untyped_vector_in_ternary_expression");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "true", "ptr", "vector_struct", "null"),
            IrPatterns.select("i1", "false", "ptr", "ternary", "vector_struct")
        );
    }

    // ============================================================================
    // Vectors in Comparison Expressions Tests
    // ============================================================================

    @Test
    public void testVectorComparison() {
        String sourceCode = wrapInMainFunction("""
            var vec1: Vector<i32> = Vector()
            var vec2: Vector<i32> = Vector()
            var is_equal: bool = vec1 == vec2
            var is_not_equal: bool = vec1 != vec2
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_comparison");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("eq", "ptr", "vec1_value", "vec2_value"),
            IrPatterns.icmp("ne", "ptr", "vec1_value", "vec2_value")
        );
    }

    @Test
    public void testUntypedVectorComparison() {
        String sourceCode = wrapInMainFunction("""
            var vec1: Vector<i32> = Vector()
            var is_equal: bool = vec1 == Vector()
            var is_not_equal: bool = vec1 != Vector()
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_comparison");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("eq", "ptr", "vec1_value", "vector_struct"),
            IrPatterns.icmp("ne", "ptr", "vec1_value", "vector_struct")
        );
    }

    @Test
    public void testNullVectorComparison() {
        String sourceCode = wrapInMainFunction("""
            var vec1: Vector<i32> = Vector()
            var is_equal: bool = vec1 == null
            var is_not_equal: bool = vec1 != null
            """);
        String ir = compileAndExpectSuccess(sourceCode, "null_vector_comparison");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("eq", "ptr", "vec1_value", "null"),
            IrPatterns.icmp("ne", "ptr", "vec1_value", "null")
        );
    }

    @Test
    public void testVectorComparisonWithNull() {
        String sourceCode = wrapInMainFunction("""
            var vec: Vector<i32> = Vector()
            var is_equal: bool = vec == null
            var is_not_equal: bool = vec != null
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_comparison_with_null");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.icmp("eq", "ptr", "vec_value", "null"),
            IrPatterns.icmp("ne", "ptr", "vec_value", "null")
        );
    }

    // ============================================================================
    // Edge Cases Tests
    // ============================================================================

    @Test
    public void testNestedVectorTypes() {
        String sourceCode = wrapInMainFunction("""
            var matrix: Vector<Vector<i32> > = Vector()
            var row1: Vector<i32> = Vector()
            row1.add(1)
            row1.add(2)
            var row2: Vector<i32> = Vector()
            row2.add(3)
            row2.add(4)
            matrix.add(row1)
            matrix.add(row2)
            var matrix_size: usize = matrix/size.get()
            var first_row: Vector<i32> = matrix[0].get()
            var first_element: i32 = first_row[0].get()
            """);
        String ir = compileAndExpectSuccess(sourceCode, "nested_vector_types");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("matrix", "ptr"),
            IrPatterns.alloca("row1", "ptr"),
            IrPatterns.alloca("row2", "ptr"),
            IrPatterns.alloca("matrix_size", usizeType),
            IrPatterns.alloca("first_row", "ptr"),
            IrPatterns.alloca("first_element", "i32")
        );

        // Verify nested vector creation and access
        assertIrContains(mainFunc,
            IrPatterns.malloc("vector_struct", "\"Vector<Vector>\""),
            IrPatterns.malloc("vector_struct", "\"Vector<i32>\"")
        );

        assertIrContainsInOrder(mainFunc,
            // Access matrix element (returns Vector<i32>)
            IrPatterns.load("ptr", "matrix"),
            IrPatterns.fieldAccess("elements_ptr", "Vector<Vector>", "matrix_value", 0),
            IrPatterns.load("ptr", "element_ptr"),
            IrPatterns.store("element", "ptr", "first_row"),
            // Access element from nested vector
            IrPatterns.load("ptr", "first_row"),
            IrPatterns.fieldAccess("elements_ptr", "Vector<i32>", "first_row_value", 0),
            IrPatterns.load("i32", "element_ptr")
        );
    }

    @Test
    public void testVectorWithComplexExpressions() {
        String sourceCode = wrapInMainFunction("""
            var numbers: Vector<i32> = Vector()
            numbers.add(5)
            numbers.add(10)
            numbers.add(15)
            var index: usize = 1 as usize
            var multiplier: i32 = 3
            var result: i32 = numbers[index].get() * multiplier + numbers[0].get()
            numbers[index].set(result)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "vector_complex_expressions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("numbers", "ptr"),
            IrPatterns.alloca("index", usizeType),
            IrPatterns.alloca("multiplier", "i32"),
            IrPatterns.alloca("result", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            // Complex expression: numbers[index].get() * multiplier + numbers[0].get()
            IrPatterns.load("ptr", "numbers"),
            IrPatterns.load(usizeType, "index"),
            IrPatterns.arrayAccess("element_ptr", "i32", "current_elements", "index_value"),
            IrPatterns.load("i32", "element_ptr"),
            IrPatterns.arrayAccess("element_ptr", "i32", "current_elements", 0),
            IrPatterns.mul("i32", "element_value", "multiplier"),
            IrPatterns.add("i32", "mul", "element_value"),
            // Set operation with result
            IrPatterns.load("ptr", "numbers"),
            IrPatterns.store("result", "i32", "element_ptr")
        );
    }

    // ============================================================================
    // Error Cases Tests
    // ============================================================================

    @Test
    public void testUntypedVectorConstructorError() {
        String sourceCode = wrapInMainFunction("""
            var untyped_vector = Vector()
            """);
        String errors = compileAndExpectFailure(sourceCode, "untyped_vector_constructor");

        assertErrorContains(errors,
            "Cannot infer type for variable untyped_vector"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Vector()/size.get()",
        "Vector()/capacity.get()",
        "Vector().add(5)",
        "Vector().insert(0, 10)",
        "Vector().remove(0)",
        "Vector()[0].get()",
        "Vector()[0].set(42)"
    })
    public void testUntypedVectorOperationErrors(String vectorOperation) {
        String sourceCode = wrapInMainFunction(vectorOperation);
        String testName = "untyped_vector_operation_" + Math.abs(vectorOperation.hashCode());
        String errors = compileAndExpectFailure(sourceCode, testName);

        assertErrorContains(errors, "Cannot access resource on untyped value");
    }

    @Test
    public void testVectorFunctionError() {
        String sourceCode = """
            def Vector() -> i32:
                return 42
            """;
        String errors = compileAndExpectFailure(sourceCode, "vector_function_error");

        assertErrorContains(errors,
            "Vector is already defined"
        );
    }

    @Test
    public void testVectorResourceError() {
        String sourceCode = """
            resource Vector{var data: i32}
            """;
        String errors = compileAndExpectFailure(sourceCode, "vector_resource_error");

        assertErrorContains(errors,
            "Vector resource already registered"
        );
    }

    @Test
    public void testArithmeticOnVectorError() {
        String sourceCode = wrapInMainFunction("""
            var vec: Vector<i32> = Vector()
            var invalid: i32 = vec + 5
            """);
        String errors = compileAndExpectFailure(sourceCode, "arithmetic_on_vector_error");

        assertErrorContains(errors,
            "Cannot perform arithmetic operation '+' on non-numeric types"
        );
    }

    @Test
    public void testArithmeticOnUntypedVectorError() {
        String sourceCode = wrapInMainFunction("""
            Vector() + 5
            """);
        String errors = compileAndExpectFailure(sourceCode, "arithmetic_on_vector_error");

        assertErrorContains(errors,
            "Cannot perform arithmetic operation '+' on non-numeric types"
        );
    }

    @Test
    public void testBitwiseOnVectorError() {
        String sourceCode = wrapInMainFunction("""
            var vec: Vector<i32> = Vector()
            var invalid: i32 = vec & 3
            """);
        String errors = compileAndExpectFailure(sourceCode, "bitwise_on_vector_error");

        assertErrorContains(errors,
            "Cannot perform bitwise operation '&' with non-integer"
        );
    }

    @Test
    public void testBitwiseOnUntypedVectorError() {
        String sourceCode = wrapInMainFunction("""
            Vector() | 1
            """);
        String errors = compileAndExpectFailure(sourceCode, "bitwise_on_vector_error");

        assertErrorContains(errors,
            "Cannot perform bitwise operation '|' with non-integer"
        );
    }

    @Test
    public void testUnaryOnVectorError() {
        String sourceCode = wrapInMainFunction("""
            var vec: Vector<i32> = Vector()
            var invalid: i32 = -vec
            """);
        String errors = compileAndExpectFailure(sourceCode, "unary_on_vector_error");

        assertErrorContains(errors,
            "Unary - requires a numeric operand"
        );
    }

    @Test
    public void testUnaryOnUntypedVectorError() {
        String sourceCode = wrapInMainFunction("""
            -Vector()
            """);
        String errors = compileAndExpectFailure(sourceCode, "unary_on_vector_error");

        assertErrorContains(errors,
            "Unary - requires a numeric operand"
        );
    }

    @Test
    public void testComparisonOnUntypedVectorError() {
        String sourceCode = wrapInMainFunction("""
            var invalid: bool = Vector() == Vector()
            """);
        String errors = compileAndExpectFailure(sourceCode, "comparison_on_vector_error");

        assertErrorContains(errors,
            "Cannot compare"
        );
    }

    @Test
    public void testComparisonOnUntypedVectorAndNullError() {
        String sourceCode = wrapInMainFunction("""
            var invalid: bool = Vector() == null
            """);
        String errors = compileAndExpectFailure(sourceCode, "comparison_on_vector_error");

        assertErrorContains(errors,
            "Cannot compare"
        );
    }

    @Test
    public void testComparisonOfDifferentVectorTypesError() {
        String sourceCode = wrapInMainFunction("""
            var vec1: Vector<i32> = Vector()
            var vec2: Vector<f32> = Vector()
            var invalid: bool = vec1 == vec2
            """);
        String errors =
            compileAndExpectFailure(sourceCode, "comparison_of_different_vector_types_error");

        assertErrorContains(errors,
            "Cannot compare Vector and Vector"
        );
    }

    // ============================================================================
    // Parameterized Test Data Providers
    // ============================================================================

    /**
     * Provides test data for different vector element types.
     *
     * @return Stream of test arguments containing type names, struct names and element types
     */
    private static Stream<Arguments> vectorTypeTestData() {
        String sizeType = "i" + POINTER_SIZE;

        return Stream.of(
            // Integer types
            Arguments.of("i8", "Vector<i8>", "i8"),
            Arguments.of("i16", "Vector<i16>", "i16"),
            Arguments.of("i32", "Vector<i32>", "i32"),
            Arguments.of("i64", "Vector<i64>", "i64"),
            Arguments.of("isize", "Vector<isize>", sizeType),
            Arguments.of("u8", "Vector<u8>", "i8"),
            Arguments.of("u16", "Vector<u16>", "i16"),
            Arguments.of("u32", "Vector<u32>", "i32"),
            Arguments.of("u64", "Vector<u64>", "i64"),
            Arguments.of("usize", "Vector<usize>", sizeType),
            Arguments.of("char", "Vector<char>", "i32"),

            // Floating-point types
            Arguments.of("f32", "Vector<f32>", "float"),
            Arguments.of("f64", "Vector<f64>", "double"),

            // Other basic types
            Arguments.of("bool", "Vector<bool>", "i1"),
            Arguments.of("String", "Vector<String>", "ptr")
        );
    }

    /**
     * Provides test data for vector operations with different numeric types.
     *
     * @return Stream of test arguments for numeric vector operations
     */
    private static Stream<Arguments> numericVectorOperationTestData() {
        return Stream.of(
            Arguments.of("i32", "i32", "42", "84"),
            Arguments.of("i64", "i64", "123456789", "246913578"),
            Arguments.of("f32", "float", "3.14", "6.28"),
            Arguments.of("f64", "double", "2.71828", "5.43656"),
            Arguments.of("u32", "i32", "100", "200"),
            Arguments.of("u64", "i64", "500", "1000")
        );
    }

    @ParameterizedTest
    @MethodSource("numericVectorOperationTestData")
    public void testNumericVectorOperations(String typeName, String llvmType, String value1,
                                            String value2) {
        String sourceCode = wrapInMainFunction(String.format("""
            var vec: Vector<%s> = Vector()
            vec.add(%s)
            vec.add(%s)
            var sum: %s = vec[0].get() + vec[1].get()
            """, typeName, value1, value2, typeName));

        String ir = compileAndExpectSuccess(sourceCode,
            "numeric_vector_operations_" + typeName.toLowerCase());

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("vec", "ptr"),
            IrPatterns.alloca("sum", llvmType)
        );

        // Verify inline vector operations and arithmetic
        assertIrContains(mainFunc,
            IrPatterns.malloc("vector_struct", "\"Vector<" + typeName + ">\""),
            IrPatterns.arrayAccess("element_ptr", llvmType, "current_elements", 0),
            IrPatterns.arrayAccess("element_ptr", llvmType, "current_elements", 1)
        );
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Verifies that compilation errors contain expected error messages.
     *
     * @param actualErrors     The actual compilation errors
     * @param expectedMessages Expected error messages
     */
    private void assertErrorContains(String actualErrors, String... expectedMessages) {
        for (String expectedMessage : expectedMessages) {
            assertTrue(actualErrors.contains(expectedMessage)
                    || actualErrors.toLowerCase().contains(expectedMessage.toLowerCase()),
                "Expected error message not found: " + expectedMessage
                    + "\nActual errors: " + actualErrors);
        }
    }
}