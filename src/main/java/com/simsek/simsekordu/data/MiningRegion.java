package com.simsek.simsekordu.data;

import net.minecraft.core.BlockPos;

/** Inclusive axis-aligned block region a miner works on. {@code min} and {@code max} are normalized corners. */
public record MiningRegion(BlockPos min, BlockPos max) {
    /** Maximum extent of a region along every axis, in blocks. */
    public static final int MAX_SIZE = 16;

    public static MiningRegion of(BlockPos a, BlockPos b) {
        return new MiningRegion(
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
    }

    public int sizeX() {
        return this.max.getX() - this.min.getX() + 1;
    }

    public int sizeY() {
        return this.max.getY() - this.min.getY() + 1;
    }

    public int sizeZ() {
        return this.max.getZ() - this.min.getZ() + 1;
    }

    public boolean fitsLimit() {
        return sizeX() <= MAX_SIZE && sizeY() <= MAX_SIZE && sizeZ() <= MAX_SIZE;
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= this.min.getX() && pos.getX() <= this.max.getX()
                && pos.getY() >= this.min.getY() && pos.getY() <= this.max.getY()
                && pos.getZ() >= this.min.getZ() && pos.getZ() <= this.max.getZ();
    }
}
