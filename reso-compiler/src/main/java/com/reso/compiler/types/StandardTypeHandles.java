package com.reso.compiler.types;

import com.reso.compiler.types.literals.FloatingPointLiteralType;
import com.reso.compiler.types.literals.IntegerLiteralType;
import com.reso.compiler.types.primary.BooleanType;
import com.reso.compiler.types.primary.CharType;
import com.reso.compiler.types.primary.FloatingPointType;
import com.reso.compiler.types.primary.IntegerType;
import com.reso.compiler.types.primary.NullType;
import com.reso.compiler.types.primary.UnitType;
import com.reso.compiler.types.primary.UnsignedIntegerType;

/**
 * Centralized registry of all standard TypeHandles.
 * This class serves as the single source of truth for all built-in types.
 */
public final class StandardTypeHandles {
    private StandardTypeHandles() {
    } // Utility class

    // Integer Types
    public static final TypeHandle<IntegerType> I8 =
        TypeHandle.of("i8", IntegerType.class);
    public static final TypeHandle<IntegerType> I16 =
        TypeHandle.of("i16", IntegerType.class);
    public static final TypeHandle<IntegerType> I32 =
        TypeHandle.of("i32", IntegerType.class);
    public static final TypeHandle<IntegerType> I64 =
        TypeHandle.of("i64", IntegerType.class);
    public static final TypeHandle<IntegerType> ISIZE =
        TypeHandle.of("isize", IntegerType.class);

    public static final TypeHandle<UnsignedIntegerType> U8 =
        TypeHandle.of("u8", UnsignedIntegerType.class);
    public static final TypeHandle<UnsignedIntegerType> U16 =
        TypeHandle.of("u16", UnsignedIntegerType.class);
    public static final TypeHandle<UnsignedIntegerType> U32 =
        TypeHandle.of("u32", UnsignedIntegerType.class);
    public static final TypeHandle<UnsignedIntegerType> U64 =
        TypeHandle.of("u64", UnsignedIntegerType.class);
    public static final TypeHandle<UnsignedIntegerType> USIZE =
        TypeHandle.of("usize", UnsignedIntegerType.class);

    public static final TypeHandle<CharType> CHAR =
        TypeHandle.of("char", CharType.class);

    // Floating Point Types
    public static final TypeHandle<FloatingPointType> F32 =
        TypeHandle.of("f32", FloatingPointType.class);
    public static final TypeHandle<FloatingPointType> F64 =
        TypeHandle.of("f64", FloatingPointType.class);

    // Basic Types
    public static final TypeHandle<BooleanType> BOOL =
        TypeHandle.of("bool", BooleanType.class);
    public static final TypeHandle<UnitType> UNIT =
        TypeHandle.of("()", UnitType.class);
    public static final TypeHandle<NullType> NULL =
        TypeHandle.of("Null", NullType.class);

    // Untyped Literal Types
    public static final TypeHandle<IntegerLiteralType> INTEGER_LITERAL =
        TypeHandle.of("IntegerLiteral", IntegerLiteralType.class);
    public static final TypeHandle<FloatingPointLiteralType> FLOATING_POINT_LITERAL =
        TypeHandle.of("FloatingPointLiteral", FloatingPointLiteralType.class);
}
