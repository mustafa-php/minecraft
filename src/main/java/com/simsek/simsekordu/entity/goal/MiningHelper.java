package com.simsek.simsekordu.entity.goal;

import com.simsek.simsekordu.data.MiningRegion;
import com.simsek.simsekordu.entity.SimsekNpc;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.Tags;

/** Block and tool rules shared by {@link MineGoal} and the NPC's task bookkeeping. */
public final class MiningHelper {
    /** Result of scanning a region: how many blocks still have to be mined, and whether all of it was loaded. */
    public record ScanResult(int mineable, boolean allLoaded) {
    }

    private MiningHelper() {
    }

    public static boolean isPickaxe(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ItemTags.PICKAXES) || stack.canPerformAction(ItemAbilities.PICKAXE_DIG));
    }

    /**
     * Whether a miner may break this block: not air, not a fluid, not unbreakable (bedrock and friends), not a
     * chest-like container and not the assigned storage block.
     */
    public static boolean isMineable(BlockGetter level, BlockPos pos, BlockState state, @Nullable BlockPos storage) {
        if (state.isAir() || pos.equals(storage) || state.getBlock() instanceof LiquidBlock) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return !isProtectedContainer(state);
    }

    private static boolean isProtectedContainer(BlockState state) {
        return state.is(Tags.Blocks.CHESTS) || state.is(Tags.Blocks.BARRELS)
                || state.getBlock() instanceof ChestBlock || state.getBlock() instanceof EnderChestBlock
                || state.getBlock() instanceof BarrelBlock || state.getBlock() instanceof ShulkerBoxBlock;
    }

    /** Counts the blocks of {@code region} that still need mining. Unloaded positions are not read (nor loaded). */
    public static ScanResult countMineable(ServerLevel level, MiningRegion region, @Nullable BlockPos storage) {
        int count = 0;
        boolean allLoaded = true;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = region.min().getX(); x <= region.max().getX(); x++) {
            for (int z = region.min().getZ(); z <= region.max().getZ(); z++) {
                for (int y = region.min().getY(); y <= region.max().getY(); y++) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor)) {
                        allLoaded = false;
                    } else if (isMineable(level, cursor, level.getBlockState(cursor), storage)) {
                        count++;
                    }
                }
            }
        }
        return new ScanResult(count, allLoaded);
    }

    /**
     * Fraction of a block broken per tick, following vanilla: tool speed (plus efficiency), divided by hardness and by 30
     * (100 without the right tool), scaled by the soldier's rank bonus.
     */
    public static float breakProgressPerTick(SimsekNpc npc, BlockGetter level, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            return 0.0F;
        }
        if (hardness == 0.0F) {
            return 1.0F;
        }
        ItemStack tool = npc.getMainHandItem();
        float speed = tool.getDestroySpeed(state);
        if (speed > 1.0F) {
            speed += (float) npc.getAttributeValue(Attributes.MINING_EFFICIENCY);
        }
        speed *= (float) npc.getRank().getMiningSpeedMultiplier();
        if (npc.isEyeInFluid(FluidTags.WATER)) {
            speed *= 0.2F;
        }
        if (!npc.onGround()) {
            speed /= 5.0F;
        }
        return speed / hardness / (canHarvest(tool, state) ? 30.0F : 100.0F);
    }

    /** Whether breaking {@code state} with {@code tool} yields drops (vanilla's "correct tool for drops"). */
    public static boolean canHarvest(ItemStack tool, BlockState state) {
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }

    /** Squared distance from a point to the closest point of the block at {@code block}. */
    public static double distanceToBlockSqr(BlockPos block, double x, double y, double z) {
        double dx = Math.max(Math.max(block.getX() - x, 0.0D), x - (block.getX() + 1.0D));
        double dy = Math.max(Math.max(block.getY() - y, 0.0D), y - (block.getY() + 1.0D));
        double dz = Math.max(Math.max(block.getZ() - z, 0.0D), z - (block.getZ() + 1.0D));
        return dx * dx + dy * dy + dz * dz;
    }

    /** Whether a soldier could stand with its feet in the cell {@code feet}: two free cells above a solid floor. */
    public static boolean isStandable(Level level, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockState at = level.getBlockState(feet);
        BlockPos head = feet.above();
        BlockState headState = level.getBlockState(head);
        BlockPos below = feet.below();
        BlockState floor = level.getBlockState(below);
        return isFreeSpace(level, feet, at) && isFreeSpace(level, head, headState)
                && floor.isFaceSturdy(level, below, Direction.UP)
                && !(floor.getBlock() instanceof MagmaBlock) && !(floor.getBlock() instanceof CampfireBlock);
    }

    private static boolean isFreeSpace(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof BaseFireBlock) && !state.is(BlockTags.FIRE)
                && !state.is(Blocks.COBWEB) && !state.is(Blocks.SWEET_BERRY_BUSH) && !state.is(Blocks.POWDER_SNOW);
    }
}
