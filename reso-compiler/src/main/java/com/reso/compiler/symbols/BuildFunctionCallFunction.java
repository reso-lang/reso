package com.reso.compiler.symbols;

import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.values.ResoValue;
import com.reso.llvm.api.IrValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@FunctionalInterface
public interface BuildFunctionCallFunction {
    @Nullable
    ResoValue buildCall(@Nonnull ResoType returnType,
                        @Nonnull IrValue value,
                        @Nonnull IrValue[] argumentValues,
                        @Nonnull String name,
                        @Nonnull CodeGenerationContext context,
                        int line,
                        int column);
}

