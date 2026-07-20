package com.acme.modres.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipValidatorTest {

    @TempDir
    File tempDir;

    @Test
    void testConstructor_withValidZipFile() throws IOException {
        File zipFile = createValidZipFile();
        
        assertDoesNotThrow(() -> {
            new ZipValidator(zipFile);
        });
    }

    @Test
    void testConstructor_withNonExistentFile() {
        File nonExistentFile = new File(tempDir, "nonexistent.zip");
        
        assertThrows(IOException.class, () -> {
            new ZipValidator(nonExistentFile);
        });
    }

    @Test
    void testConstructor_withInvalidZipFile() throws IOException {
        File invalidZip = new File(tempDir, "invalid.zip");
        try (FileOutputStream fos = new FileOutputStream(invalidZip)) {
            fos.write("This is not a zip file".getBytes());
        }
        
        assertThrows(ZipException.class, () -> {
            new ZipValidator(invalidZip);
        });
    }

    @Test
    void testIsValid_withValidZipFile() throws IOException {
        File zipFile = createValidZipFile();
        
        try (ZipValidator validator = new ZipValidator(zipFile)) {
            boolean result = validator.isValid();
            assertTrue(result);
        }
    }

    @Test
    void testIsValid_withEmptyZipFile() throws IOException {
        File zipFile = createEmptyZipFile();
        
        try (ZipValidator validator = new ZipValidator(zipFile)) {
            boolean result = validator.isValid();
            assertTrue(result);
        }
    }

    @Test
    void testConstructor_extendsZipFile() throws IOException {
        File zipFile = createValidZipFile();
        
        ZipValidator validator = new ZipValidator(zipFile);
        assertTrue(validator instanceof java.util.zip.ZipFile);
        validator.close();
    }

    @Test
    void testIsValid_withNonExistentFileAfterCreation() throws IOException {
        File zipFile = createValidZipFile();
        
        try (ZipValidator validator = new ZipValidator(zipFile)) {
            zipFile.delete();
            boolean result = validator.isValid();
            assertFalse(result);
        }
    }

    @Test
    void testIsValid_withMultipleEntries() throws IOException {
        File zipFile = createZipFileWithMultipleEntries();
        
        try (ZipValidator validator = new ZipValidator(zipFile)) {
            boolean result = validator.isValid();
            assertTrue(result);
        }
    }

    @Test
    void testConstructor_withDirectory() {
        assertThrows(IOException.class, () -> {
            new ZipValidator(tempDir);
        });
    }

    private File createValidZipFile() throws IOException {
        File zipFile = new File(tempDir, "valid.zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = new ZipEntry("test.txt");
            zos.putNextEntry(entry);
            zos.write("Test content".getBytes());
            zos.closeEntry();
        }
        return zipFile;
    }

    private File createEmptyZipFile() throws IOException {
        File zipFile = new File(tempDir, "empty.zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            // Create empty zip file
        }
        return zipFile;
    }

    private File createZipFileWithMultipleEntries() throws IOException {
        File zipFile = new File(tempDir, "multiple.zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (int i = 0; i < 3; i++) {
                ZipEntry entry = new ZipEntry("file" + i + ".txt");
                zos.putNextEntry(entry);
                zos.write(("Content " + i).getBytes());
                zos.closeEntry();
            }
        }
        return zipFile;
    }
}
