package com.reso.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for resource visibility rules in Reso.
 * Tests the visibility system where:
 * - Default field visibility is fileprivate
 * - Resource initializer visibility is determined by field visibilities
 * - If any field is fileprivate, the initializer is fileprivate
 * - If all fields are public, the initializer is public
 */
public class ResourceVisibilityTest extends BaseTest {

    // ============================================================================
    // Field Visibility Tests
    // ============================================================================

    @Test
    public void testDefaultFieldVisibilityIsFileprivate() {
        String sourceCode = """
            resource DefaultResource{var value: i32}  # Default should be fileprivate
            
            def main() -> i32:
                var res: DefaultResource = DefaultResource{42}
                return res.value  # Should work - same file
            """;

        String ir = compileAndExpectSuccess(sourceCode, "default_field_visibility");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.fieldAccess("value", "DefaultResource_struct", "res", 0));
    }

    @Test
    public void testExplicitPublicFieldVisibility() {
        String sourceCode = """
            resource PublicResource{pub var value: i32}
            
            def main() -> i32:
                var res: PublicResource = PublicResource{42}
                return res.value
            """;

        String ir = compileAndExpectSuccess(sourceCode, "explicit_public_field_visibility");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.fieldAccess("value", "PublicResource_struct", "res", 0));
    }

    @Test
    public void testMixedFieldVisibilities() {
        String sourceCode = """
            resource MixedResource{pub const public_field: i32, var private_field: i32}
            
            def main() -> i32:
                var res: MixedResource = MixedResource{42, 24}
                return res.public_field + res.private_field  # Should work - same file
            """;

        String ir = compileAndExpectSuccess(sourceCode, "mixed_field_visibilities");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContainsInOrder(mainFunc,
            IrPatterns.fieldAccess("public_field", "MixedResource_struct", "res", 0),
            IrPatterns.fieldAccess("private_field", "MixedResource_struct", "res", 1)
        );
    }

    // ============================================================================
    // Resource Initializer Visibility Tests
    // ============================================================================

    @Test
    public void testAllPublicFieldsPublicInitializer() {
        String sourceCode = """
            resource AllPublicResource{pub const field1: i32, pub var field2: String}
            
            def main() -> i32:
                var res: AllPublicResource = AllPublicResource{42, "test"}
                return res.field1
            """;

        String ir = compileAndExpectSuccess(sourceCode, "all_public_fields_public_initializer");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.malloc("AllPublicResource_instance", "AllPublicResource_struct"));
    }

    @Test
    public void testAllFileprivateFieldsFileprivateInitializer() {
        String sourceCode = """
            resource AllFileprivateResource{const field1: i32, var field2: String}  # Default fileprivate
            
            def main() -> i32:
                var res: AllFileprivateResource = AllFileprivateResource{42, "test"}
                return 0
            """;

        String ir =
            compileAndExpectSuccess(sourceCode, "all_fileprivate_fields_fileprivate_initializer");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.malloc("AllFileprivateResource_instance", "AllFileprivateResource_struct"));
    }

    @Test
    public void testMixedFieldsFileprivateInitializer() {
        String sourceCode = """
            resource MixedFieldsResource{pub var public_field: i32, var private_field: String}  # Mixed = fileprivate
            
            def main() -> i32:
                var res: MixedFieldsResource = MixedFieldsResource{42, "secret"}
                return res.public_field
            """;

        String ir = compileAndExpectSuccess(sourceCode, "mixed_fields_fileprivate_initializer");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.malloc("MixedFieldsResource_instance", "MixedFieldsResource_struct"));
    }

    // ============================================================================
    // Cross-File Visibility Tests
    // ============================================================================

    @Test
    public void testEmptyResourceVisibility() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("resource.reso", """
                resource EmptyResource{}  # No fields = public initializer
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    var empty: EmptyResource = EmptyResource{}  # Should work
                    return 0
                """)
            .build();

        String ir = compileMultipleFilesAndExpectSuccess(sourceFiles, "empty_resource_visibility");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.atomicMalloc("EmptyResource_instance", "EmptyResource_struct"));
    }

    @Test
    public void testPublicResourceInitializerCrossFile() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("library.reso", """
                resource PublicLibraryResource{pub const data: i32}
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    var lib: PublicLibraryResource = PublicLibraryResource{42}  # Should work
                    return lib.data
                """)
            .build();

        String ir = compileMultipleFilesAndExpectSuccess(sourceFiles, "public_resource_cross_file");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc, IrPatterns.atomicMalloc("PublicLibraryResource_instance",
            "PublicLibraryResource_struct"));
    }

    @Test
    public void testFileprivateResourceInitializerCrossFileFails() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("library.reso", """
                resource FileprivateLibraryResource{var data: i32}  # Default fileprivate
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    var lib: FileprivateLibraryResource = FileprivateLibraryResource{42}  # Should fail
                    return 0
                """)
            .build();

        String errors = compileMultipleFilesAndExpectFailure(sourceFiles,
            "fileprivate_resource_cross_file_fails");

        assertTrue(errors.contains("fileprivate") && errors.contains("not accessible"),
            "Should report inaccessible fileprivate resource initializer: " + errors);
    }

    @Test
    public void testMixedResourceInitializerCrossFileFails() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("library.reso", """
                resource MixedLibraryResource{pub var public_data: i32, var private_data: String}  # Mixed = fileprivate
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    var lib: MixedLibraryResource = MixedLibraryResource{42, "secret"}  # Should fail
                    return 0
                """)
            .build();

        String errors =
            compileMultipleFilesAndExpectFailure(sourceFiles, "mixed_resource_cross_file_fails");

        assertTrue(errors.contains("fileprivate") && errors.contains("not accessible"),
            "Should report inaccessible fileprivate resource initializer: " + errors);
    }

    @Test
    public void testPublicFieldAccessCrossFile() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("library.reso", """
                resource CrossFileResource{pub var public_field: i32, var private_field: String}
                
                pub def create() -> CrossFileResource:
                    return CrossFileResource{42, "secret"}
                """)
            .addFile("main.reso", """
                def main() -> i32:
                    var res = create()
                    return res.public_field  # Should work - public field
                """)
            .build();

        String ir =
            compileMultipleFilesAndExpectSuccess(sourceFiles, "public_field_access_cross_file");

        String mainFunc = extractFunction(ir, "main");
        assertNotNull(mainFunc, "Main function should be present in the IR");

        assertIrContains(mainFunc,
            IrPatterns.fieldAccess("public_field", "CrossFileResource_struct", "res", 0));
    }

    @Test
    public void testFileprivateFieldAccessCrossFileFails() {
        var sourceFiles = new MultiFileBuilder()
            .addFile("library.reso", """
                resource CrossFileResource{pub var public_field: i32, var private_field: String}
                
                pub def create() -> CrossFileResource:
                    return CrossFileResource{42, "secret"}
                """)
            .addFile("main.reso", """
                def tryToAccessPrivate() -> String:
                    var res: CrossFileResource = create()
                    return res.private_field  # Should fail - fileprivate field
                """)
            .build();

        String errors = compileMultipleFilesAndExpectFailure(sourceFiles,
            "fileprivate_field_access_cross_file_fails");

        assertTrue(errors.contains("private_field") && errors.contains("not accessible"),
            "Should report inaccessible fileprivate field: " + errors);
    }
}