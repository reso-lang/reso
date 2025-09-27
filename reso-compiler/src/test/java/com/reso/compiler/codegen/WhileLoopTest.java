package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for while statement generation in Reso.
 * Tests compilation from Reso source code to LLVM IR with verification of:
 * - While loop structure and control flow
 * - Complex conditions and unreachable code warnings
 * - Loop variable modifications
 */
public class WhileLoopTest extends BaseTest {

    // ============================================================================
    // Basic While Loop Tests
    // ============================================================================

    @Test
    public void testBasicWhileLoop() {
        String sourceCode = wrapInMainFunction("""
            var i: i32 = 0
            while i < 5: i = i + 1
            var result: i32 = i
            """);
        String ir = compileAndExpectSuccess(sourceCode, "basic_while_loop");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("i", "i32"),
            IrPatterns.alloca("result", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("0", "i32", "i"),
            IrPatterns.unconditionalBranch("while_cond"),
            IrPatterns.label("while_cond"),
            IrPatterns.icmp("slt", "i32", "i", "5"),
            IrPatterns.label("while_body"),
            IrPatterns.add("i32", "i", "1"),
            IrPatterns.unconditionalBranch("while_cond"),
            IrPatterns.label("while_end")
        );
    }

    @Test
    public void testWhileLoopWithBlockStatements() {
        String sourceCode = wrapInMainFunction("""
            var counter: i32 = 10
            var sum: i32 = 0
            while counter > 0:
                sum = sum + counter
                counter = counter - 1
                var temp: i32 = counter * 2
            """);
        String ir = compileAndExpectSuccess(sourceCode, "while_with_block_statements");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("counter", "i32"),
            IrPatterns.alloca("sum", "i32"),
            IrPatterns.alloca("temp", "i32")
        );

