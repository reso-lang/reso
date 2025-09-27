package com.reso.compiler.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.grammar.ResoParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Reso language parser.
 * Verifies correct parsing of valid Reso code and error reporting for invalid code.
 */
public class ParserTest {
    private ErrorReporter errorReporter;

    @BeforeEach
    public void setUp() {
        errorReporter = new ErrorReporter("test.reso");
    }

    @Test
    public void testEmptyProgram() {
        // An empty program is valid
        ParseResult result = Parser.parse("", errorReporter);
        assertNotNull(result);
        assertNotNull(result.getTree());
        assertFalse(errorReporter.hasErrors());
    }

    @Test
    public void testSimpleFunction() {
        String code = """
            def main() -> i32:
                return 0
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertNotNull(result);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");

        ResoParser.ProgramContext program = (ResoParser.ProgramContext) result.getTree();
        assertEquals(1, program.functionDef().size(), "Should have one function");
    }

    @Test
    public void testFunctionWithParams() {
        String code = """
            def add(a: i32, b: i32) -> i32:
                return a + b
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");

        ResoParser.ProgramContext program = (ResoParser.ProgramContext) result.getTree();
        ResoParser.FunctionDefContext function = program.functionDef(0);
        assertEquals("add", function.Identifier().getText(), "Function name should be 'add'");
        assertNotNull(function.parameterList(), "Should have parameters");
        assertEquals(2, function.parameterList().parameter().size(), "Should have 2 parameters");
    }

    @Test
    public void testFunctionWithVariables() {
        String code = """
            def calculate() -> i32:
                var x: i32 = 10
                var y: i32 = 20
                return x + y
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testInvalidStatementOutsideFunction() {
        // Statements outside functions are not allowed
        String code = """
            var x: i32 = 10
                            
            def main() -> i32:
                return 0
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertTrue(errorReporter.hasErrors(), "Should report error for statement outside function");
    }

