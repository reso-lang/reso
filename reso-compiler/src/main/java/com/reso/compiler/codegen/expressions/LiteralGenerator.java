package com.reso.compiler.codegen.expressions;

import static com.reso.compiler.types.StandardTypeHandles.U8;
import static com.reso.compiler.types.StandardTypeHandles.USIZE;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.types.ResourceType;
import com.reso.compiler.values.ResoValue;
import com.reso.compiler.values.literals.FloatingPointLiteral;
import com.reso.compiler.values.literals.FloatingPointLiteralValue;
import com.reso.compiler.values.literals.IntegerLiteral;
import com.reso.compiler.values.literals.IntegerLiteralValue;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrValue;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generator for literals.
 * Handles the generation of LLVM IR code for various literal types including:
 * integers (decimal, hexadecimal, binary, octal with underscore separators),
 * floating-point numbers (with exponential notation), strings, characters,
 * booleans, and null values.
 */
public class LiteralGenerator {

    private final CodeGenerationContext context;

    public LiteralGenerator(@Nonnull CodeGenerationContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
    }

    /**
     * Generates an untyped literal value that can be concretized later.
     *
     * @param literal The literal context to process
     * @return An untyped ResoValue, or null if the literal is unsupported
     */
    @Nullable
    public ResoValue generateLiteral(@Nullable ResoParser.LiteralContext literal) {
        if (literal == null) {
            return null;
        }

        LiteralInfo info = extractLiteralInfo(literal);
        LiteralType literalType = determineLiteralType(literal);

        return switch (literalType) {
            case INTEGER -> generateIntegerLiteral(info);
            case FLOATING_POINT -> generateFloatLiteral(info);
            case STRING -> generateStringLiteral(info);
            case CHARACTER -> generateCharacterLiteral(info);
            case BOOLEAN -> generateBoolLiteral(info);
            case NULL -> generateNullLiteral(info);
            case UNSUPPORTED -> {
                reportUnsupportedLiteralError(info);
                yield null;
            }
        };
    }

    /**
     * Generates an untyped integer literal.
     */
    @Nullable
    private IntegerLiteralValue generateIntegerLiteral(@Nonnull LiteralInfo info) {
        try {
            IntegerLiteral value = parseIntegerValue(info.text());

            // Validate that the value is within the maximum representable range
            if (!value.isWithinIntLiteralRange()) {
                context.getErrorReporter().error(
                    "Integer literal " + info.text() + " is too large to represent",
                    info.line(), info.column()
                );
                return null;
            }

            return new IntegerLiteralValue(
                context.getTypeSystem().getIntegerLiteralType(),
                context.getTypeSystem().getDefaultIntType(),
                value,
                info.line(),
                info.column()
            );

        } catch (NumberFormatException e) {
            context.getErrorReporter().error(
                "Invalid integer literal: " + info.text(),
                info.line(), info.column()
            );
            return null;
        }
    }

