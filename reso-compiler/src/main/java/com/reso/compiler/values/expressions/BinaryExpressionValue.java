package com.reso.compiler.values.expressions;

import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ResoValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Base class for untyped binary expressions.
 */
public abstract class BinaryExpressionValue extends ResoValue {
    protected final ResoValue left;
    protected final ResoValue right;
    protected final String operator;

    protected BinaryExpressionValue(@Nonnull ResoType type,
                                    @Nullable ResoType defaultType,
                                    @Nonnull ResoValue left,
                                    @Nonnull ResoValue right,
                                    @Nonnull String operator,
                                    int line,
                                    int column) {
        super(type, defaultType, null, line, column);
        this.left = left;
        this.right = right;
        this.operator = operator;
    }
}
