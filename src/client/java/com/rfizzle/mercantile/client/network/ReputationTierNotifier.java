package com.rfizzle.mercantile.client.network;

import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.client.MercantileClient;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Client-side detector that surfaces a chat notice when the local player's
 * reputation tier changes. Fed old/new scores from the reputation sync choke
 * point ({@link ClientMercantileData#setReputation}); no dedicated packet is
 * needed because the tier is a pure function of the already-synced score.
 *
 * <p>The first sync after joining a world establishes the baseline and is
 * intentionally silent — there is no prior tier to have crossed.
 */
public final class ReputationTierNotifier {

    private ReputationTierNotifier() {
    }

    /**
     * Compare the tier implied by {@code oldScore} against {@code newScore} and,
     * on a crossing, show a promotion/demotion chat line naming the new tier.
     *
     * @param hadBaseline whether a prior score had already been synced this
     *                    session; when false this is the initial sync and no
     *                    message fires.
     */
    public static void onScoreSynced(boolean hadBaseline, int oldScore, int newScore) {
        if (!hadBaseline) return;
        if (!MercantileConfig.get().enableTierChangeMessages) return;

        ReputationTier oldTier = ReputationTier.fromScore(oldScore);
        ReputationTier newTier = ReputationTier.fromScore(newScore);
        if (oldTier == newTier) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        boolean promotion = newTier.ordinal() < oldTier.ordinal(); // enum is best→worst
        player.displayClientMessage(buildMessage(promotion, newTier), false);
    }

    private static Component buildMessage(boolean promotion, ReputationTier newTier) {
        String baseKey = promotion
                ? "mercantile.message.tier_up"
                : "mercantile.message.tier_down";
        KeyMapping key = MercantileClient.KEY_REPUTATION_DETAIL;
        if (key != null && !key.isUnbound()) {
            return Component.translatable(baseKey + ".hint",
                    newTier.displayName(), key.getTranslatedKeyMessage());
        }
        return Component.translatable(baseKey, newTier.displayName());
    }
}
