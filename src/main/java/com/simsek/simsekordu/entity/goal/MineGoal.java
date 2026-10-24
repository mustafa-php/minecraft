package com.simsek.simsekordu.entity.goal;

import com.simsek.simsekordu.data.Job;
import com.simsek.simsekordu.data.MiningRegion;
import com.simsek.simsekordu.data.MiningXp;
import com.simsek.simsekordu.data.TaskStatus;
import com.simsek.simsekordu.entity.SimsekNpc;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Digs the NPC's mining region from the top layer down, one block at a time, with the best pickaxe in its inventory.
 * Drops go to the inventory; when it is full the NPC walks to the assigned storage block and empties itself into it
 * (see {@link StorageRun}); where no walkable route exists it teleports next to its destination (see
 * {@link BlockApproach}).
 *
 * <p>Nothing here scans the whole region per tick: the block being mined is cached, a layer is only scanned when a new
 * target is needed, and the full region is only counted when the task starts, a layer runs out or the task ends.
 */
public class MineGoal extends Goal {
    /** How far the NPC may be from a block (eye to closest point) and still break it. */
    private static final double MINE_REACH = 4.5D;
    private static final int UNREACHABLE_RETRY_TICKS = 600;
    private static final int IDLE_SCAN_TICKS = 60;

    private static final String NOTICE_NO_PICKAXE = "no_pickaxe";
    private static final String NOTICE_UNREACHABLE = "unreachable";

    private enum Phase {
        MINING,
        STORAGE
    }

    private final SimsekNpc npc;
    private final BlockApproach approach;
    private final StorageRun storageRun;
    private Phase phase = Phase.MINING;

    @Nullable
    private BlockPos target;
    private float progress;
    private int lastStage = -1;
    private int layerY;
    private long nextScanTick;
    private final Map<BlockPos, Long> unreachable = new HashMap<>();

