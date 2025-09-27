package com.reso.compiler.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.reso.compiler.errors.ErrorReporter;
import com.reso.compiler.types.ResoType;
import com.reso.llvm.api.IrValue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the SymbolTable class.
 */
public class SymbolTableTest {
    private SymbolTable symbolTable;
    private ErrorReporter errorReporter;
    private ResoType mockIntType;
    private ResoType mockFloatType;
    private ResoType mockVoidType;
    private IrValue mockValue;

    @BeforeEach
    public void setUp() {
        // Initialize a new symbol table for each test
        symbolTable = new SymbolTable();

        // Create mocks for dependencies
        errorReporter = mock(ErrorReporter.class);
        mockIntType = mock(ResoType.class);
        mockFloatType = mock(ResoType.class);
        mockVoidType = mock(ResoType.class);
        mockValue = mock(IrValue.class);

        // Configure mock behavior
        when(mockIntType.getName()).thenReturn("i32");
        when(mockFloatType.getName()).thenReturn("f64");
        when(mockVoidType.getName()).thenReturn("Void");
    }

    @Test
    public void testInitialState() {
        assertTrue(symbolTable.isInGlobalScope(), "Should start in global scope");
        assertFalse(symbolTable.isInFunctionScope(), "Should not start in function scope");
        assertNull(symbolTable.getCurrentFunctionReturnType(),
            "No function should be active initially");
    }

    @Test
    public void testEnterExitScope() {
        assertTrue(symbolTable.isInGlobalScope(), "Should start in global scope");

        symbolTable.enterScope();
        assertFalse(symbolTable.isInGlobalScope(),
            "Should no longer be in global scope after entering new scope");

        assertTrue(symbolTable.exitScope(errorReporter, 1, 1), "Exiting scope should succeed");
        assertTrue(symbolTable.isInGlobalScope(), "Should be back in global scope after exiting");
    }

    @Test
    public void testEnterExitMultipleScopes() {
        // Enter several nested scopes
        symbolTable.enterScope(); // Scope 1
        symbolTable.enterScope(); // Scope 2
        symbolTable.enterScope(); // Scope 3

        assertFalse(symbolTable.isInGlobalScope(),
            "Should not be in global scope after entering nested scopes");

        // Exit them one by one
        assertTrue(symbolTable.exitScope(errorReporter, 1, 1), "Exiting scope 3 should succeed");
        assertTrue(symbolTable.exitScope(errorReporter, 1, 1), "Exiting scope 2 should succeed");
        assertTrue(symbolTable.exitScope(errorReporter, 1, 1), "Exiting scope 1 should succeed");

        assertTrue(symbolTable.isInGlobalScope(),
            "Should be back in global scope after exiting all scopes");

        // Cannot exit global scope
        assertFalse(symbolTable.exitScope(errorReporter, 1, 1), "Exiting global scope should fail");
    }

    @Test
    public void testEnterExitFunctionScope() {
        assertFalse(symbolTable.isInFunctionScope(), "Should not start in function scope");

        symbolTable.enterFunctionScope(mockIntType);
        assertTrue(symbolTable.isInFunctionScope(), "Should be in function scope after entering");
        assertEquals(mockIntType, symbolTable.getCurrentFunctionReturnType(),
            "Current function return type should match entered type");

        assertTrue(symbolTable.exitFunctionScope(errorReporter, 1, 1),
            "Exiting function scope should succeed");
        assertFalse(symbolTable.isInFunctionScope(),
            "Should not be in function scope after exiting");
        assertNull(symbolTable.getCurrentFunctionReturnType(),
            "No function should be active after exiting");
    }

    @Test
    public void testNestedFunctionScopes() {
        // First function
        symbolTable.enterFunctionScope(mockIntType);
        assertEquals(mockIntType, symbolTable.getCurrentFunctionReturnType(),
            "Current function return type should be i32");

        // Cannot exit non-existent function
        symbolTable.exitFunctionScope(errorReporter, 1, 1);

        // No current function
        assertFalse(symbolTable.isInFunctionScope(),
            "Should not be in function scope after exiting");
        assertNull(symbolTable.getCurrentFunctionReturnType(),
            "Current return type should be null");

        // Second function
        symbolTable.enterFunctionScope(mockFloatType);
        assertEquals(mockFloatType, symbolTable.getCurrentFunctionReturnType(),
            "Current function return type should be f64");

        symbolTable.exitFunctionScope(errorReporter, 1, 1);
    }

    @Test
    public void testDefineVariableInGlobalScope() {
        VariableSymbol variable = symbolTable.defineVariable(
            "globalVar", mockValue, mockIntType, false, true, errorReporter, 1, 1);

        assertNull(variable, "Should not define variable in global scope");

        Symbol found = symbolTable.findSymbol("globalVar", SymbolKind.VARIABLE);
        assertNull(found, "Should not find variable in global scope");
    }

