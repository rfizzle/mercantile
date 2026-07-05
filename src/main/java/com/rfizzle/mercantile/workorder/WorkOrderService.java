package com.rfizzle.mercantile.workorder;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;

import java.util.List;
import java.util.Optional;

/**
 * Server-side execution of work orders (issue #90). Rather than assigning a profession directly,
 * an accepted order takes the target site's POI ticket and sets the villager's
 * {@code POTENTIAL_JOB_SITE} memory — mirroring vanilla {@code AcquirePoi}, including its
 * path-reachability check — so the vanilla pipeline handles the rest:
 * {@code GoToPotentialJobSite} walks the villager over, {@code AssignProfessionFromJobSite}
 * resolves the profession from the POI registry on arrival (modded professions included), and
 * {@code YieldJobSite}/timeout paths release the ticket. The POI type itself is resolved from the
 * held block via {@link PoiTypes#forState}, never a hardcoded list.
 */
public final class WorkOrderService {

    private WorkOrderService() {
    }

    /**
     * Resolves the acquirable job-site POI type identified by the held stack, or empty when the
     * stack is not a profession workstation item. Every blockstate is probed (not just the
     * default) so a modded job block whose POI matches only some states still resolves; the
     * {@code ACQUIRABLE_JOB_SITE} tag filter excludes POI blocks that are not workstations (beds,
     * bells, beehives).
     */
    public static Optional<Holder<PoiType>> resolveJobSitePoi(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return Optional.empty();
        return blockItem.getBlock().getStateDefinition().getPossibleStates().stream()
                .map(PoiTypes::forState)
                .flatMap(Optional::stream)
                .filter(holder -> holder.is(PoiTypeTags.ACQUIRABLE_JOB_SITE))
                .findFirst();
    }

    /**
     * Finds the nearest unclaimed, <em>reachable</em> workstation of the given POI type, claims
     * its ticket, and points the villager's brain at it. Returns the claimed position, or empty
     * when no free workstation of that type within {@link WorkOrder#SEARCH_RADIUS} can be pathed
     * to — in which case nothing was mutated and no fee should be charged.
     */
    public static Optional<BlockPos> placeOrder(ServerLevel level, Villager villager, Holder<PoiType> poiType) {
        PoiManager poiManager = level.getPoiManager();
        // Mirror AcquirePoi: consider the closest free sites and claim the nearest one the
        // villager can actually path to, so a player is never charged for an order the villager
        // provably cannot fulfil (a workstation sealed behind walls would otherwise eat the fee,
        // dead-claim the site for the walk timeout, and assign nothing).
        List<BlockPos> candidates = poiManager.findAllClosestFirstWithType(
                        holder -> holder.value() == poiType.value(),
                        pos -> true,
                        villager.blockPosition(),
                        WorkOrder.SEARCH_RADIUS,
                        PoiManager.Occupancy.HAS_SPACE)
                .limit(WorkOrder.MAX_CANDIDATES)
                .map(Pair::getSecond)
                .toList();

        for (BlockPos target : candidates) {
            Path path = villager.getNavigation().createPath(target, poiType.value().validRange());
            if (path == null || !path.canReach()) continue;

            // Take the ticket up front so no competitor grabs the site while the villager walks.
            // HAS_SPACE above guarantees a free ticket barring same-tick races, in which case
            // take() comes back empty and the next candidate is tried.
            Optional<BlockPos> taken = poiManager.take(
                    holder -> holder.value() == poiType.value(),
                    (holder, pos) -> pos.equals(target),
                    target, 1);
            if (taken.isEmpty()) continue;

            GlobalPos orderPos = GlobalPos.of(level.dimension(), target);
            // Release the ticket behind any prior POTENTIAL_JOB_SITE before overwriting it —
            // unless it is this very site (break-and-replace edge: the old ticket died with the
            // old POI record, and releasing here would hand back the ticket just taken).
            Optional<GlobalPos> existing = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
            if (existing.isPresent() && !existing.get().equals(orderPos)) {
                releaseTicket(level, existing.get());
            }
            villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, orderPos);
            return Optional.of(target);
        }
        return Optional.empty();
    }

    /**
     * Releases the ticket behind a {@code POTENTIAL_JOB_SITE} value, mirroring
     * {@code GoToPotentialJobSite#stop}: whoever set that memory (vanilla or us) also took the
     * ticket, so dropping the memory without a release would leak the slot.
     */
    private static void releaseTicket(ServerLevel level, GlobalPos globalPos) {
        ServerLevel targetLevel = level.getServer().getLevel(globalPos.dimension());
        if (targetLevel != null && targetLevel.getPoiManager().exists(globalPos.pos(), holder -> true)) {
            targetLevel.getPoiManager().release(globalPos.pos());
        }
    }
}
