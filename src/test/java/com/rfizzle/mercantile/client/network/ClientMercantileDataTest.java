package com.rfizzle.mercantile.client.network;

import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.network.RestockTimerS2CPayload;
import com.rfizzle.mercantile.network.VillagerInfoPanelS2CPayload;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMercantileDataTest {

    private static final int VILLAGER_A = 100;
    private static final int VILLAGER_B = 200;

    @AfterEach
    void resetState() {
        ClientMercantileData.clear();
    }

    private static VillagerInfoPanelS2CPayload infoFor(int entityId) {
        return new VillagerInfoPanelS2CPayload(
                entityId, "farmer", 1, 0, 10, 0,
                "mercantile.tier.neutral", 0, true, false, "");
    }

    private static RestockTimerS2CPayload restockFor(int entityId) {
        return new RestockTimerS2CPayload(entityId, 0L, 0, true, 2400);
    }

    private static DemandPriceS2CPayload demandFor(int entityId) {
        return new DemandPriceS2CPayload(entityId, List.of());
    }

    @Test
    void setVillagerInfoForDifferentVillagerClearsStaleRestockAndDemand() {
        ClientMercantileData.setVillagerInfo(infoFor(VILLAGER_A));
        ClientMercantileData.setRestockTimer(restockFor(VILLAGER_A));
        ClientMercantileData.setDemandPrice(demandFor(VILLAGER_A));

        ClientMercantileData.setVillagerInfo(infoFor(VILLAGER_B));

        assertNull(ClientMercantileData.getRestockTimer(),
                "restock timer for villager A must be dropped when info for B arrives");
        assertNull(ClientMercantileData.getDemandPrice(),
                "demand price for villager A must be dropped when info for B arrives");
        assertNotNull(ClientMercantileData.getVillagerInfo());
    }

    @Test
    void setVillagerInfoForSameVillagerPreservesRestockAndDemand() {
        ClientMercantileData.setVillagerInfo(infoFor(VILLAGER_A));
        RestockTimerS2CPayload restock = restockFor(VILLAGER_A);
        DemandPriceS2CPayload demand = demandFor(VILLAGER_A);
        ClientMercantileData.setRestockTimer(restock);
        ClientMercantileData.setDemandPrice(demand);

        ClientMercantileData.setVillagerInfo(infoFor(VILLAGER_A));

        assertSame(restock, ClientMercantileData.getRestockTimer(),
                "restock timer must be preserved when matching info arrives");
        assertSame(demand, ClientMercantileData.getDemandPrice(),
                "demand price must be preserved when matching info arrives");
    }

    @Test
    void setRestockTimerForMismatchedVillagerIsDropped() {
        ClientMercantileData.setVillagerInfo(infoFor(VILLAGER_A));
        RestockTimerS2CPayload original = restockFor(VILLAGER_A);
        ClientMercantileData.setRestockTimer(original);

        ClientMercantileData.setRestockTimer(restockFor(VILLAGER_B));

        assertSame(original, ClientMercantileData.getRestockTimer(),
                "mismatched restock payload must be dropped, leaving the prior value intact");
    }

    @Test
    void setDemandPriceForMismatchedVillagerIsDropped() {
        ClientMercantileData.setVillagerInfo(infoFor(VILLAGER_A));
        DemandPriceS2CPayload original = demandFor(VILLAGER_A);
        ClientMercantileData.setDemandPrice(original);

        ClientMercantileData.setDemandPrice(demandFor(VILLAGER_B));

        assertSame(original, ClientMercantileData.getDemandPrice(),
                "mismatched demand payload must be dropped, leaving the prior value intact");
    }

    @Test
    void setRestockTimerAcceptedWhenNoInfoStored() {
        ClientMercantileData.clearMerchantScreenData();
        RestockTimerS2CPayload payload = restockFor(VILLAGER_A);

        ClientMercantileData.setRestockTimer(payload);

        assertSame(payload, ClientMercantileData.getRestockTimer(),
                "restock arriving before info should be stored — info-arrival reconciles");
    }

    @Test
    void clearMerchantScreenDataClearsAllThree() {
        ClientMercantileData.setVillagerInfo(infoFor(VILLAGER_A));
        ClientMercantileData.setRestockTimer(restockFor(VILLAGER_A));
        ClientMercantileData.setDemandPrice(demandFor(VILLAGER_A));

        ClientMercantileData.clearMerchantScreenData();

        assertNull(ClientMercantileData.getVillagerInfo());
        assertNull(ClientMercantileData.getRestockTimer());
        assertNull(ClientMercantileData.getDemandPrice());
    }

    @Test
    void setNullClearsField() {
        ClientMercantileData.setVillagerInfo(infoFor(VILLAGER_A));
        ClientMercantileData.setRestockTimer(restockFor(VILLAGER_A));
        ClientMercantileData.setDemandPrice(demandFor(VILLAGER_A));

        ClientMercantileData.setVillagerInfo(null);
        ClientMercantileData.setRestockTimer(null);
        ClientMercantileData.setDemandPrice(null);

        assertNull(ClientMercantileData.getVillagerInfo());
        assertNull(ClientMercantileData.getRestockTimer());
        assertNull(ClientMercantileData.getDemandPrice());
    }

    // --- Trade index ---

    @Test
    void tradeIndexIsEmptyByDefault() {
        assertTrue(ClientMercantileData.getTradeIndex().isEmpty(),
                "trade index must be empty before any payload is received");
    }

    @Test
    void setTradeIndexStoresList() {
        // No MC bootstrap needed — TradeIndexEntry is not constructed here; we only
        // verify the list reference round-trips correctly.
        List<TradeIndexEntry> list = List.of();
        ClientMercantileData.setTradeIndex(list);
        assertSame(list, ClientMercantileData.getTradeIndex(),
                "getTradeIndex() must return the same list instance that was set");
    }

    @Test
    void clearResetsTradeIndex() {
        ClientMercantileData.setTradeIndex(List.of());
        ClientMercantileData.clear();
        assertTrue(ClientMercantileData.getTradeIndex().isEmpty(),
                "clear() must reset trade index to empty");
    }
}
