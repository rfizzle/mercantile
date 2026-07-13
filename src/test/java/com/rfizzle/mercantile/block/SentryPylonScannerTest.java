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

    // --- withinDefendedZone (entity position vs. pylon center) ---

    @Test
    void defendedZone_entityAtPylonCenter() {
        BlockPos pylon = new BlockPos(0, 0, 0);
        // Center of the pylon block is (0.5, 0.5, 0.5); an entity sitting there is dsq 0.
        assertTrue(SentryPylonScanner.withinDefendedZone(0.5, 0.5, 0.5, pylon, 4));
    }

    @Test
    void defendedZone_entityOnBoundary() {
        BlockPos pylon = new BlockPos(0, 0, 0);
        // dx = 4.5 - 0.5 = 4 -> dsq 16 == radius^2, inclusive boundary.
        assertTrue(SentryPylonScanner.withinDefendedZone(4.5, 0.5, 0.5, pylon, 4),
                "boundary (dsq == radius^2) should be inside the zone");
    }

    @Test
    void defendedZone_entityJustOutside() {
        BlockPos pylon = new BlockPos(0, 0, 0);
        // dx = 4.6 - 0.5 = 4.1 -> dsq 16.81 > 16.
        assertFalse(SentryPylonScanner.withinDefendedZone(4.6, 0.5, 0.5, pylon, 4),
                "a point past radius^2 should be outside the zone");
    }

    @Test
    void defendedZone_threeDimensionalOffset() {
        BlockPos pylon = new BlockPos(10, 20, 30);
        // Deltas 2/2/2 from the (10.5, 20.5, 30.5) center -> dsq 12.
        assertTrue(SentryPylonScanner.withinDefendedZone(12.5, 22.5, 32.5, pylon, 4));
        assertFalse(SentryPylonScanner.withinDefendedZone(12.5, 22.5, 32.5, pylon, 3),
                "dsq 12 is outside radius 3 (9)");
    }

    @Test
    void defendedZone_negativePylonCoordinates() {
        BlockPos pylon = new BlockPos(-5, 0, -5);
        // Center (-4.5, 0.5, -4.5); entity three east -> dx 3, dsq 9.
        assertTrue(SentryPylonScanner.withinDefendedZone(-1.5, 0.5, -4.5, pylon, 3));
        assertFalse(SentryPylonScanner.withinDefendedZone(-1.5, 0.5, -4.5, pylon, 2));
    }

    // --- sentryHoldsCountdown ---

    @Test
    void holds_targetInZoneHoldsRegardlessOfDamage() {
        assertTrue(SentryPylonScanner.sentryHoldsCountdown(true, false, 9999, 60),
                "a current in-zone target holds the countdown even with no recent damage");
    }

    @Test
    void holds_recentAttackerInZoneHolds() {
        assertTrue(SentryPylonScanner.sentryHoldsCountdown(false, true, 0, 60),
                "an in-zone attacker struck this very tick holds the countdown");
    }

    @Test
    void holds_attackerAtWindowBoundaryHolds() {
        assertTrue(SentryPylonScanner.sentryHoldsCountdown(false, true, 60, 60),
                "damage exactly at the window edge still counts as engaged");
    }

    @Test
    void holds_staleAttackerDoesNotHold() {
        assertFalse(SentryPylonScanner.sentryHoldsCountdown(false, true, 61, 60),
                "damage older than the window no longer holds the countdown");
    }

    @Test
    void holds_negativeTicksSinceHurtDoesNotHold() {
        assertFalse(SentryPylonScanner.sentryHoldsCountdown(false, true, -1, 60),
                "a nonsensical negative age (reset/wrapped clock) must not hold");
    }

    @Test
    void holds_noTargetNoAttackerDoesNotHold() {
        assertFalse(SentryPylonScanner.sentryHoldsCountdown(false, false, 0, 60),
                "with neither a target nor a recent attacker the countdown runs");
    }

    @Test
    void holds_recentDamageButAttackerOutOfZoneDoesNotHold() {
        // attackerInZone already folds in the zone + hostility test; a false flag means the last
        // attacker was out of zone (or gone), so recency alone must not hold.
        assertFalse(SentryPylonScanner.sentryHoldsCountdown(false, false, 3, 60),
                "recent damage from an out-of-zone source does not hold the countdown");
    }
}
