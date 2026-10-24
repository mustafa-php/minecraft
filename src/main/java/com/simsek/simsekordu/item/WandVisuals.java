package com.simsek.simsekordu.item;

import com.simsek.simsekordu.data.MiningRegion;
import com.simsek.simsekordu.entity.goal.HuntHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/** Particle outlines of what a Commander's Wand has selected. Sent to the holder only. */
public final class WandVisuals {
    private static final ParticleOptions REGION = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.2F), 1.0F);
    private static final ParticleOptions CORNER_1 = new DustParticleOptions(new Vector3f(0.2F, 1.0F, 0.3F), 1.5F);
    private static final ParticleOptions CORNER_2 = new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.25F), 1.5F);
    private static final ParticleOptions STORAGE = new DustParticleOptions(new Vector3f(0.3F, 0.6F, 1.0F), 1.5F);
    private static final ParticleOptions HUNT_AREA = new DustParticleOptions(new Vector3f(1.0F, 0.5F, 0.1F), 1.0F);
    private static final ParticleOptions HUNT_CENTER = new DustParticleOptions(new Vector3f(1.0F, 0.5F, 0.1F), 1.5F);
    private static final int HUNT_RING_POINTS = 40;

    private WandVisuals() {
    }

    public static void showCorner(ServerPlayer player, int corner, BlockPos pos) {
        outline(player, new AABB(pos), corner == 1 ? CORNER_1 : CORNER_2);
    }

    public static void showStorage(ServerPlayer player, BlockPos pos) {
        outline(player, new AABB(pos), STORAGE);
    }

    /** Outlines whatever part of the selection exists: both corners, the region between them and the storage. */
    public static void showSelection(ServerPlayer player, WandSelection selection) {
        selection.corner1().ifPresent(pos -> showCorner(player, 1, pos));
        selection.corner2().ifPresent(pos -> showCorner(player, 2, pos));
        selection.region().ifPresent(region -> showRegion(player, region));
        selection.storage().ifPresent(pos -> showStorage(player, pos));
        selection.huntCenter().ifPresent(pos -> showHuntArea(player, pos));
    }

    /** A ring on the ground at the hunting radius around {@code center}, and a short pillar marking the centre. */
    public static void showHuntArea(ServerPlayer player, BlockPos center) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double radius = HuntHelper.RADIUS;
        double y = center.getY() + 0.3D;
        for (int i = 0; i < HUNT_RING_POINTS; i++) {
            double angle = 2.0D * Math.PI * i / HUNT_RING_POINTS;
            level.sendParticles(player, HUNT_AREA, true, center.getX() + 0.5D + Math.cos(angle) * radius, y,
                    center.getZ() + 0.5D + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        for (int i = 0; i < 6; i++) {
            level.sendParticles(player, HUNT_CENTER, true, center.getX() + 0.5D, center.getY() + 0.3D + i * 0.5D,
                    center.getZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void showRegion(ServerPlayer player, MiningRegion region) {
        BlockPos min = region.min();
        BlockPos max = region.max();
        outline(player, new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D), REGION);
    }

    /** Draws the 12 edges of {@code box} with at most ~9 particles per edge. */
    private static void outline(ServerPlayer player, AABB box, ParticleOptions particle) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        double stepX = Math.max(1.0D, (box.maxX - box.minX) / 8.0D);
        double stepY = Math.max(1.0D, (box.maxY - box.minY) / 8.0D);
        double stepZ = Math.max(1.0D, (box.maxZ - box.minZ) / 8.0D);
        for (double y : ys) {
            for (double z : zs) {
                for (double x = box.minX; x <= box.maxX + 1.0E-6D; x += stepX) {
                    level.sendParticles(player, particle, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                for (double y = box.minY; y <= box.maxY + 1.0E-6D; y += stepY) {
                    level.sendParticles(player, particle, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
        for (double x : xs) {
            for (double y : ys) {
                for (double z = box.minZ; z <= box.maxZ + 1.0E-6D; z += stepZ) {
                    level.sendParticles(player, particle, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }
}
