package com.rfizzle.mercantile.reputation;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.PlayerData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-tests the pure gratitude-gift cap decision in {@link GratitudeGiftManager}. Avoids any
 * Fabric/Minecraft runtime — stays at Tier 1 (no Bootstrap, no server).
 */
class GratitudeGiftCapTest {

    private MercantileConfig config;

    @BeforeEach
    void setUp() {
        config = new MercantileConfig();
        config.gratitudeGiftsPerDay = 1;
    }

    @Test
    void firstGiftOfDayAwarded() {
        PlayerData data = new PlayerData();
        assertEquals(ReputationManager.CapDecision.AWARDED,
                GratitudeGiftManager.evaluateGratitudeGift(data, config, 1L));
        assertEquals(1, data.getDailyGratitudeGifts());
    }

    @Test
    void capBlocksSecondGiftSameDay() {
        PlayerData data = new PlayerData();
        GratitudeGiftManager.evaluateGratitudeGift(data, config, 1L);
        assertEquals(ReputationManager.CapDecision.SUBCAP_HIT,
                GratitudeGiftManager.evaluateGratitudeGift(data, config, 1L));
        assertEquals(1, data.getDailyGratitudeGifts(), "cap must hold at max");
    }

    @Test
    void giftCounterResetsOnDayRollover() {
        PlayerData data = new PlayerData();
        GratitudeGiftManager.evaluateGratitudeGift(data, config, 1L);
        assertEquals(ReputationManager.CapDecision.SUBCAP_HIT,
                GratitudeGiftManager.evaluateGratitudeGift(data, config, 1L));

        assertEquals(ReputationManager.CapDecision.AWARDED,
                GratitudeGiftManager.evaluateGratitudeGift(data, config, 2L),
                "a new day must reopen the gift budget");
        assertEquals(1, data.getDailyGratitudeGifts());
    }

    @Test
    void giftsDoNotConsumeReputationDailyCap() {
        PlayerData data = new PlayerData();
        GratitudeGiftManager.evaluateGratitudeGift(data, config, 1L);
        assertEquals(0, data.getDailyReputationEarned(),
                "gifts are items, not reputation — they must not consume the daily rep cap");
    }

    @Test
    void zeroCapDisablesGifts() {
        config.gratitudeGiftsPerDay = 0;
        PlayerData data = new PlayerData();
        assertEquals(ReputationManager.CapDecision.SUBCAP_HIT,
                GratitudeGiftManager.evaluateGratitudeGift(data, config, 1L));
        assertEquals(0, data.getDailyGratitudeGifts());
    }

    @Test
    void higherCapAllowsMultipleGifts() {
        config.gratitudeGiftsPerDay = 3;
        PlayerData data = new PlayerData();
        for (int i = 1; i <= 3; i++) {
            assertEquals(ReputationManager.CapDecision.AWARDED,
                    GratitudeGiftManager.evaluateGratitudeGift(data, config, 1L), "gift #" + i);
        }
        assertEquals(ReputationManager.CapDecision.SUBCAP_HIT,
                GratitudeGiftManager.evaluateGratitudeGift(data, config, 1L));
        assertEquals(3, data.getDailyGratitudeGifts());
    }
}
