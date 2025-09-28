package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for logical expressions in Reso.
 * Tests compilation from Reso source code to LLVM IR with verification of:
 * - Logical AND and OR operations with proper short-circuit evaluation
 * - Logical expressions in various contexts (if, while, assignments, etc.)
 * - Chained logical operations
 * - Error handling for invalid logical expressions
 * - PHI node generation for short-circuit evaluation
 */
public class LogicalExpressionTest extends BaseTest {

    // ============================================================================
    // Basic Logical Operations
    // ============================================================================

    @Test
    public void testBasicLogicalAndOperation() {
        String sourceCode = wrapInMainFunction("""
            var a: bool = true
            var b: bool = false
            var result: bool = a and b
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_logical_and");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i1"),
            IrPatterns.alloca("b", "i1"),
            IrPatterns.alloca("result", "i1"),
            IrPatterns.conditionalBranch("a", "eval_right", "merge"),
            IrPatterns.logicalAndPhi("logical_result", "entry", "b", "eval_right")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("true", "i1", "a"),
            IrPatterns.store("false", "i1", "b"),
            IrPatterns.load("i1", "a"),
            IrPatterns.conditionalBranch("a", "eval_right", "merge"),
            IrPatterns.label("eval_right"),
            IrPatterns.load("i1", "b"),
            IrPatterns.unconditionalBranch("merge"),
            IrPatterns.label("merge"),
            IrPatterns.phi("i1", "logical_result", "false", "entry", "b", "eval_right")
        );
    }

    @Test
    public void testBasicLogicalOrOperation() {
        String sourceCode = wrapInMainFunction("""
            var x: bool = false
            var y: bool = true
            var result: bool = x or y
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_logical_or");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i1"),
            IrPatterns.alloca("y", "i1"),
            IrPatterns.alloca("result", "i1"),
            IrPatterns.conditionalBranch("x", "merge", "eval_right"),
            IrPatterns.logicalOrPhi("logical_result", "entry", "y", "eval_right")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("false", "i1", "x"),
            IrPatterns.store("true", "i1", "y"),
            IrPatterns.load("i1", "x"),
            IrPatterns.conditionalBranch("x", "merge", "eval_right"),
            IrPatterns.label("eval_right"),
            IrPatterns.load("i1", "y"),
            IrPatterns.label("merge"),
            IrPatterns.phi("i1", "logical_result", "true", "entry", "y", "eval_right")
        );
    }

    // ============================================================================
    // Short-Circuit Evaluation Tests
    // ============================================================================

    @Test
    public void testShortCircuitWithFunctionCalls() {
        String sourceCode = """
            def sideEffect() -> bool:
                return true
            
            def main() -> i32:
                var shouldNotCall: bool = false
                var result: bool = shouldNotCall and sideEffect()
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "short_circuit_function_calls");

        String main = extractFunction(ir, "main");
        assertNotNull(main, "Should have main function");

        assertIrContains(main,
            IrPatterns.conditionalBranch("shouldNotCall", "eval_right", "merge"),
            IrPatterns.phi("i1", "logical_result", "false", "entry", "%sideEffect", "eval_right"),
            IrPatterns.functionCall("sideEffect", "i1", List.of())
        );
    }

    // ============================================================================
    // Logical Expressions in Control Flow
    // ============================================================================

    @Test
    public void testLogicalExpressionInIfCondition() {
        String sourceCode = wrapInMainFunction("""
            var condition1: bool = true
            var condition2: bool = false
            if condition1 and condition2:
                var x: i32 = 1
            """);
        String ir = compileAndExpectSuccess(sourceCode, "logical_in_if_condition");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition1", "i1"),
            IrPatterns.alloca("condition2", "i1"),
            IrPatterns.phi("i1", "logical_result", "false", "entry", "%condition2", "eval_right"),
            IrPatterns.conditionalBranch("condition1", "eval_right", "merge"),
            IrPatterns.conditionalBranch("logical_result", "if_then", "if_end")
        );
    }

    @Test
    public void testLogicalExpressionInWhileCondition() {
        String sourceCode = wrapInMainFunction("""
            var running: bool = true
            var hasData: bool = false
            while running and hasData:
                running = false
            """);
        String ir = compileAndExpectSuccess(sourceCode, "logical_in_while_condition");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("running", "i1"),
            IrPatterns.alloca("hasData", "i1")
        );

        assertIrContains(mainFunc,
            IrPatterns.label("while_cond"),
            IrPatterns.conditionalBranch("running", "eval_right", "merge"),
            IrPatterns.conditionalBranch("logical_result", "while_body", "while_end")
        );
    }

    @Test
    public void testNestedLogicalExpressionsInControlFlow() {
        String sourceCode = wrapInMainFunction("""
            var a: bool = true
            var b: bool = false
            var c: bool = true
            var d: bool = false
            
            if (a and b) or (c and d):
                var result: i32 = 42
            """);
        String ir = compileAndExpectSuccess(sourceCode, "nested_logical_in_control_flow");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.conditionalBranch("a", "eval_right", "merge"),
            IrPatterns.phi("i1", "logical_result", "false", "entry", "%b", "eval_right"),
            IrPatterns.conditionalBranch("c", "eval_right", "merge"),
            IrPatterns.phi("i1", "logical_result", "false", "eval_right", "%d", "eval_right"),
            IrPatterns.phi("i1", "logical_result", "true", "merge", "logical_result", "merge")
        );
    }

    // ============================================================================
    // Logical Expressions in Variable Operations
    // ============================================================================

    @Test
    public void testLogicalExpressionInVariableInitialization() {
        String sourceCode = wrapInMainFunction("""
            var isValid: bool = true
            var isEnabled: bool = false
            var canProceed: bool = isValid and isEnabled
            var shouldRetry: bool = not isValid or isEnabled
            """);
        String ir = compileAndExpectSuccess(sourceCode, "logical_in_variable_init");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("isValid", "i1"),
            IrPatterns.alloca("isEnabled", "i1"),
            IrPatterns.alloca("canProceed", "i1"),
            IrPatterns.alloca("shouldRetry", "i1")
        );

        assertIrContains(mainFunc,
            IrPatterns.phi("i1", "logical_result", "false", "entry", "%isEnabled", "eval_right"),
            IrPatterns.phi("i1", "logical_result", "true", "merge", "%isEnabled", "eval_right")
        );
    }

    @Test
    public void testLogicalExpressionInVariableAssignment() {
        String sourceCode = wrapInMainFunction("""
            var flag1: bool = true
            var flag2: bool = false
            var result: bool = false
            
            result = flag1 and flag2
            result = flag1 or flag2
            """);
        String ir = compileAndExpectSuccess(sourceCode, "logical_in_assignment");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("flag1", "i1"),
            IrPatterns.alloca("flag2", "i1"),
            IrPatterns.alloca("result", "i1")
        );

        assertIrContains(mainFunc,
            IrPatterns.phi("i1", "logical_result", "false", "entry", "%flag2", "eval_right"),
            IrPatterns.phi("i1", "logical_result", "true", "merge", "%flag2", "eval_right")
        );
    }

    // ============================================================================
    // Logical Expressions as Function Arguments
    // ============================================================================

    @Test
    public void testLogicalExpressionAsFunctionArgument() {
        String sourceCode = """
            def processFlag(flag: bool) -> i32:
                if flag:
                    return 1
                return 0
            
            def main() -> i32:
                var condition1: bool = true
                var condition2: bool = false
                var result1: i32 = processFlag(condition1 and condition2)
                var result2: i32 = processFlag(condition1 or condition2)
                return result1 + result2
            """;
        String ir = compileAndExpectSuccess(sourceCode, "logical_as_function_argument");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("condition1", "i1"),
            IrPatterns.alloca("condition2", "i1"),
            IrPatterns.alloca("result1", "i32"),
            IrPatterns.alloca("result2", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.phi("i1", "logical_result", "false", "entry", "%condition2", "eval_right"),
            IrPatterns.functionCall("processFlag", "i32",
                List.of(Map.entry("i1", "logical_result"))),
            IrPatterns.phi("i1", "logical_result", "true", "merge", "%condition2", "eval_right"),
            IrPatterns.functionCall("processFlag", "i32",
                List.of(Map.entry("i1", "logical_result")))
        );
    }

    // ============================================================================
    // Logical Expressions in Return Statements
    // ============================================================================

    @Test
    public void testLogicalExpressionInReturnStatement() {
        String sourceCode = """
            def checkConditions(a: bool, b: bool) -> bool:
                return a and b
            
            def validateInputs(x: bool, y: bool, z: bool) -> bool:
                return x or (y and z)
            
            def main() -> i32:
                var result1: bool = checkConditions(true, false)
                var result2: bool = validateInputs(false, true, true)
                return 0
            """;
        String ir = compileAndExpectSuccess(sourceCode, "logical_in_return");

        // Verify that logical expressions are properly evaluated in return statements
        String checkConditionsFunc = extractFunction(ir, "checkConditions");
        String validateInputsFunc = extractFunction(ir, "validateInputs");

        assertNotNull(checkConditionsFunc, "Should have checkConditions function");
        assertNotNull(validateInputsFunc, "Should have validateInputs function");

        assertIrContains(checkConditionsFunc,
            IrPatterns.phi("i1", "logical_result", "false", "entry", "%b", "eval_right"));
        assertIrContains(validateInputsFunc,
            IrPatterns.phi("i1", "logical_result", "false", "eval_right", "%z", "eval_right"),
            IrPatterns.phi("i1", "logical_result", "true", "entry", "%logical_result", "merge")
        );
    }

    // ============================================================================
    // Chained Logical Operations
    // ============================================================================

    @Test
    public void testChainedLogicalAndOperations() {
        String sourceCode = wrapInMainFunction("""
            var a: bool = true
            var b: bool = true
            var c: bool = false
            var d: bool = true
            var result: bool = a and b and c and d
            """);
        String ir = compileAndExpectSuccess(sourceCode, "chained_logical_and");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i1"),
            IrPatterns.alloca("b", "i1"),
            IrPatterns.alloca("c", "i1"),
            IrPatterns.alloca("d", "i1"),
            IrPatterns.alloca("result", "i1")
        );

        assertIrContains(mainFunc,
            IrPatterns.phi("i1", "logical_result", "false", "entry", "b", "eval_right"),
            IrPatterns.phi("i1", "logical_result", "false", "merge", "c", "eval_right"),
            IrPatterns.phi("i1", "logical_result", "false", "merge", "d", "eval_right")
        );
    }

    @Test
    public void testChainedLogicalOrOperations() {
        String sourceCode = wrapInMainFunction("""
            var w: bool = false
            var x: bool = false
            var y: bool = true
            var z: bool = false
            var result: bool = w or x or y or z
            """);
        String ir = compileAndExpectSuccess(sourceCode, "chained_logical_or");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("w", "i1"),
            IrPatterns.alloca("x", "i1"),
            IrPatterns.alloca("y", "i1"),
            IrPatterns.alloca("z", "i1"),
            IrPatterns.alloca("result", "i1")
        );

        assertIrContains(mainFunc,
            IrPatterns.phi("i1", "logical_result", "true", "entry", "x", "eval_right"),
            IrPatterns.phi("i1", "logical_result", "true", "merge", "y", "eval_right"),
            IrPatterns.phi("i1", "logical_result", "true", "merge", "z", "eval_right")
        );
    }

    // ============================================================================
    // Error Cases
    // ============================================================================

    @Test
    public void testLogicalAndWithNonBooleanOperands() {
        String sourceCode = wrapInMainFunction("""
            var num: i32 = 42
            var flag: bool = true
            var result: bool = num and flag
            """);
        String errors = compileAndExpectFailure(sourceCode, "logical_and_non_boolean");

        assertTrue(errors.contains("Logical operation requires boolean operands"),
            "Should report error for non-boolean operand in logical AND");
        assertTrue(errors.contains("got i32"),
            "Should specify the actual type found");
    }

    @Test
    public void testLogicalOrWithNonBooleanOperands() {
        String sourceCode = wrapInMainFunction("""
            var str: String = "test"
            var flag: bool = false
            var result: bool = flag or str
            """);
        String errors = compileAndExpectFailure(sourceCode, "logical_or_non_boolean");

        assertTrue(errors.contains("Logical operation requires boolean operands"),
            "Should report error for non-boolean operand in logical OR");
        assertTrue(errors.contains("got String"),
            "Should specify the actual type found");
    }

    @ParameterizedTest
    @ValueSource(strings = {"i8", "i16", "i32", "i64", "isize", "u8", "u16", "u32", "u64", "usize",
        "f32", "f64", "String"})
    public void testLogicalOperationsWithVariousInvalidTypes(String typeName) {
        String sourceCode = wrapInMainFunction(String.format("""
            var flag: bool = true
            var value: %s = %s
            var result: bool = flag and value
            """, typeName, getDefaultValueForType(typeName)));

        String errors = compileAndExpectFailure(sourceCode, "logical_invalid_type_" + typeName);

        assertTrue(errors.contains("Logical operation requires boolean operands"),
            "Should report error for " + typeName + " operand");
        assertTrue(errors.contains("got " + typeName),
            "Should specify the invalid type " + typeName);
    }

    @Test
    public void testLogicalExpressionOutsideFunction() {
        String sourceCode = """
            var globalFlag1: bool = true
            var globalFlag2: bool = false
            var globalResult: bool = globalFlag1 and globalFlag2
            """;
        String errors = compileAndExpectFailure(sourceCode, "logical_outside_function");

        assertFalse(errors.isEmpty(),
            "Should report error for logical expression outside function");
    }

    @Test
    public void testLogicalExpressionWithUnitTypes() {
        String sourceCode = """
            def doNothing():
                return
            
            def main() -> i32:
                var result: bool = doNothing() and doNothing()
            """;
        String errors = compileAndExpectFailure(sourceCode, "logical_with_unit");

        assertTrue(errors.contains("Logical operation requires boolean operands"),
            "Should report error for unit operand in logical AND");
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Provides default values for different types used in parameterized tests.
     */
    private String getDefaultValueForType(String typeName) {
        return switch (typeName) {
            case "i8", "i16", "i32", "i64", "isize" -> "42";
            case "u8", "u16", "u32", "u64", "usize" -> "42";
            case "f32", "f64" -> "3.14";
            case "String" -> "\"test\"";
            default -> "null";
        };
    }
}