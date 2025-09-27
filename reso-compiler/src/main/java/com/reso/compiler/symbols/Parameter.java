package com.reso.compiler.symbols;

import com.reso.compiler.types.ResoType;
import java.util.Objects;

public record Parameter(String name, ResoType type) {
    public Parameter(String name, ResoType type) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public String toString() {
        return name + ": " + type.getName();
    }
}
