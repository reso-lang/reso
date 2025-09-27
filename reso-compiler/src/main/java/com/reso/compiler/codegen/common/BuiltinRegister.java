package com.reso.compiler.codegen.common;

import static com.reso.compiler.types.StandardTypeHandles.BOOL;
import static com.reso.compiler.types.StandardTypeHandles.CHAR;
import static com.reso.compiler.types.StandardTypeHandles.F32;
import static com.reso.compiler.types.StandardTypeHandles.F64;
import static com.reso.compiler.types.StandardTypeHandles.I16;
import static com.reso.compiler.types.StandardTypeHandles.I32;
import static com.reso.compiler.types.StandardTypeHandles.I64;
import static com.reso.compiler.types.StandardTypeHandles.I8;
import static com.reso.compiler.types.StandardTypeHandles.ISIZE;
import static com.reso.compiler.types.StandardTypeHandles.U16;
import static com.reso.compiler.types.StandardTypeHandles.U32;
import static com.reso.compiler.types.StandardTypeHandles.U64;
import static com.reso.compiler.types.StandardTypeHandles.U8;
import static com.reso.compiler.types.StandardTypeHandles.UNIT;
import static com.reso.compiler.types.StandardTypeHandles.USIZE;

import com.reso.compiler.codegen.expressions.LiteralGenerator;
import com.reso.compiler.codegen.expressions.VectorGenerator;
import com.reso.compiler.symbols.Parameter;
import com.reso.compiler.symbols.Visibility;
import com.reso.compiler.symbols.resources.MethodSymbol;
import com.reso.compiler.symbols.resources.PathSegment;
import com.reso.compiler.types.GenericType;
import com.reso.compiler.types.ResoType;
import com.reso.compiler.types.ResourceType;
import com.reso.compiler.types.TypeHandle;
import com.reso.compiler.types.TypeSystem;
import com.reso.compiler.values.ResoValue;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBasicBlock;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrContext;
import com.reso.llvm.api.IrModule;
import com.reso.llvm.api.IrType;
import com.reso.llvm.api.IrValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Generator for built-in resource types and their methods.
 * Handles registration of built-in resources
 */
public final class BuiltinRegister {

    private BuiltinRegister() {
        throw new UnsupportedOperationException("Utility class");
    }

    // Configuration for primitive types and their string conversion
    private static final Map<String, PrimitiveToStringConfig> PRIMITIVE_CONFIGS = Map.ofEntries(
        Map.entry("i8", new PrimitiveToStringConfig(I8, "%d", 5, "int8_to_string")),
        Map.entry("i16", new PrimitiveToStringConfig(I16, "%d", 7, "int16_to_string")),
        Map.entry("i32", new PrimitiveToStringConfig(I32, "%d", 12, "int32_to_string")),
        Map.entry("i64", new PrimitiveToStringConfig(I64, "%lld", 21, "int64_to_string")),
        Map.entry("isize", new PrimitiveToStringConfig(ISIZE, "%lld", 21, "isize_to_string")),
        Map.entry("u8", new PrimitiveToStringConfig(U8, "%u", 4, "uint8_to_string")),
        Map.entry("u16", new PrimitiveToStringConfig(U16, "%u", 6, "uint16_to_string")),
        Map.entry("u32", new PrimitiveToStringConfig(U32, "%u", 11, "uint32_to_string")),
        Map.entry("u64", new PrimitiveToStringConfig(U64, "%llu", 21, "uint64_to_string")),
        Map.entry("usize", new PrimitiveToStringConfig(USIZE, "%llu", 21, "usize_to_string")),
        Map.entry("f32", new PrimitiveToStringConfig(F32, "%.6f", 48, "float32_to_string")),
        Map.entry("f64", new PrimitiveToStringConfig(F64, "%.15f", 64, "float64_to_string")),
        Map.entry("char", new PrimitiveToStringConfig(CHAR, "%c", 2, "char_to_string")),
        Map.entry("bool", new PrimitiveToStringConfig(BOOL, "%s", 6, "bool_to_string"))
    );

    /**
     * Configuration for primitive type to string conversion.
     */
    private record PrimitiveToStringConfig(TypeHandle<?> typeHandle, String formatString,
                                           int bufferSize,
                                           String functionName) {
    }

    public static void registerBuiltinResourceTypes(@Nonnull CodeGenerationContext context) {
        registerVectorResourceType(context);
    }

    /**
     * Registers all built-in resource types and their methods.
     */
    public static void registerBuiltinResources(@Nonnull CodeGenerationContext context) {
        registerVectorResource(context);

        // Register all primitive types with to_string methods
        registeri8(context);
        registeri16(context);
        registeri32(context);
        registeri64(context);
        registerisize(context);
        registeru8(context);
        registeru16(context);
        registeru32(context);
        registeru64(context);
        registerusize(context);
        registerf32(context);
        registerf64(context);
        registerChar(context);
        registerBool(context);
        registerUnit(context);
    }

