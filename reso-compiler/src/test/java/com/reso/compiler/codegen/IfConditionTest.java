package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for if statement generation in Reso.
 * Tests compilation from Reso source code to LLVM IR with exact verification of:
 * - If statements with simple and block statements
 * - Proper basic block creation and branching
 * - Scope management and variable visibility
 */
public class IfConditionTest extends BaseTest {

    // ============================================================================
    // Basic If Statement Tests
    // ============================================================================

    @Test
    public void testSimpleIfWithSimpleStatement() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            if x > 5: var result: i32 = 1
            """);
        String ir = compileAndExpectSuccess(sourceCode, "simple_if_simple_statement");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.icmp("sgt", "i32", "x", "5"),
            IrPatterns.conditionalBranch("icmp", "if_then", "if_end"),
            IrPatterns.label("if_then"),
            IrPatterns.store("1", "i32", "result"),
            IrPatterns.unconditionalBranch("if_end"),
            IrPatterns.label("if_end")
        );
    }

    @Test
    public void testSimpleIfWithBlockStatement() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            if x > 5:
                var result: i32 = 1
                result = result + 1
            """);
        String ir = compileAndExpectSuccess(sourceCode, "simple_if_block_statement");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.icmp("sgt", "i32", "x", "5"),
            IrPatterns.conditionalBranch("icmp", "if_then", "if_end"),
            IrPatterns.label("if_then"),
            IrPatterns.store("1", "i32", "result"),
            IrPatterns.add("i32", "result", "1"),
            IrPatterns.unconditionalBranch("if_end"),
            IrPatterns.label("if_end")
        );
    }

    @Test
    public void testIfElseWithSimpleStatements() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 3
            var result: i32 = 0
            if x > 5: result = 10
            else: result = 20
            """);
        String ir = compileAndExpectSuccess(sourceCode, "if_else_simple_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.icmp("sgt", "i32", "x", "5"),
            IrPatterns.conditionalBranch("icmp", "if_then", "else"),
            IrPatterns.label("if_then"),
            IrPatterns.store("10", "i32", "result"),
            IrPatterns.unconditionalBranch("if_end"),
            IrPatterns.label("else"),
            IrPatterns.store("20", "i32", "result"),
            IrPatterns.unconditionalBranch("if_end"),
            IrPatterns.label("if_end")
        );
    }

    @Test
    public void testIfElseWithBlockStatements() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 3
            var result: i32 = 0
            if x > 5:
                result = 10
                result = result + 5
            else:
                result = 20
                result = result - 5
            """);
        String ir = compileAndExpectSuccess(sourceCode, "if_else_block_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.icmp("sgt", "i32", "x", "5"),
            IrPatterns.conditionalBranch("icmp", "if_then", "else"),
            IrPatterns.label("if_then"),
            IrPatterns.store("10", "i32", "result"),
            IrPatterns.add("i32", "result", "5"),
            IrPatterns.unconditionalBranch("if_end"),
            IrPatterns.label("else"),
            IrPatterns.store("20", "i32", "result"),
            IrPatterns.sub("i32", "result", "5"),
            IrPatterns.unconditionalBranch("if_end"),
            IrPatterns.label("if_end")
        );
    }

    // ============================================================================
    // If-Else If Chain Tests
    // ============================================================================

    @Test
    public void testIfElseIf() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 7
            var result: i32 = 0
            if x < 5: result = 1
            else if x < 10: result = 2
            """);
        String ir = compileAndExpectSuccess(sourceCode, "if_else_if_chain");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.icmp("slt", "i32", "x", "5"),
            IrPatterns.conditionalBranch("icmp", "if_then", "elseif_cond_1"),
            IrPatterns.label("if_then"),
            IrPatterns.store("1", "i32", "result"),
            IrPatterns.unconditionalBranch("if_end"),
            IrPatterns.label("elseif_cond_1"),
            IrPatterns.icmp("slt", "i32", "x", "10"),
            IrPatterns.label("elseif_then_1"),
            IrPatterns.store("2", "i32", "result")
        );
    }

    @Test
    public void testIfElseIfChain() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 7
            var result: i32 = 0
            if x < 5: result = 1
            else if x < 10: result = 2
            else: result = 3
            """);
        String ir = compileAndExpectSuccess(sourceCode, "if_else_if_chain");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.icmp("slt", "i32", "x", "5"),
            IrPatterns.conditionalBranch("icmp", "if_then", "elseif_cond_1"),
            IrPatterns.label("if_then"),
            IrPatterns.store("1", "i32", "result"),
            IrPatterns.unconditionalBranch("if_end"),
            IrPatterns.label("elseif_cond_1"),
            IrPatterns.icmp("slt", "i32", "x", "10"),
            IrPatterns.conditionalBranch("icmp", "elseif_then_1", "else"),
            IrPatterns.label("elseif_then_1"),
            IrPatterns.store("2", "i32", "result"),
            IrPatterns.unconditionalBranch("if_end"),
            IrPatterns.label("else"),
            IrPatterns.store("3", "i32", "result"),
            IrPatterns.unconditionalBranch("if_end"),
            IrPatterns.label("if_end")
        );
    }

    @Test
    public void testMultipleElseIfChain() {
        String sourceCode = wrapInMainFunction("""
            var score: i32 = 85
            var grade: i32 = 0
            if score >= 90: grade = 4
            else if score >= 80: grade = 3
            else if score >= 70: grade = 2
            else if score >= 60: grade = 1
            else: grade = 0
            """);
        String ir = compileAndExpectSuccess(sourceCode, "multiple_else_if_chain");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("score", "i32"),
            IrPatterns.alloca("grade", "i32"),
            IrPatterns.icmp("sge", "i32", "score", "90"),
            IrPatterns.conditionalBranch("icmp", "if_then", "elseif_cond_1"),
            IrPatterns.label("elseif_cond_1"),
            IrPatterns.icmp("sge", "i32", "score", "80"),
            IrPatterns.conditionalBranch("icmp", "elseif_then_1", "elseif_cond_2"),
            IrPatterns.label("elseif_cond_2"),
            IrPatterns.icmp("sge", "i32", "score", "70"),
            IrPatterns.conditionalBranch("icmp", "elseif_then_2", "elseif_cond_3"),
            IrPatterns.label("elseif_cond_3"),
            IrPatterns.icmp("sge", "i32", "score", "60"),
            IrPatterns.conditionalBranch("icmp", "elseif_then_3", "else")
        );
    }

    // ============================================================================
    // Nested If Statement Tests
    // ============================================================================

    @Test
    public void testNestedIfStatements() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var y: i32 = 5
            var result: i32 = 0
            if x > 5:
                if y > 3:
                    result = 1
                else:
                    result = 2
            else:
                result = 3
            """);
        String ir = compileAndExpectSuccess(sourceCode, "nested_if_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.icmp("sgt", "i32", "x", "5"),
            IrPatterns.conditionalBranch("icmp", "if_then", "else"),
            IrPatterns.label("if_then"),
            IrPatterns.icmp("sgt", "i32", "y", "3"),
            IrPatterns.conditionalBranch("icmp", "if_then2", "else3"),
            IrPatterns.label("if_then2"),
            IrPatterns.store("1", "i32", "result"),
            IrPatterns.label("else3"),
            IrPatterns.store("2", "i32", "result"),
            IrPatterns.label("else"),
            IrPatterns.store("3", "i32", "result")
        );
    }

    @Test
    public void testDeeplyNestedIfStatements() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 5
            var c: i32 = 3
            var result: i32 = 0
            if a > 8:
                if b > 4:
                    if c > 2:
                        result = 1
                    else:
                        result = 2
                else:
                    result = 3
            else:
                result = 4
            """);
        String ir = compileAndExpectSuccess(sourceCode, "deeply_nested_if_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("c", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.icmp("sgt", "i32", "a", "8"),
            IrPatterns.icmp("sgt", "i32", "b", "4"),
            IrPatterns.icmp("sgt", "i32", "c", "2"),
            IrPatterns.store("1", "i32", "result"),
            IrPatterns.store("2", "i32", "result"),
            IrPatterns.store("3", "i32", "result"),
            IrPatterns.store("4", "i32", "result")
        );
    }

    // ============================================================================
    // If Statements in Different Scenarios
    // ============================================================================

    @ParameterizedTest
    @MethodSource("provideBooleanConditionTests")
    public void testIfWithBooleanConditions(String condition, String conditionValue,
                                            String description) {
        String sourceCode = wrapInMainFunction(String.format("""
            var flag: bool = true
            var other: bool = false
            var result: i32 = 0
            if %s: result = 1
            """, condition));
        String ir = compileAndExpectSuccess(sourceCode, "if_boolean_" + description);

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("flag", "i1"),
            IrPatterns.alloca("other", "i1"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.conditionalBranch(conditionValue, "if_then", "if_end")
        );
    }

    private static Stream<Arguments> provideBooleanConditionTests() {
        return Stream.of(
            Arguments.of("flag", "flag_value", "direct_variable"),
            Arguments.of("flag and other", "logical_result", "logical_and"),
            Arguments.of("flag or other", "logical_result", "logical_or"),
            Arguments.of("not flag", "not", "logical_not")
        );
    }

    @Test
    public void testIfWithFunctionCallCondition() {
        String sourceCode = """
            def is_positive(n: i32) -> bool:
                return n > 0
            
            def main() -> i32:
                var x: i32 = 10
                var result: i32 = 0
                if is_positive(x):
                    result = 1
                else:
                    result = -1
                return result
            """;
        String ir = compileAndExpectSuccess(sourceCode, "if_function_call_condition");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.functionCall("is_positive", "i1", List.of(Map.entry("i32", "x"))),
            IrPatterns.conditionalBranch("is_positive_result", "if_then", "else")
        );
    }

    @Test
    public void testIfWithFunctionCallsInBranches() {
        String sourceCode = """
            def increment(n: i32) -> i32:
                return n + 1
            
            def decrement(n: i32) -> i32:
                return n - 1
            
            def main() -> i32:
                var x: i32 = 10
                var result: i32 = 0
                if x > 5:
                    result = increment(x)
                else:
                    result = decrement(x)
                return result
            """;
        String ir = compileAndExpectSuccess(sourceCode, "if_function_calls_in_branches");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.icmp("sgt", "i32", "x", "5"),
            IrPatterns.functionCall("increment", "i32", List.of(Map.entry("i32", "x"))),
            IrPatterns.functionCall("decrement", "i32", List.of(Map.entry("i32", "x")))
        );
    }

    @Test
    public void testIfStatementsWithComplexExpressions() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            var b: i32 = 5
            var c: i32 = 3
            var result: i32 = 0
            if (a + b) > (c * 4):
                result = a - b
            else:
                result = b + c
            """);
        String ir = compileAndExpectSuccess(sourceCode, "if_complex_expressions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("c", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.add("i32", "a", "b"),
            IrPatterns.mul("i32", "c", "4"),
            IrPatterns.icmp("sgt", "i32", "add", "mul"),
            IrPatterns.sub("i32", "a", "b"),
            IrPatterns.add("i32", "b", "c")
        );
    }

    // ============================================================================
    // Variable Scope Tests
    // ============================================================================

    @Test
    public void testIfStatementVariableScope() {
        String sourceCode = wrapInMainFunction("""
            var outer: i32 = 10
            if outer > 5:
                var inner: i32 = 20
                outer = inner + 5
            var result: i32 = outer
            """);
        String ir = compileAndExpectSuccess(sourceCode, "if_variable_scope");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("outer", "i32"),
            IrPatterns.alloca("inner", "i32"),
            IrPatterns.alloca("result", "i32"),
            IrPatterns.store("10", "i32", "outer"),
            IrPatterns.store("20", "i32", "inner"),
            IrPatterns.add("i32", "inner", "5")
        );
    }

    @Test
    public void testNestedScopeVariables() {
        String sourceCode = wrapInMainFunction("""
            var a: i32 = 10
            if a > 5:
                var b: i32 = 20
                if b > 15:
                    var c: i32 = 30
                    a = a + b + c
                else:
                    a = a + b
            var result: i32 = a
            """);
        String ir = compileAndExpectSuccess(sourceCode, "nested_scope_variables");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("a", "i32"),
            IrPatterns.alloca("b", "i32"),
            IrPatterns.alloca("c", "i32"),
            IrPatterns.alloca("result", "i32")
        );
    }

    @Test
    public void testIfBlockVariableNotAccessibleOutside() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            if x > 5:
                var inner: i32 = 20
            var result: i32 = inner  # Error: inner not accessible
            """);
        String errors = compileAndExpectFailure(sourceCode, "if_block_variable_not_accessible");

        assertTrue(errors.contains("inner")
                && errors.contains("not defined"),
            "Should report error that 'inner' variable is not accessible outside if block");
    }

    @Test
    public void testIfStatementVariableShadowing() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            if x > 5:
                var x: i32 = 20  # Shadows outer x
                var inner_result: i32 = x  # Should use inner x (20)
            var outer_result: i32 = x  # Should use outer x (10)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "if_variable_shadowing");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("x1", "i32"),
            IrPatterns.alloca("outer_result", "i32"),
            IrPatterns.alloca("inner_result", "i32"),
            IrPatterns.store("10", "i32", "x"),
            IrPatterns.store("20", "i32", "x"),
            IrPatterns.store("x", "i32", "inner_result"),
            IrPatterns.store("x", "i32", "outer_result")
        );
    }

    @Test
    public void testErrorIfStatementUndefinedVariableAccess() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            if x > 5:
                var y: i32 = 20
            var result: i32 = y  # Error: y not in scope
            """);

        String errors = compileAndExpectFailure(sourceCode, "if_undefined_variable");

        assertTrue(errors.contains("y")
                && errors.contains("not defined"),
            "Should report error that 'y' variable is not defined in this scope");
    }

    @Test
    public void testErrorNestedScopeVariableAccess() {
        String sourceCode = wrapInMainFunction("""
            var outer: i32 = 10
            if outer > 5:
                var middle: i32 = 20
                if middle > 15:
                    var inner: i32 = 30
                var result1: i32 = inner  # Error: inner not in scope
            var result2: i32 = middle  # Error: middle not in scope
            """);

        String errors = compileAndExpectFailure(sourceCode, "nested_scope_error");

        assertTrue(errors.contains("inner")
                && errors.contains("not defined"),
            "Should report error that 'inner' variable is not defined in this scope");
        assertTrue(errors.contains("middle")
                && errors.contains("not defined"),
            "Should report error that 'middle' variable is not defined in this scope");
    }

    @Test
    public void testErrorVariableRedeclarationInSameScope() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            if x > 5:
                var y: i32 = 20
                var y: i32 = 30  # Error: y already declared in this scope
            """);

        String errors = compileAndExpectFailure(sourceCode, "variable_redeclaration");

        assertTrue(errors.contains("y")
                && errors.contains("already defined"),
            "Should report error that 'y' variable is already declared in this scope");
    }

    // ============================================================================
    // Edge Cases and Error Handling
    // ============================================================================

    @Test
    public void testEmptyIfBlock() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            if x > 5:
                pass
            """);
        String ir = compileAndExpectSuccess(sourceCode, "empty_if_block");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.icmp("sgt", "i32", "x", "5"),
            IrPatterns.conditionalBranch("icmp", "if_then", "if_end"),
            IrPatterns.label("if_then"),
            IrPatterns.unconditionalBranch("if_end"),
            IrPatterns.label("if_end")
        );
    }

    @Test
    public void testElseWithoutIf() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            else:  # Error: else without if
                var result: i32 = 0
            """);

        String errors = compileAndExpectFailure(sourceCode, "else_without_if");

        assertFalse(errors.isEmpty(),
            "Should report error that 'else' is used without a preceding 'if'");
    }

    @Test
    public void testElseIfWithoutIf() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            else if x > 5:  # Error: else if without if
                var result: i32 = 0
            """);

        String errors = compileAndExpectFailure(sourceCode, "else_if_without_if");

        assertFalse(errors.isEmpty(),
            "Should report error that 'else if' is used without a preceding 'if'");
    }

    @Test
    public void testIfStatementWithReturn() {
        String sourceCode = """
            def test(x: i32) -> i32:
                if x > 10:
                    return 1
                else:
                    return 0
            
            def main() -> i32:
                return test(15)
            """;
        String ir = compileAndExpectSuccess(sourceCode, "if_with_return");

        String testFunc = extractFunction(ir, "test");
        assertNotNull(testFunc, "Should find test function in IR");

        assertIrContains(testFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.icmp("sgt", "i32", "x", "10"),
            IrPatterns.conditionalBranch("icmp", "if_then", "else"),
            IrPatterns.returnStatement("i32", "1"),
            IrPatterns.returnStatement("i32", "0")
        );
    }
}