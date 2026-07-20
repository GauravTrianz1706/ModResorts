package com.acme.modres.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CustomPermissionTest {

    @Test
    void testConstructor_withName() {
        CustomPermission permission = new CustomPermission("testPermission");
        assertNotNull(permission);
        assertEquals("testPermission", permission.getName());
    }

    @Test
    void testConstructor_withNameAndActions() {
        CustomPermission permission = new CustomPermission("testPermission", "read,write");
        assertNotNull(permission);
        assertEquals("testPermission", permission.getName());
    }

    @Test
    void testGetName() {
        CustomPermission permission = new CustomPermission("myPermission");
        assertEquals("myPermission", permission.getName());
    }

    @Test
    void testConstructor_withEmptyName() {
        CustomPermission permission = new CustomPermission("");
        assertNotNull(permission);
        assertEquals("", permission.getName());
    }

    @Test
    void testConstructor_withNullActions() {
        CustomPermission permission = new CustomPermission("testPermission", null);
        assertNotNull(permission);
    }

    @Test
    void testConstructor_withEmptyActions() {
        CustomPermission permission = new CustomPermission("testPermission", "");
        assertNotNull(permission);
    }

    @Test
    void testConstructor_withMultipleActions() {
        CustomPermission permission = new CustomPermission("testPermission", "read,write,execute");
        assertNotNull(permission);
        assertEquals("testPermission", permission.getName());
    }

    @Test
    void testConstructor_extendsBasicPermission() {
        CustomPermission permission = new CustomPermission("test");
        assertTrue(permission instanceof java.security.BasicPermission);
    }

    @Test
    void testConstructor_withDifferentNames() {
        CustomPermission perm1 = new CustomPermission("permission1");
        CustomPermission perm2 = new CustomPermission("permission2");
        
        assertNotEquals(perm1.getName(), perm2.getName());
    }

    @Test
    void testConstructor_withSpecialCharacters() {
        CustomPermission permission = new CustomPermission("test.permission.*");
        assertEquals("test.permission.*", permission.getName());
    }
}
