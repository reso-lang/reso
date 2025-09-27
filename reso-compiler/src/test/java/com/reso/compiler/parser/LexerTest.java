package com.reso.compiler.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reso.grammar.ResoLexer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the Reso lexer.
 */
public class LexerTest {

    /**
     * Tests basic token recognition for various token types.
     */
    @Test
    public void testBasicTokenRecognition() {
        String input = "var x: i32 = 42;";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Integer> expectedTokenTypes = List.of(
            ResoLexer.VAR,           // var
            ResoLexer.Identifier,    // x
            ResoLexer.COLON,         // :
            ResoLexer.I32,         // i32
            ResoLexer.ASSIGN,        // =
            ResoLexer.IntegerLiteral, // 42
            ResoLexer.SEMI           // ;
        );

        // Get all tokens
        List<Token> tokens = getAllTokens(lexer);

        // Check token count (excluding EOF)
        assertEquals(expectedTokenTypes.size(), tokens.size() - 1,
            "Token count doesn't match expected count");

        // Check each token type
        for (int i = 0; i < expectedTokenTypes.size(); i++) {
            assertEquals(expectedTokenTypes.get(i), tokens.get(i).getType(),
                "Token at position " + i + " has incorrect type");
        }
    }

    /**
     * Tests keyword recognition.
     */
    @Test
    public void testKeywordRecognition() {
        String input = "if while for return def resource const var";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Integer> expectedTokenTypes = List.of(
            ResoLexer.IF,       // if
            ResoLexer.WHILE,    // while
            ResoLexer.FOR,      // for
            ResoLexer.RETURN,   // return
            ResoLexer.DEF,      // def
            ResoLexer.RESOURCE, // resource
            ResoLexer.CONST,    // const
            ResoLexer.VAR       // var
        );

        // Get all tokens
        List<Token> tokens = getAllTokens(lexer);

        // Check each token type
        for (int i = 0; i < expectedTokenTypes.size(); i++) {
            assertEquals(expectedTokenTypes.get(i), tokens.get(i).getType(),
                "Token at position " + i + " has incorrect type");
        }
    }

    /**
     * Tests operator recognition.
     */
    @Test
    public void testOperatorRecognition() {
        String input = "+ - * div rem mod = == != > < >= <= += -= *= div= rem= mod= & | ^ ~ << >>";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Integer> expectedTokenTypes = List.of(
            ResoLexer.PLUS,         // +
            ResoLexer.MINUS,        // -
            ResoLexer.MULT,         // *
            ResoLexer.DIV,          // div
            ResoLexer.REM,          // rem
            ResoLexer.MOD,          // mod
            ResoLexer.ASSIGN,       // =
            ResoLexer.EQUALS_OP,    // ==
            ResoLexer.NOT_EQUALS,   // !=
            ResoLexer.GT,           // >
            ResoLexer.LT,           // <
            ResoLexer.GTE,          // >=
            ResoLexer.LTE,          // <=
            ResoLexer.PLUS_ASSIGN,  // +=
            ResoLexer.MINUS_ASSIGN, // -=
            ResoLexer.MULT_ASSIGN,  // *=
            ResoLexer.DIV_ASSIGN,   // div=
            ResoLexer.REM_ASSIGN,   // rem=
            ResoLexer.MOD_ASSIGN,   // mod=
            ResoLexer.AND_OP,       // &
            ResoLexer.OR_OP,        // |
            ResoLexer.XOR_OP,       // ^
            ResoLexer.NOT_OP,       // ~
            ResoLexer.LSHIFT,       // <<
            ResoLexer.RSHIFT        // >>
        );

        // Get all tokens
        List<Token> tokens = getAllTokens(lexer);

        // Check each token type
        for (int i = 0; i < expectedTokenTypes.size(); i++) {
            assertEquals(expectedTokenTypes.get(i), tokens.get(i).getType(),
                "Token at position " + i + " has incorrect type");
        }
    }

    /**
     * Tests literal recognition.
     */
    @Test
    public void testLiteralRecognition() {
        String input = "42 3.14 \"hello\" 'a' true false null";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Integer> expectedTokenTypes = List.of(
            ResoLexer.IntegerLiteral,      // 42
            ResoLexer.FloatingPointLiteral, // 3.14
            ResoLexer.StringLiteral,       // "hello"
            ResoLexer.CharacterLiteral,    // 'a'
            ResoLexer.BooleanLiteral,      // true
            ResoLexer.BooleanLiteral,      // false
            ResoLexer.NullLiteral          // null
        );

        // Get all tokens
        List<Token> tokens = getAllTokens(lexer);

        // Check each token type
        for (int i = 0; i < expectedTokenTypes.size(); i++) {
            assertEquals(expectedTokenTypes.get(i), tokens.get(i).getType(),
                "Token at position " + i + " has incorrect type");
        }
    }

