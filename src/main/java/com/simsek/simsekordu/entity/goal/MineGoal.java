package com.simsek.simsekordu.entity.goal;

import com.simsek.simsekordu.data.Job;
import com.simsek.simsekordu.data.MiningRegion;
import com.simsek.simsekordu.data.MiningStatus;
import com.simsek.simsekordu.data.MiningXp;
import com.simsek.simsekordu.entity.SimsekNpc;
import com.simsek.simsekordu.item.CommanderWandItem;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Digs the NPC's mining region from the top layer down, one block at a time, with the best pickaxe in its inventory.
 * Drops go to the inventory; when it is full the NPC walks to the assigned storage block and empties itself into it.
 * Where no walkable route exists (for instance out of a deep pit) it teleports next to its destination instead.
 *
 * <p>Nothing here scans the whole region per tick: the block being mined is cached, a layer is only scanned when a new
 * target is needed, and the full region is only counted when the task starts, a layer runs out or the task ends.
 */
public class MineGoal extends Goal {
    /** How far the NPC may be from a block (eye to closest point) and still break it. */
    private static final double MINE_REACH = 4.5D;
    private static final double STORAGE_REACH = 3.5D;
    /** Stand cells are chosen this much closer than the reach, as the NPC rarely stops exactly in the cell centre. */
    private static final double STAND_MARGIN = 0.7D;
    private static final int APPROACH_TIMEOUT = 300;
    private static final int PATH_RETRY_TICKS = 10;
    private static final int MAX_PATH_FAILURES = 3;
    private static final int UNREACHABLE_RETRY_TICKS = 600;
    private static final int IDLE_SCAN_TICKS = 60;
    private static final int STORAGE_RETRY_TICKS = 100;

    private static final String NOTICE_NO_PICKAXE = "no_pickaxe";
    private static final String NOTICE_STORAGE_FULL = "storage_full";
    private static final String NOTICE_NO_STORAGE = "no_storage";
    private static final String NOTICE_UNREACHABLE = "unreachable";

    private enum Phase {
        MINING,
        TO_STORAGE,
        WAIT_STORAGE
    }

    private enum Approach {
        ARRIVED,
        MOVING,
        FAILED
    }

    private final SimsekNpc npc;
    private Phase phase = Phase.MINING;

    // mining state
    @Nullable
    private BlockPos target;
    private float progress;
    private int lastStage = -1;
    private int layerY;
    private long nextScanTick;
    private final Map<BlockPos, Long> unreachable = new HashMap<>();

    // storage state
    private long nextStorageTry;

    // movement state
    private int approachTicks;
    private long nextPathTick;
    private int pathFailures;
    private final Set<BlockPos> failedCells = new HashSet<>();

    public MineGoal(SimsekNpc npc) {
        this.npc = npc;
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
            this.npc.setMiningStatus(MiningStatus.NEEDS_PICKAXE);
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
        return this.phase == Phase.WAIT_STORAGE || ensureTool();
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
        resetApproach();
        if (region != null && this.npc.level() instanceof ServerLevel level) {
            this.layerY = region.max().getY();
            this.npc.setMiningRemaining(MiningHelper.countMineable(level, region, this.npc.getStoragePos()).mineable());
        }
        this.npc.setMiningStatus(MiningStatus.MINING);
    }

    @Override
    public void stop() {
        clearCrack();
        this.target = null;
        this.npc.getNavigation().stop();
        this.npc.clearMirroredHand();
        MiningStatus status = this.npc.getMiningStatus();
        if (status == MiningStatus.MINING || status == MiningStatus.TO_STORAGE) {
            this.npc.setMiningStatus(MiningStatus.IDLE);
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
            case TO_STORAGE -> tickToStorage(level);
            case WAIT_STORAGE -> tickWaitStorage(level);
        }
    }

    // ---- mining ---------------------------------------------------------------------------------

    private void tickMining(ServerLevel level, MiningRegion region) {
        if (!ensureTool()) {
            return;
        }
        if (this.npc.isInventoryFull()) {
            beginStorageTrip(level);
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
            resetApproach();
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
        switch (approach(level, pos, MINE_REACH)) {
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
            this.npc.setMiningStatus(MiningStatus.UNREACHABLE);
            this.npc.noticeOwnerOnce(NOTICE_UNREACHABLE, Component.translatable("message.simsekordu.task_unreachable", this.npc.getNpcName()));
        }
    }

    // ---- storage --------------------------------------------------------------------------------

    private void beginStorageTrip(ServerLevel level) {
        clearCrack();
        this.target = null;
        this.npc.getNavigation().stop();
        resetApproach();
        this.nextStorageTry = level.getGameTime() + STORAGE_RETRY_TICKS;
        if (this.npc.getStoragePos() == null) {
            this.phase = Phase.WAIT_STORAGE;
            this.npc.setMiningStatus(MiningStatus.NO_STORAGE);
            this.npc.noticeOwnerOnce(NOTICE_NO_STORAGE, Component.translatable("message.simsekordu.inventory_full_no_storage", this.npc.getNpcName()));
            return;
        }
        this.phase = Phase.TO_STORAGE;
        this.npc.setMiningStatus(MiningStatus.TO_STORAGE);
    }

    private void tickToStorage(ServerLevel level) {
        BlockPos storage = this.npc.getStoragePos();
        IItemHandler handler = storage != null && level.isLoaded(storage) ? CommanderWandItem.findItemHandler(level, storage) : null;
        if (storage == null || handler == null) {
            waitForStorage(level, MiningStatus.NO_STORAGE, NOTICE_NO_STORAGE, "message.simsekordu.inventory_full_no_storage");
            return;
        }
        this.npc.getLookControl().setLookAt(storage.getX() + 0.5D, storage.getY() + 0.5D, storage.getZ() + 0.5D);
        switch (approach(level, storage, STORAGE_REACH)) {
            case MOVING -> {
                return;
            }
            case FAILED -> {
                waitForStorage(level, MiningStatus.UNREACHABLE, NOTICE_UNREACHABLE, "message.simsekordu.storage_unreachable");
                return;
            }
            case ARRIVED -> {
            }
        }

        depositAll(handler);
        this.npc.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, storage, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.1F);
        if (this.npc.isInventoryFull()) {
            waitForStorage(level, MiningStatus.STORAGE_FULL, NOTICE_STORAGE_FULL, "message.simsekordu.storage_full");
        } else {
            this.npc.clearNotice(NOTICE_STORAGE_FULL);
            this.npc.clearNotice(NOTICE_NO_STORAGE);
            this.phase = Phase.MINING;
            this.npc.setMiningStatus(MiningStatus.MINING);
        }
    }

