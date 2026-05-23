package com.rfizzle.mercantile.sound;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillagerSoundFilterTest {

    @Test
    void matchesVillagerAmbient() {
        assertTrue(VillagerSoundFilter.isVillagerSound(
                ResourceLocation.parse("minecraft:entity.villager.ambient")));
    }

    @Test
    void matchesVillagerTrade() {
        assertTrue(VillagerSoundFilter.isVillagerSound(
                ResourceLocation.parse("minecraft:entity.villager.trade")));
    }

    @Test
    void matchesVillagerWorkProfessionSound() {
        assertTrue(VillagerSoundFilter.isVillagerSound(
                ResourceLocation.parse("minecraft:entity.villager.work_farmer")));
    }

    @Test
    void matchesVillagerYesAndNo() {
        assertTrue(VillagerSoundFilter.isVillagerSound(
                ResourceLocation.parse("minecraft:entity.villager.yes")));
        assertTrue(VillagerSoundFilter.isVillagerSound(
                ResourceLocation.parse("minecraft:entity.villager.no")));
    }

    @Test
    void rejectsZombieAmbient() {
        assertFalse(VillagerSoundFilter.isVillagerSound(
                ResourceLocation.parse("minecraft:entity.zombie.ambient")));
    }

    @Test
    void rejectsNoteBlockSound() {
        assertFalse(VillagerSoundFilter.isVillagerSound(
                ResourceLocation.parse("minecraft:block.note_block.harp")));
    }

    @Test
    void rejectsModdedNamespace() {
        assertFalse(VillagerSoundFilter.isVillagerSound(
                ResourceLocation.parse("othermod:entity.villager.foo")));
    }

    @Test
    void nullLocationIsNotVillagerSound() {
        assertFalse(VillagerSoundFilter.isVillagerSound(null));
    }

    @Test
    void scaleVolumeFactorZeroSilencesVillager() {
        ResourceLocation loc = ResourceLocation.parse("minecraft:entity.villager.ambient");
        assertEquals(0.0f, VillagerSoundFilter.scaleVolume(1.0f, loc, 0.0f));
    }

    @Test
    void scaleVolumeFactorOneUnchanged() {
        ResourceLocation loc = ResourceLocation.parse("minecraft:entity.villager.ambient");
        assertEquals(0.75f, VillagerSoundFilter.scaleVolume(0.75f, loc, 1.0f));
    }

    @Test
    void scaleVolumeFactorHalfHalvesVillager() {
        ResourceLocation loc = ResourceLocation.parse("minecraft:entity.villager.trade");
        assertEquals(0.5f, VillagerSoundFilter.scaleVolume(1.0f, loc, 0.5f));
    }

    @Test
    void scaleVolumeLeavesNonVillagerUnchanged() {
        ResourceLocation loc = ResourceLocation.parse("minecraft:entity.zombie.ambient");
        assertEquals(1.0f, VillagerSoundFilter.scaleVolume(1.0f, loc, 0.25f));
    }

    @Test
    void scaleVolumeLeavesModdedNamespaceUnchanged() {
        ResourceLocation loc = ResourceLocation.parse("othermod:entity.villager.foo");
        assertEquals(0.8f, VillagerSoundFilter.scaleVolume(0.8f, loc, 0.0f));
    }

    @Test
    void scaleVolumeWithNullLocationPassesThrough() {
        assertEquals(1.0f, VillagerSoundFilter.scaleVolume(1.0f, null, 0.5f));
    }
}
