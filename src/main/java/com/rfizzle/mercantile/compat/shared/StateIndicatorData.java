package com.rfizzle.mercantile.compat.shared;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.contract.DeliveryContract;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class StateIndicatorData {

    public static final String KEY_PRESENT = "mercantile:statePresent";
    public static final String KEY_STATES = "mercantile:stateList";
    public static final String KEY_TRADING_PLAYER = "mercantile:stateTradingPlayer";
    public static final String KEY_WORKSTATION_ITEM = "mercantile:stateWorkstationItem";

    public static final String STATE_TRADING = "trading";
    public static final String STATE_PANICKING = "panicking";
    public static final String STATE_NEEDS_WORKSTATION = "needs_workstation";
    public static final String STATE_UNEMPLOYED = "unemployed";
    public static final String STATE_HAS_CONTRACT_OFFER = "has_contract_offer";
    public static final String STATE_PROFESSION_LOCKED = "profession_locked";

    private static final Map<String, Block> PROFESSION_WORKSTATIONS = Map.ofEntries(
            Map.entry("armorer", Blocks.BLAST_FURNACE),
            Map.entry("butcher", Blocks.SMOKER),
            Map.entry("cartographer", Blocks.CARTOGRAPHY_TABLE),
            Map.entry("cleric", Blocks.BREWING_STAND),
            Map.entry("farmer", Blocks.COMPOSTER),
            Map.entry("fisherman", Blocks.BARREL),
            Map.entry("fletcher", Blocks.FLETCHING_TABLE),
            Map.entry("leatherworker", Blocks.CAULDRON),
            Map.entry("librarian", Blocks.LECTERN),
            Map.entry("mason", Blocks.STONECUTTER),
            Map.entry("shepherd", Blocks.LOOM),
            Map.entry("toolsmith", Blocks.SMITHING_TABLE),
            Map.entry("weaponsmith", Blocks.GRINDSTONE)
    );

    private StateIndicatorData() {}

    public static void write(CompoundTag tag, Villager villager) {
        tag.putBoolean(KEY_PRESENT, true);

        if (villager.isBaby()) {
            tag.put(KEY_STATES, new ListTag());
            return;
        }

        Set<String> states = new LinkedHashSet<>();

        Player tradingPlayer = villager.getTradingPlayer();
        if (tradingPlayer != null) {
            states.add(STATE_TRADING);
            tag.putString(KEY_TRADING_PLAYER, tradingPlayer.getName().getString());
        }

        if (isPanicking(villager)) {
            states.add(STATE_PANICKING);
        }

        MercantileConfig config = MercantileConfig.get();

        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (needsWorkstation(profession, villager)) {
            states.add(STATE_NEEDS_WORKSTATION);
            Block workstation = workstationFor(profession);
            if (workstation != null) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(workstation.asItem());
                tag.putString(KEY_WORKSTATION_ITEM, itemId.toString());
            }
        }

        // Unemployed adults (profession NONE, not NITWIT) can take a work order — the gesture
        // sends them to claim a nearby workstation. Gated on the owning feature toggle.
        if (config.enableWorkOrders && profession == VillagerProfession.NONE) {
            states.add(STATE_UNEMPLOYED);
        }

        // A live, unaccepted delivery offer waiting to be signed with paper.
        if (config.enableContracts && hasLiveContractOffer(villager)) {
            states.add(STATE_HAS_CONTRACT_OFFER);
        }

        // Profession lock — surfaced here so the overlay players read villager state from
        // reflects it, not just the trade GUI's title-bar glyph.
        if (config.enableProfessionLock && isProfessionLocked(villager)) {
            states.add(STATE_PROFESSION_LOCKED);
        }

        ListTag list = new ListTag();
        for (String state : states) {
            list.add(StringTag.valueOf(state));
        }
        tag.put(KEY_STATES, list);
    }

    private static boolean isPanicking(Villager villager) {
        var brain = villager.getBrain();
        return brain.hasMemoryValue(MemoryModuleType.HURT_BY)
                || brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)
                || brain.isActive(net.minecraft.world.entity.schedule.Activity.PANIC);
    }

    private static boolean hasLiveContractOffer(Villager villager) {
        MercantileVillagerData data = villager.getAttached(MercantileAttachments.VILLAGER_DATA);
        if (data == null) return false;
        DeliveryContract contract = data.getContract();
        if (contract == null || contract.accepted()) return false;
        return !contract.isExpired(villager.level().getGameTime());
    }

    private static boolean isProfessionLocked(Villager villager) {
        MercantileVillagerData data = villager.getAttached(MercantileAttachments.VILLAGER_DATA);
        return data != null && data.isProfessionLocked();
    }

    private static boolean needsWorkstation(VillagerProfession profession, Villager villager) {
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) return false;
        return !villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE);
    }

    static Block workstationFor(VillagerProfession profession) {
        if (profession == null) return null;
        return PROFESSION_WORKSTATIONS.get(profession.name());
    }
}
