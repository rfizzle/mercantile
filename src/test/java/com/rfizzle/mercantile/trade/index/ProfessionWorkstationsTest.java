package com.rfizzle.mercantile.trade.index;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProfessionWorkstationsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetCache() {
        ProfessionWorkstations.invalidateForTesting();
    }

    @Test
    void armorerMapsToBlastFurnace() {
        assertSame(Blocks.BLAST_FURNACE, ProfessionWorkstations.forProfession(vanilla("armorer")));
    }

    @Test
    void butcherMapsToSmoker() {
        assertSame(Blocks.SMOKER, ProfessionWorkstations.forProfession(vanilla("butcher")));
    }

    @Test
    void cartographerMapsToCartographyTable() {
        assertSame(Blocks.CARTOGRAPHY_TABLE, ProfessionWorkstations.forProfession(vanilla("cartographer")));
    }

    @Test
    void clericMapsToBrewingStand() {
        assertSame(Blocks.BREWING_STAND, ProfessionWorkstations.forProfession(vanilla("cleric")));
    }

    @Test
    void farmerMapsToComposter() {
        assertSame(Blocks.COMPOSTER, ProfessionWorkstations.forProfession(vanilla("farmer")));
    }

    @Test
    void fishermanMapsToBarrel() {
        assertSame(Blocks.BARREL, ProfessionWorkstations.forProfession(vanilla("fisherman")));
    }

    @Test
    void fletcherMapsToFletchingTable() {
        assertSame(Blocks.FLETCHING_TABLE, ProfessionWorkstations.forProfession(vanilla("fletcher")));
    }

    @Test
    void leatherworkerMapsToCauldron() {
        Block resolved = ProfessionWorkstations.forProfession(vanilla("leatherworker"));
        assertTrue(
                resolved == Blocks.CAULDRON
                        || resolved == Blocks.WATER_CAULDRON
                        || resolved == Blocks.LAVA_CAULDRON
                        || resolved == Blocks.POWDER_SNOW_CAULDRON,
                "leatherworker should resolve to one of the cauldron variants, got: " + resolved);
    }

    @Test
    void librarianMapsToLectern() {
        assertSame(Blocks.LECTERN, ProfessionWorkstations.forProfession(vanilla("librarian")));
    }

    @Test
    void masonMapsToStonecutter() {
        assertSame(Blocks.STONECUTTER, ProfessionWorkstations.forProfession(vanilla("mason")));
    }

    @Test
    void shepherdMapsToLoom() {
        assertSame(Blocks.LOOM, ProfessionWorkstations.forProfession(vanilla("shepherd")));
    }

    @Test
    void toolsmithMapsToSmithingTable() {
        assertSame(Blocks.SMITHING_TABLE, ProfessionWorkstations.forProfession(vanilla("toolsmith")));
    }

    @Test
    void weaponsmithMapsToGrindstone() {
        assertSame(Blocks.GRINDSTONE, ProfessionWorkstations.forProfession(vanilla("weaponsmith")));
    }

    @Test
    void noneProfessionHasNoWorkstation() {
        assertNull(ProfessionWorkstations.forProfession(vanilla("none")));
    }

    @Test
    void nitwitProfessionHasNoWorkstation() {
        assertNull(ProfessionWorkstations.forProfession(vanilla("nitwit")));
    }

    @Test
    void unknownProfessionReturnsNull() {
        assertNull(ProfessionWorkstations.forProfession(ResourceLocation.parse("madeup:profession_zzz")));
    }

    @Test
    void nullProfessionIdReturnsNull() {
        assertNull(ProfessionWorkstations.forProfession(null));
    }

    @Test
    void getByProfessionReturnsOptional() {
        assertTrue(ProfessionWorkstations.get(VillagerProfession.ARMORER).isPresent());
        assertSame(Blocks.BLAST_FURNACE,
                ProfessionWorkstations.get(VillagerProfession.ARMORER).orElseThrow());
        assertTrue(ProfessionWorkstations.get(VillagerProfession.NONE).isEmpty());
        assertTrue(ProfessionWorkstations.get(VillagerProfession.NITWIT).isEmpty());
        assertTrue(ProfessionWorkstations.get(null).isEmpty());
    }

    @Test
    void snapshotContainsAllThirteenVanillaProfessions() {
        Map<ResourceLocation, Block> snap = ProfessionWorkstations.snapshot();
        assertEquals(13, snap.size(),
                "expected exactly 13 vanilla profession→workstation mappings, got: " + snap);
        assertTrue(snap.containsKey(vanilla("farmer")));
        assertTrue(snap.containsKey(vanilla("weaponsmith")));
        assertFalse(snap.containsKey(vanilla("none")));
        assertFalse(snap.containsKey(vanilla("nitwit")));
    }

    @Test
    void snapshotIsImmutable() {
        Map<ResourceLocation, Block> snap = ProfessionWorkstations.snapshot();
        assertThrows(UnsupportedOperationException.class,
                () -> snap.put(vanilla("test"), Blocks.STONE));
    }

    @Test
    void cacheIsStableAcrossCalls() {
        Map<ResourceLocation, Block> first = ProfessionWorkstations.snapshot();
        Map<ResourceLocation, Block> second = ProfessionWorkstations.snapshot();
        assertSame(first, second, "snapshot must return the same cached instance until invalidated");
    }

    private static ResourceLocation vanilla(String name) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", name);
    }
}
