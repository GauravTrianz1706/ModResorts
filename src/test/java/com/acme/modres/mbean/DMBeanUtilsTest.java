package com.acme.modres.mbean;

import static org.junit.jupiter.api.Assertions.*;

import javax.management.MBeanOperationInfo;

import org.junit.jupiter.api.Test;

class DMBeanUtilsTest {

    @Test
    void testGetOps_withNullOpList_returnsNull() {
        MBeanOperationInfo[] result = DMBeanUtils.getOps(null);
        assertNull(result);
    }

    @Test
    void testGetOps_withNullOpMetadataList_returnsNull() {
        OpMetadataList opList = new OpMetadataList();
        opList.setOpMetadatList(null);
        
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        assertNull(result);
    }

    @Test
    void testGetOps_withEmptyOpList_returnsNull() {
        OpMetadataList opList = new OpMetadataList();
        
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        assertNull(result);
    }

    @Test
    void testGetOps_withSingleOperation() {
        OpMetadataList opList = new OpMetadataList();
        OpMetadata op = new OpMetadata("testOp", "Test operation", "void", 1);
        opList.add(op);
        
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("testOp", result[0].getName());
        assertEquals("Test operation", result[0].getDescription());
    }

    @Test
    void testGetOps_withMultipleOperations() {
        OpMetadataList opList = new OpMetadataList();
        opList.add(new OpMetadata("op1", "Operation 1", "void", 1));
        opList.add(new OpMetadata("op2", "Operation 2", "String", 2));
        opList.add(new OpMetadata("op3", "Operation 3", "int", 3));
        
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        
        assertNotNull(result);
        assertEquals(3, result.length);
    }

    @Test
    void testGetOps_preservesOperationOrder() {
        OpMetadataList opList = new OpMetadataList();
        opList.add(new OpMetadata("first", "First op", "void", 1));
        opList.add(new OpMetadata("second", "Second op", "void", 1));
        
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        
        assertEquals("first", result[0].getName());
        assertEquals("second", result[1].getName());
    }

    @Test
    void testGetOps_preservesOperationMetadata() {
        OpMetadataList opList = new OpMetadataList();
        OpMetadata op = new OpMetadata("testOp", "Test description", "String", 2);
        opList.add(op);
        
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        
        assertEquals("testOp", result[0].getName());
        assertEquals("Test description", result[0].getDescription());
        assertEquals("String", result[0].getReturnType());
        assertEquals(2, result[0].getImpact());
    }

    @Test
    void testGetOps_withDifferentImpactValues() {
        OpMetadataList opList = new OpMetadataList();
        opList.add(new OpMetadata("op1", "Op 1", "void", 0));
        opList.add(new OpMetadata("op2", "Op 2", "void", 1));
        opList.add(new OpMetadata("op3", "Op 3", "void", 2));
        
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        
        assertEquals(0, result[0].getImpact());
        assertEquals(1, result[1].getImpact());
        assertEquals(2, result[2].getImpact());
    }

    @Test
    void testGetOps_withDifferentReturnTypes() {
        OpMetadataList opList = new OpMetadataList();
        opList.add(new OpMetadata("op1", "Op 1", "void", 1));
        opList.add(new OpMetadata("op2", "Op 2", "String", 1));
        opList.add(new OpMetadata("op3", "Op 3", "int", 1));
        
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        
        assertEquals("void", result[0].getReturnType());
        assertEquals("String", result[1].getReturnType());
        assertEquals("int", result[2].getReturnType());
    }
}
