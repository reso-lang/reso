package com.reso.compiler.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.reso.compiler.symbols.resources.ResourceSymbol;
import com.reso.compiler.types.ResoType;
import com.reso.llvm.api.IrValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the Scope class.
 */
public class ScopeTest {

    @Mock
    private ResoType mockType;

    @Mock
    private IrValue mockValue;

    @BeforeEach
    public void setUp() {
        // Initialize all mocks
        MockitoAnnotations.openMocks(this);

        // Setup common mock behavior
        when(mockType.getName()).thenReturn("i32");
    }

    @Test
    public void testCreateGlobalScope() {
        Scope globalScope = new Scope(null);
        assertNull(globalScope.getParent(), "Global scope should have no parent");
    }

    @Test
    public void testCreateNestedScope() {
        Scope parentScope = new Scope(null);
        Scope childScope = new Scope(parentScope);

        assertSame(parentScope, childScope.getParent(),
            "Child scope should reference parent scope");
    }

    @Test
    public void testAddSymbol() {
        Scope scope = new Scope(null);
        Symbol symbol = new VariableSymbol("x", mockType, mockValue, false, true);

        assertTrue(scope.add(symbol), "Adding new symbol should return true");
        assertFalse(scope.add(symbol), "Adding duplicate symbol should return false");
    }

    @Test
    public void testFindSymbolInCurrentScope() {
        Scope scope = new Scope(null);
        Symbol symbol = new VariableSymbol("x", mockType, mockValue, false, true);
        scope.add(symbol);

        Symbol found = scope.find("x", SymbolKind.VARIABLE);
        assertNotNull(found, "Should find symbol in current scope");
        assertEquals("x", found.getName(), "Found symbol should have correct name");
    }

    @Test
    public void testFindSymbolInParentScope() {
        Scope parentScope = new Scope(null);
        Symbol parentSymbol = new VariableSymbol("x", mockType, mockValue, false, true);
        parentScope.add(parentSymbol);

        Scope childScope = new Scope(parentScope);
        Symbol childSymbol = new VariableSymbol("y", mockType, mockValue, false, true);
        childScope.add(childSymbol);

        Symbol foundParent = childScope.find("x", SymbolKind.VARIABLE);
        assertNotNull(foundParent, "Should find symbol in parent scope");
        assertEquals("x", foundParent.getName(), "Found symbol should have correct name");

        Symbol foundChild = childScope.find("y", SymbolKind.VARIABLE);
        assertNotNull(foundChild, "Should find symbol in current scope");
        assertEquals("y", foundChild.getName(), "Found symbol should have correct name");
    }

    @Test
    public void testSymbolShadowing() {
        Scope parentScope = new Scope(null);
        Symbol parentSymbol = new VariableSymbol("x", mockType, mockValue, false, true);
        parentScope.add(parentSymbol);

        Scope childScope = new Scope(parentScope);
        Symbol childSymbol = new VariableSymbol("x", mockType, mockValue, true, true);
        childScope.add(childSymbol);

        Symbol found = childScope.find("x", SymbolKind.VARIABLE);
        assertNotNull(found, "Should find symbol in current scope");
        assertTrue(((VariableSymbol) found).isConstant(),
            "Should find child scope's version of the symbol");
    }

    @Test
    public void testContainsSymbol() {
        Scope scope = new Scope(null);
        Symbol symbol = new VariableSymbol("x", mockType, mockValue, false, true);
        scope.add(symbol);

        assertTrue(scope.contains("x", SymbolKind.VARIABLE), "Should contain added symbol");
        assertFalse(scope.contains("y", SymbolKind.VARIABLE),
            "Should not contain non-existent symbol");
    }

