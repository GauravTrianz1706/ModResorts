package com.acme.modres.mbean;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpMetadataListTest {

    private OpMetadataList opMetadataList;

    @BeforeEach
    void setUp() {
        opMetadataList = new OpMetadataList();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(opMetadataList);
        assertNotNull(opMetadataList.getOpMetadatList());
    }

    @Test
    void testGetOpMetadatList_returnsNonNull() {
        List<OpMetadata> list = opMetadataList.getOpMetadatList();
        assertNotNull(list);
    }

    @Test
    void testGetOpMetadatList_initiallyEmpty() {
        List<OpMetadata> list = opMetadataList.getOpMetadatList();
        assertTrue(list.isEmpty());
    }

    @Test
    void testAdd_singleElement() {
        OpMetadata op = new OpMetadata("test", "description", "void", 1);
        opMetadataList.add(op);
        
        assertEquals(1, opMetadataList.getOpMetadatList().size());
    }

    @Test
    void testAdd_multipleElements() {
        opMetadataList.add(new OpMetadata("op1", "desc1", "void", 1));
        opMetadataList.add(new OpMetadata("op2", "desc2", "String", 2));
        opMetadataList.add(new OpMetadata("op3", "desc3", "int", 3));
        
        assertEquals(3, opMetadataList.getOpMetadatList().size());
    }

    @Test
    void testAdd_preservesOrder() {
        OpMetadata op1 = new OpMetadata("first", "First op", "void", 1);
        OpMetadata op2 = new OpMetadata("second", "Second op", "void", 1);
        
        opMetadataList.add(op1);
        opMetadataList.add(op2);
        
        List<OpMetadata> list = opMetadataList.getOpMetadatList();
        assertEquals("first", list.get(0).getName());
        assertEquals("second", list.get(1).getName());
    }

    @Test
    void testSetOpMetadatList() {
        List<OpMetadata> newList = new ArrayList<>();
        newList.add(new OpMetadata("op1", "desc1", "void", 1));
        
        opMetadataList.setOpMetadatList(newList);
        
        assertEquals(1, opMetadataList.getOpMetadatList().size());
    }

    @Test
    void testSetOpMetadatList_replacesExisting() {
        opMetadataList.add(new OpMetadata("old", "old desc", "void", 1));
        
        List<OpMetadata> newList = new ArrayList<>();
        newList.add(new OpMetadata("new", "new desc", "void", 1));
        
        opMetadataList.setOpMetadatList(newList);
        
        assertEquals(1, opMetadataList.getOpMetadatList().size());
        assertEquals("new", opMetadataList.getOpMetadatList().get(0).getName());
    }

    @Test
    void testSetOpMetadatList_withEmptyList() {
        opMetadataList.add(new OpMetadata("op", "desc", "void", 1));
        
        opMetadataList.setOpMetadatList(new ArrayList<>());
        
        assertTrue(opMetadataList.getOpMetadatList().isEmpty());
    }

    @Test
    void testAdd_withNullElement() {
        opMetadataList.add(null);
        
        assertEquals(1, opMetadataList.getOpMetadatList().size());
        assertNull(opMetadataList.getOpMetadatList().get(0));
    }

    @Test
    void testGetOpMetadatList_returnsModifiableList() {
        List<OpMetadata> list = opMetadataList.getOpMetadatList();
        list.add(new OpMetadata("test", "test", "void", 1));
        
        assertEquals(1, opMetadataList.getOpMetadatList().size());
    }

    @Test
    void testAdd_multipleNullElements() {
        opMetadataList.add(null);
        opMetadataList.add(null);
        
        assertEquals(2, opMetadataList.getOpMetadatList().size());
    }
}
