package com.rfizzle.mercantile.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * One pinned villager trade: which villager, which offer (by
 * {@link com.rfizzle.mercantile.trade.OfferIdentityHash} content hash), plus display snapshots
 * taken at pin time so {@code /mercantile pins} can describe the pin even while the villager
 * is unloaded. Snapshots are server-locale strings — an accepted tradeoff over shipping
 * serialized Components. The collection bookkeeping lives in {@link PlayerData}.
 */
public record PinnedTrade(UUID villagerUuid, String offerHash, String villagerName, String tradeSummary) {
    /** All string fields are clamped so a tampered save can't bloat the player file. */
    public static final int MAX_HASH_LENGTH = 512;
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_SUMMARY_LENGTH = 128;

    public static final Codec<PinnedTrade> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUIDUtil.CODEC.fieldOf("villagerUuid").forGetter(PinnedTrade::villagerUuid),
                    Codec.STRING.fieldOf("offerHash").forGetter(PinnedTrade::offerHash),
                    Codec.STRING.optionalFieldOf("villagerName", "").forGetter(PinnedTrade::villagerName),
                    Codec.STRING.optionalFieldOf("tradeSummary", "").forGetter(PinnedTrade::tradeSummary)
            ).apply(instance, PinnedTrade::new)
    );

    public PinnedTrade {
        // A truncated hash simply never matches an offer again; it cannot alias another trade.
        offerHash = truncate(offerHash, MAX_HASH_LENGTH);
        villagerName = truncate(villagerName, MAX_NAME_LENGTH);
        tradeSummary = truncate(tradeSummary, MAX_SUMMARY_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public boolean matches(UUID villagerUuid, String offerHash) {
        return this.villagerUuid.equals(villagerUuid) && this.offerHash.equals(offerHash);
    }
}
