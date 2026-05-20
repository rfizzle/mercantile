package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin extends NodeEvaluator {

    @Shadow
    protected abstract PathType getCachedPathType(int x, int y, int z);

    @Shadow
    protected abstract double getFloorLevel(BlockPos pos);

    @Inject(method = "getPathType(Lnet/minecraft/world/level/pathfinder/PathfindingContext;III)Lnet/minecraft/world/level/pathfinder/PathType;",
            at = @At("RETURN"), cancellable = true)
    private void mercantile$treatFenceGateAsPassable(PathfindingContext ctx, int x, int y, int z,
                                                     CallbackInfoReturnable<PathType> cir) {
        if (cir.getReturnValue() != PathType.FENCE) return;
        if (!(this.mob instanceof Villager)) return;
        if (!MercantileConfig.get().enablePathfindingFixes) return;
        if (!MercantileConfig.get().enablePathfindingDoors) return;

        BlockState state = ctx.getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
            cir.setReturnValue(PathType.DOOR_WOOD_CLOSED);
        }
    }

    @Inject(method = "findAcceptedNode", at = @At("HEAD"), cancellable = true)
    private void mercantile$handleStairSlabStepUp(int i, int j, int k, int l, double d,
                                                  Direction direction, PathType pathType,
                                                  CallbackInfoReturnable<Node> cir) {
        if (!(this.mob instanceof Villager)) return;
        if (!MercantileConfig.get().enablePathfindingFixes) return;
        if (!MercantileConfig.get().enablePathfindingStairs) return;

        BlockState state = this.currentContext.getBlockState(new BlockPos(i, j, k));
        if (!mercantile$isSteppableBlock(state)) return;

        PathType aboveType = this.getCachedPathType(i, j + 1, k);
        float aboveMalus = this.mob.getPathfindingMalus(aboveType);
        if (aboveMalus < 0.0F) return;

        double floorAbove = this.getFloorLevel(new BlockPos(i, j + 1, k));
        if (floorAbove - d > Math.max(1.125, (double) this.mob.maxUpStep())) return;

        Node node = this.getNode(i, j + 1, k);
        node.type = aboveType;
        node.costMalus = Math.max(node.costMalus, aboveMalus);
        cir.setReturnValue(node);
    }

    @Unique
    private static boolean mercantile$isSteppableBlock(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof StairBlock) {
            return state.getValue(StairBlock.HALF) == Half.BOTTOM;
        }
        if (block instanceof SlabBlock) {
            return state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        }
        return false;
    }

    @Inject(method = "getPathType(Lnet/minecraft/world/level/pathfinder/PathfindingContext;III)Lnet/minecraft/world/level/pathfinder/PathType;",
            at = @At("RETURN"), cancellable = true)
    private void mercantile$penalizeWaterPaths(PathfindingContext ctx, int x, int y, int z,
                                                CallbackInfoReturnable<PathType> cir) {
        PathType type = cir.getReturnValue();
        if (type != PathType.WATER_BORDER && type != PathType.WATER) return;
        if (!(this.mob instanceof Villager)) return;
        if (!MercantileConfig.get().enablePathfindingFixes) return;
        if (!MercantileConfig.get().enablePathfindingWater) return;

        if (type == PathType.WATER) {
            cir.setReturnValue(PathType.BLOCKED);
        } else {
            cir.setReturnValue(PathType.DANGER_OTHER);
        }
    }

    @Inject(method = "getNeighbors", at = @At("RETURN"), cancellable = true)
    private void mercantile$addClimbableNeighbors(Node[] nodes, Node node,
                                                   CallbackInfoReturnable<Integer> cir) {
        if (!(this.mob instanceof Villager)) return;
        if (!MercantileConfig.get().enablePathfindingFixes) return;
        if (!MercantileConfig.get().enablePathfindingLadders) return;

        BlockPos pos = node.asBlockPos();
        if (!this.currentContext.getBlockState(pos).is(BlockTags.CLIMBABLE)) return;

        int count = cir.getReturnValue();
        count = mercantile$tryAddClimbNode(nodes, count, pos.above());
        count = mercantile$tryAddClimbNode(nodes, count, pos.below());
        cir.setReturnValue(count);
    }

    @Unique
    private int mercantile$tryAddClimbNode(Node[] nodes, int count, BlockPos target) {
        if (!this.currentContext.getBlockState(target).is(BlockTags.CLIMBABLE)) return count;

        Node node = this.getNode(target.getX(), target.getY(), target.getZ());
        if (node.closed) return count;

        node.type = PathType.WALKABLE;
        node.costMalus = Math.max(node.costMalus, 0.0F);
        nodes[count] = node;
        return count + 1;
    }
}
