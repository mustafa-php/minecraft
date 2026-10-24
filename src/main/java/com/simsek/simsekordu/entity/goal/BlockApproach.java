package com.simsek.simsekordu.entity.goal;

import com.simsek.simsekordu.entity.SimsekNpc;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Gets a soldier within reach of a block: walks to a free cell near it and, where no walkable route exists (for
 * instance out of a deep pit), teleports next to it instead. Call {@link #approach} every tick while moving.
 */
public final class BlockApproach {
    public enum Result {
        ARRIVED,
        MOVING,
        FAILED
    }

    /** Stand cells are chosen this much closer than the reach, as the NPC rarely stops exactly in the cell centre. */
    private static final double STAND_MARGIN = 0.7D;
    private static final int TIMEOUT_TICKS = 300;
    private static final int PATH_RETRY_TICKS = 10;
    private static final int MAX_PATH_FAILURES = 3;

    private final SimsekNpc npc;
    private int ticks;
    private long nextPathTick;
    private int pathFailures;
    private final Set<BlockPos> failedCells = new HashSet<>();

    public BlockApproach(SimsekNpc npc) {
        this.npc = npc;
    }

    /** Whether the NPC's eyes are within {@code reach} of the closest point of {@code block}. */
    public boolean inReach(BlockPos block, double reach) {
        Vec3 eye = this.npc.getEyePosition();
        return MiningHelper.distanceToBlockSqr(block, eye.x, eye.y, eye.z) <= reach * reach;
    }

    public Result approach(ServerLevel level, BlockPos block, double reach) {
        return approach(level, block, reach, 1.0D);
    }

    public Result approach(ServerLevel level, BlockPos block, double reach, double speed) {
        PathNavigation navigation = this.npc.getNavigation();
        if (inReach(block, reach)) {
            navigation.stop();
            reset();
            return Result.ARRIVED;
        }
        if (++this.ticks > TIMEOUT_TICKS) {
            reset();
            return Result.FAILED;
        }
        long now = level.getGameTime();
        if (navigation.isInProgress() || now < this.nextPathTick) {
            return Result.MOVING;
        }
        this.nextPathTick = now + PATH_RETRY_TICKS;
        double standDistance = reach - STAND_MARGIN;
        BlockPos cell = findStandCell(level, block, standDistance, false);
        if (cell != null) {
            Path path = navigation.createPath(cell, 0);
            if (path != null && path.canReach() && navigation.moveTo(path, speed)) {
                return Result.MOVING;
            }
            this.failedCells.add(cell);
            if (++this.pathFailures < MAX_PATH_FAILURES) {
                return Result.MOVING;
            }
        }
        // No walkable route (or every cell near the block was tried): teleport next to it instead.
        BlockPos fallback = cell != null ? cell : findStandCell(level, block, standDistance, true);
        if (fallback != null && teleportTo(level, fallback)) {
            this.pathFailures = 0;
            this.failedCells.clear();
            return Result.MOVING;
        }
        reset();
        return Result.FAILED;
    }

    public void reset() {
        this.ticks = 0;
        this.nextPathTick = 0L;
        this.pathFailures = 0;
        this.failedCells.clear();
    }

    /** The standable cell closest to the NPC from which {@code block} is within {@code maxEyeDistance}. */
    @Nullable
    private BlockPos findStandCell(ServerLevel level, BlockPos block, double maxEyeDistance, boolean includeFailed) {
        double eyeHeight = this.npc.getEyeHeight();
        double maxDistanceSqr = maxEyeDistance * maxEyeDistance;
        Vec3 from = this.npc.position();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    cell.set(block.getX() + dx, block.getY() + dy, block.getZ() + dz);
                    if (MiningHelper.distanceToBlockSqr(block, cell.getX() + 0.5D, cell.getY() + eyeHeight, cell.getZ() + 0.5D) > maxDistanceSqr
                            || (!includeFailed && this.failedCells.contains(cell)) || !MiningHelper.isStandable(level, cell)) {
                        continue;
                    }
                    double distance = cell.distToCenterSqr(from.x, from.y, from.z);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cell.immutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean teleportTo(ServerLevel level, BlockPos cell) {
        Vec3 destination = Vec3.atBottomCenterOf(cell);
        if (!level.noCollision(this.npc, this.npc.getBoundingBox().move(destination.subtract(this.npc.position())))) {
            return false;
        }
        level.sendParticles(ParticleTypes.POOF, this.npc.getX(), this.npc.getY() + 0.5D, this.npc.getZ(), 8, 0.3D, 0.5D, 0.3D, 0.02D);
        this.npc.getNavigation().stop();
        this.npc.teleportTo(destination.x, destination.y, destination.z);
        this.npc.fallDistance = 0.0F;
        level.sendParticles(ParticleTypes.POOF, destination.x, destination.y + 0.5D, destination.z, 8, 0.3D, 0.5D, 0.3D, 0.02D);
        return true;
    }
}
