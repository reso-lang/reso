package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for function generation in Reso.
 * Tests compilation from Reso source code to LLVM IR with verification of:
 * - Function definition order independence
 * - Return type validation (unit and non-unit)
 * - Return statement path coverage requirements
 * - Unreachable code detection
 * - Parameter and argument processing
 */
public class FunctionTest extends BaseTest {

    // ============================================================================
    // Function Definition Order Independence
    // ============================================================================

    @Test
    public void testFunctionDefinitionOrderDoesNotMatter() {
        String sourceCode1 = """
            def main() -> i32:
                return helper()
                            
            def helper() -> i32:
                return 42
            """;

        String sourceCode2 = """
            def helper() -> i32:
                return 42
                            
            def main() -> i32:
                return helper()
            """;

        String ir1 = compileAndExpectSuccess(sourceCode1, "order_main_first");
        String ir2 = compileAndExpectSuccess(sourceCode2, "order_helper_first");

        // Both should compile successfully and contain same function patterns
        assertIrContains(ir1,
            IrPatterns.functionDefinition("main", "i32"),
            IrPatterns.functionDefinition("helper", "i32"),
            IrPatterns.functionCall("helper", "i32", List.of()),
            IrPatterns.returnStatement("i32", "42")
        );

        assertIrContains(ir2,
            IrPatterns.functionDefinition("main", "i32"),
            IrPatterns.functionDefinition("helper", "i32"),
            IrPatterns.functionCall("helper", "i32", List.of()),
            IrPatterns.returnStatement("i32", "42")
        );
    }

    @Test
    public void testMultipleFunctionDefinitionOrders() {
        String sourceCode = """
            def main() -> i32:
                var a: i32 = first()
                var b: i32 = second()
                var c: i32 = third()
                return a + b + c
                            
            def third() -> i32:
                return 30
                            
            def first() -> i32:
                return 10
                            
            def second() -> i32:
                return 20
            """;

        String ir = compileAndExpectSuccess(sourceCode, "multiple_function_order");

        assertIrContains(ir,
            IrPatterns.functionDefinition("main", "i32"),
            IrPatterns.functionDefinition("first", "i32"),
            IrPatterns.functionDefinition("second", "i32"),
            IrPatterns.functionDefinition("third", "i32"),
            IrPatterns.functionCall("first", "i32", List.of()),
            IrPatterns.functionCall("second", "i32", List.of()),
            IrPatterns.functionCall("third", "i32", List.of())
        );
    }

    // ============================================================================
    // Functions with Simple Statements vs Block Statements
    // ============================================================================

    @Test
    public void testFunctionWithSimpleStatement() {
        String sourceCode = """
            def calculate(x: i32, y: i32) -> i32: return x + y
                            
            def main() -> i32:
                return calculate(10, 20)
            """;

        String ir = compileAndExpectSuccess(sourceCode, "simple_statement");

        String calculateFunc = extractFunction(ir, "calculate");
        assertNotNull(calculateFunc, "Should find calculate function in IR");

        assertIrContains(calculateFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.add("i32", "x", "y"),
            IrPatterns.functionDefinition("calculate", "i32"),
            IrPatterns.returnStatement("i32", "add")
        );
    }

    @Test
    public void testFunctionWithBlockStatement() {
        String sourceCode = """
            def calculate(x: i32, y: i32) -> i32:
                return x + y
                            
            def main() -> i32:
                return calculate(10, 20)
            """;

        String ir = compileAndExpectSuccess(sourceCode, "block_statement");

        String calculateFunc = extractFunction(ir, "calculate");
        assertNotNull(calculateFunc, "Should find calculate function in IR");

        assertIrContains(calculateFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.add("i32", "x", "y"),
            IrPatterns.functionDefinition("calculate", "i32"),
            IrPatterns.returnStatement("i32", "add")
        );
    }

