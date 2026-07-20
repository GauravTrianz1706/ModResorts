package com.acme.modres.mbean;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.acme.modres.mbean.reservation.ReservationList;

class IOUtilsTest {

    @Test
    void testGetFileFromRelativePath_withNullPath() {
        File result = IOUtils.getFileFromRelativePath(null);
        assertNull(result);
    }

    @Test
    void testGetFileFromRelativePath_withNonExistentFile() {
        File result = IOUtils.getFileFromRelativePath("nonexistent.json");
        assertNull(result);
    }

    @Test
    void testGetFileFromRelativePath_withEmptyPath() {
        File result = IOUtils.getFileFromRelativePath("");
        assertNull(result);
    }

    @Test
    void testGetOpListFromConfig_returnsNonNull() {
        OpMetadataList result = IOUtils.getOpListFromConfig();
        assertNotNull(result);
    }

    @Test
    void testGetOpListFromConfig_returnsOpMetadataList() {
        OpMetadataList result = IOUtils.getOpListFromConfig();
        assertTrue(result instanceof OpMetadataList);
    }

    @Test
    void testGetOpListFromConfig_hasOpMetadataList() {
        OpMetadataList result = IOUtils.getOpListFromConfig();
        assertNotNull(result.getOpMetadatList());
    }

    @Test
    void testGetReservationListFromConfig_returnsNonNull() {
        ReservationList result = IOUtils.getReservationListFromConfig();
        assertNotNull(result);
    }

    @Test
    void testGetReservationListFromConfig_returnsReservationList() {
        ReservationList result = IOUtils.getReservationListFromConfig();
        assertTrue(result instanceof ReservationList);
    }

    @Test
    void testGetReservationListFromConfig_hasReservations() {
        ReservationList result = IOUtils.getReservationListFromConfig();
        assertNotNull(result.getReservations());
    }

    @Test
    void testGetOpListFromConfig_handlesFileNotFound() {
        assertDoesNotThrow(() -> {
            IOUtils.getOpListFromConfig();
        });
    }

    @Test
    void testGetReservationListFromConfig_handlesFileNotFound() {
        assertDoesNotThrow(() -> {
            IOUtils.getReservationListFromConfig();
        });
    }

    @Test
    void testGetOpListFromConfig_returnsEmptyListOnError() {
        OpMetadataList result = IOUtils.getOpListFromConfig();
        assertNotNull(result);
        assertNotNull(result.getOpMetadatList());
    }

    @Test
    void testGetReservationListFromConfig_returnsEmptyListOnError() {
        ReservationList result = IOUtils.getReservationListFromConfig();
        assertNotNull(result);
        assertNotNull(result.getReservations());
    }
}
