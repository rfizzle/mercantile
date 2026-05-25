package com.rfizzle.mercantile.data;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillagerHeadTexturesTest {

    private static final String[] VANILLA_PROFESSIONS = {
            "armorer", "butcher", "cartographer", "cleric", "farmer",
            "fisherman", "fletcher", "leatherworker", "librarian", "mason",
            "shepherd", "toolsmith", "weaponsmith", "nitwit", "none"
    };

    @Test
    void allVanillaProfessionsRegistered() {
        for (String profession : VANILLA_PROFESSIONS) {
            ResourceLocation id = ResourceLocation.withDefaultNamespace(profession);
            String texture = VillagerHeadTextures.getTextureValue(id);
            assertNotNull(texture, "Texture missing for " + profession);
            assertTrue(texture.startsWith("eyJ"), "Texture should be Base64 for " + profession);
        }
    }

    @Test
    void babyTextureRegistered() {
        String texture = VillagerHeadTextures.getTextureValue(VillagerHeadTextures.BABY);
        assertNotNull(texture);
        assertTrue(texture.startsWith("eyJ"));
    }

    @Test
    void eachVanillaProfessionHasUniqueTexture() {
        // nitwit intentionally reuses the none (unemployed) skin — vanilla nitwit has no head overlay.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String profession : VANILLA_PROFESSIONS) {
            if (profession.equals("nitwit")) continue;
            ResourceLocation id = ResourceLocation.withDefaultNamespace(profession);
            String texture = VillagerHeadTextures.getTextureValue(id);
            assertTrue(seen.add(texture), "Duplicate texture for " + profession);
        }
        // Codify the deliberate reuse so it isn't "fixed" accidentally.
        String nitwit = VillagerHeadTextures.getTextureValue(ResourceLocation.withDefaultNamespace("nitwit"));
        String none = VillagerHeadTextures.getTextureValue(ResourceLocation.withDefaultNamespace("none"));
        assertEquals(none, nitwit, "nitwit should share the none skin (no head overlay in vanilla)");
    }

    @Test
    void fallbackForUnknownProfession() {
        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath("modded", "mechanic");
        String texture = VillagerHeadTextures.getTextureValue(unknown);
        String fallback = VillagerHeadTextures.getTextureValue(VillagerHeadTextures.FALLBACK_ID);
        assertEquals(fallback, texture);
    }

    @Test
    void registerNewProfession() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("testmod", "engineer");
        String customTexture = "eyJjdXN0b20iOiJ0ZXN0In0=";
        VillagerHeadTextures.register(id, customTexture);
        assertEquals(customTexture, VillagerHeadTextures.getTextureValue(id));
    }

    @Test
    void registerOverridesExisting() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("testmod", "override_test");
        String first = "eyJmaXJzdCI6InZhbHVlIn0=";
        String second = "eyJzZWNvbmQiOiJ2YWx1ZSJ9";

        VillagerHeadTextures.register(id, first);
        assertEquals(first, VillagerHeadTextures.getTextureValue(id));

        VillagerHeadTextures.register(id, second);
        assertEquals(second, VillagerHeadTextures.getTextureValue(id));
    }

    @Test
    void profileCaching() {
        ResourceLocation id = ResourceLocation.withDefaultNamespace("farmer");
        var profile1 = VillagerHeadTextures.getProfile(id);
        var profile2 = VillagerHeadTextures.getProfile(id);
        assertSame(profile1, profile2);
    }

    @Test
    void profileFallbackForUnknown() {
        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath("testmod", "unknown_prof");
        var unknownProfile = VillagerHeadTextures.getProfile(unknown);
        var fallbackProfile = VillagerHeadTextures.getProfile(VillagerHeadTextures.FALLBACK_ID);
        assertSame(fallbackProfile, unknownProfile);
    }

    @Test
    void registerInvalidatesProfileCache() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("testmod", "cache_invalidation");
        VillagerHeadTextures.register(id, "eyJ0ZXN0MSI6InZhbHVlMSJ9");
        var profile1 = VillagerHeadTextures.getProfile(id);

        VillagerHeadTextures.register(id, "eyJ0ZXN0MiI6InZhbHVlMiJ9");
        var profile2 = VillagerHeadTextures.getProfile(id);

        assertNotSame(profile1, profile2);
    }

    @Test
    void displayNameFormatting() {
        assertEquals("Farmer", VillagerHeadTextures.formatRawId("farmer"));
        assertEquals("Stone Mason", VillagerHeadTextures.formatRawId("stone_mason"));
        assertEquals("A", VillagerHeadTextures.formatRawId("a"));
        assertEquals("Leather Worker", VillagerHeadTextures.formatRawId("leather_worker"));
    }

    @Test
    void displayNameReturnsComponent() {
        ResourceLocation farmer = ResourceLocation.withDefaultNamespace("farmer");
        var name = VillagerHeadTextures.getDisplayName(farmer);
        assertNotNull(name);
    }
}
