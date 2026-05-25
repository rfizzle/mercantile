package com.rfizzle.mercantile.compat.tradeindex;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeIndexIconTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void farmerHeadHasCustomNameFarmer() {
        ItemStack stack = TradeIndexIcon.forProfession(ResourceLocation.withDefaultNamespace("farmer"));
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        assertNotNull(name, "CUSTOM_NAME should be set on profession head");
        assertEquals("Farmer", name.getString());
    }

    @Test
    void crossProfessionHeadHasCustomName() {
        ItemStack stack = TradeIndexIcon.forProfession(TradeIndexIcon.CROSS_PROFESSION_ID);
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        assertNotNull(name, "CUSTOM_NAME should be set for cross-profession head");
        assertFalse(name.getString().isEmpty(), "cross-profession name must not be blank");
    }

    @Test
    void customNameIsNotItalic() {
        ItemStack stack = TradeIndexIcon.forProfession(ResourceLocation.withDefaultNamespace("farmer"));
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        assertNotNull(name);
        assertFalse(Boolean.TRUE.equals(name.getStyle().isItalic()),
                "CUSTOM_NAME must not be rendered italic");
    }

    @Test
    void cachedStackAlsoHasCustomName() {
        ResourceLocation id = ResourceLocation.withDefaultNamespace("librarian");
        TradeIndexIcon.forProfession(id);
        ItemStack second = TradeIndexIcon.forProfession(id);
        assertNotNull(second.get(DataComponents.CUSTOM_NAME),
                "cached copy must still carry CUSTOM_NAME");
    }
}
