package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for resource definitions in Reso.
 * Tests compilation from Reso source code to LLVM IR with verification of:
 * - Resource constructors with simple and block statements
 * - Resource methods with and without return types
 * - Return statement path coverage requirements
 * - Unreachable code detection
 * - Resource initializer requirements
 * - Multiple paths with same/different methods
 * - Error handling for invalid resource definitions
 */
public class ResourceDefinitionTest extends BaseTest {

    // ============================================================================
    // Basic Resource Definition Tests
    // ============================================================================

    @Test
    public void testEmptyResourceDefinition() {
        String sourceCode = """
            resource EmptyResource{}
            
            def main() -> i32:
                EmptyResource{}
            """;

        String ir = compileAndExpectSuccess(sourceCode, "empty_resource_definition");

        assertIrContainsInOrder(ir,
            IrPatterns.structType("EmptyResource_struct")
        );
    }

    @Test
    public void testBasicResourceDefinitionWithConstructor() {
        String sourceCode = """
            resource User{var id: i32}
            
            def main() -> i32:
                User{1}
            """;

        String ir = compileAndExpectSuccess(sourceCode, "basic_resource_definition");

        assertIrContainsInOrder(ir,
            IrPatterns.structType("User_struct")
        );
    }

    @Test
    public void testResourceWithMultipleFields() {
        String sourceCode = """
            resource Product{
                pub var name: String,
                var price: f64,
                pub const inStock: bool,
                const id: i32,
                const unit: ()
            }
            
            def main() -> i32:
                var price = 19.99
                Product{"Gadget", 1.0 + price * 2.0, true, 1001, ()}
            """;

        String ir = compileAndExpectSuccess(sourceCode, "resource_multiple_fields");

        assertIrContainsInOrder(ir,
            IrPatterns.structType("Product_struct")
        );
    }

    // ============================================================================
    // Resource Method Tests - With and Without Return Types
    // ============================================================================

    @Test
    public void testMethodWithReturnType() {
        String sourceCode = """
            resource Calculator{var value: i32}:
            
                path operations/value:
                    def get() -> i32:
                        return this.value
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "method_with_return_type");

        String getValueMethod = extractFunction(ir, "Calculator_operations_value_get");
        assertNotNull(getValueMethod, "Should find getValue method in IR");

        assertIrContainsInOrder(getValueMethod,
            IrPatterns.functionDefinition("Calculator_operations_value_get", "i32"),
            IrPatterns.alloca("this", "ptr"),
            IrPatterns.store("%0", "ptr", "this"),
            IrPatterns.fieldAccess("value", "Calculator_struct", "this", 0),
            IrPatterns.returnStatement("i32", "value")
        );
    }

    @Test
    public void testUnitMethod() {
        String sourceCode = """
            resource Logger{var message: String}:
            
                path actions:
                    def reset():
                        this.message = ""
                        return
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "unit_method");

        String resetMethod = extractFunction(ir, "Logger_actions_reset");
        assertNotNull(resetMethod, "Should find reset method in IR");