    public MineGoal(SimsekNpc npc) {
        this.npc = npc;
        this.approach = new BlockApproach(npc);
        this.storageRun = new StorageRun(npc, MiningHelper::isPickaxe);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (this.npc.level().isClientSide || this.npc.getJob() != Job.MINER
                || this.npc.getMiningRegion() == null || this.npc.isHeldInPlace()) {
            return false;
        }
        if (findBestPickaxeSlot() < 0) {
            this.npc.setTaskStatus(TaskStatus.NEEDS_PICKAXE);
            this.npc.noticeOwnerOnce(NOTICE_NO_PICKAXE, Component.translatable("message.simsekordu.needs_pickaxe", this.npc.getNpcName()));
            return false;
        }
        this.npc.clearNotice(NOTICE_NO_PICKAXE);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.npc.getJob() != Job.MINER || this.npc.getMiningRegion() == null || this.npc.isHeldInPlace()) {
            return false;
        }
        return this.phase == Phase.STORAGE || ensureTool();
    }

    @Override
    public void start() {
        MiningRegion region = this.npc.getMiningRegion();
        this.phase = Phase.MINING;
        this.target = null;
        this.progress = 0.0F;
        this.lastStage = -1;
        this.nextScanTick = 0L;
        this.unreachable.clear();
        this.approach.reset();
        if (region != null && this.npc.level() instanceof ServerLevel level) {
            this.layerY = region.max().getY();
            this.npc.setMiningRemaining(MiningHelper.countMineable(level, region, this.npc.getStoragePos()).mineable());
        }
        this.npc.setTaskStatus(TaskStatus.MINING);
    }

    @Override
    public void stop() {
        clearCrack();
        this.target = null;
        this.npc.getNavigation().stop();
        this.npc.clearMirroredHand();
        TaskStatus status = this.npc.getTaskStatus();
        if (status == TaskStatus.MINING || status == TaskStatus.TO_STORAGE) {
            this.npc.setTaskStatus(TaskStatus.IDLE);
        }
    }

    @Override
    public void tick() {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return;
        }
        MiningRegion region = this.npc.getMiningRegion();
        if (region == null) {
            return;
        }
        switch (this.phase) {
            case MINING -> tickMining(level, region);
            case STORAGE -> tickStorage(level);
        }
    }

    // ---- mining ---------------------------------------------------------------------------------

    private void tickMining(ServerLevel level, MiningRegion region) {
        if (!ensureTool()) {
            return;
        }
        if (this.npc.isInventoryFull()) {
            clearCrack();
            this.target = null;
            this.storageRun.begin(level);
            this.phase = Phase.STORAGE;
            return;
        }
        long now = level.getGameTime();
        if (this.target == null) {
            if (now < this.nextScanTick) {
                return;
            }
            this.target = selectTarget(level, region, now);
            this.progress = 0.0F;
            this.lastStage = -1;
            if (this.target == null) {
                onNoTargets(level, region, now);
                return;
            }
            this.approach.reset();
            this.npc.clearNotice(NOTICE_UNREACHABLE);
        }

        BlockPos pos = this.target;
        BlockState state = level.getBlockState(pos);
        if (!MiningHelper.isMineable(level, pos, state, this.npc.getStoragePos())) {
            // Someone else removed (or replaced) it.
            clearCrack();
            this.target = null;
            return;
        }
        this.npc.getLookControl().setLookAt(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        switch (this.approach.approach(level, pos, MINE_REACH)) {
            case MOVING -> {
                return;
            }
            case FAILED -> {
                this.unreachable.put(pos, now + UNREACHABLE_RETRY_TICKS);
                clearCrack();
                this.target = null;
                return;
            }
            case ARRIVED -> {
            }
        }

        this.progress += MiningHelper.breakProgressPerTick(this.npc, level, pos, state);
        int stage = Math.min(9, (int) (this.progress * 10.0F));
        if (stage != this.lastStage) {
            level.destroyBlockProgress(this.npc.getId(), pos, stage);
            this.lastStage = stage;
        }
        if (this.npc.tickCount % 4 == 0) {
            SoundType sound = state.getSoundType(level, pos, this.npc);
            level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 8.0F, sound.getPitch() * 0.5F);
        }
        if (this.npc.tickCount % 6 == 0) {
            this.npc.swing(InteractionHand.MAIN_HAND);
        }
        if (this.progress >= 1.0F) {
            breakTarget(level, pos, state);
        }
    }

    private void tickStorage(ServerLevel level) {
        switch (this.storageRun.tick(level)) {
            case WALKING -> this.npc.setTaskStatus(TaskStatus.TO_STORAGE);
            case FULL -> this.npc.setTaskStatus(TaskStatus.STORAGE_FULL);
            case NO_STORAGE -> this.npc.setTaskStatus(TaskStatus.NO_STORAGE);
            case UNREACHABLE -> this.npc.setTaskStatus(TaskStatus.UNREACHABLE);
            case DONE -> {
                this.phase = Phase.MINING;
                this.npc.setTaskStatus(TaskStatus.MINING);
            }
        }
    }

    private void breakTarget(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack tool = this.npc.getMainHandItem();
        // As for a player: without the right tool the block still breaks (slowly) but drops nothing.
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = MiningHelper.canHarvest(tool, state)
                ? Block.getDrops(state, level, pos, blockEntity, this.npc, tool) : List.of();
        float hardness = state.getDestroySpeed(level, pos);
        clearCrack();
        this.target = null;
        if (!level.destroyBlock(pos, false, this.npc)) {
            return;
        }
        for (ItemStack drop : drops) {
            this.npc.storeOrSpill(drop);
        }
        this.npc.addXp(MiningXp.xpFor(state));
        this.npc.setMiningRemaining(this.npc.getMiningRemaining() - 1);
        damageTool(tool, hardness);
    }

    private void damageTool(ItemStack tool, float hardness) {
        if (hardness != 0.0F) {
            Tool toolData = tool.get(DataComponents.TOOL);
            int damage = toolData != null ? toolData.damagePerBlock() : 1;
            if (damage > 0) {
                tool.hurtAndBreak(damage, this.npc, EquipmentSlot.MAINHAND);
            }
        }
        if (tool.isEmpty()) {
            Container inventory = this.npc.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (inventory.getItem(i) == tool) {
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
            this.npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            this.npc.messageOwner(Component.translatable("message.simsekordu.pickaxe_broke", this.npc.getNpcName()));
            // The "needs a pickaxe" notice would only repeat what was just said.
            if (findBestPickaxeSlot() < 0) {
                this.npc.markNoticeSent(NOTICE_NO_PICKAXE);
            }
        }
    }

    /** Nearest mineable block of the highest layer that still has one, or null when the region is exhausted. */
    @Nullable
    private BlockPos selectTarget(ServerLevel level, MiningRegion region, long now) {
        this.unreachable.values().removeIf(expiry -> expiry <= now);
        BlockPos storage = this.npc.getStoragePos();
        Vec3 from = this.npc.position();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (; this.layerY >= region.min().getY(); this.layerY--) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int x = region.min().getX(); x <= region.max().getX(); x++) {
                for (int z = region.min().getZ(); z <= region.max().getZ(); z++) {
                    cursor.set(x, this.layerY, z);
                    if (!level.isLoaded(cursor) || this.unreachable.containsKey(cursor)) {
                        continue;
                    }
                    if (!MiningHelper.isMineable(level, cursor, level.getBlockState(cursor), storage)) {
                        continue;
                    }
                    double distance = cursor.distToCenterSqr(from.x, from.y, from.z);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private void onNoTargets(ServerLevel level, MiningRegion region, long now) {
        MiningHelper.ScanResult scan = MiningHelper.countMineable(level, region, this.npc.getStoragePos());
        this.npc.setMiningRemaining(scan.mineable());
        if (scan.mineable() == 0 && scan.allLoaded()) {
            this.npc.completeMiningTask();
            return;
        }
        // Blocks are left but none can be picked right now (unreachable, or chunks not loaded): look again later.
        this.layerY = region.max().getY();
        this.nextScanTick = now + IDLE_SCAN_TICKS;
        if (!this.unreachable.isEmpty()) {
            this.npc.setTaskStatus(TaskStatus.UNREACHABLE);
            this.npc.noticeOwnerOnce(NOTICE_UNREACHABLE, Component.translatable("message.simsekordu.task_unreachable", this.npc.getNpcName()));
        }
    }

    // ---- tool -----------------------------------------------------------------------------------

    /** Makes sure the main hand shows (the same object as) a pickaxe from the inventory. False if there is none. */
    private boolean ensureTool() {
        if (this.npc.isMainHandMirrored() && MiningHelper.isPickaxe(this.npc.getMainHandItem())) {
            return true;
        }
        int slot = findBestPickaxeSlot();
        if (slot < 0) {
            this.npc.clearMirroredHand();
            return false;
        }
        this.npc.setItemSlot(EquipmentSlot.MAINHAND, this.npc.getInventory().getItem(slot));
        return true;
    }

    private int findBestPickaxeSlot() {
        Container inventory = this.npc.getInventory();
        BlockState stone = Blocks.STONE.defaultBlockState();
        int best = -1;
        float bestSpeed = -1.0F;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (MiningHelper.isPickaxe(stack) && stack.getDestroySpeed(stone) > bestSpeed) {
                bestSpeed = stack.getDestroySpeed(stone);
                best = i;
            }
        }
        return best;
    }

    private void clearCrack() {
        if (this.target != null && this.lastStage >= 0 && this.npc.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(this.npc.getId(), this.target, -1);
        }
        this.progress = 0.0F;
        this.lastStage = -1;
    }
}
