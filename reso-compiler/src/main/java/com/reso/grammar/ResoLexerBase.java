package com.reso.grammar;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

abstract class ResoLexerBase extends Lexer {
    // A queue where extra tokens are pushed on (see the NEWLINE lexer rule).
    private java.util.LinkedList<Token> tokens = new java.util.LinkedList<>();
    // The stack that keeps track of the indentation level.
    private Deque<Integer> indents = new ArrayDeque<>();
    // The amount of opened braces, brackets and parenthesis.
    private int opened = 0;
    // The most recently produced token.
    private Token lastToken = null;

    // Method to get the token type for specific tokens
    public int getTokenType(String tokenName) {
        try {
            Class<?> lexerClass = Class.forName("com.reso.grammar.ResoLexer");
            Field field = lexerClass.getField(tokenName);
            return field.getInt(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected ResoLexerBase(CharStream input) {
        super(input);
    }

    @Override
    public void emit(Token t) {
        super.setToken(t);
        tokens.offer(t);
    }

    @Override
    public Token nextToken() {
        // Check if the end-of-file is ahead and there are still some DEDENTS expected.
        if (_input.LA(1) == EOF && !this.indents.isEmpty()) {
            // Remove any trailing EOF tokens from our buffer.
            for (int i = tokens.size() - 1; i >= 0; i--) {
                if (tokens.get(i).getType() == EOF) {
                    tokens.remove(i);
                }
            }

            // First emit an extra line break that serves as the end of the statement.
            this.emit(commonToken(getTokenType("NEWLINE"), "\n"));

            // Now emit as much DEDENT tokens as needed.
            while (!indents.isEmpty()) {
                this.emit(createDedent());
                indents.pop();
            }

            // Put the EOF back on the token stream.
            this.emit(commonToken(EOF, "<EOF>"));
        }

        Token next = super.nextToken();

        if (next.getChannel() == Token.DEFAULT_CHANNEL) {
            // Keep track of the last token on the default channel.
            this.lastToken = next;
        }

        return tokens.isEmpty() ? next : tokens.poll();
    }

    private Token createDedent() {
        CommonToken dedent = commonToken(getTokenType("DEDENT"), "");
        dedent.setLine(this.lastToken.getLine());
        return dedent;
    }

    private CommonToken commonToken(int type, String text) {
        int stop = this.getCharIndex() - 1;
        int start = text.isEmpty() ? stop : stop - text.length() + 1;
        return new CommonToken(this._tokenFactorySourcePair, type, DEFAULT_TOKEN_CHANNEL, start,
            stop);
    }

    static int getIndentationCount(String spaces) {
        int count = 0;
        for (char ch : spaces.toCharArray()) {
            if (ch == '\t') {
                count += 8 - (count % 8);
            } else {
                // A normal space char.
                count++;
            }
        }

        return count;
    }

    boolean atStartOfInput() {
        return super.getCharPositionInLine() == 0 && super.getLine() == 1;
    }

    void openBrace() {
        this.opened++;
    }

    void closeBrace() {
        this.opened--;
    }

    void onNewLine() {
        String newLine = getText().replaceAll("[^\r\n\f]+", "");
        String spaces = getText().replaceAll("[\r\n\f]+", "");

        // Strip newlines inside open clauses except if we are near EOF.
        // We keep NEWLINEs near EOF tosatisfy the final newline needed
        // by the single_put rule used by the REPL.
        int next = _input.LA(1);
        int nextnext = _input.LA(2);
        if (opened > 0
            || (nextnext != -1 && (next == '\r' || next == '\n' || next == '\f' || next == '#'))) {
            // If we're inside a list or on a blank line, ignore all indents,
            // dedents and line breaks.
            skip();
        } else {
            emit(commonToken(getTokenType("NEWLINE"), newLine));
            int indent = getIndentationCount(spaces);
            int previous = indents.isEmpty() ? 0 : indents.peek();
            if (indent == previous) {
                // skip indents of the same size as the present indent-size
                skip();
            } else if (indent > previous) {
                indents.push(indent);
                emit(commonToken(getTokenType("INDENT"), spaces));
            } else {
                // Possibly emit more than 1 DEDENT token.
                while (!indents.isEmpty() && indents.peek() > indent) {
                    this.emit(createDedent());
                    indents.pop();
                }
            }
        }
    }

    @Override
    public void reset() {
        tokens = new java.util.LinkedList<>();
        indents = new ArrayDeque<>();
        opened = 0;
        lastToken = null;
        super.reset();
    }
}