    /**
     * Tests type recognition.
     */
    @Test
    public void testTypeRecognition() {
        String input = "i8 i16 i32 i64 f32 f64 bool char String";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Integer> expectedTokenTypes = List.of(
            ResoLexer.I8,       // i8
            ResoLexer.I16,      // i16
            ResoLexer.I32,      // i32
            ResoLexer.I64,      // i64
            ResoLexer.F32,    // f32
            ResoLexer.F64,    // f64
            ResoLexer.BOOL_TYPE,  // bool
            ResoLexer.CHAR_TYPE   // char
        );

        // Get all tokens
        List<Token> tokens = getAllTokens(lexer);

        // Check each token type
        for (int i = 0; i < expectedTokenTypes.size(); i++) {
            assertEquals(expectedTokenTypes.get(i), tokens.get(i).getType(),
                "Token at position " + i + " has incorrect type");
        }
    }

    /**
     * Tests comment recognition.
     */
    @Test
    public void testCommentRecognition() {
        String input = "var x = 42; # This is a comment\nvar y = 43;";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Integer> expectedTokenTypes = List.of(
            ResoLexer.VAR,            // var
            ResoLexer.Identifier,     // x
            ResoLexer.ASSIGN,         // =
            ResoLexer.IntegerLiteral, // 42
            ResoLexer.SEMI,           // ;
            ResoLexer.NEWLINE,        // \n
            ResoLexer.VAR,            // var
            ResoLexer.Identifier,     // y
            ResoLexer.ASSIGN,         // =
            ResoLexer.IntegerLiteral, // 43
            ResoLexer.SEMI            // ;
        );

        // Get all tokens
        List<Token> tokens = getAllTokens(lexer);

        // Check token count (excluding EOF)
        assertEquals(expectedTokenTypes.size(), tokens.size() - 1,
            "Token count doesn't match expected count");

        // Check each token type
        for (int i = 0; i < expectedTokenTypes.size(); i++) {
            assertEquals(expectedTokenTypes.get(i), tokens.get(i).getType(),
                "Token at position " + i + " has incorrect type");
        }
    }

    /**
     * Tests Unicode character literal recognition in lexer.
     */
    @Test
    public void testUnicodeCharacterLiteralRecognition() {
        String input = "'\\u{41}' '\\u{1F600}' '\\u{20AC}'";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Integer> expectedTokenTypes = List.of(
            ResoLexer.CharacterLiteral, // '\\u{41}' (A)
            ResoLexer.CharacterLiteral, // '\\u{1F600}' (😀)
            ResoLexer.CharacterLiteral  // '\\u{20AC}' (€)
        );

        List<Token> tokens = getAllTokens(lexer);

        assertEquals(expectedTokenTypes.size(), tokens.size() - 1,
            "Token count doesn't match expected count");

        for (int i = 0; i < expectedTokenTypes.size(); i++) {
            assertEquals(expectedTokenTypes.get(i), tokens.get(i).getType(),
                "Token at position " + i + " has incorrect type");
        }
    }

    /**
     * Tests various Unicode escape sequence formats in character literals.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "'\\u{0}'",         // 1 hex digit
        "'\\u{20}'",        // 2 hex digits
        "'\\u{391}'",       // 3 hex digits
        "'\\u{20AC}'",      // 4 hex digits
        "'\\u{1F600}'",     // 5 hex digits
        "'\\u{10FFFF}'"     // 6 hex digits (max Unicode)
    })
    public void testUnicodeEscapeLengthVariations(String input) {
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        Token token = lexer.nextToken();
        assertEquals(ResoLexer.CharacterLiteral, token.getType(),
            "Unicode character literal should be recognized: " + input);
        assertEquals(input, token.getText(),
            "Token text should match input exactly");
    }

    /**
     * Tests Unicode string literal recognition in lexer.
     */
    @Test
    public void testUnicodeStringLiteralRecognition() {
        String input = "\"Hello \\u{1F44B} World \\u{2764}!\"";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        Token token = lexer.nextToken();
        assertEquals(ResoLexer.StringLiteral, token.getType(),
            "Unicode string literal should be recognized");
        assertEquals(input, token.getText(),
            "Token text should include Unicode escapes");
    }

    /**
     * Tests mixed character literals with standard and Unicode escapes.
     */
    @Test
    public void testMixedEscapeSequences() {
        String input = "'\\n' '\\u{A}' '\\t' '\\u{1F600}' '\\\\'";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Integer> expectedTokenTypes = List.of(
            ResoLexer.CharacterLiteral, // '\n'
            ResoLexer.CharacterLiteral, // '\\u{A}' (newline via Unicode)
            ResoLexer.CharacterLiteral, // '\t'
            ResoLexer.CharacterLiteral, // '\\u{1F600}' (😀)
            ResoLexer.CharacterLiteral  // '\\'
        );

        List<Token> tokens = getAllTokens(lexer);

        assertEquals(expectedTokenTypes.size(), tokens.size() - 1,
            "Token count doesn't match expected count");

        for (int i = 0; i < expectedTokenTypes.size(); i++) {
            assertEquals(expectedTokenTypes.get(i), tokens.get(i).getType(),
                "Token at position " + i + " should be character literal");
        }
    }