    @Test
    public void testDefineVariableInFunctionScope() {
        symbolTable.enterFunctionScope(mockVoidType);

        VariableSymbol variable = symbolTable.defineVariable(
            "localVar", mockValue, mockIntType, false, true, errorReporter, 1, 1);

        assertNotNull(variable, "Should successfully define variable in function scope");

        Symbol found = symbolTable.findSymbol("localVar", SymbolKind.VARIABLE);
        assertNotNull(found, "Should find defined variable");
        assertEquals("localVar", found.getName(), "Found variable should have correct name");

        // Exit function and verify the variable is no longer accessible
        symbolTable.exitFunctionScope(errorReporter, 1, 1);
        assertNull(symbolTable.findSymbol("localVar", SymbolKind.VARIABLE),
            "Variable should not be accessible after exiting scope");
    }

    @Test
    public void testDefineVariableWithSameName() {
        // enter function scope
        symbolTable.enterFunctionScope(mockVoidType);

        // Define variable
        symbolTable.defineVariable("x", mockValue, mockIntType, false, true, errorReporter, 1, 1);

        // Enter a new scope
        symbolTable.enterScope();

        // Define variable with same name in inner scope
        VariableSymbol variable =
            symbolTable.defineVariable("x", mockValue, mockFloatType, false, true, errorReporter, 2,
                1);
        assertNotNull(variable,
            "Should successfully define variable with same name in inner scope");

        // Variable found should be the inner scope one
        Symbol found = symbolTable.findSymbol("x", SymbolKind.VARIABLE);
        assertNotNull(found, "Should find defined variable");

        // Exit inner scope
        symbolTable.exitScope(errorReporter, 3, 1);

        // Now should find the variable
        found = symbolTable.findSymbol("x", SymbolKind.VARIABLE);
        assertNotNull(found, "Should find variable");
    }

    @Test
    public void testDefineVariableWithDuplicateName() {
        // enter function scope
        symbolTable.enterFunctionScope(mockVoidType);

        // Define variable
        symbolTable.defineVariable("duplicateVar", mockValue, mockIntType, false, true,
            errorReporter, 1, 1);

        // Try to define another variable with the same name in the same scope
        VariableSymbol variable = symbolTable.defineVariable(
            "duplicateVar", mockValue, mockFloatType, false, true, errorReporter, 2, 1);

        assertNull(variable, "Should fail to define variable with duplicate name in same scope");
    }

    @Test
    public void testDefineConstantVariable() {
        // enter function scope
        symbolTable.enterFunctionScope(mockVoidType);

        symbolTable.defineVariable("constVar", mockValue, mockIntType, true, true, errorReporter, 1,
            1);

        Symbol found = symbolTable.findSymbol("constVar", SymbolKind.VARIABLE);
        assertNotNull(found, "Should find defined constant");
        assertTrue(((VariableSymbol) found).isConstant(), "Variable should be constant");
    }

    @Test
    public void testDefineUninitializedVariable() {
        // enter function scope
        symbolTable.enterFunctionScope(mockVoidType);

        symbolTable.defineVariable("uninitVar", mockValue, mockIntType, false, false, errorReporter,
            1, 1);

        Symbol found = symbolTable.findSymbol("uninitVar", SymbolKind.VARIABLE);
        assertNotNull(found, "Should find defined variable");
        assertFalse(((VariableSymbol) found).isInitialized(), "Variable should be uninitialized");
        assertFalse(((VariableSymbol) found).isReadable(),
            "Uninitialized variable should not be readable");
    }

    @Test
    public void testInitializeVariable() {
        // enter function scope
        symbolTable.enterFunctionScope(mockVoidType);

        // Define uninitialized variable
        symbolTable.defineVariable("x", mockValue, mockIntType, false, false, errorReporter, 1, 1);

        // Initialize it
        boolean result = symbolTable.initializeVariable("x", errorReporter, 2, 1);
        assertTrue(result, "Should successfully initialize variable");

        // Check that the variable is now initialized
        VariableSymbol variable = (VariableSymbol) symbolTable.findSymbol("x", SymbolKind.VARIABLE);
        assertTrue(variable.isInitialized(), "Variable should be initialized");
        assertTrue(variable.isReadable(), "Initialized variable should be readable");
    }

    @Test
    public void testInitializeConstantVariable() {
        // enter function scope
        symbolTable.enterFunctionScope(mockVoidType);

        // Define initialized constant variable
        symbolTable.defineVariable("constVar", mockValue, mockIntType, true, true, errorReporter, 1,
            1);

        // Try to initialize it again (should fail)
        boolean result = symbolTable.initializeVariable("constVar", errorReporter, 2, 1);
        assertFalse(result, "Should fail to reinitialize constant variable");
    }

    @Test
    public void testInitializeNonExistentVariable() {
        // enter function scope
        symbolTable.enterFunctionScope(mockVoidType);

        boolean result = symbolTable.initializeVariable("nonexistent", errorReporter, 1, 1);
        assertFalse(result, "Should fail to initialize non-existent variable");
    }