        assertIrContainsInOrder(mainFunc,
            IrPatterns.store("10", "i32", "counter"),
            IrPatterns.store("0", "i32", "sum"),
            IrPatterns.unconditionalBranch("while_cond"),
            IrPatterns.icmp("sgt", "i32", "counter", "0"),
            IrPatterns.label("while_body"),
            IrPatterns.add("i32", "sum", "counter"),
            IrPatterns.sub("i32", "counter", "1"),
            IrPatterns.mul("i32", "counter", "2"),
            IrPatterns.unconditionalBranch("while_cond"),
            IrPatterns.label("while_end")
        );
    }

    // ============================================================================
    // While Loops with Break and Continue
    // ============================================================================

    @Test
    public void testWhileWithBreakStatement() {
        String sourceCode = wrapInMainFunction("""
            var i: i32 = 0
            while i < 10:
                i = i + 1
                if i == 5:
                    break
                var temp: i32 = i * 2
            var result: i32 = i
            """);
        String ir = compileAndExpectSuccess(sourceCode, "while_with_break");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Basic while structure
            IrPatterns.label("while_cond"),
            IrPatterns.label("while_body"),
            IrPatterns.label("while_end"),

            // Break statement creates unconditional branch to while_end
            IrPatterns.unconditionalBranch("while_end"),

            // If statement for break condition
            IrPatterns.icmp("eq", "i32", "i", "5"),

            IrPatterns.alloca("temp", "i32")
        );
    }

    @Test
    public void testWhileWithBreakAndUnreachableCodeWarning() {
        String sourceCode = wrapInMainFunction("""
            var i: i32 = 0
            while true:
                i = i + 1
                if i == 3:
                    break
                    var unreachable: i32 = 42
            var after_loop: i32 = i
            """);

        // This should produce warnings about unreachable code
        String warnings = compileAndExpectWarnings(sourceCode, "while_break_unreachable");

        assertTrue(warnings.contains("unreachable") || warnings.contains("Unreachable"),
            "Should warn about unreachable code after break");
    }

    @Test
    public void testWhileWithContinueStatement() {
        String sourceCode = wrapInMainFunction("""
            var i: i32 = 0
            var sum: i32 = 0
            while i < 10:
                i = i + 1
                if i rem 2 == 0:
                    continue
                sum = sum + i
            var result: i32 = sum
            """);
        String ir = compileAndExpectSuccess(sourceCode, "while_with_continue");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Basic while structure
            IrPatterns.label("while_cond"),
            IrPatterns.label("while_body"),
            IrPatterns.label("while_end"),

            // Continue statement creates unconditional branch to while_cond
            IrPatterns.unconditionalBranch("while_cond"),

            // Modulo operation for continue condition
            IrPatterns.srem("i32", "i", "2"),
            IrPatterns.icmp("eq", "i32", "srem", "0")
        );
    }

    @Test
    public void testWhileWithContinueAndUnreachableCodeWarning() {
        String sourceCode = wrapInMainFunction("""
            var i: i32 = 0
            while i < 5:
                i = i + 1
                continue
                var unreachable: i32 = 99
            """);

        String warnings = compileAndExpectWarnings(sourceCode, "while_continue_unreachable");

        assertTrue(warnings.contains("unreachable") || warnings.contains("Unreachable"),
            "Should warn about unreachable code after continue");
    }

    // ============================================================================
    // Nested While Loops
    // ============================================================================

    @Test
    public void testNestedWhileLoops() {
        String sourceCode = wrapInMainFunction("""
            var i: i32 = 0
            var j: i32 = 0
            var sum: i32 = 0
                            
            while i < 3:
                j = 0
                while j < 3:
                    sum = sum + (i * j)
                    j = j + 1
                i = i + 1
                            
            var result: i32 = sum
            """);
        String ir = compileAndExpectSuccess(sourceCode, "nested_while_loops");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Outer loop condition
            IrPatterns.icmp("slt", "i32", "i", "3"),
            // Inner loop condition
            IrPatterns.icmp("slt", "i32", "j", "3"),
            // Arithmetic in inner loop
            IrPatterns.mul("i32", "i", "j"),
            IrPatterns.add("i32", "sum", "mul")
        );
    }

    @Test
    public void testNestedWhileWithBreakAndContinue() {
        String sourceCode = wrapInMainFunction("""
            var i: i32 = 0
            var count: i32 = 0
                            
            while i < 5 + 1:
                var j: i32 = 0
                while j < 5:
                    j = j + 1
                    if j == 2:
                        continue
                    if j == 4:
                        break
                    count = count + 1
                i = i + 1
                if i == 3:
                    break
                            
            var result: i32 = count
            """);
        String ir = compileAndExpectSuccess(sourceCode, "nested_while_break_continue");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Break and continue branches
            IrPatterns.unconditionalBranch("while_cond"),
            IrPatterns.unconditionalBranch("while_end"),

            // Conditions for break/continue
            IrPatterns.icmp("eq", "i32", "j", "2"),
            IrPatterns.icmp("eq", "i32", "j", "4"),
            IrPatterns.icmp("eq", "i32", "i", "3")
        );
    }

    // ============================================================================
    // Complex While Loop Scenarios
    // ============================================================================

    @Test
    public void testWhileWithComplexConditions() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var y: i32 = 5
            var active: bool = true
                            
            while (x > 0) and (y > 0) and active:
                x = x - 1
                y = y - 1
                if x == y:
                    active = false
                            
            var result: i32 = x + y
            """);
        String ir = compileAndExpectSuccess(sourceCode, "while_complex_conditions");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("y", "i32"),
            IrPatterns.alloca("active", "i1"),

            // Complex condition evaluation with short-circuit logic
            IrPatterns.icmp("sgt", "i32", "x", "0"),
            IrPatterns.icmp("sgt", "i32", "y", "0"),
            IrPatterns.load("i1", "active")
        );
    }

    @Test
    public void testWhileLoopModifyingLoopVariable() {
        String sourceCode = wrapInMainFunction("""
            var n: i32 = 100
            var steps: i32 = 0
                            
            while n > 1:
                steps = steps + 1
                if n rem 2 == 0:
                    n = n div 2
                else:
                    n = (n * 3) + 1
                            
            var result: i32 = steps
            """);
        String ir = compileAndExpectSuccess(sourceCode, "while_modifying_loop_var");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("n", "i32"),
            IrPatterns.alloca("steps", "i32"),

            // Loop condition
            IrPatterns.icmp("sgt", "i32", "n", "1"),

            // Modulo and division operations
            IrPatterns.srem("i32", "n", "2"),
            IrPatterns.sdiv("i32", "n", "2"),
            IrPatterns.mul("i32", "n", "3"),
            IrPatterns.add("i32", "mul", "1")
        );
    }

    @Test
    public void testWhileWithFunctionCalls() {
        String sourceCode = """
            def should_continue(x: i32) -> bool:
                return x < 10
                            
            def process(value: i32) -> i32:
                return value * 2
                            
            def main() -> i32:
                var i: i32 = 0
                var result: i32 = 0
                            
                while should_continue(i):
                    result = process(i)
                    i = i + 1
                            
                return result
            """;
        String ir = compileAndExpectSuccess(sourceCode, "while_with_function_calls");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            // Function calls in condition and body
            IrPatterns.functionCall("should_continue", "i1", List.of()),
            IrPatterns.functionCall("process", "i32", List.of()),

            // While loop structure
            IrPatterns.conditionalBranch("should_continue_result", "while_body", "while_end")
        );
    }

    // ============================================================================
    // Infinite Loop and Edge Cases
    // ============================================================================

    @Test
    public void testWhileLoopWithEmptyBody() {
        String sourceCode = wrapInMainFunction("""
            var i: i32 = 5
            while i > 0:
                pass
            var result: i32 = i
            """);
        String ir = compileAndExpectSuccess(sourceCode, "while_empty_body");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.label("while_cond"),
            IrPatterns.label("while_body"),
            IrPatterns.label("while_end"),
            IrPatterns.icmp("sgt", "i32", "i", "0"),
            // Body should just jump back to condition
            IrPatterns.unconditionalBranch("while_cond")
        );
    }

    // ============================================================================
    // Error Cases
    // ============================================================================

    @Test
    public void testWhileWithNonBooleanCondition() {
        String sourceCode = wrapInMainFunction("""
            var i: i32 = 5
            while i:  # Invalid: i32 is not bool
                i = i - 1
            """);

        String errors = compileAndExpectFailure(sourceCode, "while_non_boolean_condition");

        assertTrue(
            errors.contains("boolean") || errors.contains("bool") || errors.contains("condition"),
            "Should report error about non-boolean condition");
    }

    @Test
    public void testBreakOutsideLoop() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            if x > 5:
                break  # Invalid: break outside loop
            """);

        String errors = compileAndExpectFailure(sourceCode, "break_outside_loop");

        assertTrue(
            errors.contains("break") && (errors.contains("loop") || errors.contains("outside")),
            "Should report error about break outside loop");
    }

    @Test
    public void testContinueOutsideLoop() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            if x > 5:
                continue  # Invalid: continue outside loop
            """);

        String errors = compileAndExpectFailure(sourceCode, "continue_outside_loop");

        assertTrue(
            errors.contains("continue") && (errors.contains("loop") || errors.contains("outside")),
            "Should report error about continue outside loop");
    }

    // ============================================================================
    // Variable Scope in While Loops
    // ============================================================================

    @Test
    public void testWhileLoopVariableScope() {
        String sourceCode = wrapInMainFunction("""
            var counter: i32 = 0
            var sum: i32 = 0
                            
            while counter < 5:
                var temp: i32 = counter * 2
                sum = sum + temp
                counter = counter + 1
                            
            var result: i32 = sum
            """);
        String ir = compileAndExpectSuccess(sourceCode, "while_variable_scope");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("counter", "i32"),
            IrPatterns.alloca("sum", "i32"),
            IrPatterns.alloca("temp", "i32"),
            IrPatterns.alloca("result", "i32")
        );
    }

    @Test
    public void testNestedWhileLoopVariableScope() {
        String sourceCode = wrapInMainFunction("""
            var i: i32 = 0
            var total: i32 = 0
                            
            while i < 3:
                var outer_val: i32 = i * 10
                var j: i32 = 0
                            
                while j < 2:
                    var inner_val: i32 = j * 5
                    total = total + outer_val + inner_val
                    j = j + 1
                            
                i = i + 1
                            
            var result: i32 = total
            """);
        String ir = compileAndExpectSuccess(sourceCode, "nested_while_variable_scope");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("i", "i32"),
            IrPatterns.alloca("total", "i32"),
            IrPatterns.alloca("outer_val", "i32"),
            IrPatterns.alloca("j", "i32"),
            IrPatterns.alloca("inner_val", "i32"),
            IrPatterns.alloca("result", "i32")
        );
    }

    @Test
    public void testWhileLoopVariableShadowing() {
        String sourceCode = wrapInMainFunction("""
            var x: i32 = 10
            var result: i32 = x  # Should use outer x (10)
                            
            while x > 0:
                var x: i32 = 999  # Shadows outer x
                result = result + x  # Should use inner x (999)
                x = x - 1  # Modifies inner x
                break  # Exit to avoid infinite loop with outer x
                            
            var final_x: i32 = x  # Should use outer x (still 10)
            """);
        String ir = compileAndExpectSuccess(sourceCode, "while_variable_shadowing");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.alloca("x", "i32"),
            IrPatterns.alloca("x2", "i32"),  // Second allocation for shadowed variable
            IrPatterns.alloca("result", "i32"),
            IrPatterns.alloca("final_x", "i32")
        );
    }

    @Test
    public void testErrorWhileBlockVariableNotAccessibleOutside() {
        String sourceCode = wrapInMainFunction("""
            var counter: i32 = 0
                            
            while counter < 3:
                var loop_var: i32 = counter * 2
                counter = counter + 1
                            
            var result: i32 = loop_var  # Error: loop_var not in scope
            """);

        String errors = compileAndExpectFailure(sourceCode, "while_variable_not_accessible");

        assertTrue(errors.contains("loop_var")
                && errors.contains("not defined"),
            "Should report error that 'loop_var' variable is not defined outside while block");
    }

    @Test
    public void testErrorNestedWhileVariableAccess() {
        String sourceCode = wrapInMainFunction("""
            var i: i32 = 0
                            
            while i < 2:
                var outer_temp: i32 = i
                var j: i32 = 0
                            
                while j < 2:
                    var inner_temp: i32 = j
                    j = j + 1
                            
                var result1: i32 = inner_temp  # Error: inner_temp not in scope
                i = i + 1
                            
            var result2: i32 = outer_temp  # Error: outer_temp not in scope
            """);

        String errors = compileAndExpectFailure(sourceCode, "nested_while_variable_error");

        assertTrue(errors.contains("inner_temp")
                && errors.contains("not defined"),
            "Should report error that 'inner_temp' variable is not defined in outer while scope");
        assertTrue(errors.contains("outer_temp")
                && errors.contains("not defined"),
            "Should report error that 'outer_temp' variable is not defined outside while block");
    }

    @Test
    public void testErrorVariableRedeclarationInWhileLoop() {
        String sourceCode = wrapInMainFunction("""
            var counter: i32 = 0
                            
            while counter < 2:
                var temp: i32 = counter
                var temp: i32 = counter + 1  # Error: temp already declared
                counter = counter + 1
            """);

        String errors = compileAndExpectFailure(sourceCode, "while_variable_redeclaration");

        assertTrue(errors.contains("temp")
                && errors.contains("already defined"),
            "Should report error that 'temp' variable is already declared in this scope");
    }
}