    /**
     * Generates an untyped floating-point literal.
     */
    @Nullable
    private FloatingPointLiteralValue generateFloatLiteral(@Nonnull LiteralInfo info) {
        try {
            FloatingPointLiteral value = parseFloatValue(info);

            return new FloatingPointLiteralValue(
                context.getTypeSystem().getFloatingPointLiteralType(),
                context.getTypeSystem().getDefaultFloatType(),
                value,
                info.line(),
                info.column()
            );

        } catch (NumberFormatException e) {
            context.getErrorReporter().error(
                "Invalid floating-point literal: " + info.text(),
                info.line(), info.column()
            );
            return null;
        }
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Extracts information from a literal context.
     */
    @Nonnull
    private LiteralInfo extractLiteralInfo(@Nonnull ResoParser.LiteralContext literal) {
        int line = literal.getStart().getLine();
        int column = literal.getStart().getCharPositionInLine();
        String text = extractLiteralText(literal);
        return new LiteralInfo(text, line, column);
    }

    /**
     * Determines the type of literal from the context.
     */
    @Nonnull
    private LiteralType determineLiteralType(@Nonnull ResoParser.LiteralContext literal) {
        if (literal.IntegerLiteral() != null) {
            return LiteralType.INTEGER;
        }
        if (literal.FloatingPointLiteral() != null) {
            return LiteralType.FLOATING_POINT;
        }
        if (literal.StringLiteral() != null) {
            return LiteralType.STRING;
        }
        if (literal.CharacterLiteral() != null) {
            return LiteralType.CHARACTER;
        }
        if (literal.BooleanLiteral() != null) {
            return LiteralType.BOOLEAN;
        }
        if (literal.NullLiteral() != null) {
            return LiteralType.NULL;
        }
        return LiteralType.UNSUPPORTED;
    }

    /**
     * Extracts the text content from a literal context.
     */
    @Nonnull
    private String extractLiteralText(@Nonnull ResoParser.LiteralContext literal) {
        String minus = literal.MINUS() != null ? literal.MINUS().getText() : "";
        return switch (determineLiteralType(literal)) {
            case INTEGER -> minus + literal.IntegerLiteral().getText();
            case FLOATING_POINT -> minus + literal.FloatingPointLiteral().getText();
            case STRING -> literal.StringLiteral().getText();
            case CHARACTER -> literal.CharacterLiteral().getText();
            case BOOLEAN -> literal.BooleanLiteral().getText();
            case NULL -> "null";
            case UNSUPPORTED -> "";
        };
    }

    /**
     * Parses an integer value with support for different number bases.
     */

    @Nonnull
    private IntegerLiteral parseIntegerValue(@Nonnull String text) throws NumberFormatException {
        // Remove underscores
        String cleanText = text.replace("_", "");

        // Check for negative sign and handle it
        boolean isNegative = cleanText.startsWith("-");
        if (isNegative) {
            cleanText = cleanText.substring(1);
        }

        BigInteger value;
        if (cleanText.startsWith("0x") || cleanText.startsWith("0X")) {
            // Hexadecimal
            value = new BigInteger(cleanText.substring(2), 16);
        } else if (cleanText.startsWith("0b") || cleanText.startsWith("0B")) {
            // Binary
            value = new BigInteger(cleanText.substring(2), 2);
        } else if (cleanText.startsWith("0o") || cleanText.startsWith("0O")) {
            // Octal
            value = new BigInteger(cleanText.substring(2), 8);
        } else {
            // Decimal
            value = new BigInteger(cleanText);
        }

        // Apply negative sign if needed
        if (isNegative) {
            value = value.negate();
        }

        return IntegerLiteral.fromBigInteger(value, context.getIrModule());
    }

    /**
     * Parses a floating-point value with support for exponential notation and underscores.
     * Supports formats like: 3.14, 1e6, 2.5e-4, 1.23E+2, 6.022e23
     */
    private FloatingPointLiteral parseFloatValue(@Nonnull LiteralInfo info)
        throws NumberFormatException {
        String text = info.text();

        // Remove underscores for easier parsing
        String cleanText = text.replaceAll("_", "");
        return FloatingPointLiteral.fromDouble(Double.parseDouble(cleanText));
    }

    /**
     * Handles a string literal by processing escape sequences and creating the IR value.
     */
    @Nullable
    public ResoValue generateStringLiteral(@Nonnull LiteralInfo info) {
        String processedText = processStringLiteralText(info);

        // Validate UTF-8
        if (!isValidUtf8(processedText)) {
            context.getErrorReporter()
                .error("Invalid UTF-8 in string literal", info.line, info.column);
            return null;
        }

        ResourceType stringType = context.getTypeSystem().getResourceType("String");

        if (stringType == null) {
            context.getErrorReporter().error(
                "String type is not defined in the type system",
                info.line(), info.column()
            );
            return null;
        }

        // Get or create cached global string constant
        IrValue globalString = context.getOrCreateGlobalString(processedText);

        // Create String instance with the UTF-8 data
        return createStringInstance(stringType, globalString,
            processedText.getBytes(StandardCharsets.UTF_8).length, info.line, info.column);
    }

    /**
     * Processes string literal text by removing quotes and handling escape sequences.
     */
    private String processStringLiteralText(@Nonnull LiteralInfo info) {
        return processStringEscapes(info);
    }

    /**
     * Validates that the string contains valid UTF-8.
     */
    private boolean isValidUtf8(@Nonnull String text) {
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            String reconstructed = new String(bytes, StandardCharsets.UTF_8);
            return text.equals(reconstructed);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates a String instance using the global string.
     */
    @Nullable
    private ResoValue createStringInstance(@Nonnull ResourceType stringType,
                                           @Nonnull IrValue globalString,
                                           int byteLength, int line, int column) {
        try {
            IrBuilder irBuilder = context.getIrBuilder();

            // Allocate String struct
            IrValue stringStruct = IrFactory.createGCMalloc(
                irBuilder,
                context.getIrModule(),
                stringType.getStructType(),
                IrFactory.declareGCMalloc(context.getIrModule()),
                "string_instance"
            );

            // Create vector using the global string
            ResoType uint8Type = context.getTypeSystem().getType(U8);

            // Create Vector<u8> that points to the global string data
            IrValue vectorInstance =
                createVectorWithGlobalString(uint8Type, globalString, byteLength, line, column);
            if (vectorInstance == null) {
                return null;
            }

            // Store vector in String struct (field 0: data)
            IrValue dataField = IrFactory.createStructGEP(
                irBuilder,
                stringType.getStructType(),
                stringStruct,
                0,
                "data_field"
            );

            IrFactory.createStore(irBuilder, vectorInstance, dataField);

            return new ResoValue(stringType, stringStruct, line, column);

        } catch (Exception e) {
            context.getErrorReporter()
                .error("Failed to create string instance: " + e.getMessage(), line, column);
            return null;
        }
    }

    /**
     * Creates a Vector<u8> that points to the global string data.
     */
    @Nullable
    private IrValue createVectorWithGlobalString(@Nonnull ResoType uint8Type,
                                                 @Nonnull IrValue globalString,
                                                 int byteLength, int line, int column) {
        try {
            // Get or create Vector<u8> type
            var vectorType = context.getTypeSystem().getOrCreateVectorType(uint8Type);
            IrBuilder irBuilder = context.getIrBuilder();

            // Allocate vector struct
            IrValue vectorStruct = IrFactory.createGCMalloc(
                irBuilder,
                context.getIrModule(),
                vectorType.getStructType(),
                IrFactory.declareGCMalloc(context.getIrModule()),
                "byte_vector"
            );

            ResoType usizeType = context.getTypeSystem().getType(USIZE);

            // Initialize vector struct fields
            // Field 0: elements pointer (points directly to global string)
            IrValue elementsField = IrFactory.createStructGEP(
                irBuilder,
                vectorType.getStructType(),
                vectorStruct,
                0,
                "elements_field"
            );

            IrFactory.createStore(irBuilder, globalString, elementsField);

            // Field 1: size
            IrValue sizeField = IrFactory.createStructGEP(
                irBuilder,
                vectorType.getStructType(),
                vectorStruct,
                1,
                "size_field"
            );
            IrValue sizeValue =
                IrFactory.createConstantInt(usizeType.getType(), byteLength + 1, false);
            IrFactory.createStore(irBuilder, sizeValue, sizeField);

            // Field 2: capacity (same as size for immutable strings)
            IrValue capacityField = IrFactory.createStructGEP(
                irBuilder,
                vectorType.getStructType(),
                vectorStruct,
                2,
                "capacity_field"
            );
            IrValue capacityValue =
                IrFactory.createConstantInt(usizeType.getType(), byteLength + 1, false);
            IrFactory.createStore(irBuilder, capacityValue, capacityField);

            return vectorStruct;

        } catch (Exception e) {
            context.getErrorReporter()
                .error("Failed to create byte vector: " + e.getMessage(), line, column);
            return null;
        }
    }

    /**
     * Handles a character literal by processing escape sequences and creating the IR value.
     */
    @Nonnull
    private ResoValue generateCharacterLiteral(@Nonnull LiteralInfo info) {
        int charValue = processCharacterLiteralText(info);
        ResoType charType = context.getTypeSystem().getCharType();

        IrValue llvmValue = IrFactory.createConstantInt(charType.getType(), charValue, false);
        return new ResoValue(charType, llvmValue, info.line, info.column);
    }

    /**
     * Processes character literal text by removing quotes and handling escape sequences.
     */
    private int processCharacterLiteralText(@Nonnull LiteralInfo info) {
        return processCharEscape(info);
    }

    /**
     * Handles a boolean literal by parsing the text and creating the IR value.
     */
    @Nonnull
    private ResoValue generateBoolLiteral(@Nonnull LiteralInfo info) {
        boolean value = Boolean.parseBoolean(info.text);
        ResoType boolType = context.getTypeSystem().getBoolType();

        IrValue llvmValue = IrFactory.createConstantInt(boolType.getType(), value ? 1 : 0, false);
        return new ResoValue(boolType, llvmValue, info.line, info.column);
    }

    /**
     * Handles a null literal by creating the appropriate IR value.
     */
    @Nonnull
    private ResoValue generateNullLiteral(@Nonnull LiteralInfo info) {
        ResoType nullType = context.getTypeSystem().getNullType();
        IrValue llvmValue = IrFactory.createConstantNull(nullType.getType());
        return new ResoValue(nullType, llvmValue, info.line, info.column);
    }

    /**
     * Removes surrounding quotes from a string.
     */
    private String removeQuotes(String text) {
        if (text.length() >= 2
            && ((text.startsWith("\"") && text.endsWith("\""))
            || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    /**
     * Processes escape sequences in a string using a more readable approach.
     */
    @Nonnull
    private String processStringEscapes(@Nonnull LiteralInfo info) {
        String withoutQuotes = removeQuotes(info.text);
        StringBuilder result = new StringBuilder(withoutQuotes.length());

        for (int i = 0; i < withoutQuotes.length(); i++) {
            char current = withoutQuotes.charAt(i);

            if (current == '\\' && i + 1 < withoutQuotes.length()) {
                if (withoutQuotes.charAt(i + 1) == 'u' && i + 2 < withoutQuotes.length()
                    && withoutQuotes.charAt(i + 2) == '{') {
                    // Unicode escape sequence
                    int endIndex = withoutQuotes.indexOf('}', i + 3);
                    if (endIndex != -1) {
                        String hexCode = withoutQuotes.substring(i + 3, endIndex);
                        try {
                            int codePoint = Integer.parseInt(hexCode, 16);
                            if (Character.isValidCodePoint(codePoint)) {
                                result.appendCodePoint(codePoint);
                                i = endIndex; // Skip to after the closing brace
                                continue;
                            }
                        } catch (NumberFormatException e) {
                            context.getErrorReporter().error(
                                "Invalid Unicode escape sequence: " + hexCode,
                                info.line(), info.column()
                            );
                        }
                    }
                    // If Unicode parsing fails, treat as normal characters
                    result.append(current);
                } else {
                    // Standard escape sequence
                    char next = withoutQuotes.charAt(i + 1);
                    char escaped = mapEscapeCharacter(next);
                    result.append(escaped);
                    i++; // Skip the next character as it's part of the escape sequence
                }
            } else {
                result.append(current);
            }
        }

        return result.toString();
    }

    /**
     * Maps escape sequence characters to their actual values.
     */
    private char mapEscapeCharacter(char escapeChar) {
        return switch (escapeChar) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case '\'' -> '\'';
            case '\"' -> '\"';
            case '\\' -> '\\';
            default -> escapeChar; // Return the character as-is for unknown escapes
        };
    }

    /**
     * Processes escape sequences in a character literal.
     * Returns an int to support full Unicode range (0 to 0x10FFFF).
     */
    private int processCharEscape(@Nonnull LiteralInfo info) {
        String withoutQuotes = removeQuotes(info.text);

        if (withoutQuotes.length() == 1) {
            return withoutQuotes.charAt(0);
        }

        if (withoutQuotes.length() >= 2 && withoutQuotes.charAt(0) == '\\') {
            if (withoutQuotes.charAt(1) == 'u' && withoutQuotes.length() > 3
                && withoutQuotes.charAt(2) == '{') {
                // Unicode escape sequence
                int endIndex = withoutQuotes.indexOf('}');
                if (endIndex != -1) {
                    String hexCode = withoutQuotes.substring(3, endIndex);
                    try {
                        int codePoint = Integer.parseInt(hexCode, 16);
                        if (Character.isValidCodePoint(codePoint)) {
                            return codePoint;
                        } else {
                            context.getErrorReporter().error(
                                "Invalid Unicode code point: " + hexCode,
                                info.line(), info.column()
                            );
                        }
                    } catch (NumberFormatException e) {
                        context.getErrorReporter().error(
                            "Invalid Unicode escape sequence: " + hexCode,
                            info.line(), info.column()
                        );
                    }
                }
                // Invalid Unicode escape, return replacement character
                return 0xFFFD; // Unicode replacement character
            } else {
                // Standard escape sequence
                return mapEscapeCharacter(withoutQuotes.charAt(1));
            }
        }

        // Fallback for malformed character literals
        return withoutQuotes.charAt(0);
    }

    /**
     * Reports an error for unsupported literal types.
     */
    private void reportUnsupportedLiteralError(LiteralInfo info) {
        context.getErrorReporter().error("Unsupported literal type", info.line(), info.column());
    }

    /**
     * Enumeration of literal types.
     */
    private enum LiteralType {
        INTEGER,
        FLOATING_POINT,
        STRING,
        CHARACTER,
        BOOLEAN,
        NULL,
        UNSUPPORTED
    }

    /**
     * Record to hold literal information.
     */
    public record LiteralInfo(String text, int line, int column) {
    }
}