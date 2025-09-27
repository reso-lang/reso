package com.reso.compiler.values.literals;

import javax.annotation.Nonnull;

/**
 * Represents a floating-point literal value.
 */
public final class FloatingPointLiteral {
    private static final float F32_MIN_NORMAL = Float.MIN_NORMAL;
    private static final float F32_MAX_VALUE = Float.MAX_VALUE;
    private static final double F64_MIN_NORMAL = Double.MIN_NORMAL;
    private static final double F64_MAX_VALUE = Double.MAX_VALUE;

    private final double value;

    private FloatingPointLiteral(double value) {
        this.value = value;
    }

    @Nonnull
    public static FloatingPointLiteral fromDouble(double value) {
        return new FloatingPointLiteral(value);
    }

    public double getValue() {
        return value;
    }

    public boolean isInRange(String typeName) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return false;
        }

        return switch (typeName) {
            case "f32" -> {
                if (value == 0.0) {
                    yield true;
                }
                double absValue = Math.abs(value);
                yield absValue <= F32_MAX_VALUE && (absValue == 0.0 || absValue >= F32_MIN_NORMAL);
            }
            case "f64", "FloatingPointLiteral" -> {
                if (value == 0.0) {
                    yield true;
                }
                double absValue = Math.abs(value);
                yield absValue <= F64_MAX_VALUE && (absValue == 0.0 || absValue >= F64_MIN_NORMAL);
            }
            default -> false;
        };
    }

    @Override
    public String toString() {
        return value + "";
    }
}