    @Test
    public void testUpdateSymbol() {
        Scope scope = new Scope(null);
        Symbol originalSymbol = new VariableSymbol("x", mockType, mockValue, false, false);
        scope.add(originalSymbol);

        Symbol updatedSymbol = new VariableSymbol("x", mockType, mockValue, false, true);
        assertTrue(scope.update(updatedSymbol), "Updating existing symbol should return true");

        Symbol found = scope.find("x", SymbolKind.VARIABLE);
        assertTrue(((VariableSymbol) found).isInitialized(),
            "Symbol should be updated to initialized");

        Symbol nonExistentSymbol = new VariableSymbol("z", mockType, mockValue, false, true);
        assertFalse(scope.update(nonExistentSymbol),
            "Updating non-existent symbol should return false");
    }

    @Test
    public void testFindNonExistentSymbol() {
        Scope scope = new Scope(null);
        assertNull(scope.find("nonexistent", SymbolKind.VARIABLE),
            "Finding non-existent symbol should return null");
    }

    @Test
    public void testNestedScopes() {
        // Create a chain of scopes
        Scope globalScope = new Scope(null);
        Scope functionScope = new Scope(globalScope);
        Scope blockScope = new Scope(functionScope);
        Scope innerBlockScope = new Scope(blockScope);

        // Add symbols to different scopes
        globalScope.add(new VariableSymbol("global", mockType, mockValue, true, true));
        functionScope.add(new VariableSymbol("param", mockType, mockValue, false, true));
        blockScope.add(new VariableSymbol("local", mockType, mockValue, false, true));
        innerBlockScope.add(new VariableSymbol("innerLocal", mockType, mockValue, false, true));

        // Test finding from innermost scope
        assertNotNull(innerBlockScope.find("innerLocal", SymbolKind.VARIABLE),
            "Should find symbol in current scope");
        assertNotNull(innerBlockScope.find("local", SymbolKind.VARIABLE),
            "Should find symbol in parent scope");
        assertNotNull(innerBlockScope.find("param", SymbolKind.VARIABLE),
            "Should find symbol in grandparent scope");
        assertNotNull(innerBlockScope.find("global", SymbolKind.VARIABLE),
            "Should find symbol in global scope");

        // Test finding from middle scope
        assertNull(blockScope.find("innerLocal", SymbolKind.VARIABLE),
            "Should not find symbol from child scope");
        assertNotNull(blockScope.find("local", SymbolKind.VARIABLE),
            "Should find symbol in current scope");
        assertNotNull(blockScope.find("param", SymbolKind.VARIABLE),
            "Should find symbol in parent scope");
        assertNotNull(blockScope.find("global", SymbolKind.VARIABLE),
            "Should find symbol in global scope");
    }

    @Test
    public void testDifferentSymbolTypes() {
        Scope scope = new Scope(null);

        // Create different types of symbols
        VariableSymbol varSymbol = new VariableSymbol("x", mockType, mockValue, false, true);

        // Mock a function symbol
        FunctionSymbol funcSymbol = Mockito.mock(FunctionSymbol.class);
        when(funcSymbol.getName()).thenReturn("x");
        when(funcSymbol.getKind()).thenReturn(SymbolKind.FUNCTION);

        // Mock a resource symbol
        ResourceSymbol resSymbol = Mockito.mock(ResourceSymbol.class);
        when(resSymbol.getName()).thenReturn("x");
        when(resSymbol.getKind()).thenReturn(SymbolKind.RESOURCE);

        // Add symbols
        scope.add(varSymbol);
        scope.add(funcSymbol);
        scope.add(resSymbol);

        // Test finding by name
        Symbol foundVar = scope.find("x", SymbolKind.VARIABLE);
        assertNotNull(foundVar, "Should find variable symbol");
        assertEquals(SymbolKind.VARIABLE, foundVar.getKind(), "Symbol should be a variable");

        Symbol foundFunc = scope.find("x", SymbolKind.FUNCTION);
        assertNotNull(foundFunc, "Should find function symbol");
        assertEquals(SymbolKind.FUNCTION, foundFunc.getKind(), "Symbol should be a function");

        Symbol foundRes = scope.find("x", SymbolKind.RESOURCE);
        assertNotNull(foundRes, "Should find resource symbol");
        assertEquals(SymbolKind.RESOURCE, foundRes.getKind(), "Symbol should be a resource");
    }
}