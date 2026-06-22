package com.rfizzle.mercantile.block;

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
    private static final int IDLE_HOSTILE_CHECK_INTERVAL_TICKS = 10;
    private static final int BELL_RING_COOLDOWN_TICKS = 200;

    private static final int[] ALL_SLOTS = {0};
    private static final int[] NO_SLOTS = new int[0];
    private static final int SLOT = 0;

    private int fuel = 0;
    private ItemStack virtualSlot = ItemStack.EMPTY;
    private boolean syncingSlot = false;
    private int outOfFuelCooldown = 0;
    private int scanCooldown = SCAN_INTERVAL_TICKS;
    private int bellCooldown = 0;
    private int idleTicks = 0;
    private int idleHostileCheckCooldown = IDLE_HOSTILE_CHECK_INTERVAL_TICKS;
    private final LinkedHashSet<UUID> sentries = new LinkedHashSet<>();

    public SentryPylonBlockEntity(BlockPos pos, BlockState state) {
        super(MercantileRegistry.SENTRY_PYLON_BE, pos, state);
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
            scanCooldown = SCAN_INTERVAL_TICKS;
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
            idleTicks = 0;
            idleHostileCheckCooldown = IDLE_HOSTILE_CHECK_INTERVAL_TICKS;
            return;
        }

        if (idleHostileCheckCooldown > 0) {
            idleHostileCheckCooldown--;
        }
        if (idleHostileCheckCooldown == 0) {
            idleHostileCheckCooldown = IDLE_HOSTILE_CHECK_INTERVAL_TICKS;
            int radius = MercantileConfig.get().pylonDetectionRadius;
            LivingEntity threat = SentryPylonScanner.findNearestHostile(server, worldPosition, radius);
            if (threat != null) {
                if (idleTicks != 0) {
                    idleTicks = 0;
                    setChanged();
                }
                return;
            }
        }

        idleTicks++;
        int threshold = MercantileConfig.get().sentryDespawnSeconds * 20;
        if (idleTicks >= threshold) {
            despawnAllSentries(server);
        }
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
        idleHostileCheckCooldown = IDLE_HOSTILE_CHECK_INTERVAL_TICKS;
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
        BlockState current = getBlockState();
        if (current.hasProperty(SentryPylonBlock.POWERED) && current.getValue(SentryPylonBlock.POWERED)) {
            return;
        }

        pruneSentries(server);

        int radius = MercantileConfig.get().pylonDetectionRadius;
        LivingEntity threat = SentryPylonScanner.findNearestHostile(server, worldPosition, radius);
        if (threat == null) {
            return;
        }

        if (MercantileConfig.get().enablePylonBellAlarm && bellCooldown <= 0) {
            tryRingBell(server);
        }

        if (sentries.size() >= MercantileConfig.get().pylonMaxGolems) {
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
        idleHostileCheckCooldown = IDLE_HOSTILE_CHECK_INTERVAL_TICKS;
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
                        // The ring itself carries to the 96-block glow broadcast (see BellBlockMixin),
                        // so a distant player hears it and isn't left with silently glowing villagers.
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
        scanCooldown = Mth.clamp(tag.getInt("ScanCooldown"), 0, SCAN_INTERVAL_TICKS);
        bellCooldown = Mth.clamp(tag.getInt("BellCooldown"), 0, BELL_RING_COOLDOWN_TICKS);
        int despawnThreshold = MercantileConfig.get().sentryDespawnSeconds * 20;
        idleTicks = Mth.clamp(tag.getInt("IdleTicks"), 0, despawnThreshold);
        idleHostileCheckCooldown = IDLE_HOSTILE_CHECK_INTERVAL_TICKS;
        sentries.clear();
        ListTag sentriesTag = tag.getList("Sentries", net.minecraft.nbt.Tag.TAG_LONG);
        for (int i = 0; i + 1 < sentriesTag.size(); i += 2) {
            long msb = ((LongTag) sentriesTag.get(i)).getAsLong();
            long lsb = ((LongTag) sentriesTag.get(i + 1)).getAsLong();
            sentries.add(new UUID(msb, lsb));
        }
    }
}
