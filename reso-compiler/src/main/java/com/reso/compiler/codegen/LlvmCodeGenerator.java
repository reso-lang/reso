package com.reso.compiler.codegen;

import com.reso.compiler.api.CodeGenerator;
import com.reso.compiler.codegen.common.BuiltinRegister;
import com.reso.compiler.codegen.common.CodeGenerationContext;
import com.reso.compiler.codegen.expressions.ExpressionGenerator;
import com.reso.compiler.codegen.functions.FunctionGenerator;
import com.reso.compiler.codegen.functions.FunctionSignatureRegister;
import com.reso.compiler.codegen.resources.ResourceGenerator;
import com.reso.compiler.codegen.resources.ResourceSignatureRegister;
import com.reso.compiler.codegen.statements.StatementGenerator;
import com.reso.compiler.core.CompilationUnit;
import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.symbols.SymbolTable;
import com.reso.compiler.types.TypeRegistry;
import com.reso.compiler.types.TypeSystem;
import com.reso.compiler.types.TypeSystemImpl;
import com.reso.grammar.ResoParser;
import com.reso.llvm.IrFactory;
import com.reso.llvm.api.IrBuilder;
import com.reso.llvm.api.IrContext;
import com.reso.llvm.api.IrModule;
import com.reso.llvm.api.IrTargetMachine;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * LLVM implementation of the code generator.
 */
public class LlvmCodeGenerator implements CodeGenerator {
    private final CodeGenerationContext context;

    /**
     * Creates a new LLVM code generator.
     *
     * @param moduleName    The module name
     * @param errorReporter The error reporter
     */
    public LlvmCodeGenerator(@Nonnull String moduleName, @Nonnull ErrorReporter errorReporter) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(errorReporter, "Error reporter cannot be null");

        // Initialize LLVM
        IrFactory.initialize();

        // Create LLVM components
        IrContext irContext = IrFactory.createContext();
        IrModule irModule = IrFactory.createModule(irContext, moduleName);
        try (IrTargetMachine hostTargetMachine = IrFactory.createHostTargetMachine()) {
            irModule.setTargetTriple(hostTargetMachine.getTriple());
            irModule.setDataLayout(hostTargetMachine.getDataLayout());
        }
        IrBuilder irBuilder = IrFactory.createBuilder(irContext);

        // Create type system and symbol table
        TypeRegistry typeRegistry = TypeRegistry.createWithStandardTypes(irModule);
        TypeSystem typeSystem = new TypeSystemImpl(irBuilder, typeRegistry);
        SymbolTable symbolTable = new SymbolTable();

        // Create the shared context
        this.context = new CodeGenerationContext(
            irContext, irModule, irBuilder, typeSystem, symbolTable);

        this.context.setErrorReporter(errorReporter);

        // Create the specialized generators
        ExpressionGenerator expressionGenerator = new ExpressionGenerator(context);
        StatementGenerator statementGenerator = new StatementGenerator(context);
        FunctionGenerator functionGenerator = new FunctionGenerator(context);
        ResourceGenerator resourceGenerator = new ResourceGenerator(context);

        // Initialize generators in the context
        this.context.initializeGenerators(expressionGenerator, statementGenerator,
            functionGenerator, resourceGenerator);
    }

    @Override
    public void registerFunctionDeclarations(@Nonnull LlvmCodeGenerator codeGenerator,
                                             @Nonnull List<CompilationUnit> compilationUnits) {
        Objects.requireNonNull(codeGenerator, "Code generator cannot be null");
        Objects.requireNonNull(compilationUnits, "Compilation units cannot be null");

        for (CompilationUnit unit : compilationUnits) {
            if (unit.isParsed()) {
                context.setErrorReporter(unit.getErrorReporter());

                if (unit.getParseResult().getTree() instanceof ResoParser.ProgramContext program) {
                    FunctionSignatureRegister.registerFunctionDeclarations(context, program,
                        unit.getFileIdentifier());
                } else {
                    context.getErrorReporter()
                        .error("Invalid parse tree for resource registration");
                }
            }
        }

        BuiltinRegister.registerBuiltinFunctions(context);
    }

    @Override
    public void registerResourceDeclarations(@Nonnull LlvmCodeGenerator codeGenerator,
                                             @Nonnull List<CompilationUnit> compilationUnits) {
        Objects.requireNonNull(codeGenerator, "Code generator cannot be null");
        Objects.requireNonNull(compilationUnits, "Compilation units cannot be null");

        for (CompilationUnit unit : compilationUnits) {
            if (unit.isParsed()) {
                context.setErrorReporter(unit.getErrorReporter());

                if (unit.getParseResult().getTree() instanceof ResoParser.ProgramContext program) {
                    ResourceSignatureRegister.registerResourceTypeDeclarations(context, program);
                } else {
                    context.getErrorReporter()
                        .error("Invalid parse tree for resource registration");
                }
            }
        }

        BuiltinRegister.registerBuiltinResourceTypes(context);

        for (CompilationUnit unit : compilationUnits) {
            if (unit.isParsed()) {
                context.setErrorReporter(unit.getErrorReporter());

                if (unit.getParseResult().getTree() instanceof ResoParser.ProgramContext program) {
                    ResourceSignatureRegister.registerResourceDeclarations(context, program,
                        unit.getFileIdentifier());
                } else {
                    context.getErrorReporter()
                        .error("Invalid parse tree for resource registration");
                }
            }
        }

        BuiltinRegister.registerBuiltinResources(context);
    }

    @Override
    public void generateCode(@Nonnull ParseTree tree, @Nonnull ErrorReporter errorReporter,
                             @Nonnull String fileIdentifier) {
        Objects.requireNonNull(tree, "Parse tree cannot be null");
        Objects.requireNonNull(errorReporter, "Error reporter cannot be null");

        context.setErrorReporter(errorReporter);
        context.enterFileContext(fileIdentifier);

        if (tree instanceof ResoParser.ProgramContext program) {
            for (int i = 0; i < program.getChildCount(); i++) {
                if (program.getChild(i) instanceof ResoParser.FunctionDefContext funcCtx) {
                    context.getFunctionGenerator().generateFunction(funcCtx);
                } else if (program.getChild(
                    i) instanceof ResoParser.ResourceDefContext resourceCtx) {
                    String resourceName = resourceCtx.Identifier().getText();
                    var resourceSymbol = context.getSymbolTable().findResource(resourceName);
                    if (resourceSymbol != null) {
                        context.getResourceGenerator().generateResource(resourceCtx);
                    }
                }
            }
        } else {
            context.getErrorReporter().error("Invalid parse tree");
        }

        context.exitAccessContext(); // Exit file context
    }

    @Override
    @Nonnull
    public String generateIr() {
        return IrFactory.generateIR(context.getIrModule());
    }

    @Override
    @Nonnull
    public CodeGenerationContext getCodeGenerationContext() {
        return context;
    }

    @Override
    public void close() {
        // Clean up resources in reverse order of creation
        IrBuilder irBuilder = context.getIrBuilder();
        IrModule irModule = context.getIrModule();
        IrContext irContext = context.getIrContext();

        irBuilder.close();
        irModule.close();
        irContext.close();
    }
}