    // ============================================================================
    // Return Types: Unit vs Non-Unit Functions
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {"i32", "f64", "bool"})
    public void testNonUnitFunctionsMustReturnValue(String returnType) {
        String defaultValue = switch (returnType) {
            case "i32" -> "42";
            case "f64" -> "3.14";
            case "bool" -> "true";
            default -> "0";
        };

        String sourceCode = String.format("""
            def test() -> %s:
                return %s
                            
            def main() -> i32:
                var result: %s = test()
                return 0
            """, returnType, defaultValue, returnType);

        String ir = compileAndExpectSuccess(sourceCode, "non_unit_" + returnType.toLowerCase());

        String llvmType = switch (returnType) {
            case "i32" -> "i32";
            case "f64" -> "double";
            case "bool" -> "i1";
            default -> "i32";
        };

        assertIrContains(ir,
            IrPatterns.functionDefinition("test", llvmType),
            IrPatterns.returnStatement(llvmType, defaultValue)
        );
    }

    @Test
    public void testUnitFunctionsCannotReturnValue() {
        String sourceCode = """
            def do_something():
                return 42
                            
            def main() -> i32:
                do_something()
                return 0
            """;

        String errors = compileAndExpectFailure(sourceCode, "unit_with_return_value");

        assertTrue(errors.contains("Cannot convert integer literal to non-integer type ()"),
            "Should report error for returning value from unit function");
    }

    @Test
    public void testUnitFunctionImplicitReturn() {
        String sourceCode = """
            def setup():
                var x: i32 = 10
                            
            def cleanup():
                return
                            
            def another() -> ():
                return ()
                            
            def tidy() -> ():
                var y: i32 = 20
                            
            def do_nothing() -> ():
                return
                            
            def quiet() -> ():
                return ()
                            
            def main() -> i32:
                setup()
                cleanup()
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "unit_implicit_return");

        assertIrContainsInOrder(ir,
            IrPatterns.functionDefinition("setup", "%unit"),
            IrPatterns.returnStatement("%unit", "zeroinitializer"),
            IrPatterns.functionDefinition("cleanup", "%unit"),
            IrPatterns.returnStatement("%unit", "zeroinitializer"),
            IrPatterns.functionDefinition("another", "%unit"),
            IrPatterns.returnStatement("%unit", "zeroinitializer"),
            IrPatterns.functionDefinition("tidy", "%unit"),
            IrPatterns.returnStatement("%unit", "zeroinitializer"),
            IrPatterns.functionDefinition("do_nothing", "%unit"),
            IrPatterns.returnStatement("%unit", "zeroinitializer"),
            IrPatterns.functionDefinition("quiet", "%unit"),
            IrPatterns.returnStatement("%unit", "zeroinitializer")
        );
    }

    @Test
    public void testUnitArgumentFunctionError() {
        String sourceCode = """
            def logMessage(msg: ()):
                return
                            
            def main() -> i32:
                logMessage(())
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "unit_argument_function");

        assertIrContains(ir,
            IrPatterns.functionDefinition("logMessage", "%unit"),
            IrPatterns.functionCall("logMessage", "%unit",
                List.of(Map.entry("%unit", "zeroinitializer")))
        );
    }

    // ============================================================================
    // Return Statement Path Coverage Requirements
    // ============================================================================

    @Test
    public void testAllPathsMustReturnInNonUnitFunction() {
        String sourceCode = """
            def conditional(flag: bool) -> i32:
                if flag:
                    return 10
                # Missing return in else path
                            
            def main() -> i32:
                return conditional(true)
            """;

        String errors = compileAndExpectFailure(sourceCode, "missing_return_path");
        assertTrue(errors.contains("must return a value"),
            "Should report error for missing return in non-unit function");
    }

    @Test
    public void testAllBranchesReturn() {
        String sourceCode = """
            def safe_conditional(flag: bool) -> i32:
                if flag:
                    return 10
                else:
                    return 20
                            
            def main() -> i32:
                return safe_conditional(false)
            """;

        String ir = compileAndExpectSuccess(sourceCode, "all_branches_return");

        String safeFunc = extractFunction(ir, "safe_conditional");
        assertNotNull(safeFunc, "Should find safe_conditional function in IR");

        assertIrContains(safeFunc,
            IrPatterns.returnStatement("i32", "10"),
            IrPatterns.returnStatement("i32", "20")
        );
    }

    @Test
    public void testComplexControlFlowWithReturns() {
        String sourceCode = """
            def complex_flow(a: i32, b: i32) -> i32:
                if a > 0:
                    if b > 0:
                        return a + b
                    else:
                        return a - b
                else:
                    if b > 0:
                        return b - a
                    else:
                        return 0
                            
            def main() -> i32:
                return complex_flow(5, 3)
            """;

        String ir = compileAndExpectSuccess(sourceCode, "complex_control_flow");

        String complexFunc = extractFunction(ir, "complex_flow");
        assertNotNull(complexFunc, "Should find complex_flow function in IR");

        // Should contain multiple return statements
        assertIrContains(complexFunc,
            IrPatterns.add("i32", "a", "b"),
            IrPatterns.sub("i32", "a", "b"),
            IrPatterns.sub("i32", "b", "a"),
            IrPatterns.returnStatement("i32", "0")
        );
    }

    @Test
    public void testUnitFunctionDoesNotRequireExplicitReturn() {
        String sourceCode = """
            def unit_without_return():
                var x: i32 = 42
                            
            def unit_with_if_no_return(flag: bool):
                if flag:
                    var y: i32 = 10
                            
            def main() -> i32:
                unit_without_return()
                unit_with_if_no_return(true)
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "unit_no_explicit_return");

        assertIrContains(ir,
            IrPatterns.functionDefinition("unit_without_return", "%unit"),
            IrPatterns.functionDefinition("unit_with_if_no_return", "%unit")
        );
    }

    @Test
    public void testReturnInWhileLoop() {
        String sourceCode = """
            def find_first(start: i32, target: i32) -> i32:
                var current: i32 = start
                while current < 100:
                    if current == target:
                        return current
                    current = current + 1
                return -1
                            
            def main() -> i32:
                return find_first(10, 15)
            """;

        String ir = compileAndExpectSuccess(sourceCode, "return_in_while");

        String findFirstFunc = extractFunction(ir, "find_first");
        assertNotNull(findFirstFunc, "Should find find_first function in IR");

        assertIrContains(findFirstFunc,
            IrPatterns.alloca("current", "i32"),
            IrPatterns.icmp("slt", "i32", "current", "100"),
            IrPatterns.icmp("eq", "i32", "current", "target"),
            IrPatterns.load("i32", "current"),
            IrPatterns.returnStatement("i32", "current"),
            IrPatterns.returnStatement("i32", "-1")
        );
    }

    @Test
    public void testReturnInWhileLoopWithoutFinalReturn() {
        String sourceCode = """
            def search_loop(limit: i32) -> i32:
                var i: i32 = 0
                while i < limit:
                    if i == 5:
                        return i
                    i = i + 1
                # Missing final return - should cause error
                            
            def main() -> i32:
                return search_loop(10)
            """;

        String errors = compileAndExpectFailure(sourceCode, "while_missing_return");
        assertTrue(errors.contains("must return a value"),
            "Should report error for missing return after while loop");
    }

    @Test
    public void testMultipleReturnsInWhileLoop() {
        String sourceCode = """
            def complex_while(max: i32) -> i32:
                var count: i32 = 0
                while count < max:
                    if count rem 2 == 0:
                        if count == 10:
                            return count
                    else:
                        if count == 15:
                            return count * 2
                    count = count + 1
                return count
                            
            def main() -> i32:
                return complex_while(20)
            """;

        String ir = compileAndExpectSuccess(sourceCode, "multiple_returns_while");

        String complexFunc = extractFunction(ir, "complex_while");
        assertNotNull(complexFunc, "Should find complex_while function in IR");

        assertIrContains(complexFunc,
            IrPatterns.srem("i32", "count", "2"),
            IrPatterns.icmp("eq", "i32", "count", "10"),
            IrPatterns.icmp("eq", "i32", "count", "15"),
            IrPatterns.mul("i32", "count", "2"),
            IrPatterns.returnStatement("i32", "count")
        );
    }

    @Test
    public void testReturnWithTernaryExpression() {
        String sourceCode = """
            def max_value(a: i32, b: i32) -> i32:
                return a if a > b else b if b > a else 0
                            
            def main() -> i32:
                return max_value(10, 20)
            """;

        String ir = compileAndExpectSuccess(sourceCode, "return_ternary");

        String maxFunc = extractFunction(ir, "max_value");
        assertNotNull(maxFunc, "Should find max_value function in IR");

        assertIrContains(maxFunc,
            IrPatterns.icmp("sgt", "i32", "a", "b"),
            IrPatterns.select("i1", "icmp", "i32", "a", "b"),
            IrPatterns.select("i1", "icmp", "i32", "ternary", "0")
        );
    }

    // ============================================================================
    // Unreachable Code Detection
    // ============================================================================

    @Test
    public void testUnreachable_codeAfterReturn() {
        String sourceCode = """
            def unreachable() -> i32:
                return 42
                var x: i32 = 10  # This should be unreachable
                return x
                            
            def main() -> i32:
                return unreachable()
            """;

        String warnings = compileAndExpectWarnings(sourceCode, "unreachable_after_return");

        assertTrue(warnings.contains("Unreachable code"),
            "Should detect unreachable code after return statement");
    }

    @Test
    public void testUnreachable_codeAfterReturnInWhileLoop() {
        String sourceCode = """
            def unreachable_while() -> i32:
                var i: i32 = 0
                while i < 10:
                    return i
                    i = i + 1  # This should be unreachable
                return -1
                            
            def main() -> i32:
                return unreachable_while()
            """;

        String warnings = compileAndExpectWarnings(sourceCode, "unreachable_in_while");
        assertTrue(warnings.contains("Unreachable code"),
            "Should detect unreachable code after return in while loop");
    }

    @Test
    public void testUnreachable_codeAfterAllBranchesReturn() {
        String sourceCode = """
            def unreachable_after_if(flag: bool) -> i32:
                if flag:
                    return 10
                else:
                    return 20
                var unused: i32 = 30  # This should be unreachable
                return unused
                            
            def main() -> i32:
                return unreachable_after_if(true)
            """;

        String warnings = compileAndExpectWarnings(sourceCode, "unreachable_after_if");

        assertTrue(warnings.contains("Unreachable code"),
            "Should detect unreachable code after if statement where all branches return");
    }

    @Test
    public void testReachableCodeAfterPartialReturn() {
        String sourceCode = """
            def reachable_code(flag: bool) -> i32:
                if flag:
                    return 10
                # This code is reachable when flag is false
                var result: i32 = 20
                return result
                            
            def main() -> i32:
                return reachable_code(false)
            """;

        String ir = compileAndExpectSuccess(sourceCode, "reachable_after_partial_return");

        String reachableFunc = extractFunction(ir, "reachable_code");
        assertNotNull(reachableFunc, "Should find reachable_code function in IR");

        assertIrContains(reachableFunc,
            IrPatterns.alloca("result", "i32"),
            IrPatterns.store("20", "i32", "result")
        );
    }

    @Test
    public void testUnreachable_codeAfterElse() {
        String sourceCode = """
            def complex_flow(a: i32, b: i32) -> i32:
                if a > 0:
                    if b > 0:
                        return a + b
                    else:
                        return a - b
                else:
                    if b > 0:
                        return b - a
                    else:
                        return 0
                return -1  # This should be unreachable
                            
            def main() -> i32:
                return complex_flow(5, 3)
            """;

        String warnings = compileAndExpectWarnings(sourceCode, "unreachable_after_else");

        assertTrue(warnings.contains("Unreachable code"),
            "Should detect unreachable code after if statement where all branches return");
    }

    // ============================================================================
    // Main Method Special Handling
    // ============================================================================

    @Test
    public void testMainFunctionMustReturni32() {
        String sourceCode = """
            def main() -> f64:
                return 3.14
            """;

        String errors = compileAndExpectFailure(sourceCode, "main_wrong_return_type");

        assertTrue(errors.contains("Main function must return i32"),
            "Should enforce i32 return type for main function");
    }

    @Test
    public void testMainFunctionImplicitReturnZero() {
        String sourceCode = """
            def main() -> i32:
                var x: i32 = 42
                # No explicit return - should add implicit return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "main_implicit_return");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Should find main function in IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.store("42", "i32", "x"),
            IrPatterns.returnStatement("i32", "0")
        );
    }

    @Test
    public void testMainFunctionExplicitReturn() {
        String sourceCode = """
            def main() -> i32:
                var status: i32 = 1
                return status
            """;

        String ir = compileAndExpectSuccess(sourceCode, "main_explicit_return");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Should find main function in IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("status", "i32"),
            IrPatterns.store("1", "i32", "status"),
            IrPatterns.load("i32", "status")
        );
    }

    @Test
    public void testMainFunctionCannotTakeParameters() {
        String sourceCode = """
            def main(argc: i32) -> i32:
                return argc
            """;

        String errors = compileAndExpectFailure(sourceCode, "main_with_parameters");

        assertTrue(errors.contains("Main function should not have any parameters"),
            "Should reject main function with parameters");
    }

    @Test
    public void testMainFunctionUnitReturnType() {
        String sourceCode = """
            def main():
                return
            """;

        String errors = compileAndExpectFailure(sourceCode, "main_unit_return_type");

        assertTrue(errors.contains("Main function must have explicit return type i32"),
            "Should enforce i32 return type for main function");
    }

    // ============================================================================
    // Function Parameters and Arguments
    // ============================================================================

    @ParameterizedTest
    @MethodSource("functionParameterTestCases")
    public void testFunctionWithParameters(String paramTypes, String argValues, String llvmTypes) {
        String[] types = paramTypes.split(",");
        String[] args = argValues.split(",");
        String[] llvmTypeArray = llvmTypes.split(",");

        StringBuilder params = new StringBuilder();
        StringBuilder callArgs = new StringBuilder();

        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                params.append(", ");
                callArgs.append(", ");
            }
            params.append("param").append(i).append(": ").append(types[i]);
            callArgs.append(args[i]);
        }

        String sourceCode = String.format("""
            def test_func(%s) -> i32:
                return 0
                            
            def main() -> i32:
                return test_func(%s)
            """, params, callArgs);

        String ir = compileAndExpectSuccess(sourceCode, "params_" + paramTypes.replace(",", "_"));

        // Verify function definition includes correct parameter types
        for (String llvmType : llvmTypeArray) {
            assertIrContains(ir, llvmType.trim());
        }
    }

    private static Stream<Arguments> functionParameterTestCases() {
        return Stream.of(
            Arguments.of("i32", "42", "i32"),
            Arguments.of("f64", "3.14", "double"),
            Arguments.of("bool", "true", "i1"),
            Arguments.of("i32,f64", "10,2.5", "i32,double"),
            Arguments.of("bool,i32,f64", "false,100,1.23", "i1,i32,double")
        );
    }

    // ============================================================================
    // Function Definition Patterns and Edge Cases
    // ============================================================================

    @Test
    public void testEmptyFunction() {
        String sourceCode = """
            def empty() -> ():
                # Empty function body
                            
            def main() -> i32:
                empty()
                return 0
            """;

        String errors = compileAndExpectFailure(sourceCode, "empty_function");

        assertNotNull(errors, "Should report error for empty function");
    }

    @Test
    public void testRecursiveFunction() {
        String sourceCode = """
            def factorial(n: i32) -> i32:
                if n <= 1:
                    return 1
                else:
                    return n * factorial(n - 1)
                            
            def main() -> i32:
                return factorial(5)
            """;

        String ir = compileAndExpectSuccess(sourceCode, "recursive_function");

        String factorialFunc = extractFunction(ir, "factorial");
        assertNotNull(factorialFunc, "Should find factorial function in IR");

        assertIrContains(factorialFunc,
            IrPatterns.icmp("sle", "i32", "n", "1"),
            IrPatterns.sub("i32", "n", "1"),
            IrPatterns.functionCall("factorial", "i32", List.of(Map.entry("i32", "sub")))
        );
    }

    @Test
    public void testMutuallyRecursiveFunctions() {
        String sourceCode = """
            def is_even(n: i32) -> bool:
                if n == 0:
                    return true
                else:
                    return is_odd(n - 1)
                            
            def is_odd(n: i32) -> bool:
                if n == 0:
                    return false
                else:
                    return is_even(n - 1)
                            
            def main() -> i32:
                var result: bool = is_even(4)
                return 0
            """;

        String ir = compileAndExpectSuccess(sourceCode, "mutually_recursive");

        assertIrContains(ir,
            IrPatterns.functionDefinition("is_even", "i1"),
            IrPatterns.functionDefinition("is_odd", "i1"),
            IrPatterns.functionCall("is_odd", "i1", List.of(Map.entry("i32", "sub"))),
            IrPatterns.functionCall("is_even", "i1", List.of(Map.entry("i32", "sub")))
        );
    }
}