    @Test
    public void testFindReadableVariable() {
        // enter function scope
        symbolTable.enterFunctionScope(mockVoidType);

        // Define initialized variable
        symbolTable.defineVariable("readableVar", mockValue, mockIntType, false, true,
            errorReporter, 1, 1);

        // Define uninitialized variable
        symbolTable.defineVariable("unreadableVar", mockValue, mockIntType, false, false,
            errorReporter, 2, 1);

        // Find readable variable
        VariableSymbol readable =
            symbolTable.findReadableVariable("readableVar", errorReporter, 3, 1);
        assertNotNull(readable, "Should find readable variable");

        // Find unreadable variable (should fail)
        VariableSymbol unreadable =
            symbolTable.findReadableVariable("unreadableVar", errorReporter, 4, 1);
        assertNull(unreadable, "Should not find unreadable variable");

        // Find non-existent variable (should fail)
        VariableSymbol nonexistent =
            symbolTable.findReadableVariable("nonexistent", errorReporter, 5, 1);
        assertNull(nonexistent, "Should not find non-existent variable");
    }

    @Test
    public void testDefineFunction() {
        List<Parameter> params =
            List.of(new Parameter("a", mockIntType), new Parameter("b", mockIntType));

        // Define function
        boolean result = symbolTable.defineFunction(
            "add", mockValue, mockIntType, params, Visibility.GLOBAL, "test.reso", errorReporter, 1,
            1);

        assertTrue(result, "Should successfully define function");

        // Find the function
        FunctionSymbol function = symbolTable.findFunction("add");
        assertNotNull(function, "Should find defined function");
        assertEquals("add", function.getName(), "Function should have correct name");
        assertEquals(mockIntType, function.getReturnType(),
            "Function should have correct return type");
        assertEquals(2, function.getParameters().size(),
            "Function should have correct number of parameters");
    }

    @Test
    public void testDefineFunctionWithDuplicateName() {
        // Define first function
        symbolTable.defineFunction(
            "duplicate", mockValue, mockIntType, List.of(), Visibility.GLOBAL, "test.reso",
            errorReporter, 1, 1);

        // Try to define another function with the same name
        boolean result = symbolTable.defineFunction(
            "duplicate", mockValue, mockFloatType, List.of(), Visibility.GLOBAL, "test.reso",
            errorReporter, 2, 1);

        assertFalse(result, "Should fail to define function with duplicate name");
    }

    @Test
    public void testFindFunction() {
        // Define function
        symbolTable.defineFunction(
            "calculate", mockValue, mockIntType, List.of(new Parameter("param", mockFloatType)),
            Visibility.GLOBAL, "test.reso", errorReporter, 1, 1);

        // Find the function
        FunctionSymbol function = symbolTable.findFunction("calculate");
        assertNotNull(function, "Should find defined function");

        // Find non-existent function
        FunctionSymbol nonexistent = symbolTable.findFunction("nonexistent");
        assertNull(nonexistent, "Should not find non-existent function");
    }

    @Test
    public void testSymbolVisibility() {
        // Enter function scope
        symbolTable.enterFunctionScope(mockVoidType);

        // Define function parameter
        symbolTable.defineVariable("param", mockValue, mockIntType, false, true, errorReporter, 2,
            1);

        // Check visibility from function scope
        assertNotNull(symbolTable.findSymbol("param", SymbolKind.VARIABLE),
            "Parameter should be visible from function scope");

        // Enter block scope inside function
        symbolTable.enterScope();

        // Define local variable in block
        symbolTable.defineVariable("local", mockValue, mockIntType, false, true, errorReporter, 3,
            1);

        // Check visibility from block scope
        assertNotNull(symbolTable.findSymbol("param", SymbolKind.VARIABLE),
            "Parameter should be visible from block scope");
        assertNotNull(symbolTable.findSymbol("local", SymbolKind.VARIABLE),
            "Local variable should be visible from block scope");

        // Exit block scope
        symbolTable.exitScope(errorReporter, 4, 1);

        // Check visibility from function scope again
        assertNotNull(symbolTable.findSymbol("param", SymbolKind.VARIABLE),
            "Parameter should still be visible");
        assertNull(symbolTable.findSymbol("local", SymbolKind.VARIABLE),
            "Local variable should not be visible after exiting block scope");
    }

    @Test
    public void testFindSymbolWithNonVariableType() {
        // Define function
        symbolTable.defineFunction("func", mockValue, mockVoidType, List.of(), Visibility.GLOBAL,
            "test.reso", errorReporter, 1, 1);

        // Try to find it as a variable
        VariableSymbol varSymbol = symbolTable.findReadableVariable("func", errorReporter, 2, 1);
        assertNull(varSymbol, "Should not find function as a variable");
    }
}