        assertIrContainsInOrder(resetMethod,
            IrPatterns.functionDefinition("Logger_actions_reset", "%unit"),
            IrPatterns.alloca("this", "ptr"),
            IrPatterns.store("%0", "ptr", "this"),
            IrPatterns.returnStatement("%unit", "zeroinitializer")
        );
    }

    @Test
    public void testUnitMethodWithoutExplicitReturn() {
        String sourceCode = """
            resource Storage{var data: String}:
            
                path operations:
                    def clear() -> ():
                        this.data = ""
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "unit_method_implicit_return");

        String clearMethod = extractFunction(ir, "Storage_operations_clear");
        assertNotNull(clearMethod, "Should find clear method in IR");

        assertIrContainsInOrder(clearMethod,
            IrPatterns.functionDefinition("Storage_operations_clear", "%unit"),
            IrPatterns.alloca("this", "ptr"),
            IrPatterns.store("%0", "ptr", "this"),
            IrPatterns.returnStatement("%unit", "zeroinitializer")
        );
    }

    // ============================================================================
    // Return Statement Path Coverage Tests
    // ============================================================================

    @Test
    public void testMethodWithAllPathsReturning() {
        String sourceCode = """
            resource NumberProcessor{var value: i32}:
            
                path operations/sign:
                    def get() -> i32:
                        if this.value > 0:
                            return 1
                        else:
                            return -1
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "method_all_paths_return");

        String getSignMethod = extractFunction(ir, "NumberProcessor_operations_sign_get");
        assertNotNull(getSignMethod, "Should find getSign method in IR");

        assertIrContainsInOrder(getSignMethod,
            IrPatterns.functionDefinition("NumberProcessor_operations_sign_get", "i32"),
            IrPatterns.icmp("sgt", "i32", "value", "0"),
            IrPatterns.conditionalBranch("icmp", "if_then", "else"),
            IrPatterns.returnStatement("i32", "1"),
            IrPatterns.returnStatement("i32", "-1")
        );
    }

    @Test
    public void testMethodMissingReturnPath() {
        String sourceCode = """
            resource Validator{var threshold: i32}:
            
                path checks:
                    def isValid(value: i32) -> bool:
                        if value > this.threshold:
                            return true
                        # Missing else branch with return
            
            def main() -> i32:
                return 0
            """;

        String errors = compileAndExpectFailure(sourceCode, "method_missing_return_path");

        assertTrue(errors.contains("must return a value"),
            "Should report missing return path error: " + errors);
    }

    @Test
    public void testMethodWithComplexControlFlowReturns() {
        String sourceCode = """
            resource ComplexProcessor{var data: i32}:
            
                path processing/complex:
                    def process(input: i32) -> i32:
                        if input < 0:
                            return -1
                        else if input == 0:
                            return 0
                        else:
                            if input > 100:
                                return 100
                            else:
                                return input
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "method_complex_control_flow");

        String processMethod = extractFunction(ir, "ComplexProcessor_processing_complex_process");
        assertNotNull(processMethod, "Should find processComplex method in IR");

        assertIrContains(processMethod,
            IrPatterns.functionDefinition("ComplexProcessor_processing_complex_process", "i32"),
            IrPatterns.icmp("slt", "i32", "input", "0"),
            IrPatterns.icmp("eq", "i32", "input", "0"),
            IrPatterns.icmp("sgt", "i32", "input", "100"),
            IrPatterns.returnStatement("i32", "-1"),
            IrPatterns.returnStatement("i32", "0"),
            IrPatterns.returnStatement("i32", "100"),
            IrPatterns.returnStatement("i32", "input")
        );
    }

    // ============================================================================
    // Paths and Methods Tests
    // ============================================================================

    @Test
    public void testRootPathWithMethods() {
        String sourceCode = """
            resource RootPathResource{var value: i32}:
            
                path:
                    def get() -> i32:
                        return this.value
            
                    def set(newValue: i32) -> ():
                        this.value = newValue
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "root_path_with_methods");

        String getValueMethod = extractFunction(ir, "RootPathResource_get");
        assertNotNull(getValueMethod, "Should find getValue method in IR");
        assertIrContainsInOrder(getValueMethod,
            IrPatterns.functionDefinition("RootPathResource_get", "i32"),
            IrPatterns.alloca("this", "ptr"),
            IrPatterns.store("%0", "ptr", "this"),
            IrPatterns.fieldAccess("value", "RootPathResource_struct", "this", 0),
            IrPatterns.returnStatement("i32", "value")
        );

        String setValueMethod = extractFunction(ir, "RootPathResource_set");
        assertNotNull(setValueMethod, "Should find setValue method in IR");
        assertIrContainsInOrder(setValueMethod,
            IrPatterns.functionDefinition("RootPathResource_set", "%unit"),
            IrPatterns.alloca("this", "ptr"),
            IrPatterns.store("%0", "ptr", "this"),
            IrPatterns.alloca("newValue", "i32"),
            IrPatterns.store("%1", "i32", "newValue"),
            IrPatterns.fieldAccess("value", "RootPathResource_struct", "this", 0),
            IrPatterns.returnStatement("%unit", "zeroinitializer")
        );
    }

    @Test
    public void testIdentifierPathWithMethods() {
        String sourceCode = """
            resource IdentPathResource{var id: i32}:
            
                path id:
                    def get() -> i32:
                        return this.id
            
                    def set(id: i32):
                        this.id = id
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "identifier_path_with_methods");

        String getMethod = extractFunction(ir, "IdentPathResource_id_get");
        assertNotNull(getMethod, "Should find get method in IR");
        assertIrContainsInOrder(getMethod,
            IrPatterns.functionDefinition("IdentPathResource_id_get", "i32"),
            IrPatterns.alloca("this", "ptr"),
            IrPatterns.store("%0", "ptr", "this"),
            IrPatterns.fieldAccess("id", "IdentPathResource_struct", "this", 0),
            IrPatterns.returnStatement("i32", "id")
        );

        String setMethod = extractFunction(ir, "IdentPathResource_id_set");
        assertNotNull(setMethod, "Should find set method in IR");
        assertIrContainsInOrder(setMethod,
            IrPatterns.functionDefinition("IdentPathResource_id_set", "%unit"),
            IrPatterns.alloca("this", "ptr"),
            IrPatterns.store("%0", "ptr", "this"),
            IrPatterns.alloca("id", "i32"),
            IrPatterns.store("%1", "i32", "id"),
            IrPatterns.fieldAccess("id", "IdentPathResource_struct", "this", 0),
            IrPatterns.returnStatement("%unit", "zeroinitializer")
        );
    }

    @Test
    public void testIndexerPathWithMethods() {
        String sourceCode = """
            resource IndexerResource{var id: i32}:
            
                path [id :i32]:
                    def get() -> i32:
                        return this.id
            
                    def set(index: i32):
                        this.id = id
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "indexer_path_with_methods");

        String getMethod = extractFunction(ir, "\"IndexerResource_\\{i32\\}_get\"");
        assertNotNull(getMethod, "Should find get method in IR");
        assertIrContainsInOrder(getMethod,
            IrPatterns.functionDefinition("\"IndexerResource_\\{i32\\}_get\"", "i32"),
            IrPatterns.alloca("this", "ptr"),
            IrPatterns.store("%0", "ptr", "this"),
            IrPatterns.alloca("id", "i32"),
            IrPatterns.store("%1", "i32", "id"),
            IrPatterns.fieldAccess("id", "IndexerResource_struct", "this", 0),
            IrPatterns.returnStatement("i32", "id")
        );

        String setMethod = extractFunction(ir, "\"IndexerResource_\\{i32\\}_set\"");
        assertNotNull(setMethod, "Should find set method in IR");
        assertIrContainsInOrder(setMethod,
            IrPatterns.functionDefinition("\"IndexerResource_\\{i32\\}_set\"", "%unit"),
            IrPatterns.alloca("this", "ptr"),
            IrPatterns.store("%0", "ptr", "this"),
            IrPatterns.alloca("id", "i32"),
            IrPatterns.store("%1", "i32", "id"),
            IrPatterns.alloca("index", "i32"),
            IrPatterns.store("%2", "i32", "index"),
            IrPatterns.fieldAccess("id", "IndexerResource_struct", "this", 0),
            IrPatterns.returnStatement("%unit", "zeroinitializer")
        );
    }

    @Test
    public void testCombinedPathWithMethods() {
        String sourceCode = """
            resource ComboPathResource{var id: i32, var price: f64}:
            
                path id[c: char]/index[i: i32]:
                    def get(f: f64) -> i32:
                        return this.id
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "combined_path_with_methods");

        String getMethod =
            extractFunction(ir, "\"ComboPathResource_id_\\{char\\}_index_\\{i32\\}_get\"");
        assertNotNull(getMethod, "Should find get method in IR");
        assertIrContainsInOrder(getMethod,
            IrPatterns.functionDefinition("\"ComboPathResource_id_\\{char\\}_index_\\{i32\\}_get\"",
                "i32"),
            IrPatterns.alloca("this", "ptr"),
            IrPatterns.store("%0", "ptr", "this"),
            IrPatterns.alloca("c", "i32"),
            IrPatterns.store("%1", "i32", "c"),
            IrPatterns.alloca("i", "i32"),
            IrPatterns.store("%2", "i32", "i"),
            IrPatterns.alloca("f", "double"),
            IrPatterns.store("%3", "double", "f"),
            IrPatterns.fieldAccess("id", "ComboPathResource_struct", "this", 0),
            IrPatterns.returnStatement("i32", "id")
        );
    }

    @Test
    public void testMultiplePathsWithSameMethods() {
        String sourceCode = """
            resource MultiPath{var value: i32, const constant: i32}:
            
                path constant:
                    def get() -> i32:
                        return this.constant
            
                    def double() -> i32:
                        return this.constant * 2
            
                path value:
                    def get() -> i32:
                        return this.value
            
                    def double() -> i32:
                        return this.value * 2
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "multiple_paths_different_methods");

        assertIrContains(ir,
            IrPatterns.functionDefinition("MultiPath_constant_get", "i32"),
            IrPatterns.functionDefinition("MultiPath_constant_double", "i32"),
            IrPatterns.functionDefinition("MultiPath_value_get", "i32"),
            IrPatterns.functionDefinition("MultiPath_value_double", "i32")
        );
    }

    @Test
    public void testSamePathWithDifferentMethods() {
        String sourceCode = """
            resource SamePath{var value: i32}:
            
                path operations:
                    def get() -> i32:
                        return this.value
            
                path operations:
                    def set(value: i32):
                        this.value = value
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "same_path_different_methods");

        assertIrContains(ir,
            IrPatterns.functionDefinition("SamePath_operations_get", "i32"),
            IrPatterns.functionDefinition("SamePath_operations_set", "%unit")
        );
    }

    @Test
    public void testIndexerPathWithDifferentMethods() {
        String sourceCode = """
            resource SamePath{var value: i32}:
            
                path [i: i32]:
                    def get() -> i32:
                        return this.value
            
                path [i: i32]:
                    def set(value: i32):
                        this.value = value
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "same_path_different_methods");

        assertIrContains(ir,
            IrPatterns.functionDefinition("\"SamePath_\\{i32\\}_get\"", "i32"),
            IrPatterns.functionDefinition("\"SamePath_\\{i32\\}_set\"", "%unit")
        );
    }

    @Test
    public void testMixedPathsAndRootMethods() {
        String sourceCode = """
            resource MixedResource{var value: i32}:
            
                path:
                    def get() -> i32:
                        return this.value
            
                path operations:
                    def double() -> i32:
                        return this.value * 2
            
                path:
                    def set(newValue: i32):
                        this.value = newValue
            
                path operations:
                    def increment():
                        this.value = this.value + 1
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "mixed_paths_and_root_methods");

        assertIrContains(ir,
            IrPatterns.functionDefinition("MixedResource_get", "i32"),
            IrPatterns.functionDefinition("MixedResource_set", "%unit"),
            IrPatterns.functionDefinition("MixedResource_operations_double", "i32"),
            IrPatterns.functionDefinition("MixedResource_operations_increment", "%unit")
        );
    }

    @Test
    public void testRootPathWithDuplicateMethodNames() {
        String sourceCode = """
            resource BadResource{var value: i32}:
            
                path:
                    def get() -> i32:
                        return this.value
            
                    def get() -> f32:  # Duplicate method name
                        return this.value as f32 * 2.0
            
            def main() -> i32:
                return 0
            """;

        String errors = compileAndExpectFailure(sourceCode, "duplicate_method_names_root_path");

        assertTrue(errors.contains("already defined"),
            "Should report duplicate method names: " + errors);
    }

    @Test
    public void testRootPathsWithDuplicateMethodNames() {
        String sourceCode = """
            resource BadResource{var value: i32}:
            
                path:
                    def get() -> i32:
                        return this.value
            
                path:
                    def get() -> f32:  # Duplicate method name
                        return this.value as f32 * 2.0
            
            def main() -> i32:
                return 0
            """;

        String errors = compileAndExpectFailure(sourceCode, "duplicate_method_names_root_path");

        assertTrue(errors.contains("already defined"),
            "Should report duplicate method names: " + errors);
    }

    @Test
    public void testMethodWithoutPath() {
        String sourceCode = """
            resource NoPathResource{var value: i32}:
            
                def get() -> i32:  # Method without path
                    return this.value
            
            def main() -> i32:
                return 0
            """;

        String errors = compileAndExpectFailure(sourceCode, "method_without_path");

        assertNotNull(errors, "Should report error for method without path");
    }

    @Test
    public void testDuplicateMethodNamesInSamePath() {
        String sourceCode = """
            resource BadResource{var value: i32}:
            
                path operations/value:
                    def get() -> i32:
                        return this.value
            
                    def get() -> i32:  # Duplicate method name
                        return this.value * 2
            
            def main() -> i32:
                return 0
            """;

        String errors = compileAndExpectFailure(sourceCode, "duplicate_method_names");

        assertTrue(errors.contains("already defined"),
            "Should report duplicate method names: " + errors);
    }

    @Test
    public void testDuplicateMethodNamesInSplitPath() {
        String sourceCode = """
            resource BadResource{var value: i32}:
            
                path operations/value:
                    def get() -> i32:
                        return this.value
            
                path operations/value:
                    def get() -> i32:  # Duplicate method name
                        return this.value * 2
            
            def main() -> i32:
                return 0
            """;

        String errors = compileAndExpectFailure(sourceCode, "duplicate_method_names_split_path");

        assertTrue(errors.contains("already defined"),
            "Should report duplicate method names: " + errors);
    }

    @Test
    public void testDuplicateMethodNamesInIndexerPath() {
        String sourceCode = """
            resource BadResource{var value: i32}:
            
                path [i: i32]:
                    def get() -> i32:
                        return this.value
            
                path [i: i32]:
                    def get() -> i32:  # Duplicate method name
                        return this.value * 2
            
            def main() -> i32:
                return 0
            """;

        String errors = compileAndExpectFailure(sourceCode, "duplicate_method_names_indexer_path");
        assertTrue(errors.contains("already defined"),
            "Should report duplicate method names: " + errors);
    }

    @Test
    public void testDuplicateMethodNamesInDifferentIndexerPath() {
        String sourceCode = """
            resource BadResource{var value: i32}:
            
                path [i: i32]:
                    def get() -> i32:
                        return this.value
            
                path [f: f32]:
                    def get() -> i32:  # Duplicate method name
                        return this.value * 2
            
            def main() -> i32:
                return 0
            """;

        String errors = compileAndExpectFailure(sourceCode, "duplicate_method_names_indexer_path");
        assertTrue(errors.contains("already defined"),
            "Should report duplicate method names: " + errors);
    }

    // ============================================================================
    // Unreachable Code Tests
    // ============================================================================

    @Test
    public void testUnreachableCodeAfterReturn() {
        String sourceCode = """
            resource TestResource{var value: i32}:
            
                path operations/value:
                    def process() -> i32:
                        return this.value
                        var unreachable: i32 = 42  # Unreachable code
            
            def main() -> i32:
                return 0
            """;

        String warnings = compileAndExpectWarnings(sourceCode, "unreachable_code_after_return");

        assertTrue(warnings.contains("unreachable"),
            "Should report unreachable code warning: " + warnings);
    }

    @Test
    public void testUnreachableCodeInIfStatement() {
        String sourceCode = """
            resource TestResource{var value: i32}:
            
                path operations/value:
                    def process(flag: bool) -> i32:
                        if flag:
                            return 1
                            var unreachable: i32 = 42  # Unreachable
                        else:
                            return 0
            
            def main() -> i32:
                return 0
            """;

        String warnings = compileAndExpectWarnings(sourceCode, "unreachable_code_in_if");

        assertTrue(warnings.contains("unreachable"),
            "Should report unreachable code warning: " + warnings);
    }

    // ============================================================================
    // Parameter Coverage Tests
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "def noParams() -> i32: return 42",
        "def oneParam(x: i32) -> i32: return x",
        "def twoParams(x: i32, y: i32) -> i32: return x + y",
        "def multipleParams(a: i32, b: bool, c: String, d: f64) -> i32: return a"
    })
    public void testMethodParameterVariations(String methodDef) {
        String sourceCode = String.format("""
            resource TestResource{var value: i32}:
            
                path test:
                    %s
            
            def main() -> i32:
                return 0
            """, methodDef);

        String testName =
            "method_params_" + methodDef.substring(4, methodDef.indexOf('(')).toLowerCase();
        String ir = compileAndExpectSuccess(sourceCode, testName);

        // Should contain the method definition
        assertTrue(ir.contains("TestResource_test_"),
            "Should contain method definition in IR");
    }

    // ============================================================================
    // Resource Reference Tests
    // ============================================================================

    @Test
    public void testResourceComposition() {
        String sourceCode = """
            resource Project{pub const person: Person, pub const address: Address}:
            
                path person/name:
                    pub def get() -> String:
                        return this.person.name
            
            resource Address{pub var street: String, pub var city: String}
            
            resource Person{pub var name: String, pub var address: Address}:
            
                path info/address/city:
                    def get() -> String:
                        return this.address.city
            
            def main() -> i32:
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "resource_composition");

        assertIrContainsInOrder(ir,
            IrPatterns.structType("Project_struct"),
            IrPatterns.structType("Person_struct"),
            IrPatterns.structType("Address_struct"),
            IrPatterns.functionDefinition("Project_person_name_get", "ptr"),
            IrPatterns.fieldAccess("person", "Project_struct", "this", 0),
            IrPatterns.fieldAccess("name", "Person_struct", "person", 0),
            IrPatterns.functionDefinition("Person_info_address_city_get", "ptr"),
            IrPatterns.fieldAccess("address", "Person_struct", "this", 1),
            IrPatterns.fieldAccess("city", "Address_struct", "address", 1)
        );
    }
}