    /**
     * Tests high Unicode code points that require more than 16 bits.
     */
    @Test
    public void testHighUnicodeCodePoints() {
        String input = "'\\u{10000}' '\\u{1F4A9}' '\\u{2000B}' '\\u{10FFFF}'";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Token> tokens = getAllTokens(lexer);

        // Should have 4 character literal tokens plus EOF
        assertEquals(5, tokens.size(), "Should have 4 character literals plus EOF");

        for (int i = 0; i < 4; i++) {
            assertEquals(ResoLexer.CharacterLiteral, tokens.get(i).getType(),
                "High Unicode character literal should be recognized at position " + i);
        }

        assertEquals(ResoLexer.EOF, tokens.get(4).getType(),
            "Last token should be EOF");
    }

    /**
     * Tests Unicode escapes in string literals within larger expressions.
     */
    @Test
    public void testUnicodeInComplexExpression() {
        String input =
            "var emoji: char = '\\u{1F600}'; var message: String = \"Hello \\u{1F44B}!\";";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Integer> expectedTokenTypes = List.of(
            ResoLexer.VAR,              // var
            ResoLexer.Identifier,       // emoji
            ResoLexer.COLON,            // :
            ResoLexer.CHAR_TYPE,        // char
            ResoLexer.ASSIGN,           // =
            ResoLexer.CharacterLiteral, // '\\u{1F600}'
            ResoLexer.SEMI,             // ;
            ResoLexer.VAR,              // var
            ResoLexer.Identifier,       // message
            ResoLexer.COLON,            // :
            ResoLexer.Identifier,       // String
            ResoLexer.ASSIGN,           // =
            ResoLexer.StringLiteral,    // "Hello \\u{1F44B}!"
            ResoLexer.SEMI              // ;
        );

        List<Token> tokens = getAllTokens(lexer);

        assertEquals(expectedTokenTypes.size(), tokens.size() - 1,
            "Token count doesn't match expected count for complex expression");

        for (int i = 0; i < expectedTokenTypes.size(); i++) {
            assertEquals(expectedTokenTypes.get(i), tokens.get(i).getType(),
                "Token at position " + i + " has incorrect type in complex expression");
        }
    }

    /**
     * Tests edge case Unicode escapes.
     */
    @Test
    public void testUnicodeEdgeCases() {
        String input = "'\\u{0}' '\\u{7F}' '\\u{80}' '\\u{FFFF}' '\\u{10000}'";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Token> tokens = getAllTokens(lexer);

        // Should have 5 character literal tokens plus EOF
        assertEquals(6, tokens.size(), "Should have 5 character literals plus EOF");

        String[] expectedTexts = {
            "'\\u{0}'",      // Null character
            "'\\u{7F}'",     // DEL character
            "'\\u{80}'",     // First extended ASCII
            "'\\u{FFFF}'",   // Last 16-bit character
            "'\\u{10000}'"   // First 17-bit character
        };

        for (int i = 0; i < 5; i++) {
            assertEquals(ResoLexer.CharacterLiteral, tokens.get(i).getType(),
                "Edge case Unicode character should be recognized at position " + i);
            assertEquals(expectedTexts[i], tokens.get(i).getText(),
                "Token text should match expected at position " + i);
        }
    }

