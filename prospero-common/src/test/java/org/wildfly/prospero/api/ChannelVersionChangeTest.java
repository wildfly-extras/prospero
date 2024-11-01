package org.wildfly.prospero.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChannelVersionChangeTest {

    @Test
    public void printModifiedVersionWithPhysicalVersion_PhysicalVersionOnly() throws Exception {
        final ChannelVersionChange change = new ChannelVersionChange.Builder("test")
                .setOldPhysicalVersion("1.0.0")
                .setNewPhysicalVersion("1.0.1")
                .build();

        assertEquals("test: 1.0.0 -> 1.0.1", change.shortDescription());
    }

    @Test
    public void printModifiedVersionWithLogicalVersion_LogicalAndPhysicalVersion() throws Exception {
        final ChannelVersionChange change = new ChannelVersionChange.Builder("test")
                .setOldPhysicalVersion("1.0.0")
                .setOldLogicalVersion("Update 1.0")
                .setNewPhysicalVersion("1.0.1")
                .setNewLogicalVersion("Update 1.1")
                .build();

        assertEquals("test: Update 1.0 -> Update 1.1", change.shortDescription());
    }

    @Test
    public void printModifiedVersionWithPhysicalVersion_NewLogicalVersionOnly() throws Exception {
        final ChannelVersionChange change = new ChannelVersionChange.Builder("test")
                .setOldPhysicalVersion("1.0.0")
                .setNewPhysicalVersion("1.0.1")
                .setNewLogicalVersion("Update 1.1")
                .build();

        assertEquals("test: 1.0.0 -> 1.0.1", change.shortDescription());
    }

    @Test
    public void printModifiedVersionWithPhysicalVersion_OldLogicalVersionOnly() throws Exception {
        final ChannelVersionChange change = new ChannelVersionChange.Builder("test")
                .setOldPhysicalVersion("1.0.0")
                .setNewPhysicalVersion("1.0.1")
                .setOldLogicalVersion("Update 1.0")
                .build();

        assertEquals("test: 1.0.0 -> 1.0.1", change.shortDescription());
    }

    @Test
    public void printAddedVersionWithPhysicalVersion_PhysicalVersionOnly() throws Exception {
        final ChannelVersionChange change = new ChannelVersionChange.Builder("test")
                .setNewPhysicalVersion("1.0.1")
                .build();

        assertEquals("test: [] -> 1.0.1", change.shortDescription());
    }

    @Test
    public void printAddedVersionWithPhysicalVersion_PhysicalAndLogicalVersion() throws Exception {
        final ChannelVersionChange change = new ChannelVersionChange.Builder("test")
                .setNewPhysicalVersion("1.0.1")
                .setNewLogicalVersion("Update 1.1")
                .build();

        assertEquals("test: [] -> Update 1.1", change.shortDescription());
    }

    @Test
    public void printRemovedVersionWithPhysicalVersion_PhysicalVersionOnly() throws Exception {
        final ChannelVersionChange change = new ChannelVersionChange.Builder("test")
                .setOldPhysicalVersion("1.0.0")
                .build();

        assertEquals("test: 1.0.0 -> []", change.shortDescription());
    }

    @Test
    public void printRemovedVersionWithPhysicalVersion_PhysicalAndLogicalVersion() throws Exception {
        final ChannelVersionChange change = new ChannelVersionChange.Builder("test")
                .setOldPhysicalVersion("1.0.0")
                .setOldLogicalVersion("Update 1.0")
                .build();

        assertEquals("test: Update 1.0 -> []", change.shortDescription());
    }

    @Test
    public void isDowngrade_IfPhysicalVersionsAreSet() throws Exception {
        final ChannelVersionChange change = new ChannelVersionChange.Builder("test")
                .setOldPhysicalVersion("1.0.1")
                .setNewPhysicalVersion("1.0.0")
                .setOldLogicalVersion("Update 1.0")
                .build();

        assertTrue(change.isDowngrade());
    }

    @Test
    public void addedChannelIsNotDowngrade() throws Exception {
        final ChannelVersionChange change = new ChannelVersionChange.Builder("test")
                .setNewPhysicalVersion("1.0.0")
                .setOldLogicalVersion("Update 1.0")
                .build();

        assertFalse(change.isDowngrade());
    }

    @Test
    public void removedChannelIsNotDowngrade() throws Exception {
        final ChannelVersionChange change = new ChannelVersionChange.Builder("test")
                .setNewPhysicalVersion("1.0.0")
                .setOldLogicalVersion("Update 1.0")
                .build();

        assertFalse(change.isDowngrade());
    }
}