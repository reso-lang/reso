
package com.reso.compiler.values.literals;

import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrModule;
import java.math.BigInteger;
import javax.annotation.Nonnull;

/**
 * Represents an integer literal value.
 * Provides safe conversion between BigInteger and long values.
 */
public final class IntegerLiteral {
    private static final BigInteger I8_MIN = BigInteger.valueOf(-128L);
    private static final BigInteger I8_MAX = BigInteger.valueOf(127L);
    private static final BigInteger I16_MIN = BigInteger.valueOf(-32768L);
    private static final BigInteger I16_MAX = BigInteger.valueOf(32767L);
    private static final BigInteger I32_MIN = BigInteger.valueOf(-2147483648L);
    private static final BigInteger I32_MAX = BigInteger.valueOf(2147483647L);
    private static final BigInteger I64_MIN = BigInteger.valueOf(-9223372036854775808L);
    private static final BigInteger I64_MAX = BigInteger.valueOf(9223372036854775807L);
    private static final BigInteger U8_MIN = BigInteger.valueOf(0L);
    private static final BigInteger U8_MAX = BigInteger.valueOf(255L);
    private static final BigInteger U16_MIN = BigInteger.valueOf(0L);
    private static final BigInteger U16_MAX = BigInteger.valueOf(65535L);
    private static final BigInteger U32_MIN = BigInteger.valueOf(0L);
    private static final BigInteger U32_MAX = BigInteger.valueOf(4294967295L);
    private static final BigInteger U64_MIN = BigInteger.valueOf(0L);
    private static final BigInteger U64_MAX = new BigInteger("18446744073709551615");

    private final BigInteger value;
    private final IrModule irModule;

    private IntegerLiteral(@Nonnull BigInteger value, @Nonnull IrModule irModule) {
        this.value = value;
        this.irModule = irModule;
    }

    /**
     * Creates from a BigInteger value.
     */
    @Nonnull
    public static IntegerLiteral fromBigInteger(@Nonnull BigInteger value,
                                                @Nonnull IrModule irModule) {
        return new IntegerLiteral(value, irModule);
    }

    /**
     * Creates from a long value.
     */
    @Nonnull
    public static IntegerLiteral fromLong(long value, @Nonnull IrModule irModule) {
        return new IntegerLiteral(BigInteger.valueOf(value), irModule);
    }

    /**
     * Creates from unsigned long interpretation.
     */
    @Nonnull
    public static IntegerLiteral fromUnsignedLong(long value, @Nonnull IrModule irModule) {
        BigInteger bigValue = value >= 0
            ? BigInteger.valueOf(value) :
                BigInteger.valueOf(value).add(BigInteger.ONE.shiftLeft(64));
        return new IntegerLiteral(bigValue, irModule);
    }

    /**
     * Checks if value is in the range of the given type.
     */
    public boolean isInRange(@Nonnull String typeName) {
        return switch (typeName) {
            case "i8" -> value.compareTo(I8_MIN) >= 0
                && value.compareTo(I8_MAX) <= 0;
            case "i16" -> value.compareTo(I16_MIN) >= 0
                && value.compareTo(I16_MAX) <= 0;
            case "i32" -> value.compareTo(I32_MIN) >= 0
                && value.compareTo(I32_MAX) <= 0;
            case "i64" -> value.compareTo(I64_MIN) >= 0 && value.compareTo(I64_MAX) <= 0;
            case "isize", "usize" -> isInRange(getSizeTypeName(typeName));
            case "u8" -> value.compareTo(U8_MIN) >= 0
                && value.compareTo(U8_MAX) <= 0;
            case "u16" -> value.compareTo(U16_MIN) >= 0
                && value.compareTo(U16_MAX) <= 0;
            case "u32" -> value.compareTo(U32_MIN) >= 0
                && value.compareTo(U32_MAX) <= 0;
            case "u64" -> value.compareTo(U64_MIN) >= 0
                && value.compareTo(U64_MAX) <= 0;
            case "IntegerLiteral" -> value.compareTo(I64_MIN) >= 0
                && value.compareTo(U64_MAX) <= 0;
            default -> false;
        };
    }

    private String getSizeTypeName(String typeName) {
        int pointerSize = IrFactory.getTargetPointerSize(irModule);
        if (pointerSize == 32) {
            if (typeName.equals("isize")) {
                return "i32";
            } else if (typeName.equals("usize")) {
                return "u32";
            }
        } else if (pointerSize == 64) {
            if (typeName.equals("isize")) {
                return "i64";
            } else if (typeName.equals("usize")) {
                return "u64";
            }
        }
        throw new IllegalStateException("Unsupported pointer size: " + pointerSize);
    }

    public boolean isWithinIntLiteralRange() {
        return value.compareTo(I64_MIN) >= 0 && value.compareTo(U64_MAX) <= 0;
    }

    public BigInteger getBigInteger() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}