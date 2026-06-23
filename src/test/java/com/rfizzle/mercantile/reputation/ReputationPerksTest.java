package com.rfizzle.mercantile.reputation;

import com.rfizzle.mercantile.api.ReputationTier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReputationPerksTest {

    private static String keyOf(Component component) {
        assertInstanceOf(TranslatableContents.class, component.getContents());
        return ((TranslatableContents) component.getContents()).getKey();
    }

    private static List<String> keysOf(List<Component> components) {
        return components.stream().map(ReputationPerksTest::keyOf).toList();
    }

    private static Object firstArg(Component component) {
        Object[] args = ((TranslatableContents) component.getContents()).getArgs();
        assertTrue(args.length >= 1, "expected a substitution arg");
        return args[0];
    }

    // ---- Price percentage tracks the live pricing function ----

    @ParameterizedTest
    @CsvSource({
            "1000, -15",   // Honored
            "300,  -10",   // Trusted
            "75,    -5",   // Liked
            "0,      0",   // Neutral
    })
    void priceModifierPercentMatchesTiers(int score, int expectedPercent) {
        assertEquals(expectedPercent, ReputationPerks.priceModifierPercent(score));
    }

    @Test
    void distrustedRampsMarkupWithinRange() {
        // DISTRUSTED ramps ~10% at -1 to ~25% at -149.
        assertEquals(10, ReputationPerks.priceModifierPercent(-1));
        assertEquals(25, ReputationPerks.priceModifierPercent(-149));
    }

    @Test
    void percentIsDerivedNotDuplicated() {
        // The shared-truth guarantee: the panel's figure is exactly the economy's
        // modifier against a base of 100, never a separately maintained constant.
        for (int score : new int[]{1500, 1000, 500, 300, 75, 0, -50, -149, -200}) {
            assertEquals(ReputationTier.priceModifierForScore(score, 100),
                    ReputationPerks.priceModifierPercent(score));
        }
    }

    // ---- Active perk lines per standing ----

    @Test
    void honoredGrantsDiscountAndBothExclusivePools() {
        List<Component> perks = ReputationPerks.activePerks(1000);
        assertEquals(List.of(
                "hud.mercantile.rep_detail.perk.discount",
                "hud.mercantile.rep_detail.perk.exclusive_profession",
                "hud.mercantile.rep_detail.perk.exclusive_cross"), keysOf(perks));
        assertEquals(15, firstArg(perks.get(0)));
    }

    @Test
    void trustedGrantsProfessionExclusiveOnly() {
        List<Component> perks = ReputationPerks.activePerks(300);
        assertEquals(List.of(
                "hud.mercantile.rep_detail.perk.discount",
                "hud.mercantile.rep_detail.perk.exclusive_profession"), keysOf(perks));
        assertEquals(10, firstArg(perks.get(0)));
    }

    @Test
    void likedShowsDiscountAndLockedExclusive() {
        List<Component> perks = ReputationPerks.activePerks(75);
        assertEquals(List.of(
                "hud.mercantile.rep_detail.perk.discount",
                "hud.mercantile.rep_detail.perk.exclusive_locked"), keysOf(perks));
        assertEquals(ReputationTier.TRUSTED.minScore(),
                firstArg(perks.get(1)), "locked line names the unlock score");
    }

    @Test
    void neutralShowsNoModifierAndLockedExclusive() {
        List<Component> perks = ReputationPerks.activePerks(0);
        assertEquals(List.of(
                "hud.mercantile.rep_detail.perk.no_modifier",
                "hud.mercantile.rep_detail.perk.exclusive_locked"), keysOf(perks));
    }

    @Test
    void distrustedShowsMarkupAndLockedExclusive() {
        List<Component> perks = ReputationPerks.activePerks(-50);
        List<String> keys = keysOf(perks);
        assertEquals("hud.mercantile.rep_detail.perk.markup", keys.get(0));
        assertTrue(keys.contains("hud.mercantile.rep_detail.perk.exclusive_locked"));
    }

    @Test
    void reviledShowsRefusalAndNoExclusiveOrLockedLine() {
        List<Component> perks = ReputationPerks.activePerks(-200);
        assertEquals(List.of("hud.mercantile.rep_detail.perk.refused"), keysOf(perks));
    }

    // ---- Exclusive-trade gate ----

    @Test
    void exclusiveTradesUnlockAtTrustedFloor() {
        int floor = ReputationTier.TRUSTED.minScore();
        assertEquals(floor, ReputationPerks.exclusiveUnlockScore());
        assertFalse(ReputationPerks.exclusiveTradesUnlocked(floor - 1));
        assertTrue(ReputationPerks.exclusiveTradesUnlocked(floor));
    }
}
