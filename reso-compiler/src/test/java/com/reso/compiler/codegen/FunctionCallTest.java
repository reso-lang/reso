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
 * Tests for function calls in Reso.
 * Tests compilation from Reso source code to LLVM IR with exact verification
 * of function call generation in various contexts and scenarios.
 */
public class FunctionCallTest extends BaseTest {

    // ============================================================================
    // Function Calls in Variable Initialization
    // ============================================================================

    @Test
    public void testFunctionCallInVariableInitialization() {
        String sourceCode = """
            def add(a: i32, b: i32) -> i32:
                return a + b
            
            def main() -> i32:
                var result: i32 = add(10, 20)
                return result
            """;
        String ir = compileAndExpectSuccess(sourceCode, "function_call_in_variable_init");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("result", "i32"),
            IrPatterns.functionCall("add", "i32",
                List.of(Map.entry("i32", "10"), Map.entry("i32", "20"))),
            IrPatterns.store("%add_result", "i32", "result")
        );
    }

    @Test
    public void testFunctionCallWithUntypedLiterals() {
        String sourceCode = """
            def multiply(x: i32, y: f64) -> f64:
                return x as f64 * y
            
            def main() -> i32:
                var result: f64 = multiply(2, 3.14)
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "untyped_literals");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("result", "double"),
            IrPatterns.functionCall("multiply", "double",
                List.of(Map.entry("i32", "2"), Map.entry("double", "3.140000e\\+00")))
        );
    }

    // ============================================================================
    // Function Calls in Variable Assignments
    // ============================================================================

    @Test
    public void testFunctionCallInAssignment() {
        String sourceCode = """
            def subtract(a: i32, b: i32) -> i32:
                return a - b
            
            def main() -> i32:
                var x: i32 = 0
                x = subtract(100, 25)
                return x
            """;
        String ir = compileAndExpectSuccess(sourceCode, "function_call_in_assignment");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.store("0", "i32", "x"),
            IrPatterns.functionCall("subtract", "i32",
                List.of(Map.entry("i32", "100"), Map.entry("i32", "25"))),
            IrPatterns.store("%subtract_result", "i32", "x")
        );
    }

    @Test
    public void testMultipleFunctionCallsInAssignments() {
        String sourceCode = """
            def square(n: i32) -> i32:
                return n * n
            
            def main() -> i32:
                var a: i32 = square(5)
                var b: i32 = square(7)
                var c: i32 = square(a)
                return c
            """;
        String ir = compileAndExpectSuccess(sourceCode, "multiple_function_calls");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("c", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("square", "i32", List.of(Map.entry("i32", "5"))),
            IrPatterns.functionCall("square", "i32", List.of(Map.entry("i32", "7"))),
            IrPatterns.load("i32", "a"),
            IrPatterns.functionCall("square", "i32", List.of(Map.entry("i32", "a")))
        );
    }

    // ============================================================================
    // Function Calls in Conditional Expressions
    // ============================================================================

    @Test
    public void testFunctionCallInIfCondition() {
        String sourceCode = """
            def isPositive(n: i32) -> bool:
                return n > 0
            
            def main() -> i32:
                var x: i32 = 10
                if isPositive(x):
                    return 1
                else:
                    return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "function_call_in_if_condition");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.load("i32", "x"),
            IrPatterns.functionCall("isPositive", "i1", List.of(Map.entry("i32", "x")))
        );
    }

    @Test
    public void testFunctionCallInWhileCondition() {
        String sourceCode = """
            def hasNext() -> bool:
                return true
            
            def process():
                return
            
            def main() -> i32:
                while hasNext():
                    process()
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "function_call_in_while_condition");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.functionCall("hasNext", "i1", List.of()),
            IrPatterns.functionCall("process", "%unit", List.of())
        );
    }

    // ============================================================================
    // Function Calls as Arguments to Other Functions
    // ============================================================================

    @Test
    public void testFunctionCallAsArgument() {
        String sourceCode = """
            def add(a: i32, b: i32) -> i32:
                return a + b
            
            def multiply(a: i32, b: i32) -> i32:
                return a * b
            
            def main() -> i32:
                var result: i32 = multiply(add(2, 3), add(4, 5))
                return result
            """;
        String ir = compileAndExpectSuccess(sourceCode, "function_call_as_argument");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("add", "i32",
                List.of(Map.entry("i32", "2"), Map.entry("i32", "3"))),
            IrPatterns.functionCall("add", "i32",
                List.of(Map.entry("i32", "4"), Map.entry("i32", "5"))),
            IrPatterns.functionCall("multiply", "i32",
                List.of(Map.entry("i32", "add_result"), Map.entry("i32", "add_result1")))
        );
    }

    @Test
    public void testNestedFunctionCalls() {
        String sourceCode = """
            def increment(n: i32) -> i32:
                return n + 1
            
            def double(n: i32) -> i32:
                return n * 2
            
            def main() -> i32:
                var result: i32 = double(increment(5))
                return result
            """;
        String ir = compileAndExpectSuccess(sourceCode, "nested_function_calls");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.functionCall("increment", "i32", List.of(Map.entry("i32", "5"))),
            IrPatterns.functionCall("double", "i32", List.of(Map.entry("i32", "increment_result")))
        );
    }

    // ============================================================================
    // Function Calls in Return Statements
    // ============================================================================

    @Test
    public void testFunctionCallInReturnStatement() {
        String sourceCode = """
            def calculate(a: i32, b: i32) -> i32:
                return a * b + 10
            
            def main() -> i32:
                return calculate(6, 7)
            """;
        String ir = compileAndExpectSuccess(sourceCode, "function_call_in_return");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.functionCall("calculate", "i32",
                List.of(Map.entry("i32", "6"), Map.entry("i32", "7")))
        );
    }

    // ============================================================================
    // Complex Expression Arguments
    // ============================================================================

    @Test
    public void testFunctionCallWithComplexExpressionArguments() {
        String sourceCode = """
            def operation(a: i32, b: i32, c: i32) -> i32:
                return a + b * c
            
            def main() -> i32:
                var x: i32 = 2
                var y: i32 = 3
                var result: i32 = operation(x + 1, y * 2, 10 - 4)
                return result
            """;
        String ir = compileAndExpectSuccess(sourceCode, "complex_expression_arguments");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.alloca("result", "i32")
        );

        // The expressions should be evaluated before the function call
        assertIrContains(mainFunc,
            IrPatterns.load("i32", "x"),
            IrPatterns.add("i32", "x", "1"),
            IrPatterns.load("i32", "y"),
            IrPatterns.mul("i32", "y", "2"),
            IrPatterns.functionCall("operation", "i32", List.of(
                Map.entry("i32", "add"),
                Map.entry("i32", "mul"),
                Map.entry("i32", "6")
            ))
        );
    }

    // ============================================================================
    // Unit Function Calls
    // ============================================================================

    @Test
    public void testUnitFunctionCalls() {
        String sourceCode = """
            def printMessage(a: ()):
                return ()
            
            def setup(config: i32) -> ():
                return
            
            def main() -> i32:
                var a = printMessage(())
                var b : () = setup(42)
                a = setup(100)
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "unit_function_calls");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.alloca("b", "%unit"),
            IrPatterns.alloca("a", "%unit"),
            IrPatterns.functionCall("printMessage", "%unit",
                List.of(Map.entry("%unit", "zeroinitializer"))),
            IrPatterns.store("%printMessage_result", "%unit", "a"),
            IrPatterns.functionCall("setup", "%unit", List.of(Map.entry("i32", "42"))),
            IrPatterns.store("%setup_result", "%unit", "b"),
            IrPatterns.functionCall("setup", "%unit", List.of(Map.entry("i32", "100"))),
            IrPatterns.store("%setup_result1", "%unit", "a")
        );
    }

    // ============================================================================
    // Error Cases
    // ============================================================================

    @ParameterizedTest
    @MethodSource("incompatibleArguments")
    public void testIncompatibleArgumentsIn(String paramType, String argType, String argValue) {
        String sourceCode = String.format("""
            def testFunc(param: %s) -> %s:
                return param
            
            def main() -> i32:
                var arg: %s = %s
                var result: %s = testFunc(arg)
                return 0
            """, paramType, paramType, argType, argValue, paramType);

        String errors =
            compileAndExpectFailure(sourceCode, "type_conversion_" + paramType + "_" + argType);

        assertTrue(errors.contains("Cannot convert argument")
                && errors.contains(argType + " to " + paramType),
            "Should report type conversion error from " + argType + " to " + paramType);
    }

    static Stream<Arguments> incompatibleArguments() {
        return Stream.of(
            // i8 to larger integer types
            Arguments.of("i16", "i8", "42"),
            Arguments.of("i32", "i8", "42"),
            Arguments.of("i64", "i8", "42"),

            // i16 to larger integer types
            Arguments.of("i32", "i16", "1000"),
            Arguments.of("i64", "i16", "1000"),

            // i32 to i64
            Arguments.of("i64", "i32", "100000"),

            // Integer to floating point
            Arguments.of("f32", "i32", "42"),
            Arguments.of("f64", "i32", "42"),

            // f32 to f64
            Arguments.of("f64", "f32", "3.14"),

            // bool to i32
            Arguments.of("i32", "bool", "true")
        );
    }

    @Test
    public void testUndefinedFunctionCall() {
        String sourceCode = """
            def main() -> i32:
                var result: i32 = undefinedFunction(42)
                return result
            """;
        String errors = compileAndExpectFailure(sourceCode, "undefined_function");

        assertTrue(errors.contains("Function not defined: undefinedFunction"),
            "Should report undefined function error");
    }

    @Test
    public void testWrongArgumentCount() {
        String sourceCode = """
            def add(a: i32, b: i32) -> i32:
                return a + b
            
            def main() -> i32:
                var result: i32 = add(10)  # Missing second argument
                return result
            """;
        String errors = compileAndExpectFailure(sourceCode, "wrong_argument_count");

        assertTrue(errors.contains("requires 2 arguments, but got 1"),
            "Should report wrong argument count error");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 5})
    public void testVariousWrongArgumentCounts(int argumentCount) {
        String args = "42, ".repeat(Math.max(0, argumentCount - 1))
            + (argumentCount > 0 ? "42" : "");
        if (argumentCount == 0) {
            args = "";
        }

        String sourceCode = String.format("""
            def twoArgFunction(a: i32, b: i32) -> i32:
                return a + b
            
            def main() -> i32:
                var result: i32 = twoArgFunction(%s)
                return result
            """, args);

        String errors = compileAndExpectFailure(sourceCode, "wrong_arg_count_" + argumentCount);

        assertTrue(errors.contains("requires 2 arguments, but got " + argumentCount),
            "Should report wrong argument count for " + argumentCount + " arguments");
    }

    // ============================================================================
    // Recursive Function Calls
    // ============================================================================

    @Test
    public void testRecursiveFunctionCall() {
        String sourceCode = """
            def factorial(n: i32) -> i32:
                if n <= 1:
                    return 1
                else:
                    return n * factorial(n - 1)
            
            def main() -> i32:
                var result: i32 = factorial(5)
                return result
            """;
        String ir = compileAndExpectSuccess(sourceCode, "recursive_function");

        String factorialFunc = extractFunction(ir, "factorial");
        assertNotNull(factorialFunc, "Factorial function should be present in the IR");

        assertIrContains(factorialFunc,
            IrPatterns.functionCall("factorial", "i32", List.of(Map.entry("i32", "sub")))
        );
    }

    // ============================================================================
    // Function Calls with Mixed Types
    // ============================================================================

    @Test
    public void testFunctionCallWithMixedTypes() {
        String sourceCode = """
            def compute(i: i32, f: f64, b: bool) -> f64:
                if b:
                    return i as f64 + f
                else:
                    return f - i as f64
            
            def main() -> i32:
                var result: f64 = compute(10, 3.14, true)
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "mixed_types");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.functionCall("compute", "double", List.of(
                Map.entry("i32", "10"),
                Map.entry("double", "3.140000e\\+00"),
                Map.entry("i1", "true")
            ))
        );
    }
}