    private void waitForStorage(ServerLevel level, MiningStatus status, String noticeId, String messageKey) {
        this.phase = Phase.WAIT_STORAGE;
        this.nextStorageTry = level.getGameTime() + STORAGE_RETRY_TICKS;
        this.npc.getNavigation().stop();
        this.npc.setMiningStatus(status);
        this.npc.noticeOwnerOnce(noticeId, Component.translatable(messageKey, this.npc.getNpcName()));
    }

    private void tickWaitStorage(ServerLevel level) {
        long now = level.getGameTime();
        if (now < this.nextStorageTry) {
            return;
        }
        this.nextStorageTry = now + STORAGE_RETRY_TICKS;
        if (!this.npc.isInventoryFull()) {
            // The owner emptied the inventory by hand.
            this.phase = Phase.MINING;
            this.npc.setMiningStatus(MiningStatus.MINING);
            return;
        }
        BlockPos storage = this.npc.getStoragePos();
        IItemHandler handler = storage != null && level.isLoaded(storage) ? CommanderWandItem.findItemHandler(level, storage) : null;
        if (handler != null && canDepositAny(handler)) {
            resetApproach();
            this.phase = Phase.TO_STORAGE;
            this.npc.setMiningStatus(MiningStatus.TO_STORAGE);
        }
    }

    /** Moves everything except pickaxes into the storage; what does not fit stays in the inventory. */
    private void depositAll(IItemHandler handler) {
        Container inventory = this.npc.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || MiningHelper.isPickaxe(stack)) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
            if (rest.getCount() != stack.getCount()) {
                inventory.setItem(i, rest);
            }
        }
    }

    private boolean canDepositAny(IItemHandler handler) {
        Container inventory = this.npc.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && !MiningHelper.isPickaxe(stack)
                    && ItemHandlerHelper.insertItemStacked(handler, stack.copy(), true).getCount() < stack.getCount()) {
                return true;
            }
        }
        return false;
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

    // ---- movement -------------------------------------------------------------------------------

    private boolean inReach(BlockPos block, double reach) {
        Vec3 eye = this.npc.getEyePosition();
        return MiningHelper.distanceToBlockSqr(block, eye.x, eye.y, eye.z) <= reach * reach;
    }

    /**
     * Gets the NPC within {@code reach} of {@code block}: walks to a free cell near it, and if no walkable path exists
     * (for instance out of a deep pit) falls back to teleporting to such a cell.
     */
    private Approach approach(ServerLevel level, BlockPos block, double reach) {
        PathNavigation navigation = this.npc.getNavigation();
        if (inReach(block, reach)) {
            navigation.stop();
            resetApproach();
            return Approach.ARRIVED;
        }
        if (++this.approachTicks > APPROACH_TIMEOUT) {
            resetApproach();
            return Approach.FAILED;
        }
        long now = level.getGameTime();
        if (navigation.isInProgress() || now < this.nextPathTick) {
            return Approach.MOVING;
        }
        this.nextPathTick = now + PATH_RETRY_TICKS;
        double standDistance = reach - STAND_MARGIN;
        BlockPos cell = findStandCell(level, block, standDistance, false);
        if (cell != null) {
            Path path = navigation.createPath(cell, 0);
            if (path != null && path.canReach() && navigation.moveTo(path, 1.0D)) {
                return Approach.MOVING;
            }
            this.failedCells.add(cell);
            if (++this.pathFailures < MAX_PATH_FAILURES) {
                return Approach.MOVING;
            }
        }
        // No walkable route (e.g. out of a deep pit, or every cell near the block was tried): teleport next to it instead.
        BlockPos fallback = cell != null ? cell : findStandCell(level, block, standDistance, true);
        if (fallback != null && teleportTo(level, fallback)) {
            this.pathFailures = 0;
            this.failedCells.clear();
            return Approach.MOVING;
        }
        resetApproach();
        return Approach.FAILED;
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

    private void resetApproach() {
        this.approachTicks = 0;
        this.nextPathTick = 0L;
        this.pathFailures = 0;
        this.failedCells.clear();
    }

    private void clearCrack() {
        if (this.target != null && this.lastStage >= 0 && this.npc.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(this.npc.getId(), this.target, -1);
        }
        this.progress = 0.0F;
        this.lastStage = -1;
    }
}
