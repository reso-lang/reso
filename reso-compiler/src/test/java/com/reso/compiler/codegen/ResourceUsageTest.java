package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for resource usage scenarios including resource initializer, field access,
 * method calls, path expressions, nested access, assignments, and access control.
 * These tests verify proper code generation for various resource usage patterns
 * both inside and outside of resource contexts, testing access levels and
 * complex expression scenarios.
 */
public class ResourceUsageTest extends BaseTest {

    // ============================================================================
    // Basic Resource Initializer Tests
    // ============================================================================

    @Test
    public void testBasicResourceInitializer() {
        String sourceCode = """
            resource Point{var x: i32, var y: i32}
                            
            def main() -> i32:
                var point: Point = Point{10, 20}
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "basic_resource_initializer");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.atomicMalloc("Point_instance", "Point_struct"),
            IrPatterns.fieldAccess("x", "Point_struct", "Point_instance", 0),
            IrPatterns.store("10", "i32", "x"),
            IrPatterns.fieldAccess("y", "Point_struct", "Point_instance", 1),
            IrPatterns.store("20", "i32", "y")
        );
    }

    @Test
    public void testEmptyResourceInitializer() {
        String sourceCode = """
            resource EmptyMarker{}
                            
            def main() -> i32:
                var marker: EmptyMarker = EmptyMarker{}
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "empty_resource_initializer");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.atomicMalloc("EmptyMarker_instance", "EmptyMarker_struct")
        );
    }

    @Test
    public void testResourceInitializerWithMixedTypes() {
        String sourceCode = """
            resource User{var name: String, var age: i32, var active: bool, const unit: ()}
                            
            def main() -> i32:
                var user: User = User{"Alice", 25, true, ()}
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "resource_initializer_mixed_types");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.malloc("User_instance", "User_struct"),
            IrPatterns.fieldAccess("name", "User_struct", "User_instance", 0),
            IrPatterns.fieldAccess("age", "User_struct", "User_instance", 1),
            IrPatterns.fieldAccess("active", "User_struct", "User_instance", 2),
            IrPatterns.fieldAccess("unit", "User_struct", "User_instance", 3)
        );
    }

    @Test
    public void testResourceInitializerInFunctionReturn() {
        String sourceCode = """
            resource Array{var x: f64, var y: f64}
                            
            def Array(x: f64, y: f64) -> Array:
                return Array{x, y}
                            
            def main() -> i32:
                var v: Array = Array(1.0 * 3.0, 2.0)
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "resource_initializer_in_return");

        String arrayFunction = extractFunction(ir, "Array");
        assertNotNull(arrayFunction, "Function 'Array' should be present in IR");

        assertIrContainsInOrder(arrayFunction,
            IrPatterns.atomicMalloc("Array_instance", "Array_struct"),
            IrPatterns.fieldAccess("x", "Array_struct", "Array_instance", 0),
            IrPatterns.fieldAccess("y", "Array_struct", "Array_instance", 1),
            IrPatterns.returnStatement("ptr", "Array_instance")
        );
    }

    @Test
    public void testResourceInitializerInFunctionParameter() {
        String sourceCode = """
            resource Circle{var radius: f64}
                            
            def calculate_area(circle: Circle) -> f64:
                return 3.14159 * circle.radius * circle.radius
                            
            def main() -> i32:
                var area: f64 = calculate_area(Circle{5.0})
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "resource_initializer_in_parameter");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.atomicMalloc("Circle_instance", "Circle_struct"),
            IrPatterns.fieldAccess("radius", "Circle_struct", "Circle_instance", 0),
            IrPatterns.store("5.000000e\\+00", "double", "radius"),
            IrPatterns.functionCall("calculate_area", "calculate_area", "double", List.of())
        );
    }

    @Test
    public void testNestedResourceInitializers() {
        String sourceCode = """
            resource Address{var street: String, var city: String}
                            
            resource Person{var name: String, var address: Address}
                            
            def main() -> i32:
                var person: Person = Person{"John", Address{"Main St", "Springfield"}}
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "nested_resource_initializers");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.malloc("Person_instance", "Person_struct"),
            IrPatterns.fieldAccess("name", "Person_struct", "Person_instance", 0),
            IrPatterns.malloc("Address_instance", "Address_struct"),
            IrPatterns.fieldAccess("street", "Address_struct", "Address_instance", 0),
            IrPatterns.fieldAccess("city", "Address_struct", "Address_instance", 1),
            IrPatterns.fieldAccess("address", "Person_struct", "Person_instance", 1)
        );
    }

    @Test
    public void testResourceInitializerWithExpressions() {
        String sourceCode = """
            resource Calculator{var result: i32}
                            
            def main() -> i32:
                var a: i32 = 10
                var b: i32 = 20
                var calc: Calculator = Calculator{a + b * 2}
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "resource_initializer_with_expressions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("calc", "ptr"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("a", "i32"),
            IrPatterns.store("10", "i32", "a"),
            IrPatterns.store("20", "i32", "b"),
            IrPatterns.atomicMalloc("Calculator_instance", "Calculator_struct"),
            IrPatterns.load("i32", "a"),
            IrPatterns.load("i32", "b"),
            IrPatterns.mul("i32", "b", "2"),
            IrPatterns.add("i32", "a", "mul"),
            IrPatterns.fieldAccess("result", "Calculator_struct", "Calculator_instance", 0),
            IrPatterns.store("add", "i32", "result"),
            IrPatterns.store("Calculator_instance", "ptr", "calc")
        );
    }


    // ============================================================================
    // Simple Field and Method Access Tests
    // ============================================================================

    @Test
    public void testSimpleFieldAccess() {
        String sourceCode = """
            resource User{var id: i32, var age: i32, var unit: ()}
                            
            def main() -> i32:
                var user: User = User{1, 25, ()}
                var id: i32 = user.id
                var age: i32 = user.age
                var unit: () = user.unit
                return age
            """;

        String ir = compileAndExpectSuccess(sourceCode, "simple_field_access");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fieldAccess("id", "User_struct", "user", 0),
            IrPatterns.fieldAccess("age", "User_struct", "user", 1),
            IrPatterns.fieldAccess("unit", "User_struct", "user", 2),
            IrPatterns.returnStatement("i32", "age")
        );
    }

    @Test
    public void testSimpleMethodCall() {
        String sourceCode = """
            resource Calculator{pub var value: i32}:
                            
                path:
                    pub def get() -> i32:
                        return this.value
                
                    pub def increment():
                        this.value = this.value + 1
                            
            def main() -> i32:
                var calc: Calculator = Calculator{5}
                var result: i32 = calc.get()
                calc.increment()
                return result
            """;

        String ir = compileAndExpectSuccess(sourceCode, "simple_method_call");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("Calculator_get", "get", "i32", List.of(
                Map.entry("ptr", "%calc")
            )),
            IrPatterns.functionCall("Calculator_increment", "increment", "%unit", List.of(
                Map.entry("ptr", "%calc")
            ))
        );
    }

    // ============================================================================
    // Path Expression Tests
    // ============================================================================

    @Test
    public void testSimplePathMethodAccess() {
        String sourceCode = """
            resource User{pub var id: i32, var age: i32}:
                            
                path id:
                    def get() -> i32:
                        return this.id
                            
                    def set(newId: i32):
                        this.id = newId
                            
            def main() -> i32:
                var user: User = User{1, 25}
                var id: i32 = user/id.get()
                user/id.set(2)
                return id
            """;

        String ir = compileAndExpectSuccess(sourceCode, "simple_path_method_access");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("User_id_get", "get", "i32", List.of(
                Map.entry("ptr", "%user")
            )),
            IrPatterns.functionCall("User_id_set", "set", "%unit", List.of(
                Map.entry("ptr", "%user"),
                Map.entry("i32", "2")
            ))
        );
    }

    @Test
    public void testIndexerPathAccess() {
        String sourceCode = """
            resource Resource{var data: i32}:
                            
                path index/{i: i32}:
                    pub def get() -> i32:
                        return this.data + i
                            
                    def set(value: i32):
                        this.data = value - i
                            
            def main() -> i32:
                var res: Resource = Resource{10}
                var value: i32 = res/index/{5}.get()
                res/index/{3}.set(20)
                return value
            """;

        String ir = compileAndExpectSuccess(sourceCode, "parameterized_path_access");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("\"Resource_index_\\{i32\\}_get\"", "get", "i32", List.of(
                Map.entry("ptr", "%res"),
                Map.entry("i32", "5")
            )),
            IrPatterns.functionCall("\"Resource_index_\\{i32\\}_set\"", "set", "%unit", List.of(
                Map.entry("ptr", "%res"),
                Map.entry("i32", "3"),
                Map.entry("i32", "20")
            ))
        );
    }

    @Test
    public void testIndexerPathAccessWithUnitType() {
        String sourceCode = """
            resource Resource{var data: i32}:
                            
                path index/{i: ()}:
                    pub def get() -> i32:
                        return this.data + 1
                            
                    def set(value: i32, j: ()):
                        this.data = value - 1
                            
            def main() -> i32:
                var res: Resource = Resource{10}
                var value: i32 = res/index/{()}.get()
                res/index/{()}.set(20, ())
                return value
            """;

        String ir = compileAndExpectSuccess(sourceCode, "parameterized_path_access_unit");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("\"Resource_index_\\{\\(\\)\\}_get\"", "get", "i32", List.of(
                Map.entry("ptr", "%res"),
                Map.entry("%unit", "zeroinitializer")
            )),
            IrPatterns.functionCall("\"Resource_index_\\{\\(\\)\\}_set\"", "set", "%unit", List.of(
                Map.entry("ptr", "%res"),
                Map.entry("%unit", "zeroinitializer"),
                Map.entry("i32", "20"),
                Map.entry("%unit", "zeroinitializer")
            ))
        );
    }

    @Test
    public void testComplexPathWithMultipleParameters() {
        String sourceCode = """
            resource Matrix{var value: i32}:
                            
                path row/{r: i32}/col/{c: i32}:
                    pub def get() -> i32:
                        return this.value + r * 10 + c
                            
                    pub def set(newValue: i32):
                        this.value = newValue
                            
            def main() -> i32:
                var matrix: Matrix = Matrix{0}
                var element: i32 = matrix/row/{2}/col/{3}.get()
                matrix/row/{1 + 3 * element}/col/{4}.set(42)
                return element
            """;

        String ir = compileAndExpectSuccess(sourceCode, "complex_path_multiple_params");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("\"Matrix_row_\\{i32\\}_col_\\{i32\\}_get\"", "get", "i32",
                List.of(
                    Map.entry("ptr", "%matrix"),
                    Map.entry("i32", "2"),
                    Map.entry("i32", "3")
                )),
            IrPatterns.functionCall("\"Matrix_row_\\{i32\\}_col_\\{i32\\}_set\"", "set", "%unit",
                List.of(
                    Map.entry("ptr", "%matrix"),
                    Map.entry("i32", "add"),
                    Map.entry("i32", "4"),
                    Map.entry("i32", "42")
                ))
        );
    }

    // ============================================================================
    // Nested Access Tests
    // ============================================================================

    @Test
    public void testNestedResourceAccess() {
        String sourceCode = """
            resource Address{pub var street: i32, pub var city: i32}:
                            
                path full/address:
                    pub def get() -> i32:
                        return this.street + this.city
                            
            resource Person{pub var id: i32, pub var address: Address}
                            
            def main() -> i32:
                var addr: Address = Address{1, 54}
                var person: Person = Person{0, addr}
                var street: i32 = person.address.street
                var fullAddr: i32 = person.address/full/address.get()
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "nested_resource_access");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fieldAccess("address", "Person_struct", "person", 1),
            IrPatterns.fieldAccess("street", "Address_struct", "address", 0),
            IrPatterns.functionCall("Address_full_address_get", "get", "i32", List.of(
                Map.entry("ptr", "%address")
            ))
        );
    }

    @Test
    public void testChainedMethodCalls() {
        String sourceCode = """
            resource Builder{var value: i32}:
                
                path:
                    pub def add(n: i32) -> Builder:
                        this.value = this.value + n
                        return this
                
                    pub def multiply(n: i32) -> Builder:
                        this.value = this.value * n
                        return this
                            
                path value:
                    pub def get() -> i32:
                        return this.value
                            
            def main() -> i32:
                var builder: Builder = Builder{5}
                return builder.add(3).multiply(2)/value.get()
            """;

        String ir = compileAndExpectSuccess(sourceCode, "chained_method_calls");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("Builder_add", "add", "ptr", List.of(
                Map.entry("ptr", "%builder"),
                Map.entry("i32", "3")
            )),
            IrPatterns.functionCall("Builder_multiply", "multiply", "ptr", List.of(
                Map.entry("ptr", "%add_result"),
                Map.entry("i32", "2")
            )),
            IrPatterns.functionCall("Builder_value_get", "get", "i32", List.of(
                Map.entry("ptr", "%multiply_result")
            ))
        );
    }

    @Test
    public void testNestedPathAccess() {
        String sourceCode = """
            resource Container{pub var data: Data}
                            
            resource Data{var value: i32}:
                            
               path access:
                    pub def get() -> i32:
                        return this.value
                            
                    pub def set(newValue: i32):
                        this.value = newValue
                            
            def main() -> i32:
                var data: Data = Data{10}
                var container: Container = Container{data}
                var value: i32 = container.data/access.get()
                container.data/access.set(20)
                return value
            """;

        String ir = compileAndExpectSuccess(sourceCode, "nested_path_access");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fieldAccess("data", "Container_struct", "container", 0),
            IrPatterns.functionCall("Data_access_get", "get", "i32", List.of(
                Map.entry("ptr", "%data")
            )),
            IrPatterns.functionCall("Data_access_set", "set", "%unit", List.of(
                Map.entry("ptr", "%data"),
                Map.entry("i32", "20")
            ))
        );
    }

    // ============================================================================
    // Field Assignment Tests
    // ============================================================================

    @Test
    public void testSimpleFieldAssignment() {
        String sourceCode = """
            resource User{pub var id: i32, pub var age: i32, var unit: ()}
                            
            def main() -> i32:
                var user: User = User{1, 25, ()}
                user.id = 2
                user.age = 30
                user.unit = ()
                return user.age
            """;

        String ir = compileAndExpectSuccess(sourceCode, "simple_field_assignment");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fieldAccess("id", "User_struct", "user", 0),
            IrPatterns.store("2", "i32", "id_ptr"),
            IrPatterns.fieldAccess("age", "User_struct", "user", 1),
            IrPatterns.store("30", "i32", "age_ptr"),
            IrPatterns.fieldAccess("unit", "User_struct", "user", 2),
            IrPatterns.store("zeroinitializer", "%unit", "unit_ptr")
        );
    }

    @Test
    public void testNullFieldAssignment() {
        String sourceCode = """
            resource User{pub var id: i32, pub var friend: User}
                            
            def main() -> i32:
                var user: User = User{1, User{2, null}}
                user.friend = null
            """;

        String ir = compileAndExpectSuccess(sourceCode, "null_field_assignment");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fieldAccess("friend_ptr", "User_struct", "user", 1),
            IrPatterns.store("null", "ptr", "friend_ptr")
        );
    }

    @Test
    public void testNestedFieldAssignment() {
        String sourceCode = """
            resource Address{pub var street: i32, pub var city: i32}
                            
            resource Person{pub var id: i32, pub var address: Address}:
                            
                path address:
                    pub def get() -> Address:
                        return this.address
                            
            def main() -> i32:
                var addr: Address = Address{1, 25}
                var person: Person = Person{0, addr}
                person.address.street = 5
                person/address.get().city = 3
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "nested_field_assignment");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fieldAccess("address", "Person_struct", "person", 1),
            IrPatterns.fieldAccess("street", "Address_struct", "address", 0),
            IrPatterns.store("5", "i32", "street_ptr"),
            IrPatterns.functionCall("Person_address_get", "get", "ptr", List.of(
                Map.entry("ptr", "%person")
            )),
            IrPatterns.fieldAccess("city", "Address_struct", "get", 1),
            IrPatterns.store("3", "i32", "city_ptr")
        );
    }

    @Test
    public void testFieldAssignmentWithExpressions() {
        String sourceCode = """
            resource Counter{pub var value: i32}
                            
            def main() -> i32:
                var counter: Counter = Counter{5}
                counter.value = counter.value + 10
                counter.value = counter.value * 2
                return counter.value
            """;

        String ir = compileAndExpectSuccess(sourceCode, "field_assignment_with_expressions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fieldAccess("value", "Counter_struct", "counter", 0),
            IrPatterns.add("i32", "value", "10"),
            IrPatterns.store("add", "i32", "value_ptr"),
            IrPatterns.fieldAccess("value", "Counter_struct", "counter", 0),
            IrPatterns.mul("i32", "value", "2"),
            IrPatterns.store("mul", "i32", "value_ptr")
        );
    }

    // ============================================================================
    // Context Tests (Loops, Conditions)
    // ============================================================================

    @Test
    public void testResourceUsageInLoops() {
        String sourceCode = """
            resource Counter{pub var value: i32}:
                            
                path:
                    pub def increment():
                        this.value = this.value + 1
                            
            def main() -> i32:
                var counter: Counter = Counter{0}
                var i: i32 = 0
                while i < 10:
                    counter.increment()
                    i = i + 1
                return counter.value
            """;

        String ir = compileAndExpectSuccess(sourceCode, "resource_usage_in_loops");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.label("while_cond"),
            IrPatterns.icmp("slt", "i32", "i", "10"),
            IrPatterns.conditionalBranch("icmp", "while_body", "while_end"),
            IrPatterns.label("while_body"),
            IrPatterns.functionCall("Counter_increment", "increment", "%unit", List.of(
                Map.entry("ptr", "%counter")
            ))
        );
    }

    @Test
    public void testResourceUsageInConditions() {
        String sourceCode = """
            resource Validator{pub var threshold: i32}:
                            
                path:
                    pub def is_valid(value: i32) -> bool:
                        return value > this.threshold
                            
            def main() -> i32:
                var validator: Validator = Validator{50}
                var testValue: i32 = 75
                            
                if validator.is_valid(testValue):
                    return 1
                else:
                    return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "resource_usage_in_conditions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("Validator_is_valid", "is_valid", "i1", List.of(
                Map.entry("ptr", "%validator"),
                Map.entry("i32", "%testValue")
            )),
            IrPatterns.conditionalBranch("is_valid_result", "if_then", "else")
        );
    }

    @Test
    public void testTernaryFieldAccess() {
        String sourceCode = """
            resource Config{pub var mode: i32}
                            
            def main() -> i32:
                var config_a: Config = Config{1}
                var config_b: Config = Config{2}
                var is_debug: bool = true
                var mode_value: i32 = (config_a if is_debug else config_b).mode
                return mode_value
            """;

        String ir = compileAndExpectSuccess(sourceCode, "ternary_field_access");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "is_debug", "ptr", "config_a", "config_b"),
            IrPatterns.fieldAccess("mode", "Config_struct", "ternary", 0)
        );
    }

    @Test
    public void testTernaryMethodCall() {
        String sourceCode = """
            resource Service{var id: i32}:
                            
                path id:
                    pub def get() -> i32:
                        return this.id
                            
            def main() -> i32:
                var service_a: Service = Service{10}
                var service_b: Service = Service{20}
                var use_a: bool = false
                var id_value: i32 = (service_a if use_a else service_b)/id.get()
                return id_value
            """;

        String ir = compileAndExpectSuccess(sourceCode, "ternary_method_call");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.select("i1", "use_a", "ptr", "service_a", "service_b"),
            IrPatterns.functionCall("Service_id_get", "get", "i32", List.of())
        );
    }

    // ============================================================================
    // Method Argument Tests
    // ============================================================================

    @Test
    public void testMethodWithNoArguments() {
        String sourceCode = """
            resource Simple{var value: i32}:
                            
                path value:
                    pub def get() -> i32:
                        return this.value
                            
            def main() -> i32:
                var simple: Simple = Simple{42}
                return simple/value.get()
            """;

        String ir = compileAndExpectSuccess(sourceCode, "method_no_arguments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.functionCall("Simple_value_get", "get", "i32", List.of(
                Map.entry("ptr", "%simple")
            )));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    public void testMethodWithMultipleArguments(int argCount) {
        StringBuilder params = new StringBuilder();
        StringBuilder args = new StringBuilder();
        StringBuilder paramList = new StringBuilder();

        for (int i = 0; i < argCount; i++) {
            if (i > 0) {
                params.append(", ");
                args.append(", ");
                paramList.append(", ");
            }
            params.append("param").append(i).append(": i32");
            args.append(i + 10);
            paramList.append("param").append(i);
        }

        String sourceCode = String.format("""
                resource MultiParam{var result: i32}:
                    path:
                        pub def calculate(%s) -> i32:
                            return %s
                                        
                def main() -> i32:
                    var calc: MultiParam = MultiParam{1}
                    return calc.calculate(%s)
                """, params,
            argCount > 0 ? paramList.toString().replace(", ", " + ") : "0",
            args);

        String testName = "method_" + argCount + "_arguments";
        String ir = compileAndExpectSuccess(sourceCode, testName);

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        // Build expected call pattern
        String expectedCall = "MultiParam_calculate";
        assertIrContains(mainFunc, expectedCall);
    }

    @Test
    public void testMethodWithMixedArgumentTypes() {
        String sourceCode = """
            resource TypeMixer{var data: i32}:
                
                path types:
                    pub def mix(int_val: i32, float_val: f64, bool_val: bool, char_val: char) -> i32:
                        return char_val as i32 if bool_val else float_val as i32 + int_val
                            
            def main() -> i32:
                var mixer: TypeMixer = TypeMixer{0}
                var result = mixer/types.mix(42, 3.14, true, 'C')
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "method_mixed_argument_types");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.functionCall("TypeMixer_types_mix", "mix", "i32", List.of(
                Map.entry("ptr", "%mixer"),
                Map.entry("i32", "42"),
                Map.entry("double", "3.140000e\\+00"),
                Map.entry("i1", "true"),
                Map.entry("i32", "67")
            )));
    }

    @Test
    public void testMethodWithComplexExpressionArguments() {
        String sourceCode = """
            resource MathProcessor{var factor: i32}:
                            
                path:
                    pub def process(a: i32, b: i32, c: f64) -> f64:
                        return (this.factor * a + b) as f64 * c
                            
            def main() -> i32:
                var processor: MathProcessor = MathProcessor{2}
                var x: i32 = 10
                var y: i32 = 5
                var z: f64 = 1.5
                            
                # Complex expressions as arguments
                var result: f64 = processor.process(x + 3, y * 2, z div 2.0 + 1.0)
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "method_complex_expression_args");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.add("i32", "x", "3"),
            IrPatterns.mul("i32", "y", "2"),
            IrPatterns.fdiv("double", "z", "2.0"),
            IrPatterns.fadd("double", "fdiv", "1.0"),
            IrPatterns.functionCall("MathProcessor_process", "process", "double", List.of(
                Map.entry("ptr", "%processor"),
                Map.entry("i32", "%add"),
                Map.entry("i32", "%mul"),
                Map.entry("double", "%fadd")
            ))
        );
    }

    // ============================================================================
    // Advanced Scenarios
    // ============================================================================

    @Test
    public void testCompareResources() {
        String sourceCode = """
            resource User{pub var id: i32, pub var age: i32}
                            
            def main() -> i32:
                var user_a: User = User{1, 25}
                var user_b: User = User{2, 30}
                var equal: bool = false
                equal = user_a == user_b
                equal = user_a == null
                equal = null != user_b
                equal = null == null
            """;

        String ir = compileAndExpectSuccess(sourceCode, "compare_resources");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.icmp("eq", "ptr", "user_a", "user_b"),
            IrPatterns.icmp("eq", "ptr", "user_a", "null"),
            IrPatterns.icmp("ne", "ptr", "null", "user_b"),
            IrPatterns.store("true", "i1", "equal")
        );
    }

    @Test
    public void testNullAssignment() {
        String sourceCode = """
            resource User{pub var id: i32, pub var age: i32}
                            
            def main() -> i32:
                var user: User = null
            """;

        String ir = compileAndExpectSuccess(sourceCode, "null_assignment");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("user", "ptr"),
            IrPatterns.store("null", "ptr", "user")
        );
    }

    @Test
    public void testNullTernary() {
        String sourceCode = """
            resource User{pub var id: i32, pub var age: i32}
                            
            def main() -> i32:
                var user: User = User{1, 25}
                var is_user: bool = true
                var selected_user: User = user if is_user else null
            """;

        String ir = compileAndExpectSuccess(sourceCode, "null_ternary");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.select("i1", "is_user", "ptr", "user", "null"),
            IrPatterns.store("ternary", "ptr", "selected_user")
        );
    }

    @Test
    public void testNullAsMethodArgument() {
        String sourceCode = """
            resource User{pub var id: i32, pub var age: i32}:
                            
                path age:
                    pub def update(user: User):
                        this.age = user.age
                            
            def main() -> i32:
                var user: User = User{1, 25}
                user/age.update(null)  # Passing null as argument
                return user.age
            """;

        String ir = compileAndExpectSuccess(sourceCode, "null_as_method_argument");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.functionCall("User_age_update", "update", "%unit", List.of(
                Map.entry("ptr", "%user"),
                Map.entry("ptr", "null")
            )));
    }

    @Test
    public void testNullAsFunctionArgument() {
        String sourceCode = """
            resource User{pub var id: i32, pub var age: i32}
                            
            def process_user(user: User) -> i32:
                return user.age if user != null else -1
                            
            def main() -> i32:
                return process_user(null)
            """;

        String ir = compileAndExpectSuccess(sourceCode, "null_as_function_argument");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.functionCall("process_user", "process_user", "i32", List.of(
                Map.entry("ptr", "null")
            )));
    }

    @Test
    public void testResourceAsMethodArgument() {
        String sourceCode = """
            resource Point{pub var x: i32, pub var y: i32}
                            
            resource Calculator{var id: i32}:
                            
                path:
                    pub def distance(p1: Point, p2: Point) -> f64:
                        var dx: i32 = p1.x - p2.x
                        var dy: i32 = p1.y - p2.y
                        return (dx * dx + dy * dy) as f64
                            
            def main() -> i32:
                var calc: Calculator = Calculator{1}
                var point1: Point = Point{0, 0}
                var point2: Point = Point{3, 4}
                var dist: f64 = calc.distance(point1, point2)
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "resource_as_method_argument");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.functionCall("Calculator_distance", "distance", "double", List.of(
                Map.entry("ptr", "%calc"),
                Map.entry("ptr", "%point1"),
                Map.entry("ptr", "%point2")
            )));
    }

    @Test
    public void testResourceMethodReturningResource() {
        String sourceCode = """
            resource Factory{var counter: i32}:
                            
                path product:
                    pub def create(id: i32) -> Product:
                        this.counter = this.counter + id
                        return Product{this.counter}
                            
            resource Product{pub var id: i32}
                            
            def main() -> i32:
                var factory: Factory = Factory{0}
                var product: Product = factory/product.create(1)
                return product.id
            """;

        String ir = compileAndExpectSuccess(sourceCode, "resource_method_returning_resource");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("Factory_product_create", "create", "ptr", List.of(
                Map.entry("ptr", "%factory"),
                Map.entry("i32", "1")
            )),
            IrPatterns.fieldAccess("id", "Product_struct", "product", 0)
        );
    }

    @Test
    public void testRecursiveResourceMethodCall() {
        String sourceCode = """
            resource Counter{var value: i32}:
                            
                path:
                    pub def fibonacci(n: i32) -> i32:
                        if n <= 1:
                            return n
                        else:
                            return this.fibonacci(n - 1) + this.fibonacci(n - 2)
                            
            def main() -> i32:
                var counter: Counter = Counter{0}
                return counter.fibonacci(10)
            """;

        String ir = compileAndExpectSuccess(sourceCode, "recursive_resource_method");

        String fibonacciFunc = extractFunction(ir, "Counter_fibonacci");
        assertNotNull(fibonacciFunc, "Fibonacci function should be present in the IR");

        assertIrContainsInOrder(fibonacciFunc,
            IrPatterns.icmp("sle", "i32", "n", "1"),
            IrPatterns.sub("i32", "n", "1"),
            IrPatterns.sub("i32", "n", "2"),
            IrPatterns.functionCall("Counter_fibonacci", "fibonacci", "i32", List.of(
                Map.entry("ptr", "%this"),
                Map.entry("i32", "sub")
            )),
            IrPatterns.functionCall("Counter_fibonacci", "fibonacci", "i32", List.of(
                Map.entry("ptr", "%this"),
                Map.entry("i32", "sub")
            ))
        );

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("Counter_fibonacci", "fibonacci", "i32", List.of(
                Map.entry("ptr", "%counter"),
                Map.entry("i32", "10")
            ))
        );
    }

    // ============================================================================
    // Error Handling Tests
    // ============================================================================

    @Test
    public void testInvalidMethodArgumentCount() {
        String sourceCode = """
            resource TestResource{var data: i32}:
                
                path:
                    pub def calculate(a: i32, b: i32) -> i32:
                        return a + b + this.data
                            
            def main() -> i32:
                var res: TestResource = TestResource{5}
                return res.calculate(10)  # Missing second argument
            """;

        String error = compileAndExpectFailure(sourceCode, "invalid_method_argument_count");

        assertTrue(error.contains("requires 2 arguments, but got 1"),
            "Should report argument count mismatch");
    }

    @Test
    public void testInvalidMethodArgumentType() {
        String sourceCode = """
            resource TestResource{var data: i32}:
                            
                path:
                    pub def calculate(a: i32, b: i32) -> i32:
                        return a + b + this.data
                            
            def main() -> i32:
                var res: TestResource = TestResource{5}
                return res.calculate(10, 2.0)  # Wrong type for second argument
            """;

        String error = compileAndExpectFailure(sourceCode, "invalid_method_argument_type");

        assertTrue(error.contains("convert argument 2"), "Should report argument type mismatch");
    }

    @Test
    public void testAccessNonExistentField() {
        String sourceCode = """
            resource TestResource{pub var data: i32}
                            
            def main() -> i32:
                var res: TestResource = TestResource{5}
                return res.non_existent_field  # Field doesn't exist
            """;

        String error = compileAndExpectFailure(sourceCode, "access_non_existent_field");

        assertTrue(error.contains("Field 'non_existent_field' not found"),
            "Should report non-existent field");
    }

    @Test
    public void testCallNonExistentMethod() {
        String sourceCode = """
            resource TestResource{var data: i32}
                            
            def main() -> i32:
                var res: TestResource = TestResource{5}
                return res.non_existent_method()  # Method doesn't exist
            """;

        String error = compileAndExpectFailure(sourceCode, "call_non_existent_method");

        assertTrue(error.contains("Method 'non_existent_method' not found"),
            "Should report non-existent method");
    }

    @Test
    public void testComparisonOfDifferentArrayTypes() {
        String sourceCode = """
            resource ResourceA{var data: i32}
                            
            resource ResourceB{var data: i32}
                            
            def main() -> i32:
                var res_a = ResourceA{5}
                var res_b = ResourceB{5}
                var equal = res_a == res_b
            """;

        String error = compileAndExpectFailure(sourceCode, "call_non_existent_method");

        assertTrue(error.contains("Cannot compare ResourceA and ResourceB"),
            "Should report incompatible types for comparison");
    }

    @Test
    public void testAssignNullToIntField() {
        String sourceCode = """
            resource TestResource{pub var data: i32}
                            
            def main() -> i32:
                var res: TestResource = TestResource{5}
                res.data = null  # Invalid assignment
                return 0
            """;

        String error = compileAndExpectFailure(sourceCode, "assign_null_to_int_field");

        assertTrue(error.contains("Cannot convert value of type Null to type i32"),
            "Should report invalid null assignment");
    }
}