package com.reso.compiler.symbols;

import com.reso.compiler.types.ResoType;
import com.reso.llvm.api.IrValue;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Variable symbol implementation.
 */
public class VariableSymbol implements Symbol {
    private final String name;
    private final ResoType type;
    private final IrValue llvmValue;
    private final boolean isConstant;
    private boolean isInitialized;

    /**
     * Creates a new variable symbol.
     *
     * @param name          The variable name
     * @param type          The variable type
     * @param llvmValue     The LLVM value
     * @param isConstant    Whether the variable is constant
     * @param isInitialized Whether the variable is initialized
     */
    public VariableSymbol(
        @Nonnull String name,
        @Nonnull ResoType type,
        @Nonnull IrValue llvmValue,
        boolean isConstant,
        boolean isInitialized) {
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.llvmValue = Objects.requireNonNull(llvmValue, "LLVM value cannot be null");
        this.isConstant = isConstant;
        this.isInitialized = isInitialized;
    }

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public ResoType getType() {
        return type;
    }

    @Nonnull
    public IrValue getLlvmValue() {
        return llvmValue;
    }

    @Override
    @Nonnull
    public SymbolKind getKind() {
        return SymbolKind.VARIABLE;
    }

    /**
     * Checks if this variable is constant.
     *
     * @return true if this variable is constant
     */
    public boolean isConstant() {
        return isConstant;
    }

    /**
     * Checks if this variable is initialized.
     *
     * @return true if this variable is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Checks if this variable can be read.
     *
     * @return true if this variable can be read
     */
    public boolean isReadable() {
        return isInitialized;
    }

    /**
     * Creates a new variable symbol with initialized state.
     *
     * @return A new variable symbol
     */
    public VariableSymbol initialized() {
        return new VariableSymbol(name, type, llvmValue, isConstant, true);
    }
}