    /**
     * Tests mixed content with Unicode characters and regular code.
     */
    @Test
    public void testMixedUnicodeAndRegularCode() {
        String input = """
            def print_emoji() {
                var smiley: char = '\\u{1F600}';
                var heart: char = '\\u{2764}';
                var message: String = "I \\u{2764} Reso!";
                return message;
            }
            """;

        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));
        List<Token> tokens = getAllTokens(lexer);

        // Count specific token types
        long charLiterals = tokens.stream()
            .filter(t -> t.getType() == ResoLexer.CharacterLiteral)
            .count();
        long stringLiterals = tokens.stream()
            .filter(t -> t.getType() == ResoLexer.StringLiteral)
            .count();

        assertEquals(2, charLiterals, "Should have 2 character literals with Unicode");
        assertEquals(1, stringLiterals, "Should have 1 string literal with Unicode");

        // Verify no tokenization errors occurred
        assertTrue(tokens.stream().noneMatch(t -> t.getType() == Token.INVALID_TYPE),
            "No tokens should be invalid");
    }

    /**
     * Tests integer literal recognition with different formats.
     */
    @Test
    public void testIntegerLiteralFormats() {
        String input = "0 42 1234567890";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Token> tokens = getAllTokens(lexer);

        // Check that all tokens are recognized as integer literals
        for (int i = 0; i < 3; i++) {
            assertEquals(ResoLexer.IntegerLiteral, tokens.get(i).getType(),
                "Token at position " + i + " should be an integer literal");
        }
    }

    /**
     * Tests floating-point literal recognition with different formats.
     */
    @Test
    public void testFloatingPointLiteralFormats() {
        String input = "0.0 3.14 .123 42.0";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Token> tokens = getAllTokens(lexer);

        // Check that all tokens are recognized as floating-point literals
        for (int i = 0; i < 4; i++) {
            assertEquals(ResoLexer.FloatingPointLiteral, tokens.get(i).getType(),
                "Token at position " + i + " should be a floating-point literal");
        }
    }

    /**
     * Tests indentation handling for the lexer.
     */
    @Test
    public void testIndentationHandling() {
        String input = "if true:\n    var x = 42\n    var y = 43\nvar z = 44";
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Integer> expectedTokenTypes = List.of(
            ResoLexer.IF,             // if
            ResoLexer.BooleanLiteral, // true
            ResoLexer.COLON,          // :
            ResoLexer.NEWLINE,        // \n
            ResoLexer.INDENT,         // indentation
            ResoLexer.VAR,            // var
            ResoLexer.Identifier,     // x
            ResoLexer.ASSIGN,         // =
            ResoLexer.IntegerLiteral, // 42
            ResoLexer.NEWLINE,        // \n
            ResoLexer.VAR,            // var
            ResoLexer.Identifier,     // y
            ResoLexer.ASSIGN,         // =
            ResoLexer.IntegerLiteral, // 43
            ResoLexer.NEWLINE,        // \n
            ResoLexer.DEDENT,         // dedentation
            ResoLexer.VAR,            // var
            ResoLexer.Identifier,     // z
            ResoLexer.ASSIGN,         // =
            ResoLexer.IntegerLiteral  // 44
        );

        // Get all tokens
        List<Token> tokens = getAllTokens(lexer);

        // Check token count (excluding EOF)
        assertEquals(expectedTokenTypes.size(), tokens.size() - 1,
            "Token count doesn't match expected count");

        // Check each token type
        for (int i = 0; i < expectedTokenTypes.size(); i++) {
            assertEquals(expectedTokenTypes.get(i), tokens.get(i).getType(),
                "Token at position " + i + " has incorrect type");
        }
    }

    /**
     * Tests complex indentation patterns with mixed blocks.
     */
    @Test
    public void testComplexIndentation() {
        String input = """
            if true:
                if false:
                    var x = 1
                else:
                    var y = 2
            var z = 3""";

        ResoLexer lexer = new ResoLexer(CharStreams.fromString(input));

        List<Token> tokens = getAllTokens(lexer);

        int indentCount = 0;
        int dedentCount = 0;

        for (Token token : tokens) {
            if (token.getType() == ResoLexer.INDENT) {
                indentCount++;
            } else if (token.getType() == ResoLexer.DEDENT) {
                dedentCount++;
            }
        }

        assertEquals(3, indentCount, "Should have 3 INDENT tokens");
        assertEquals(3, dedentCount, "Should have 3 DEDENT tokens");
    }

    /**
     * Parameterized test for identifier recognition.
     * Valid identifiers: start with letter or underscore,
     * followed by letters, digits, or underscores.
     */
    @ParameterizedTest
    @MethodSource("validIdentifiers")
    public void testValidIdentifiers(String identifier) {
        ResoLexer lexer = new ResoLexer(CharStreams.fromString(identifier));
        Token token = lexer.nextToken();

        assertEquals(ResoLexer.Identifier, token.getType(),
            "Token should be recognized as an identifier");
        assertEquals(identifier, token.getText(),
            "Token text should match the input identifier");
    }

    /**
     * Provides valid identifiers for testing.
     */
    static Stream<Arguments> validIdentifiers() {
        return Stream.of(
            Arguments.of("x"),
            Arguments.of("variableName"),
            Arguments.of("variable_name"),
            Arguments.of("_private"),
            Arguments.of("camelCase"),
            Arguments.of("snake_case"),
            Arguments.of("PascalCase"),
            Arguments.of("name123"),
            Arguments.of("_123"),
            Arguments.of("very_long_variable_name_with_underscores_and_numbers_123")
        );
    }

    /**
     * Helper method to get all tokens from a lexer.
     */
    private List<Token> getAllTokens(ResoLexer lexer) {
        List<Token> tokens = new ArrayList<>();
        Token token;
        do {
            token = lexer.nextToken();
            tokens.add(token);
        } while (token.getType() != ResoLexer.EOF);
        return tokens;
    }
}
