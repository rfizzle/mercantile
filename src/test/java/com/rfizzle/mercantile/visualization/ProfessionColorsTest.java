package com.rfizzle.mercantile.visualization;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfessionColorsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void knownProfessionsReturnNonDefaultColor() {
        Vector3f def = ProfessionColors.defaultColor();
        for (String name : new String[] {
                "armorer", "butcher", "cartographer", "cleric", "farmer",
                "fisherman", "fletcher", "leatherworker", "librarian",
                "mason", "shepherd", "toolsmith", "weaponsmith"
        }) {
            Vector3f c = ProfessionColors.lookup(ResourceLocation.withDefaultNamespace(name));
            assertNotNull(c, "color for " + name);
            assertNotSame(def, c, "profession " + name + " should not return the default sentinel");
        }
    }

    @Test
    void unknownProfessionReturnsDefault() {
        Vector3f c = ProfessionColors.lookup(ResourceLocation.fromNamespaceAndPath("modded", "wizard"));
        assertSame(ProfessionColors.defaultColor(), c);
    }

    @Test
    void nullIdReturnsDefault() {
        Vector3f c = ProfessionColors.lookup((ResourceLocation) null);
        assertSame(ProfessionColors.defaultColor(), c);
    }

    @Test
    void nullProfessionReturnsDefault() {
        Vector3f c = ProfessionColors.lookup((VillagerProfession) null);
        assertSame(ProfessionColors.defaultColor(), c);
    }

    @Test
    void vanillaFarmerLookupViaRegistryMatchesIdLookup() {
        Vector3f byProfession = ProfessionColors.lookup(VillagerProfession.FARMER);
        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(VillagerProfession.FARMER);
        Vector3f byId = ProfessionColors.lookup(id);
        assertEquals(byId, byProfession);
        assertNotSame(ProfessionColors.defaultColor(), byProfession);
    }
}