    /**
     * Registers any global built-in functions.
     */
    public static void registerBuiltinFunctions(@Nonnull CodeGenerationContext context) {
        registerPrintFunction(context);
        registerPrintlnFunction(context);
    }

    /**
     * Registers the i8 type with its methods.
     * i8 methods:
     * - int8.to_string() -> String
     */
    private static void registeri8(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "i8");
    }

    /**
     * Registers the i16 type with its methods.
     * i16 methods:
     * - int16.to_string() -> String
     */
    private static void registeri16(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "i16");
    }

    /**
     * Registers the i32 type with its methods.
     * i32 methods:
     * - int32.to_string() -> String
     */
    private static void registeri32(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "i32");
    }

    /**
     * Registers the i64 type with its methods.
     * i64 methods:
     * - int64.to_string() -> String
     */
    private static void registeri64(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "i64");
    }

    /**
     * Registers the isize type with its methods.
     * isize methods:
     * - isize.to_string() -> String
     */
    private static void registerisize(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "isize");
    }

    /**
     * Registers the u8 type with its methods.
     * u8 methods:
     * - uint8.to_string() -> String
     */
    private static void registeru8(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "u8");
    }

    /**
     * Registers the u16 type with its methods.
     * u16 methods:
     * - uint16.to_string() -> String
     */
    private static void registeru16(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "u16");
    }

    /**
     * Registers the u32 type with its methods.
     * u32 methods:
     * - uint32.to_string() -> String
     */
    private static void registeru32(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "u32");
    }

    /**
     * Registers the u64 type with its methods.
     * u64 methods:
     * - uint64.to_string() -> String
     */
    private static void registeru64(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "u64");
    }

    /**
     * Registers the usize type with its methods.
     * usize methods:
     * - usize.to_string() -> String
     */
    private static void registerusize(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "usize");
    }

    /**
     * Registers the f32 type with its methods.
     * f32 methods:
     * - float32.to_string() -> String
     */
    private static void registerf32(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "f32");
    }

    /**
     * Registers the f64 type with its methods.
     * f64 methods:
     * - float64.to_string() -> String
     */
    private static void registerf64(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "f64");
    }

    /**
     * Registers the char type with its methods.
     * char methods:
     * - char.to_string() -> String
     */
    private static void registerChar(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "char");
    }

    /**
     * Registers the bool type with its methods.
     * bool methods:
     * - bool.to_string() -> String
     */
    private static void registerBool(@Nonnull CodeGenerationContext context) {
        registerPrimitiveWithToString(context, "bool");
    }

    /**
     * Registers the unit type with its methods.
     * unit methods:
     * - unit.to_string() -> String
     */
    private static void registerUnit(@Nonnull CodeGenerationContext context) {
        ResoType unitType = context.getTypeSystem().getType(UNIT);
        MethodSymbol toStringMethodSymbol = createUnitToStringMethodSymbol(context);

        context.getSymbolTable().defineType(
                "()",
                unitType,
                List.of(toStringMethodSymbol),
                "",
                context.getErrorReporter(),
                0,
                0
        );
    }


    /**
     * Registers the print function for console output.
     * print(text: String) -> Unit
     * Prints the given string to the console without a newline.
     */
    private static void registerPrintFunction(@Nonnull CodeGenerationContext context) {
        ResourceType stringType = context.getTypeSystem().getResourceType("String");
        ResoType unitType = context.getTypeSystem().getType(UNIT);

        List<Parameter> parameters = List.of(
            new Parameter("text", stringType)
        );

        context.getSymbolTable().defineFunction(
            "print",
            createPrintFunction(context, "print", false),
            unitType,
            parameters,
            Visibility.GLOBAL,
            "",
            context.getErrorReporter(),
            0,
            0
        );
    }

    /**
     * Registers the println function for console output.
     * println(text: String) -> Unit
     * Prints the given string to the console with a newline.
     */
    private static void registerPrintlnFunction(@Nonnull CodeGenerationContext context) {
        ResourceType stringType = context.getTypeSystem().getResourceType("String");
        ResoType unitType = context.getTypeSystem().getType(UNIT);

        List<Parameter> parameters = List.of(
            new Parameter("text", stringType)
        );

        context.getSymbolTable().defineFunction(
            "println",
            createPrintFunction(context, "println", true),
            unitType,
            parameters,
            Visibility.GLOBAL,
            "",
            context.getErrorReporter(),
            0,
            0
        );
    }

    /**
     * Creates the IR function for print or println.
     *
     * @param context      The code generation context
     * @param functionName The name of the function ("print" or "println")
     * @param addNewline   Whether to add a newline at the end
     * @return The IR function value
     */
    private static IrValue createPrintFunction(
        @Nonnull CodeGenerationContext context,
        @Nonnull String functionName,
        boolean addNewline) {
        IrModule module = context.getIrModule();

        // Get or create printf function
        IrValue printfFunction = getOrCreatePrintfFunction(context);

        // Get types
        ResourceType stringType = context.getTypeSystem().getResourceType("String");
        ResoType unitType = context.getTypeSystem().getType(UNIT);

        // Create function type: void functionName(String text)
        IrType[] functionParams = {stringType.getType()};
        IrType functionType =
            IrFactory.createFunctionType(unitType.getType(), functionParams, false);

        // Create the function
        IrValue function = IrFactory.addFunction(module, functionName, functionType);

        // Generate function body
        generatePrintFunctionBody(context, function, printfFunction, addNewline);

        return function;
    }

    /**
     * Generates the body of a print/println function.
     *
     * @param context        The code generation context
     * @param function       The function to generate body for
     * @param printfFunction The printf function reference
     * @param addNewline     Whether to add a newline
     */
    private static void generatePrintFunctionBody(
        @Nonnull CodeGenerationContext context,
        @Nonnull IrValue function,
        @Nonnull IrValue printfFunction,
        boolean addNewline) {

        IrBuilder builder = context.getIrBuilder();

        // Create entry block
        IrBasicBlock entryBlock = IrFactory.createBasicBlock(function, "entry");
        IrFactory.positionAtEnd(builder, entryBlock);

        // Get the String parameter
        IrValue stringParam = function.getParam(0);

        // Extract the char* from String (String contains Vector<u8>)
        IrValue charPtr = extractStringData(context, stringParam, builder);

        // Create format strings
        IrValue formatString = addNewline
            ? context.getOrCreateGlobalString("%s\n")
            : context.getOrCreateGlobalString("%s");

        // Call printf(format, string_data)
        IrValue[] printfArgs = {formatString, charPtr};
        IrFactory.createCall(builder, printfFunction, printfArgs, "printf_call");

        // Return Unit value
        ResoType unitType = context.getTypeSystem().getUnitType();
        IrValue unitValue = IrFactory.createConstantNamedStruct(unitType.getType(), new IrValue[0]);
        IrFactory.createReturn(context.getIrBuilder(), unitValue);
    }

    /**
     * Extracts the char* data from a String resource.
     *
     * @param context     The code generation context
     * @param stringValue The String resource instance
     * @param builder     The IR builder
     * @return The char* pointer to the string data
     */
    private static IrValue extractStringData(
        @Nonnull CodeGenerationContext context,
        @Nonnull IrValue stringValue,
        @Nonnull IrBuilder builder) {

        try {
            ResourceType stringType = context.getTypeSystem().getResourceType("String");
            ResoType uint8Type = context.getTypeSystem().getType(U8);
            ResourceType vectorType = context.getTypeSystem().getOrCreateVectorType(uint8Type);

            // String struct has Vector<u8> at field 0 (data field)
            IrValue dataFieldPtr = IrFactory.createStructGEP(
                builder,
                stringType.getStructType(),
                stringValue,
                0,
                "string_data_field"
            );

            // Load the Vector<u8> pointer
            IrValue vectorPtr =
                IrFactory.createLoad(builder, dataFieldPtr, vectorType.getType(), "vector_ptr");

            // Vector struct has elements pointer at field 0
            IrValue elementsFieldPtr = IrFactory.createStructGEP(
                builder,
                vectorType.getStructType(),
                vectorPtr,
                0,
                "elements_field"
            );

            // Load the elements pointer (char*)
            return IrFactory.createLoad(
                builder,
                elementsFieldPtr,
                IrFactory.createPointerType(uint8Type.getType(), 0),
                "elements_ptr"
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to extract string data: " + e.getMessage(), e);
        }
    }

    /**
     * Gets or creates the printf function reference.
     *
     * @param context The code generation context
     * @return The printf function reference
     */
    private static IrValue getOrCreatePrintfFunction(@Nonnull CodeGenerationContext context) {
        IrContext irContext = context.getIrContext();
        IrModule module = context.getIrModule();

        // Create types for printf function
        IrType int8Type = IrFactory.createi8Type(irContext);
        IrType charPtrType = IrFactory.createPointerType(int8Type, 0);
        IrType intType = IrFactory.createi32Type(irContext);

        // Create printf function type: int printf(const char* format, ...)
        IrType[] printfParams = {charPtrType};
        IrType printfFunctionType =
            IrFactory.createFunctionType(intType, printfParams, true); // variadic

        // Add or get existing function
        if (!module.hasFunction("printf")) {
            return IrFactory.addFunction(module, "printf", printfFunctionType);
        } else {
            return module.getFunction("printf", printfFunctionType);
        }
    }

    /**
     * Generic method to register a primitive type with its to_string method.
     *
     * @param context  The code generation context
     * @param typeName The name of the primitive type
     */
    private static void registerPrimitiveWithToString(@Nonnull CodeGenerationContext context,
                                                      @Nonnull String typeName) {
        PrimitiveToStringConfig config = PRIMITIVE_CONFIGS.get(typeName);
        if (config == null) {
            throw new IllegalArgumentException("No configuration found for type: " + typeName);
        }

        ResoType primitiveType = context.getTypeSystem().getType(config.typeHandle);
        MethodSymbol toStringMethodSymbol = createToStringMethodSymbol(context, config);

        context.getSymbolTable().defineType(
            typeName,
            primitiveType,
            List.of(toStringMethodSymbol),
            "",
            context.getErrorReporter(),
            0,
            0
        );
    }

    /**
     * Creates the to_string() method symbol for any primitive type.
     *
     * @param context The code generation context
     * @param config  The configuration for the primitive type
     * @return The method symbol for to_string()
     */
    private static MethodSymbol createToStringMethodSymbol(
        @Nonnull CodeGenerationContext context,
        @Nonnull PrimitiveToStringConfig config) {

        List<Parameter> parameters = List.of();
        List<PathSegment> pathSegments = List.of();
        IrValue irValue = createPrimitiveToStringFunction(context, config);

        return new MethodSymbol(
            "to_string",
            context.getTypeSystem().getResourceType("String"),
            parameters,
            irValue,
            Visibility.GLOBAL,
            pathSegments
        );
    }

    /**
     * Creates the IR function value for primitive.to_string() method.
     * Uses snprintf to convert a primitive value to a String.
     *
     * @param context The code generation context
     * @param config  The configuration for the primitive type
     * @return The IR function value for the to_string method
     */
    private static IrValue createPrimitiveToStringFunction(
        @Nonnull CodeGenerationContext context,
        @Nonnull PrimitiveToStringConfig config) {
        IrModule module = context.getIrModule();

        // Get or create snprintf function
        IrValue snprintfFunction = getOrCreateSnprintfFunction(context);

        // Get String resource type
        ResourceType stringType = context.getTypeSystem().getResourceType("String");

        // Get primitive type
        ResoType primitiveType = context.getTypeSystem().getType(config.typeHandle);

        // Create wrapper function type
        IrType[] wrapperParams = {primitiveType.getType()};
        IrType wrapperFunctionType =
            IrFactory.createFunctionType(stringType.getType(), wrapperParams, false);

        // Create the wrapper function
        IrValue wrapperFunction =
            IrFactory.addFunction(module, config.functionName, wrapperFunctionType);

        // Generate wrapper function body
        generateToStringFunctionBody(context, wrapperFunction, config, stringType,
            snprintfFunction);

        return wrapperFunction;
    }

    /**
     * Generates the body of a primitive to_string function.
     *
     * @param context          The code generation context
     * @param wrapperFunction  The wrapper function to generate body for
     * @param config           The primitive type configuration
     * @param stringType       The String resource type
     * @param snprintfFunction The snprintf function reference
     */
    private static void generateToStringFunctionBody(
        @Nonnull CodeGenerationContext context,
        @Nonnull IrValue wrapperFunction,
        @Nonnull PrimitiveToStringConfig config,
        @Nonnull ResourceType stringType,
        @Nonnull IrValue snprintfFunction) {

        IrBuilder builder = context.getIrBuilder();

        // Create entry block
        IrBasicBlock entryBlock = IrFactory.createBasicBlock(wrapperFunction, "entry");
        IrFactory.positionAtEnd(builder, entryBlock);

        // Get the primitive parameter
        IrValue primitiveParam = wrapperFunction.getParam(0);

        primitiveParam =
            convertParameterIfNeeded(context, primitiveParam, config.typeHandle, builder);

        // Allocate heap buffer for string data
        ResoType uint8Type = context.getTypeSystem().getType(U8);
        ResoType usizeType = context.getTypeSystem().getType(USIZE);
        IrValue stringDataPtr = IrFactory.createGCArrayMalloc(
            builder,
            context.getIrModule(),
            uint8Type.getType(),
            IrFactory.createConstantInt(usizeType.getType(), config.bufferSize, false),
            IrFactory.declareGCMalloc(context.getIrModule()),
            "string_data"
        );

        // Get format string
        IrValue formatString = context.getOrCreateGlobalString(config.formatString);

        // Call snprintf with appropriate arguments directly into the heap buffer
        IrValue formattedLength;
        if (config.typeHandle.equals(BOOL)) {
            formattedLength =
                handleBooleanToString(context, wrapperFunction, stringDataPtr, primitiveParam,
                    snprintfFunction);
        } else {
            formattedLength = callSnprintf(context, stringDataPtr, config.bufferSize, formatString,
                primitiveParam, snprintfFunction);
        }

        // add + 1 to length for null terminator
        formattedLength = IrFactory.createAdd(builder, formattedLength,
            IrFactory.createConstantInt(formattedLength.getType(), 1, false), "length_with_null");

        // Create String instance from the heap buffer (no copy needed)
        IrValue stringInstance =
            createStringFromHeapBuffer(context, stringType, stringDataPtr, formattedLength,
                config.bufferSize, builder);

        // Return the String instance
        IrFactory.createReturn(builder, stringInstance);
    }

    private static IrValue convertParameterIfNeeded(
        @Nonnull CodeGenerationContext context,
        @Nonnull IrValue param,
        @Nonnull TypeHandle<?> typeHandle,
        @Nonnull IrBuilder builder) {
        TypeSystem typeSystem = context.getTypeSystem();

        if (typeHandle.equals(I8)) {
            // Convert i8 to i32
            param = IrFactory.createSExt(builder, param, typeSystem.getType(I32).getType(),
                "i8_to_i32");
        } else if (typeHandle.equals(U8)) {
            // Convert u8 to u32
            param = IrFactory.createZExt(builder, param, typeSystem.getType(U32).getType(),
                "u8_to_u32");
        } else if (typeHandle.equals(I16)) {
            // Convert i16 to i32
            param = IrFactory.createSExt(builder, param, typeSystem.getType(I32).getType(),
                "i16_to_i32");
        } else if (typeHandle.equals(U16)) {
            // Convert u16 to u32
            param = IrFactory.createZExt(builder, param, typeSystem.getType(U32).getType(),
                "u16_to_u32");
        } else if (typeHandle.equals(F32)) {
            // Convert f32 to f64
            param = IrFactory.createFPExt(builder, param, typeSystem.getType(F64).getType(),
                "f32_to_f64");
        }
        return param;
    }

    /**
     * Handles boolean to string conversion (true/false).
     * Writes directly to the heap buffer.
     *
     * @param context          The code generation context
     * @param function         The function containing this logic
     * @param heapBuffer       The heap buffer to write to
     * @param boolParam        The boolean parameter
     * @param snprintfFunction The snprintf function reference
     * @return The length of the formatted string
     */
    private static IrValue handleBooleanToString(
        @Nonnull CodeGenerationContext context,
        @Nonnull IrValue function,
        @Nonnull IrValue heapBuffer,
        @Nonnull IrValue boolParam,
        @Nonnull IrValue snprintfFunction) {

        IrBuilder builder = context.getIrBuilder();
        IrContext irContext = context.getIrContext();

        // Create blocks for true and false cases
        IrBasicBlock trueBlock = IrFactory.createBasicBlock(function, "bool_true");
        IrBasicBlock falseBlock = IrFactory.createBasicBlock(function, "bool_false");
        IrBasicBlock mergeBlock = IrFactory.createBasicBlock(function, "bool_merge");

        // Branch based on boolean value
        IrFactory.createCondBr(builder, boolParam, trueBlock, falseBlock);

        // True case
        IrFactory.positionAtEnd(builder, trueBlock);
        IrValue trueString = context.getOrCreateGlobalString("true");
        IrValue trueLength =
            callSnprintf(context, heapBuffer, 6, context.getOrCreateGlobalString("%s"), trueString,
                snprintfFunction);
        IrFactory.createBr(builder, mergeBlock);

        // False case
        IrFactory.positionAtEnd(builder, falseBlock);
        IrValue falseString = context.getOrCreateGlobalString("false");
        IrValue falseLength =
            callSnprintf(context, heapBuffer, 6, context.getOrCreateGlobalString("%s"), falseString,
                snprintfFunction);
        IrFactory.createBr(builder, mergeBlock);

        // Merge block
        IrFactory.positionAtEnd(builder, mergeBlock);
        IrValue phi =
            IrFactory.createPhi(builder, IrFactory.createi32Type(irContext), "bool_length");
        IrFactory.addIncoming(phi, trueLength, trueBlock);
        IrFactory.addIncoming(phi, falseLength, falseBlock);

        return phi;
    }

    /**
     * Calls snprintf with the given parameters.
     *
     * @param context          The code generation context
     * @param buffer           The buffer to write to
     * @param bufferSize       The size of the buffer
     * @param formatString     The format string
     * @param value            The value to format
     * @param snprintfFunction The snprintf function reference
     * @return The length returned by snprintf
     */
    private static IrValue callSnprintf(
        @Nonnull CodeGenerationContext context,
        @Nonnull IrValue buffer,
        int bufferSize,
        @Nonnull IrValue formatString,
        @Nonnull IrValue value,
        @Nonnull IrValue snprintfFunction) {

        IrBuilder builder = context.getIrBuilder();

        IrType sizeType = context.getTypeSystem().getType(USIZE).getType();
        IrValue bufferSizeValue = IrFactory.createConstantInt(sizeType, bufferSize, false);
        IrValue[] snprintfArgs = {buffer, bufferSizeValue, formatString, value};

        return IrFactory.createCall(builder, snprintfFunction, snprintfArgs, "sprintf_result");
    }

    /**
     * Gets or creates the snprintf function reference.
     *
     * @param context The code generation context
     * @return The snprintf function reference
     */
    private static IrValue getOrCreateSnprintfFunction(@Nonnull CodeGenerationContext context) {
        IrContext irContext = context.getIrContext();
        IrModule module = context.getIrModule();

        // Create types for snprintf function
        IrType int8Type = IrFactory.createi8Type(irContext);
        IrType charPtrType = IrFactory.createPointerType(int8Type, 0);
        IrType sizeType = context.getTypeSystem().getType(USIZE).getType();
        IrType intType = IrFactory.createi32Type(irContext);

        // Create snprintf function type:
        // int snprintf(char* str, size_t size, const char* format, ...)
        IrType[] snprintfParams = {charPtrType, sizeType, charPtrType};
        IrType snprintfFunctionType = IrFactory.createFunctionType(intType, snprintfParams, true);

        // Add or get existing function
        if (!module.hasFunction("snprintf")) {
            return IrFactory.addFunction(module, "snprintf", snprintfFunctionType);
        } else {
            return module.getFunction("snprintf", snprintfFunctionType);
        }
    }

    /**
     * Creates a String resource instance from a heap-allocated char buffer.
     *
     * @param context    The code generation context
     * @param stringType The String resource type
     * @param heapBuffer The heap-allocated char buffer containing the string data
     * @param length     The length of the string
     * @param bufferSize The size of the allocated buffer
     * @param builder    The IR builder
     * @return The String instance
     */
    private static IrValue createStringFromHeapBuffer(
        @Nonnull CodeGenerationContext context,
        @Nonnull ResourceType stringType,
        @Nonnull IrValue heapBuffer,
        @Nonnull IrValue length,
        int bufferSize,
        @Nonnull IrBuilder builder) {

        try {
            // Get required types
            ResoType usizeType = context.getTypeSystem().getType(USIZE);
            ResoType uint8Type = context.getTypeSystem().getType(U8);
            ResourceType vectorType = context.getTypeSystem().getOrCreateVectorType(uint8Type);

            // Allocate String struct
            IrValue stringStruct = IrFactory.createGCMalloc(
                context.getIrBuilder(),
                context.getIrModule(),
                stringType.getStructType(),
                IrFactory.declareGCMalloc(context.getIrModule()),
                "string_instance"
            );

            // Create and initialize Vector<u8> instance (reusing the heap buffer)
            IrValue vectorStruct =
                createStringVectorStruct(vectorType, heapBuffer, length, bufferSize, usizeType,
                    builder, context.getIrModule());

            // Store vector in String struct (field 0: data)
            IrValue dataField =
                IrFactory.createStructGEP(builder, stringType.getStructType(), stringStruct, 0,
                    "data_field");
            IrFactory.createStore(builder, vectorStruct, dataField);

            return stringStruct;

        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to create String from heap buffer: " + e.getMessage(), e);
        }
    }

    /**
     * Creates and initializes a Vector struct for string data.
     *
     * @param vectorType The vector type
     * @param dataPtr    The data pointer
     * @param length     The length of the data
     * @param bufferSize The size of the allocated buffer
     * @param usizeType  The usize type
     * @param builder    The IR builder
     * @return The initialized vector struct
     */
    private static IrValue createStringVectorStruct(
        @Nonnull ResourceType vectorType,
        @Nonnull IrValue dataPtr,
        @Nonnull IrValue length,
        int bufferSize,
        @Nonnull ResoType usizeType,
        @Nonnull IrBuilder builder,
        @Nonnull IrModule module) {

        // Create Vector<u8> instance
        IrValue vectorStruct = IrFactory.createGCMalloc(
            builder,
            module,
            vectorType.getStructType(),
            IrFactory.declareGCMalloc(module),
            "byte_vector"
        );


        // Initialize vector struct fields
        // Field 0: elements pointer
        IrValue elementsField =
            IrFactory.createStructGEP(builder, vectorType.getStructType(), vectorStruct, 0,
                "elements_field");
        IrFactory.createStore(builder, dataPtr, elementsField);

        // Field 1: size
        IrValue sizeField =
            IrFactory.createStructGEP(builder, vectorType.getStructType(), vectorStruct, 1,
                "size_field");
        IrFactory.createStore(builder, length, sizeField);

        // Field 2: capacity
        IrValue capacityField =
            IrFactory.createStructGEP(builder, vectorType.getStructType(), vectorStruct, 2,
                "capacity_field");
        IrValue capacityValue = IrFactory.createConstantInt(usizeType.getType(), bufferSize, false);
        IrFactory.createStore(builder, capacityValue, capacityField);

        return vectorStruct;
    }

    /**
     * Creates the to_string() method symbol for unit type.
     *
     * @param context The code generation context
     * @return The method symbol for to_string()
     */
    private static MethodSymbol createUnitToStringMethodSymbol(
        @Nonnull CodeGenerationContext context) {
        List<Parameter> parameters = List.of();
        List<PathSegment> pathSegments = List.of();

        return new MethodSymbol(
            "to_string",
            context.getTypeSystem().getResourceType("String"),
            parameters,
            null,
            Visibility.GLOBAL,
            pathSegments,
            (_, _, _, _, _, _, _, _) -> createUnitString(context));
    }

    /**
     * Creates the IR value for the unit String.
     * Always returns the string "()".
     *
     * @param context The code generation context
     * @return The IR function value for the to_string method
     */
    private static ResoValue createUnitString(@Nonnull CodeGenerationContext context) {
        return context.getExpressionGenerator().getLiteralGenerator()
            .generateStringLiteral(new LiteralGenerator.LiteralInfo("()", 0, 0));
    }

    /**
     * Registers the Vector resource type with a generic element type.
     * Vector<T> where T is a generic type parameter.
     */
    private static void registerVectorResourceType(@Nonnull CodeGenerationContext context) {
        // Create the Vector resource type
        GenericType elementType = new GenericType("T", 0);
        context.getTypeSystem().createResourceType("Vector", null, null, List.of(elementType));
    }

    /**
     * Registers the Vector resource symbol with all its methods.
     * Vector methods:
     * - vec/{index}.get() -> element
     * - vec/{index}.set(value) -> unit
     * - vec.add(element) -> unit
     * - vec.insert(index, element) -> unit
     * - vec.remove(index) -> element
     * - vec/size.get() -> usize
     * - vec/capacity.get() -> usize
     */
    private static void registerVectorResource(@Nonnull CodeGenerationContext context) {
        if (context.getSymbolTable().findResource("Vector") != null) {
            context.getErrorReporter().error("Vector resource already registered");
            return;
        }

        ResourceType vectorType = context.getTypeSystem().getResourceType("Vector");
        ResoType elementType = vectorType.getGenericTypes().getFirst();

        // Register all vector methods
        MethodSymbol getMethodSymbol = createVectorGetMethodSymbol(context, elementType);
        MethodSymbol setMethodSymbol = createVectorSetMethodSymbol(context, elementType);
        MethodSymbol addMethodSymbol = createVectorAddMethodSymbol(context, elementType);
        MethodSymbol insertMethodSymbol = createVectorInsertMethodSymbol(context, elementType);
        MethodSymbol removeMethodSymbol = createVectorRemoveMethodSymbol(context, elementType);
        MethodSymbol sizeGetMethodSymbol = createVectorSizeGetMethodSymbol(context);
        MethodSymbol capacityGetMethodSymbol = createVectorCapacityGetMethodSymbol(context);

        // Register the constructor function
        context.getSymbolTable().defineFunction(
            "Vector",
            null,
            vectorType,
            Collections.emptyList(),
            Visibility.GLOBAL,
            "",
            (ResoType returnType,
             IrValue _,
             IrValue[] _,
             String _,
             CodeGenerationContext codeGenContext,
             int line,
             int column) -> VectorGenerator.generateVectorConstructor(returnType, codeGenContext,
                line, column),
            context.getErrorReporter(), 0, 0);

        // Register the resource symbol in the symbol table
        context.getSymbolTable()
            .defineResource("Vector", vectorType, List.of(), List.of(getMethodSymbol,
                setMethodSymbol,
                addMethodSymbol,
                insertMethodSymbol,
                removeMethodSymbol,
                sizeGetMethodSymbol,
                capacityGetMethodSymbol), "", context.getErrorReporter(), 0, 0);
    }

    private static MethodSymbol createVectorGetMethodSymbol(@Nonnull CodeGenerationContext context,
                                                            @Nonnull ResoType elementType) {
        List<Parameter> parameters = List.of(
            new Parameter("index", context.getTypeSystem().getType(USIZE))
        );

        List<PathSegment> pathSegments = List.of(
            new PathSegment("index", context.getTypeSystem().getType(USIZE))
        );

        return new MethodSymbol(
            "get",
            elementType,
            parameters,
            null,
            Visibility.GLOBAL,
            pathSegments,
            (ResoType resourceType,
             ResoType _,
             IrValue _,
             IrValue[] argumentValues,
             String _,
             CodeGenerationContext codeGenContext,
             int line,
             int column) -> VectorGenerator.generateVectorReadElement(resourceType, argumentValues,
                codeGenContext, line, column));
    }

    private static MethodSymbol createVectorSetMethodSymbol(@Nonnull CodeGenerationContext context,
                                                            ResoType elementType) {
        List<Parameter> parameters = Arrays.asList(
            new Parameter("index", context.getTypeSystem().getType(USIZE)),
            new Parameter("value", elementType)
        );

        List<PathSegment> pathSegments = List.of(
            new PathSegment("index", context.getTypeSystem().getType(USIZE))
        );

        return new MethodSymbol(
            "set",
            context.getTypeSystem().getType(UNIT),
            parameters,
            null,
            Visibility.GLOBAL,
            pathSegments,
            (ResoType resourceType,
             ResoType _,
             IrValue _,
             IrValue[] argumentValues,
             String _,
             CodeGenerationContext codeGenContext,
             int line,
             int column) -> VectorGenerator.generateVectorWriteElement(resourceType, argumentValues,
                codeGenContext, line, column));
    }

    private static MethodSymbol createVectorAddMethodSymbol(@Nonnull CodeGenerationContext context,
                                                            @Nonnull ResoType elementType) {
        List<Parameter> parameters = List.of(
            new Parameter("element", elementType)
        );

        return new MethodSymbol(
            "add",
            context.getTypeSystem().getType(UNIT),
            parameters,
            null,
            Visibility.GLOBAL,
            Collections.emptyList(),
            (ResoType resourceType,
             ResoType _,
             IrValue _,
             IrValue[] argumentValues,
             String _,
             CodeGenerationContext codeGenContext,
             int line,
             int column) -> VectorGenerator.generateVectorAddElement(resourceType, argumentValues,
                codeGenContext, line, column));
    }


    private static MethodSymbol createVectorInsertMethodSymbol(
        @Nonnull CodeGenerationContext context, @Nonnull ResoType elementType) {
        List<Parameter> parameters = Arrays.asList(
            new Parameter("index", context.getTypeSystem().getType(USIZE)),
            new Parameter("element", elementType)
        );

        return new MethodSymbol(
            "insert",
            context.getTypeSystem().getType(UNIT),
            parameters,
            null,
            Visibility.GLOBAL,
            Collections.emptyList(),
            (ResoType resourceType,
             ResoType _,
             IrValue _,
             IrValue[] argumentValues,
             String _,
             CodeGenerationContext codeGenContext,
             int line,
             int column) -> VectorGenerator.generateVectorInsertElement(resourceType,
                argumentValues, codeGenContext, line, column));
    }

    private static MethodSymbol createVectorRemoveMethodSymbol(
        @Nonnull CodeGenerationContext context, @Nonnull ResoType elementType) {
        List<Parameter> parameters = List.of(
            new Parameter("index", context.getTypeSystem().getType(USIZE))
        );

        return new MethodSymbol(
            "remove",
            elementType,
            parameters,
            null,
            Visibility.GLOBAL,
            Collections.emptyList(),
            (ResoType resourceType,
             ResoType _,
             IrValue _,
             IrValue[] argumentValues,
             String _,
             CodeGenerationContext codeGenContext,
             int line,
             int column) -> VectorGenerator.generateVectorRemoveElement(resourceType,
                argumentValues, codeGenContext, line, column));
    }

    private static MethodSymbol createVectorSizeGetMethodSymbol(
        @Nonnull CodeGenerationContext context) {
        List<PathSegment> pathSegments = List.of(
            new PathSegment("size")
        );

        return new MethodSymbol(
            "get",
            context.getTypeSystem().getType(USIZE),
            Collections.emptyList(),
            null,
            Visibility.GLOBAL,
            pathSegments,
            (ResoType resourceType,
             ResoType _,
             IrValue _,
             IrValue[] argumentValues,
             String _,
             CodeGenerationContext codeGenContext,
             int line,
             int column) -> VectorGenerator.generateVectorSizeAccess(resourceType, argumentValues,
                codeGenContext, line, column));
    }

    private static MethodSymbol createVectorCapacityGetMethodSymbol(
        @Nonnull CodeGenerationContext context) {
        List<PathSegment> pathSegments = List.of(
            new PathSegment("capacity")
        );

        return new MethodSymbol(
            "get",
            context.getTypeSystem().getType(USIZE),
            Collections.emptyList(),
            null,
            Visibility.GLOBAL,
            pathSegments,
            (ResoType resourceType,
             ResoType _,
             IrValue _,
             IrValue[] argumentValues,
             String _,
             CodeGenerationContext codeGenContext,
             int line,
             int column) -> VectorGenerator.generateVectorCapacityAccess(resourceType,
                argumentValues, codeGenContext, line, column));
    }
}