    @Test
    public void testIfStatement() {
        String code = """
            def test(value: i32) -> i32:
                if value > 0:
                    return 1
                else if value < 0:
                    return -1
                else:
                    return 0
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testWhileLoop() {
        String code = """
            def count_down(start: i32) -> i32:
                var count: i32 = start
                while count > 0:
                    count = count - 1
                return count
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testBreakContinue() {
        String code = """
            def process_numbers(max: i32) -> i32:
                var sum: i32 = 0
                var i: i32 = 0
                while i < max:
                    i = i + 1
                    if i rem 2 == 0: continue
                    if i > 10:
                        break
                    sum = sum + i
                return sum
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testArithmeticExpressions() {
        String code = """
            def calculate(a: i32, b: i32) -> i32:
                var result: i32 = (a + b) * (a - b) div 2 rem 10 mod 3
                return result
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testBitwiseOperations() {
        String code = """
            def bitwise_ops(a: i32, b: i32) -> i32:
                var result: i32 = 0
                result = a & b
                result = result | (a ^ b)
                result = ~result
                result = result << 2
                result = result >> 1
                return result
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testLogicalOperations() {
        String code = """
            def logical_ops(a: bool, b: bool, c: bool) -> bool:
                var result: bool = false
                result = a and b
                result = result or c
                result = not result
                return result
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testCompoundAssignments() {
        String code = """
            def compound_assign(a: i32) -> i32:
                var x: i32 = a
                x += 5
                x -= 2
                x *= 3
                x div= 2
                x rem= 3
                x mod= 10
                x &= 4
                x |= 7
                x ^= 4
                x <<= 2
                x >>= 1
                return x
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testResourceDefinition() {
        String code = """
            resource User{
                var name: String,
                const email: String}:
                            
                path:
                    def get() -> String:
                        return this.name.append(this.email)
                            
                path name:
                    def get() -> String:
                        return this.name
                            
                path profile:
                    def get() -> String:
                        return "Profile of " + this.name
                            
            def main() -> i32:
                return 0
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");

        ResoParser.ProgramContext program = (ResoParser.ProgramContext) result.getTree();
        assertEquals(1, program.resourceDef().size(), "Should have one resource definition");
    }

    @Test
    public void testResourceVisibilityDefinition() {
        String code = """
            resource User{
                pub var name: String,
                pub const email: String}:
                            
                path:
                    def get() -> String:
                        return this.name.append(this.email)
                            
                path name:
                    def get() -> String:
                        return this.name
                            
                path profile:
                    pub def get() -> String:
                        return "Profile of " + this.name
                            
            def main() -> i32:
                return 0
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        System.out.println(errorReporter.getErrors());
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");

        ResoParser.ProgramContext program = (ResoParser.ProgramContext) result.getTree();
        assertEquals(1, program.resourceDef().size(), "Should have one resource definition");
    }

    @Test
    public void testDifferentTypes() {
        String code = """
            def type_test() -> i32:
                var a: i8 = 42
                var b: i16 = 1000
                var c: i32 = 100000
                var d: i64 = 1000000000
                var e: f32 = 3.14
                var f: f64 = 2.71828
                var g: bool = true
                var h: char = 'A'
                var i: String = "Hello, Reso!"
                return 0
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testLiterals() {
        String code = """
            def literals() -> i32:
                var a = 42          # Integer literal
                var b = 3.14        # Floating-point literal
                var c = true        # boolean literal
                var d = 'X'         # Character literal
                var e = "String"    # String literal
                var f = null        # Null literal
                return 0
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testNestedFunctions() {
        String code = """
            def outer(x: i32) -> i32:
                def inner(y: i32) -> i32:
                    return y * 2
                            
                return inner(x) + 5
            """;
        // Nested functions are not allowed in Reso based on the grammar
        ParseResult result = Parser.parse(code, errorReporter);
        assertTrue(errorReporter.hasErrors(), "Should report error for nested function");
    }

    @Test
    public void testTypeConversions() {
        String code = """
            def converting() -> i32:
                var a: i32 = 42
                var b: f64 = a as f64
                var c: i64 = a as i64
                var d: i8 = 15 as i8
                var e: f32 = 3.14159 as f32
                return b as i32
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testUnaryOperators() {
        String code = """
            def unary_ops() -> i32:
                var a: i32 = 42
                var b: i32 = -a
                var c: i32 = +a
                var d: i32 = ~a
                var e: bool = true
                var f: bool = not e
                return 0
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testSyntaxError() {
        String code = """
            def syntax_error() -> i32:
                var x: i32 = 10
                var y: i32 = 20
                return x + * y  # Syntax error: unexpected *
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertTrue(errorReporter.hasErrors(), "Should report syntax error");
    }

    @Test
    public void testIndentation() {
        String code = """
            def indentation_test() -> i32:
                var x: i32 = 10
              var y: i32 = 20  # Wrong indentation
                return x + y
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        // The Reso grammar uses indentation for blocks
        assertTrue(errorReporter.hasErrors(), "Should report indentation error");
    }

    @Test
    public void testMultipleFunctions() {
        String code = """
            def add(a: i32, b: i32) -> i32:
                return a + b
                            
            def subtract(a: i32, b: i32) -> i32:
                return a - b
                            
            def multiply(a: i32, b: i32) -> i32:
                return a * b
                            
            def main() -> i32:
                var a: i32 = add(5, 3)
                var b: i32 = subtract(10, 4)
                var c: i32 = multiply(a, b)
                return c
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");

        ResoParser.ProgramContext program = (ResoParser.ProgramContext) result.getTree();
        assertEquals(4, program.functionDef().size(), "Should have four functions");
    }

    @Test
    public void testComments() {
        String code = """
            # main
            def main() -> i32:
                # Another comment
                var x: i32 = 42  # End of line comment
                return x  # Return statement
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse without errors");
    }

    @Test
    public void testSimpleTernaryOperator() {
        String code = """
            def test_ternary(x: i32) -> i32:
                var result: i32 = 10 if x > 5 else 0
                return result
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse simple ternary without errors");

        ResoParser.ProgramContext program = (ResoParser.ProgramContext) result.getTree();
        assertEquals(1, program.functionDef().size(), "Should have one function");
    }

    @Test
    public void testNestedTernaryOperator() {
        String code = """
            def test_nested_ternary(x: i32, y: i32) -> i32:
                var result: i32 = x if x > 0 else y if y > 0 else 0
                return result
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(), "Should parse nested ternary without errors");
    }

    @Test
    public void testTernaryInExpressions() {
        String code = """
            def helper(x: i32) -> i32:
                return x * 2
                            
            def test_ternary_in_expressions(a: i32, b: i32, flag: bool) -> i32:
                var result1: i32 = (a if flag else b) + 10
                var result2: i32 = a * (5 if b > 0 else 1)
                var result3: i32 = (a + b) if (a > b) else (a - b)
                var result4: i32 = helper(a) if b else c
                return result1 + result2 + result3
            """;
        ParseResult result = Parser.parse(code, errorReporter);
        assertFalse(errorReporter.hasErrors(),
            "Should parse ternary in complex expressions without errors");
    }
}