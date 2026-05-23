package com.rfizzle.mercantile.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SentryPylonScannerTest {

    // --- isHostileClassification ---

    @Test
    void deadEntityIsNotHostile() {
        assertFalse(SentryPylonScanner.isHostileClassification(
                MobCategory.MONSTER, false, true, false, false),
                "dead entity should never be hostile even if classified as Enemy");
    }

    @Test
    void ironGolemIsNotHostile() {
        assertFalse(SentryPylonScanner.isHostileClassification(
                MobCategory.MONSTER, true, false, true, false),
                "iron golem is never a hostile target");
    }

    @Test
    void sentryGolemIsNotHostile() {
        assertFalse(SentryPylonScanner.isHostileClassification(
                MobCategory.MONSTER, true, true, false, true),
                "sentry-tagged entity is never a hostile target");
    }

    @Test
    void enemyMobIsHostile() {
        assertTrue(SentryPylonScanner.isHostileClassification(
                MobCategory.CREATURE, true, true, false, false),
                "Enemy interface implementer (e.g. neutral wolf-like) is hostile");
    }

    @Test
    void monsterCategoryIsHostile() {
        assertTrue(SentryPylonScanner.isHostileClassification(
                MobCategory.MONSTER, true, false, false, false),
                "MONSTER category mob without Enemy interface is still hostile");
    }

    @Test
    void passiveAnimalIsNotHostile() {
        assertFalse(SentryPylonScanner.isHostileClassification(
                MobCategory.CREATURE, true, false, false, false),
                "passive animal should not be hostile");
    }

    @Test
    void ambientMobIsNotHostile() {
        assertFalse(SentryPylonScanner.isHostileClassification(
                MobCategory.AMBIENT, true, false, false, false),
                "ambient mob (bat) should not be hostile");
    }

    @Test
    void waterCreatureIsNotHostile() {
        assertFalse(SentryPylonScanner.isHostileClassification(
                MobCategory.WATER_CREATURE, true, false, false, false),
                "water creature should not be hostile");
    }

    @Test
    void sentryFlagOverridesEnemyAndMonsterCategory() {
        // A sentry golem could be classified as Enemy=true in some configurations;
        // the sentry flag must short-circuit before the enemy/category check.
        assertFalse(SentryPylonScanner.isHostileClassification(
                MobCategory.MONSTER, true, true, false, true),
                "sentry flag must short-circuit even when Enemy and MONSTER category match");
    }

    // --- isWithinRadius ---

    @Test
    void withinRadius_pointAtCenter() {
        BlockPos pylon = new BlockPos(0, 0, 0);
        assertTrue(SentryPylonScanner.isWithinRadius(pylon, pylon, 16L));
    }

    @Test
    void withinRadius_pointOnBoundary() {
        BlockPos pylon = new BlockPos(0, 0, 0);
        BlockPos candidate = new BlockPos(4, 0, 0); // dsq = 16
        assertTrue(SentryPylonScanner.isWithinRadius(candidate, pylon, 16L),
                "boundary point (dsq == maxDistSq) should be within radius");
    }

    @Test
    void withinRadius_pointJustOutside() {
        BlockPos pylon = new BlockPos(0, 0, 0);
        BlockPos candidate = new BlockPos(5, 0, 0); // dsq = 25
        assertFalse(SentryPylonScanner.isWithinRadius(candidate, pylon, 16L),
                "point beyond radius should be excluded");
    }

    @Test
    void withinRadius_threeDimensional() {
        BlockPos pylon = new BlockPos(10, 20, 30);
        BlockPos candidate = new BlockPos(12, 22, 32); // dsq = 4 + 4 + 4 = 12
        assertTrue(SentryPylonScanner.isWithinRadius(candidate, pylon, 12L),
                "boundary in 3D should be within radius");
        assertFalse(SentryPylonScanner.isWithinRadius(candidate, pylon, 11L),
                "just-too-small radius should exclude the point");
    }

    @Test
    void withinRadius_negativeOffsets() {
        BlockPos pylon = new BlockPos(0, 0, 0);
        BlockPos candidate = new BlockPos(-3, 0, -4); // dsq = 9 + 0 + 16 = 25
        assertTrue(SentryPylonScanner.isWithinRadius(candidate, pylon, 25L));
        assertFalse(SentryPylonScanner.isWithinRadius(candidate, pylon, 24L));
    }

    @Test
    void withinRadius_largeCoordinatesDoNotOverflow() {
        // 100_000 -> dx^2 = 1e10 (well within long range)
        BlockPos pylon = new BlockPos(0, 0, 0);
        BlockPos candidate = new BlockPos(100_000, 0, 0);
        long dsq = 100_000L * 100_000L;
        assertTrue(SentryPylonScanner.isWithinRadius(candidate, pylon, dsq));
        assertFalse(SentryPylonScanner.isWithinRadius(candidate, pylon, dsq - 1));
    }
}
