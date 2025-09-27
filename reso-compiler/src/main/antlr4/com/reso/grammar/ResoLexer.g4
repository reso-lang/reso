lexer grammar ResoLexer;

// Import base lexer class
options {
    superClass = ResoLexerBase;
}

tokens {
    INDENT,
    DEDENT
}

// Keywords
RESOURCE    : 'resource';
PATH        : 'path';
DEF         : 'def';
PASS        : 'pass';
VAR         : 'var';
IF          : 'if';
ELSE        : 'else';
FOR         : 'for';
WHILE       : 'while';
RETURN      : 'return';
TRY         : 'try';
CATCH       : 'catch';
FINALLY     : 'finally';
THROW       : 'throw';
THIS        : 'this';
IN          : 'in';
IS          : 'is';
BREAK       : 'break';
CONTINUE    : 'continue';
AND         : 'and';
OR          : 'or';
NOT         : 'not';
CONST       : 'const';
ENUM        : 'enum';
MATCH       : 'match';
CASE        : 'case';
DEFAULT     : 'default';
AS          : 'as';
LINK        : 'link';
IMPORT      : 'import';
PUB         : 'pub';
RECORD      : 'record';
ASYNC       : 'async';
AWAIT       : 'await';

// Types
I8        : 'i8';
I16       : 'i16';
I32       : 'i32';
I64       : 'i64';
ISIZE     : 'isize';

U8       : 'u8';
U16      : 'u16';
U32      : 'u32';
U64      : 'u64';
USIZE    : 'usize';

F32     : 'f32';
F64     : 'f64';

BOOL_TYPE   : 'bool';

CHAR_TYPE   : 'char';

// Operators
PLUS        : '+';
MINUS       : '-';
MULT        : '*';
DIV         : 'div';
REM         : 'rem';
MOD         : 'mod';
ASSIGN      : '=';
EQUALS_OP   : '==';
NOT_EQUALS  : '!=';
GT          : '>';
LT          : '<';
GTE         : '>=';
LTE         : '<=';
PLUS_ASSIGN : '+=';
MINUS_ASSIGN: '-=';
MULT_ASSIGN : '*=';
DIV_ASSIGN  : 'div=';
REM_ASSIGN  : 'rem=';
MOD_ASSIGN  : 'mod=';
AND_OP      : '&';
OR_OP       : '|';
XOR_OP      : '^';
NOT_OP      : '~';
AND_ASSIGN  : '&=';
OR_ASSIGN   : '|=';
XOR_ASSIGN  : '^=';
LSHIFT      : '<<';
RSHIFT      : '>>';
LSHIFT_ASSIGN: '<<=';
RSHIFT_ASSIGN: '>>=';

// Separators
SLASH       : '/';
DOT         : '.';
COMMA       : ',';
COLON       : ':';
SEMI        : ';';
LPAREN      : '(' {openBrace();};
RPAREN      : ')' {closeBrace();};
LBRACE      : '{' {openBrace();};
RBRACE      : '}' {closeBrace();};
LBRACK      : '[' {openBrace();};
RBRACK      : ']' {closeBrace();};
ARROW       : '->';
QUESTION    : '?';
AT          : '@';

// Integer Literals

IntegerLiteral:
    DecimalIntegerLiteral
    | HexadecimalIntegerLiteral
    | BinaryIntegerLiteral
    | OctalIntegerLiteral;

// Decimal integers

fragment DecimalIntegerLiteral:
    '0'
    | NonZeroDigit (DecDigitOrUnderscore)*;

fragment DecDigitOrUnderscore: DecDigit | '_';
fragment DecDigit: [0-9];
fragment NonZeroDigit: [1-9];

// Hexadecimal integers

fragment HexadecimalIntegerLiteral:
    '0' [xX] HexDigit HexDigitOrUnderscore*;

fragment HexDigitOrUnderscore: HexDigit | '_';
fragment HexDigit: [0-9a-fA-F];

// Binary integers

fragment BinaryIntegerLiteral:
    '0' [bB] BinDigit BinDigitOrUnderscore*;

fragment BinDigitOrUnderscore: BinDigit | '_';
fragment BinDigit: [01];

// Octal integers

fragment OctalIntegerLiteral:
    '0' [oO] OctDigit OctDigitOrUnderscore*;

fragment OctDigitOrUnderscore: OctDigit | '_';
fragment OctDigit: [0-7];

// Floating-Point Literals

FloatingPointLiteral:
    DecimalFloatingPointLiteral;

fragment DecimalFloatingPointLiteral:
    Digits '.' Digits? ExponentPart?
    | '.' Digits ExponentPart?
    | Digits ExponentPart;

fragment ExponentPart: [eE] [+-]? Digits;

fragment Digits: DecDigit (DecDigitOrUnderscore)*;

// boolean Literals

BooleanLiteral: 'true' | 'false';

// Character Literals

CharacterLiteral: '\'' SingleCharacter '\'' | '\'' EscapeSequence '\'';

fragment SingleCharacter: ~['\\\r\n];

// String Literals

StringLiteral: '"' StringCharacters? '"';

fragment StringCharacters: StringCharacter+;

fragment StringCharacter: ~["\\\r\n] | EscapeSequence;

// Escape Sequences for Character and String Literals

fragment EscapeSequence:
    '\\' [btnfr"'\\]           // Standard escapes
    | UnicodeEscape;           // Unicode escapes

fragment UnicodeEscape:
    '\\u{' HexDigit HexDigit? HexDigit? HexDigit? HexDigit? HexDigit? '}';

// The Null Literal

NullLiteral: 'null';

// Identifiers
Identifier      : [a-zA-Z_][a-zA-Z0-9_]*;

// Whitespace and comments
SKIP_: ( SPACES | COMMENT | LINE_JOINING) -> skip;
NEWLINE: ({atStartOfInput()}? SPACES | ( '\r'? '\n' | '\r' | '\f') SPACES?) {onNewLine();};

// Fragments
fragment SPACES    : [ \t]+;
fragment LINE_JOINING: '\\' SPACES? ( '\r'? '\n' | '\r' | '\f');
fragment COMMENT     : '#' ~[\r\n\f]*;