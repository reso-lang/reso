parser grammar ResoParser;

options {
    tokenVocab = ResoLexer;
}

// Top-level constructs
program
    : (NEWLINE | resourceDef | functionDef)* EOF
    ;

functionDef
    : visibility? DEF Identifier LPAREN parameterList? RPAREN (ARROW type)? COLON block
    ;

// Resource definition
resourceDef
    : RESOURCE Identifier LBRACE resourceFields? RBRACE (COLON NEWLINE INDENT resourceBody DEDENT)?
    ;

resourceFields
    : resourceField (COMMA resourceField)*
    ;

resourceField
    : visibility? (VAR | CONST) Identifier (COLON type)
    ;

resourceBody
    : resourcePath+
    ;

visibility
    : PUB
    ;

pass
    : PASS NEWLINE
    ;

resourcePath
    : PATH ((Identifier | resourceIndexer) (resourcePathSegment)*)? COLON NEWLINE INDENT resourceMethod+ DEDENT
    ;

resourcePathSegment
    : SLASH Identifier
    | resourceIndexer
    ;

resourceMethod
    : visibility? DEF Identifier LPAREN parameterList? RPAREN (ARROW type)? COLON block
    ;

resourceIndexer
    : LBRACK Identifier COLON type RBRACK
    ;

type
    : primitiveType
    | referenceType
    | genericType
    | tupleType
    ;

primitiveType
    : I8
    | I16
    | I32
    | I64
    | ISIZE
    | U8
    | U16
    | U32
    | U64
    | USIZE
    | F32
    | F64
    | BOOL_TYPE
    | CHAR_TYPE
    ;

numericType
    : I8
    | I16
    | I32
    | I64
    | ISIZE
    | U8
    | U16
    | U32
    | U64
    | USIZE
    | F32
    | F64
    | CHAR_TYPE
    ;

referenceType
    : Identifier
    ;

genericType
    : Identifier LT type (COMMA type)* GT
    ;

tupleType
    : LPAREN /*((type COMMA)+ type?)?*/ RPAREN
    ;

parameterTypeList
    : type (COMMA type)*
    ;

// Parameters
parameterList
    : parameter (COMMA parameter)*
    ;

parameter
    : Identifier COLON type
    ;

// Simple statements
simple_stmts
    : simple_stmt (SEMI simple_stmt)* SEMI? NEWLINE
    ;

simple_stmt
    : variableDeclaration
    | assignmentStatement
    | expressionStatement
    | returnStatement
    | breakStatement
    | continueStatement
    | pass_stmt
    ;

compound_stmt
    : ifStatement
    | whileStatement
    ;

// Statements
statement
    : simple_stmts
    | compound_stmt
    ;

variableDeclaration
    : (VAR | CONST) Identifier (COLON type)? (ASSIGN expression)?
    ;

assignmentStatement
    : expression (
        ASSIGN
        | PLUS_ASSIGN
        | MINUS_ASSIGN
        | MULT_ASSIGN
        | DIV_ASSIGN
        | REM_ASSIGN
        | MOD_ASSIGN
        | AND_ASSIGN
        | OR_ASSIGN
        | XOR_ASSIGN
        | LSHIFT_ASSIGN
        | RSHIFT_ASSIGN
      ) expression
    ;

expressionStatement
    : expression
    ;

returnStatement
    : RETURN expression?
    ;

breakStatement
    : BREAK
    ;

continueStatement
    : CONTINUE
    ;

pass_stmt
    : PASS
    ;

block
    : simple_stmts
    | NEWLINE INDENT statement+ DEDENT
    ;

// Control flow statements
ifStatement
    : IF expression COLON block
      (ELSE IF expression COLON block)*
      (ELSE COLON block)?
    ;

whileStatement
    : WHILE expression COLON block
    ;

// Expressions
expression
    : primary                                                                   #primaryExpr
    | expression pathAccess? DOT Identifier LPAREN expressionList? RPAREN       #methodCallExpr
    | expression DOT Identifier                                                 #fieldAccessExpr
    | Identifier LPAREN expressionList? RPAREN                                  #functionCallExpr
    | Identifier LBRACE expressionList? RBRACE                                  #resourceInitializerExpr
    | expression AS numericType                                                 #typeConversionExpr
    | (PLUS | MINUS | NOT | NOT_OP) expression                                  #unaryExpr
    | expression (MULT | DIV | REM | MOD) expression                            #multiplicativeExpr
    | expression (PLUS | MINUS) expression                                      #additiveExpr
    | expression (LSHIFT | RSHIFT) expression                                   #shiftExpr
    | expression AND_OP expression                                              #bitwiseAndExpr
    | expression XOR_OP expression                                              #bitwiseXorExpr
    | expression OR_OP expression                                               #bitwiseOrExpr
    | expression (LT | GT | LTE | GTE) expression                               #relationalExpr
    | expression (EQUALS_OP | NOT_EQUALS) expression                            #equalityExpr
    | expression AND expression                                                 #logicalAndExpr
    | expression OR expression                                                  #logicalOrExpr
    | expression IF expression ELSE expression                                  #ternaryExpr
    | LPAREN /* expressionList? */ RPAREN                                       #tupleExpr
    ;

primary
    : Identifier
    | THIS
    | literal
    | LPAREN expression RPAREN
    ;

expressionList
    : expression (COMMA expression)*
    ;

pathAccess
    : (pathSegment)+
    ;

pathSegment
    : SLASH Identifier
    | LBRACK expression RBRACK
    ;

// Literals
literal
    : MINUS? IntegerLiteral
    | MINUS? FloatingPointLiteral
    | StringLiteral
    | CharacterLiteral
    | BooleanLiteral
    | NullLiteral
    ;