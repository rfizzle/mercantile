package com.rfizzle.mercantile.block;

import com.rfizzle.mercantile.compat.tribulation.TribulationCompat;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.particle.MercantileParticles;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SentryPylonBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final Vector3f RED = new Vector3f(1.0f, 0.0f, 0.0f);
    private static final int OUT_OF_FUEL_ALERT_COOLDOWN_TICKS = 40;
    private static final int SCAN_INTERVAL_TICKS = 40;
    /** Radius at which {@link #SCAN_INTERVAL_TICKS} holds; beyond it the cadence stretches. */
    private static final int SCAN_INTERVAL_BASE_RADIUS = 32;
    /** Mirror of the config clamp ceiling on {@code pylonDetectionRadius}; bounds the saved cooldown. */
    private static final int MAX_DETECTION_RADIUS = 128;
    private static final int MAX_SCAN_INTERVAL_TICKS = scanIntervalTicks(MAX_DETECTION_RADIUS);
    /** Recheck cadence baseline — see {@link #idleHostileCheckIntervalTicks}. */
    private static final int IDLE_HOSTILE_CHECK_INTERVAL_TICKS = 10;
    private static final int BELL_RING_COOLDOWN_TICKS = 200;
    /**
     * A sentry struck by an in-zone hostile within this many ticks still counts as engaged, so the
     * despawn countdown holds through the lull between exchanges even when the golem has no active
     * target (e.g. taking fire it can't yet path to). Kept below vanilla's 100-tick {@code lastHurtByMob}
     * auto-clear so the timestamp read stays meaningful.
     */
    private static final int RECENT_DAMAGE_WINDOW_TICKS = 60;

    private static final int[] ALL_SLOTS = {0};
    private static final int[] NO_SLOTS = new int[0];
    private static final int SLOT = 0;

    private int fuel = 0;
    private ItemStack virtualSlot = ItemStack.EMPTY;
    private boolean syncingSlot = false;
    private int outOfFuelCooldown = 0;
    private int scanCooldown;
    private int bellCooldown = 0;
    private int idleTicks = 0;
    private int idleHostileCheckCooldown = idleHostileCheckInterval();
    private final LinkedHashSet<UUID> sentries = new LinkedHashSet<>();

    public SentryPylonBlockEntity(BlockPos pos, BlockState state) {
        super(MercantileRegistry.SENTRY_PYLON_BE, pos, state);
        // Stagger the first scan by a position-derived phase so pylons loaded on the same tick
        // don't all sweep on the same tick. loadAdditional overrides this for an existing pylon,
        // preserving the phase it was placed with.
        int interval = scanIntervalTicks(MercantileConfig.get().pylonDetectionRadius);
        this.scanCooldown = scanPhaseOffset(pos, interval);
    }

    /**
     * Scan cadence scaled by detection radius. Each scan sweeps an AABB whose volume grows with the
     * cube of the radius (plus per-candidate line-of-sight raycasts), so a wide-radius pylon scans
     * proportionally less often: the baseline holds up to {@link #SCAN_INTERVAL_BASE_RADIUS} and
     * stretches linearly beyond it. Paired with the per-pylon phase stagger, this caps the tick cost
     * a field of maxed-out pylons can impose without making a default-radius pylon feel sluggish.
     */
    static int scanIntervalTicks(int radius) {
        long scaled = Math.round((double) SCAN_INTERVAL_TICKS * radius / SCAN_INTERVAL_BASE_RADIUS);
        return (int) Math.max(SCAN_INTERVAL_TICKS, scaled);
    }

    /**
     * Idle-hostile recheck cadence, scaled by detection radius exactly like {@link #scanIntervalTicks}.
     * While a pylon holds sentries it rechecks for a threat by running the same radius-wide AABB +
     * line-of-sight query the main scan does, so a wide-radius pylon must recheck proportionally less
     * often rather than pay the full query on a fixed cadence: the baseline holds up to
     * {@link #SCAN_INTERVAL_BASE_RADIUS} and stretches linearly beyond it. At the default radius the
     * interval is the baseline, so despawn-countdown responsiveness is unchanged there.
     */
    static int idleHostileCheckIntervalTicks(int radius) {
        long scaled = Math.round((double) IDLE_HOSTILE_CHECK_INTERVAL_TICKS * radius / SCAN_INTERVAL_BASE_RADIUS);
        return (int) Math.max(IDLE_HOSTILE_CHECK_INTERVAL_TICKS, scaled);
    }

    /**
     * The idle-hostile recheck interval at this pylon's configured detection radius. Reads the raw
     * {@code pylonDetectionRadius} — the same radius the recheck query itself uses, and the same source
     * {@link #scanIntervalTicks} is fed for the main scan cadence.
     */
    private int idleHostileCheckInterval() {
        return idleHostileCheckIntervalTicks(MercantileConfig.get().pylonDetectionRadius);
    }

    /**
     * A deterministic phase offset in {@code [0, interval)} derived from the block position, so two
     * pylons at different positions land on different ticks in the scan cycle. Uses an avalanche mix
     * of the packed position to spread axis-aligned grids evenly rather than clustering on the low bits.
     */
    static int scanPhaseOffset(BlockPos pos, int interval) {
        long h = pos.asLong();
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (int) Math.floorMod(h, (long) interval);
    }

    public int getFuel() {
        return fuel;
    }

    public int getMaxFuel() {
        return MercantileConfig.get().pylonMaxFuel;
    }

    public void setFuel(int value) {
        int clamped = Mth.clamp(value, 0, getMaxFuel());
        if (clamped == fuel) {
            return;
        }
        fuel = clamped;
        if (clamped == 0) {
            virtualSlot = ItemStack.EMPTY;
        } else if (!virtualSlot.isEmpty()) {
            virtualSlot.setCount(clamped);
        }
        syncingSlot = true;
        try {
            setChanged();
        } finally {
            syncingSlot = false;
        }
        if (level != null) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        updateVisualState();
    }

    public boolean addFuel(int amount) {
        if (amount <= 0) return false;
        int max = getMaxFuel();
        if (fuel >= max) return false;
        setFuel(fuel + amount);
        return true;
    }

    public boolean consumeFuel(int amount) {
        if (amount <= 0) return true;
        if (fuel < amount) return false;
        setFuel(fuel - amount);
        return true;
    }

    public void tryAlertOutOfFuel() {
        if (outOfFuelCooldown > 0) return;
        if (!(level instanceof ServerLevel server)) return;

        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 1.05;
        double cz = worldPosition.getZ() + 0.5;
        server.sendParticles(new DustParticleOptions(RED, 1.5f), cx, cy, cz, 12, 0.25, 0.1, 0.25, 0.0);
        server.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASEDRUM.value(),
                SoundSource.BLOCKS, 0.8f, 0.5f);
        outOfFuelCooldown = OUT_OF_FUEL_ALERT_COOLDOWN_TICKS;
        setChanged();
    }

    public int getOutOfFuelCooldown() {
        return outOfFuelCooldown;
    }

    public int getScanCooldown() {
        return scanCooldown;
    }

    public Set<UUID> getSentries() {
        return Collections.unmodifiableSet(sentries);
    }

    public int getIdleTicks() {
        return idleTicks;
    }

    public void addSentryForTesting(UUID uuid) {
        sentries.add(uuid);
        idleTicks = 0;
        setChanged();
    }

    public void setScanCooldownForTesting(int value) {
        this.scanCooldown = value;
    }

    public void tickServerCommon() {
        if (outOfFuelCooldown > 0) {
            outOfFuelCooldown--;
        }
        if (scanCooldown > 0) {
            scanCooldown--;
        }
        if (bellCooldown > 0) {
            bellCooldown--;
        }
        if (scanCooldown == 0) {
            scanCooldown = scanIntervalTicks(MercantileConfig.get().pylonDetectionRadius);
            if (level instanceof ServerLevel server) {
                runScanCycle(server);
            }
        }
        if (level instanceof ServerLevel server) {
            tickDespawnCountdown(server);
        }
    }

    private void tickDespawnCountdown(ServerLevel server) {
        pruneSentries(server);
        if (sentries.isEmpty()) {
            // No re-prime here: the cooldown isn't decremented while empty, and every path that
            // adds a sentry (runScanCycle) or resets state (despawn, load) re-primes it fresh.
            idleTicks = 0;
            return;
        }

        // A redstone-disabled pylon stops scanning and winds its summons down (spec §18): while
        // POWERED it skips both hold checks below, so no scan cost is paid and idleTicks accrues
        // unconditionally — its sentries despawn on the normal countdown. An enabled pylon runs the
        // holds: a sentry fighting — or under fire from — an in-zone threat keeps the countdown open
        // even when the pylon itself has no line of sight to that threat (issue #164), and the
        // pylon-LoS recheck is the fallback for a threat that's present but not yet engaged.
        if (!isPowered()) {
            // The golem's own target/attacker is authoritative for "combat is happening here". Cheap
            // enough to run every tick: a bounded set of already-pruned sentries, no raycasts.
            if (anySentryEngaged(server)) {
                if (idleTicks != 0) {
                    idleTicks = 0;
                    setChanged();
                }
                return;
            }

            if (idleHostileCheckCooldown > 0) {
                idleHostileCheckCooldown--;
            }
            if (idleHostileCheckCooldown == 0) {
                idleHostileCheckCooldown = idleHostileCheckInterval();
                int radius = MercantileConfig.get().pylonDetectionRadius;
                LivingEntity threat = SentryPylonScanner.findNearestVisibleHostile(server, worldPosition, radius);
                if (threat != null) {
                    if (idleTicks != 0) {
                        idleTicks = 0;
                        setChanged();
                    }
                    return;
                }
            }
        }

        idleTicks++;
        int threshold = MercantileConfig.get().sentryDespawnSeconds * 20;
        if (idleTicks >= threshold) {
            despawnAllSentries(server);
        }
    }

    /**
     * Whether any tracked sentry is currently engaged with an in-zone threat — its target or its most
     * recent attacker is a live hostile inside the defended sphere. Walks the (already-pruned) sentry
     * set and reads each golem's target/attacker directly; no entity search or raycast.
     */
    private boolean anySentryEngaged(ServerLevel server) {
        int radius = MercantileConfig.get().pylonDetectionRadius;
        for (UUID uuid : sentries) {
            if (!(server.getEntity(uuid) instanceof IronGolem golem)
                    || !golem.isAlive() || !SentryGolemTag.isSentry(golem)) {
                continue;
            }
            boolean targetInZone = isInZoneThreat(golem.getTarget(), radius);
            boolean attackerInZone = isInZoneThreat(golem.getLastHurtByMob(), radius);
            int ticksSinceHurt = golem.tickCount - golem.getLastHurtByMobTimestamp();
            if (SentryPylonScanner.sentryHoldsCountdown(
                    targetInZone, attackerInZone, ticksSinceHurt, RECENT_DAMAGE_WINDOW_TICKS)) {
                return true;
            }
        }
        return false;
    }

    /** A live hostile within this pylon's defended sphere. {@code isHostile} already rejects the dead. */
    private boolean isInZoneThreat(@Nullable LivingEntity entity, int radius) {
        return entity != null
                && SentryPylonScanner.isHostile(entity)
                && SentryPylonScanner.withinDefendedZone(
                        entity.getX(), entity.getY(), entity.getZ(), worldPosition, radius);
    }

    private void despawnAllSentries(ServerLevel server) {
        if (sentries.isEmpty()) {
            idleTicks = 0;
            return;
        }
        List<UUID> snapshot = new ArrayList<>(sentries);
        for (UUID uuid : snapshot) {
            Entity entity = server.getEntity(uuid);
            if (entity instanceof IronGolem golem && golem.isAlive() && SentryGolemTag.isSentry(golem)) {
                despawnSentry(server, golem);
            }
        }
        sentries.clear();
        idleTicks = 0;
        idleHostileCheckCooldown = idleHostileCheckInterval();
        setChanged();
        updateVisualState();
    }

    private void despawnSentry(ServerLevel server, IronGolem golem) {
        double gx = golem.getX();
        double gy = golem.getY() + golem.getBbHeight() * 0.5;
        double gz = golem.getZ();
        server.sendParticles(MercantileParticles.GOLEM_SHARD, gx, gy, gz, 12, 0.4, 0.6, 0.4, 0.05);
        server.playSound(null, golem.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE,
                SoundSource.HOSTILE, 1.0f, 0.9f);
        golem.discard();
    }

    private void runScanCycle(ServerLevel server) {
        if (!MercantileConfig.get().enableSentryPylon) {
            return;
        }
        if (isPowered()) {
            return;
        }

        pruneSentries(server);

        // With Tribulation installed the golem cap and detection radius scale with the local
        // threat tier; otherwise these are exactly the configured defaults.
        TribulationCompat.EffectivePylonLimits limits =
                TribulationCompat.effectiveLimits(server, worldPosition, MercantileConfig.get());
        int radius = limits.detectionRadius();
        LivingEntity threat = SentryPylonScanner.findNearestVisibleHostile(server, worldPosition, radius);
        if (threat == null) {
            return;
        }

        if (MercantileConfig.get().enablePylonBellAlarm && bellCooldown <= 0) {
            tryRingBell(server);
        }

        if (sentries.size() >= limits.maxGolems()) {
            return;
        }

        if (fuel <= 0) {
            tryAlertOutOfFuel();
            return;
        }

        BlockPos spawnPos = SentryPylonScanner.findSpawnPos(server, threat.blockPosition(), worldPosition, radius);
        if (spawnPos == null) {
            return;
        }
        if (!threat.isAlive()) {
            return;
        }

        IronGolem golem = new IronGolem(EntityType.IRON_GOLEM, server);
        golem.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                server.getRandom().nextFloat() * 360.0f, 0.0f);
        golem.setPlayerCreated(true);
        golem.setPersistenceRequired();
        golem.finalizeSpawn(server, server.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.MOB_SUMMONED, null);
        SentryGolemTag.markAsSentry(golem, worldPosition);
        if (!server.addFreshEntity(golem)) {
            return;
        }

        sentries.add(golem.getUUID());
        idleTicks = 0;
        idleHostileCheckCooldown = idleHostileCheckInterval();
        consumeFuel(1);
        setChanged();
        updateVisualState();
    }

    private void tryRingBell(ServerLevel server) {
        SentryPylonScanner.findNearestBell(server, worldPosition, MercantileConfig.get().pylonDetectionRadius)
                .ifPresent(bellPos -> {
                    BlockState state = server.getBlockState(bellPos);
                    if (state.getBlock() instanceof BellBlock bellBlock) {
                        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(bellPos), Direction.UP, bellPos, false);
                        // onHit's final arg skips hit-position validation when false, forcing the ring
                        // for this non-player (Direction.UP) trigger. The ring itself carries to the
                        // 96-block glow broadcast (see BellBlockMixin), so a distant player hears it
                        // and isn't left with silently glowing villagers.
                        bellBlock.onHit(server, state, hit, null, false);
                        bellCooldown = BELL_RING_COOLDOWN_TICKS;
                        setChanged();
                    }
                });
    }

    private void pruneSentries(ServerLevel server) {
        if (sentries.isEmpty()) return;
        Iterator<UUID> it = sentries.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity entity = server.getEntity(uuid);
            if (!(entity instanceof IronGolem golem) || !golem.isAlive() || !SentryGolemTag.isSentry(golem)) {
                it.remove();
            }
        }
    }

    /**
     * Whether the pylon is redstone-disabled. A powered pylon neither scans/spawns
     * ({@link #runScanCycle}) nor holds its sentries against the despawn countdown
     * ({@link #tickDespawnCountdown}) — the single read both paths share.
     */
    private boolean isPowered() {
        BlockState current = getBlockState();
        return current.hasProperty(SentryPylonBlock.POWERED) && current.getValue(SentryPylonBlock.POWERED);
    }

    public void updateVisualState() {
        if (level == null || level.isClientSide) return;
        BlockState current = getBlockState();
        if (!current.hasProperty(SentryPylonBlock.STATE) || !current.hasProperty(SentryPylonBlock.POWERED)) {
            return;
        }
        boolean powered = current.getValue(SentryPylonBlock.POWERED);
        PylonStateProperty desired;
        if (powered || fuel == 0) {
            desired = PylonStateProperty.EMPTY;
        } else if (!sentries.isEmpty()) {
            desired = PylonStateProperty.ACTIVE;
        } else {
            desired = PylonStateProperty.IDLE;
        }
        if (current.getValue(SentryPylonBlock.STATE) != desired) {
            level.setBlock(worldPosition, current.setValue(SentryPylonBlock.STATE, desired),
                    Block.UPDATE_CLIENTS);
        }
    }

    // --- WorldlyContainer ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        return MercantileConfig.get().enableSentryPylon ? ALL_SLOTS : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return false;
    }

    // --- Container ---

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return fuel <= 0;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot != SLOT) return ItemStack.EMPTY;
        if (fuel <= 0) {
            virtualSlot = ItemStack.EMPTY;
        } else if (virtualSlot.isEmpty() || virtualSlot.getCount() != fuel) {
            virtualSlot = new ItemStack(Items.IRON_BLOCK, fuel);
        }
        return virtualSlot;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!MercantileConfig.get().enableSentryPylon) return;
        if (slot != SLOT) return;
        if (stack.isEmpty()) {
            setFuel(0);
            return;
        }
        if (!stack.is(Items.IRON_BLOCK)) return;
        setFuel(stack.getCount());
    }

    @Override
    public int getMaxStackSize() {
        return getMaxFuel();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        setFuel(0);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        MercantileConfig cfg = MercantileConfig.get();
        if (!cfg.enableSentryPylon) return false;
        return slot == SLOT && stack.is(Items.IRON_BLOCK) && fuel < cfg.pylonMaxFuel;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (!syncingSlot && !virtualSlot.isEmpty() && virtualSlot.is(Items.IRON_BLOCK)
                && MercantileConfig.get().enableSentryPylon) {
            int slotCount = virtualSlot.getCount();
            if (slotCount != fuel) {
                syncingSlot = true;
                try {
                    int clamped = Mth.clamp(slotCount, 0, getMaxFuel());
                    virtualSlot.setCount(clamped);
                    if (clamped != fuel) {
                        fuel = clamped;
                        updateVisualState();
                        if (level != null) {
                            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
                        }
                    }
                } finally {
                    syncingSlot = false;
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("Fuel", fuel);
        tag.putInt("OutOfFuelCooldown", outOfFuelCooldown);
        tag.putInt("ScanCooldown", scanCooldown);
        tag.putInt("BellCooldown", bellCooldown);
        tag.putInt("IdleTicks", idleTicks);
        ListTag sentriesTag = new ListTag();
        for (UUID uuid : sentries) {
            sentriesTag.add(LongTag.valueOf(uuid.getMostSignificantBits()));
            sentriesTag.add(LongTag.valueOf(uuid.getLeastSignificantBits()));
        }
        tag.put("Sentries", sentriesTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        fuel = Mth.clamp(tag.getInt("Fuel"), 0, getMaxFuel());
        outOfFuelCooldown = Mth.clamp(tag.getInt("OutOfFuelCooldown"), 0, OUT_OF_FUEL_ALERT_COOLDOWN_TICKS);
        scanCooldown = Mth.clamp(tag.getInt("ScanCooldown"), 0, MAX_SCAN_INTERVAL_TICKS);
        bellCooldown = Mth.clamp(tag.getInt("BellCooldown"), 0, BELL_RING_COOLDOWN_TICKS);
        int despawnThreshold = MercantileConfig.get().sentryDespawnSeconds * 20;
        idleTicks = Mth.clamp(tag.getInt("IdleTicks"), 0, despawnThreshold);
        idleHostileCheckCooldown = idleHostileCheckInterval();
        sentries.clear();
        ListTag sentriesTag = tag.getList("Sentries", net.minecraft.nbt.Tag.TAG_LONG);
        for (int i = 0; i + 1 < sentriesTag.size(); i += 2) {
            long msb = ((LongTag) sentriesTag.get(i)).getAsLong();
            long lsb = ((LongTag) sentriesTag.get(i + 1)).getAsLong();
            sentries.add(new UUID(msb, lsb));
        }
    }
}
