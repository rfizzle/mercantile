package com.rfizzle.mercantile.reputation;

import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.GratitudeGiftTables;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.mixin.ItemEntityAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Honored-tier players occasionally receive a thrown, profession-flavored gratitude gift from a
 * nearby villager — the inverse of the player-to-villager gifting flow. Piggybacks on the
 * reputation proximity tick; no scanning of its own.
 */
public final class GratitudeGiftManager {

    /**
     * Inverse per-proximity-check chance (checks run every second): a gift lands on average after
     * ~3 minutes spent near villagers, so it reads as an occasional gesture rather than a payout.
     */
    private static final int GIFT_CHANCE_INVERSE = 180;

    private GratitudeGiftManager() {
    }

    /** Called from the reputation proximity tick with the already-computed nearby villager list. */
    public static void maybeGift(ServerPlayer player, List<Villager> nearby) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation || !config.enableGratitudeGifts) return;
        if (player.serverLevel().random.nextInt(GIFT_CHANCE_INVERSE) != 0) return;
        tryGiveGratitudeGift(player, nearby);
    }

    /**
     * Deterministic entry point (no random-chance gate): checks config, tier, and the per-day cap,
     * then has a random nearby villager toss a gift. Returns whether a gift was given.
     */
    public static boolean tryGiveGratitudeGift(ServerPlayer player, List<Villager> nearby) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation || !config.enableGratitudeGifts) return false;
        if (nearby.isEmpty()) return false;

        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        ReputationManager.migrateIfNeeded(data);
        if (ReputationTier.fromScore(data.getScore()) != ReputationTier.HONORED) return false;

        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        if (evaluateGratitudeGift(data, config, currentDay) != ReputationManager.CapDecision.AWARDED) {
            return false;
        }

        Villager giver = nearby.get(player.serverLevel().random.nextInt(nearby.size()));
        ResourceLocation professionKey = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(giver.getVillagerData().getProfession());
        String profession = professionKey == null ? "" : professionKey.getPath();
        ItemStack gift = GratitudeGiftTables.rollGift(profession, player.serverLevel().random);

        throwGiftTo(giver, gift, player);
        giver.playSound(SoundEvents.VILLAGER_YES, 1.0f, giver.getVoicePitch());
        // Happy villager particles, same channel the inbound gifting flow uses.
        giver.level().broadcastEntityEvent(giver, (byte) 14);
        return true;
    }

    /**
     * Vanilla {@code BehaviorUtils.throwItem} toss, plus an {@code ItemEntity} pickup target locked
     * to the recipient — without it the food-heavy gift stacks get hoovered up by the giver or a
     * neighboring villager before the player can reach them, silently burning the daily gift.
     */
    private static void throwGiftTo(Villager giver, ItemStack gift, ServerPlayer player) {
        ItemEntity itemEntity = new ItemEntity(
                giver.level(), giver.getX(), giver.getEyeY() - 0.3, giver.getZ(), gift);
        itemEntity.setThrower(giver);
        ((ItemEntityAccessor) itemEntity).setTarget(player.getUUID());
        Vec3 velocity = player.position().subtract(giver.position()).normalize().multiply(0.3, 0.3, 0.3);
        itemEntity.setDeltaMovement(velocity);
        itemEntity.setDefaultPickUpDelay();
        giver.level().addFreshEntity(itemEntity);
    }

    /**
     * Cap decision for gratitude gifts (stateful — mutates {@code data}). Counts gifts given, not
     * reputation: gifts are items, so they consume neither the daily rep total nor any rep sub-cap.
     * Rolls daily counters over when {@code currentDay} is newer. Pure of game classes; unit-testable.
     */
    public static ReputationManager.CapDecision evaluateGratitudeGift(PlayerData data, MercantileConfig config, long currentDay) {
        ReputationManager.rolloverIfNewDay(data, currentDay);
        if (data.getDailyGratitudeGifts() >= config.gratitudeGiftsPerDay) {
            return ReputationManager.CapDecision.SUBCAP_HIT;
        }
        data.incrementDailyGratitudeGifts();
        return ReputationManager.CapDecision.AWARDED;
    }
}
