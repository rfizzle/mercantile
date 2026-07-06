package com.rfizzle.mercantile.reputation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager.EnchantmentSpec;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager.ExclusiveTrade;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager.LevelPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 2 (fabric-loader-junit): exercises {@link ExclusiveTradesManager#parseTrade} with and without
 * enchantment components. Only the item side needs the registry ({@code Bootstrap.bootStrap()});
 * enchantment IDs are stored as strings and not resolved until offer construction, so no dynamic
 * registry is required here.
 */
class ExclusiveTradesParseTest {

    private static final Gson GSON = new Gson();
    private static final int DEFAULT_MIN_SCORE = ReputationTier.TRUSTED.minScore();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ExclusiveTrade parse(String json) {
        return ExclusiveTradesManager.parseTrade(GSON.fromJson(json, JsonObject.class), DEFAULT_MIN_SCORE);
    }

    @Test
    void parsesGearEnchantments() {
        ExclusiveTrade trade = parse("""
                {
                  "input_1": { "item": "minecraft:emerald", "count": 64 },
                  "output": {
                    "item": "minecraft:diamond_sword",
                    "components": {
                      "enchantments": [
                        { "id": "minecraft:sharpness", "level": 5 },
                        { "id": "minecraft:unbreaking", "level": 3 }
                      ]
                    }
                  }
                }
                """);

        assertNotNull(trade);
        assertEquals(List.of(
                new EnchantmentSpec("minecraft:sharpness", 5),
                new EnchantmentSpec("minecraft:unbreaking", 3)
        ), trade.enchantments());
        assertTrue(trade.storedEnchantments().isEmpty(), "gear enchantments must not populate stored list");
    }

    @Test
    void parsesStoredEnchantmentsWithDefaultLevel() {
        ExclusiveTrade trade = parse("""
                {
                  "input_1": { "item": "minecraft:emerald", "count": 32 },
                  "output": {
                    "item": "minecraft:enchanted_book",
                    "components": {
                      "stored_enchantments": [ { "id": "minecraft:mending" } ]
                    }
                  }
                }
                """);

        assertNotNull(trade);
        // level omitted -> defaults to 1
        assertEquals(List.of(new EnchantmentSpec("minecraft:mending", 1)), trade.storedEnchantments());
        assertTrue(trade.enchantments().isEmpty());
    }

    @Test
    void componentFreeTradeHasEmptySpecLists() {
        ExclusiveTrade trade = parse("""
                {
                  "input_1": { "item": "minecraft:emerald", "count": 4 },
                  "output": { "item": "minecraft:iron_ingot", "count": 8 }
                }
                """);

        assertNotNull(trade);
        assertTrue(trade.enchantments().isEmpty());
        assertTrue(trade.storedEnchantments().isEmpty());
    }

    @Test
    void parsesRequiresAdvancementGate() {
        ExclusiveTrade trade = parse("""
                {
                  "input_1": { "item": "minecraft:emerald", "count": 20 },
                  "output": { "item": "minecraft:dragon_breath", "count": 1 },
                  "requires_advancement": "minecraft:end/kill_dragon"
                }
                """);

        assertNotNull(trade);
        assertEquals(ResourceLocation.parse("minecraft:end/kill_dragon"),
                trade.requiresAdvancement().orElseThrow());
    }

    @Test
    void ungatedTradeHasNoAdvancementRequirement() {
        ExclusiveTrade trade = parse("""
                {
                  "input_1": { "item": "minecraft:emerald", "count": 4 },
                  "output": { "item": "minecraft:iron_ingot", "count": 8 }
                }
                """);

        assertNotNull(trade);
        assertTrue(trade.requiresAdvancement().isEmpty());
        assertNull(trade.enchantRandomly());
    }

    @Test
    void parsesGenerativeEnchantBook() {
        ExclusiveTrade trade = parse("""
                {
                  "input_1": { "item": "minecraft:emerald", "count": 30 },
                  "output": {
                    "item": "minecraft:enchanted_book",
                    "enchant_randomly": "#meridian:rarity/common",
                    "level": "mid"
                  }
                }
                """);

        assertNotNull(trade);
        assertNotNull(trade.enchantRandomly());
        assertEquals(net.minecraft.tags.TagKey.create(Registries.ENCHANTMENT,
                ResourceLocation.parse("meridian:rarity/common")), trade.enchantRandomly().tag());
        assertEquals(LevelPolicy.MID, trade.enchantRandomly().policy());
        assertTrue(trade.storedEnchantments().isEmpty(), "generative output must not populate fixed specs");
    }

    @Test
    void generativeLevelDefaultsToMid() {
        ExclusiveTrade trade = parse("""
                {
                  "input_1": { "item": "minecraft:emerald", "count": 30 },
                  "output": {
                    "item": "minecraft:enchanted_book",
                    "enchant_randomly": "meridian:rarity/uncommon"
                  }
                }
                """);

        assertNotNull(trade);
        assertEquals(LevelPolicy.MID, trade.enchantRandomly().policy());
    }

    @Test
    void generativeAndFixedEnchantsAreMutuallyExclusive() {
        ExclusiveTrade trade = parse("""
                {
                  "input_1": { "item": "minecraft:emerald", "count": 30 },
                  "output": {
                    "item": "minecraft:enchanted_book",
                    "enchant_randomly": "#meridian:rarity/common",
                    "components": { "stored_enchantments": [ { "id": "minecraft:mending" } ] }
                  }
                }
                """);

        assertNull(trade, "a trade declaring both enchant_randomly and fixed enchantments must be skipped");
    }

    @Test
    void levelPolicyComputesMidAndMax() {
        // enchantment range [1, 5]: mid = ceil(5/2) = 3, floored at min
        assertEquals(3, LevelPolicy.MID.computeLevel(1, 5));
        assertEquals(5, LevelPolicy.MAX.computeLevel(1, 5));
        // single-level enchantment [1, 1]: mid floored at min
        assertEquals(1, LevelPolicy.MID.computeLevel(1, 1));
        // range [2, 4]: mid = ceil(4/2) = 2, not below min
        assertEquals(2, LevelPolicy.MID.computeLevel(2, 4));
        assertEquals(4, LevelPolicy.MAX.computeLevel(2, 4));
    }

    @Test
    void unknownLevelPolicyDefaultsToMid() {
        assertEquals(LevelPolicy.MID, LevelPolicy.fromName("nonsense"));
        assertEquals(LevelPolicy.MAX, LevelPolicy.fromName("MAX"));
    }

    @Test
    void specListsAreImmutable() {
        ExclusiveTrade trade = parse("""
                {
                  "input_1": { "item": "minecraft:emerald", "count": 1 },
                  "output": {
                    "item": "minecraft:diamond_pickaxe",
                    "components": { "enchantments": [ { "id": "minecraft:efficiency", "level": 4 } ] }
                  }
                }
                """);

        assertNotNull(trade);
        assertThrows(UnsupportedOperationException.class,
                () -> trade.enchantments().add(new EnchantmentSpec("minecraft:fortune", 3)));
    }
}
