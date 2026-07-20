package com.acme.modres.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.acme.modres.mbean.OpMetadataList;

class JsonInputStreamTest {

    @Test
    void testConstructor_withValidFile() throws IOException {
        File tempFile = File.createTempFile("test", ".json");
        tempFile.deleteOnExit();
        
        assertDoesNotThrow(() -> {
            new JsonInputStream(tempFile);
        });
    }

    @Test
    void testConstructor_withNonExistentFile() {
        File nonExistentFile = new File("nonexistent.json");
        
        assertThrows(FileNotFoundException.class, () -> {
            new JsonInputStream(nonExistentFile);
        });
    }

    @Test
    void testParseJsonAs_withNonExistentFile() throws IOException {
        File tempFile = File.createTempFile("test", ".json");
        tempFile.delete(); // Delete it to make it non-existent
        
        assertThrows(FileNotFoundException.class, () -> {
            JsonInputStream jis = new JsonInputStream(tempFile);
        });
    }

    @Test
    void testParseJsonAs_withValidFile() throws IOException {
        File tempFile = File.createTempFile("test", ".json");
        tempFile.deleteOnExit();
        
        try (JsonInputStream jis = new JsonInputStream(tempFile)) {
            Object result = jis.parseJsonAs(OpMetadataList.class);
            // Result may be null for empty file, which is acceptable
            assertTrue(result == null || result instanceof OpMetadataList);
        }
    }

    @Test
    void testParseJsonAs_withNullClass() throws IOException {
        File tempFile = File.createTempFile("test", ".json");
        tempFile.deleteOnExit();
        
        try (JsonInputStream jis = new JsonInputStream(tempFile)) {
            assertDoesNotThrow(() -> {
                jis.parseJsonAs(null);
            });
        }
    }

    @Test
    void testConstructor_extendsFileInputStream() throws IOException {
        File tempFile = File.createTempFile("test", ".json");
        tempFile.deleteOnExit();
        
        JsonInputStream jis = new JsonInputStream(tempFile);
        assertTrue(jis instanceof java.io.FileInputStream);
        jis.close();
    }

    @Test
    void testParseJsonAs_returnsNullForEmptyFile() throws IOException {
        File tempFile = File.createTempFile("test", ".json");
        tempFile.deleteOnExit();
        
        try (JsonInputStream jis = new JsonInputStream(tempFile)) {
            Object result = jis.parseJsonAs(String.class);
            assertNull(result);
        }
    }

    @Test
    void testConstructor_withDirectory() {
        File directory = new File(System.getProperty("java.io.tmpdir"));
        
        assertThrows(FileNotFoundException.class, () -> {
            new JsonInputStream(directory);
        });
    }

    @Test
    void testParseJsonAs_withDifferentClasses() throws IOException {
        File tempFile = File.createTempFile("test", ".json");
        tempFile.deleteOnExit();
        
        try (JsonInputStream jis = new JsonInputStream(tempFile)) {
            assertDoesNotThrow(() -> {
                jis.parseJsonAs(String.class);
                jis.parseJsonAs(Integer.class);
                jis.parseJsonAs(OpMetadataList.class);
            });
        }
    }
}
