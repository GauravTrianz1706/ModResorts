package com.acme.modres.mbean;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpMetadataTest {

    private OpMetadata opMetadata;

    @BeforeEach
    void setUp() {
        opMetadata = new OpMetadata();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(opMetadata);
    }

    @Test
    void testParameterizedConstructor() {
        OpMetadata op = new OpMetadata("testOp", "Test operation", "void", 1);
        
        assertEquals("testOp", op.getName());
        assertEquals("Test operation", op.getDescription());
        assertEquals("void", op.getType());
        assertEquals(1, op.getImpact());
    }

    @Test
    void testSetName() {
        opMetadata.setName("operationName");
        assertEquals("operationName", opMetadata.getName());
    }

    @Test
    void testGetName() {
        opMetadata.setName("testName");
        assertEquals("testName", opMetadata.getName());
    }

    @Test
    void testSetDescription() {
        opMetadata.setDescription("Test description");
        assertEquals("Test description", opMetadata.getDescription());
    }

    @Test
    void testGetDescription() {
        opMetadata.setDescription("description");
        assertEquals("description", opMetadata.getDescription());
    }

    @Test
    void testSetType() {
        opMetadata.setType("String");
        assertEquals("String", opMetadata.getType());
    }

    @Test
    void testGetType() {
        opMetadata.setType("int");
        assertEquals("int", opMetadata.getType());
    }

    @Test
    void testSetImpact() {
        opMetadata.setImpact(2);
        assertEquals(2, opMetadata.getImpact());
    }

    @Test
    void testGetImpact() {
        opMetadata.setImpact(3);
        assertEquals(3, opMetadata.getImpact());
    }

    @Test
    void testSetName_withNull() {
        opMetadata.setName(null);
        assertNull(opMetadata.getName());
    }

    @Test
    void testSetDescription_withNull() {
        opMetadata.setDescription(null);
        assertNull(opMetadata.getDescription());
    }

    @Test
    void testSetType_withNull() {
        opMetadata.setType(null);
        assertNull(opMetadata.getType());
    }

    @Test
    void testSetImpact_withZero() {
        opMetadata.setImpact(0);
        assertEquals(0, opMetadata.getImpact());
    }

    @Test
    void testSetImpact_withNegative() {
        opMetadata.setImpact(-1);
        assertEquals(-1, opMetadata.getImpact());
    }

    @Test
    void testParameterizedConstructor_withNullValues() {
        OpMetadata op = new OpMetadata(null, null, null, 0);
        
        assertNull(op.getName());
        assertNull(op.getDescription());
        assertNull(op.getType());
        assertEquals(0, op.getImpact());
    }

    @Test
    void testSetName_withEmptyString() {
        opMetadata.setName("");
        assertEquals("", opMetadata.getName());
    }

    @Test
    void testSetDescription_withEmptyString() {
        opMetadata.setDescription("");
        assertEquals("", opMetadata.getDescription());
    }
}
