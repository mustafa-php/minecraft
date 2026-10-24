package com.simsek.simsekordu.entity.goal;

import com.simsek.simsekordu.entity.SimsekNpc;
import com.simsek.simsekordu.item.CommanderWandItem;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * A trip to the soldier's storage block that empties its inventory into it, shared by every job (miner, hunter).
 * Items for which {@code keep} is true (the job's tools) stay in the inventory. When the storage is missing, full
 * or out of reach the soldier waits, tells its owner once, and tries again every few seconds.
 *
 * <p>Usage: call {@link #begin} when the inventory is full, then {@link #tick} every tick until it returns
 * {@link State#DONE}.
 */
public final class StorageRun {
    public enum State {
        WALKING,
        FULL,
        NO_STORAGE,
        UNREACHABLE,
        DONE
    }

    private enum Stage {
        WALK,
        WAIT
    }

    private static final double REACH = 3.5D;
    private static final int RETRY_TICKS = 100;

    private static final String NOTICE_FULL = "storage_full";
    private static final String NOTICE_NONE = "no_storage";
    private static final String NOTICE_UNREACHABLE = "storage_unreachable";

    private final SimsekNpc npc;
    private final Predicate<ItemStack> keep;
    private final BlockApproach approach;
    private Stage stage = Stage.WALK;
    private State waitState = State.FULL;
    private long nextTry;

    public StorageRun(SimsekNpc npc, Predicate<ItemStack> keep) {
        this.npc = npc;
        this.keep = keep;
        this.approach = new BlockApproach(npc);
    }

    public void begin(ServerLevel level) {
        this.approach.reset();
        this.npc.getNavigation().stop();
        this.stage = Stage.WALK;
        this.nextTry = level.getGameTime() + RETRY_TICKS;
    }

    public State tick(ServerLevel level) {
        return this.stage == Stage.WALK ? walk(level) : waitForStorage(level);
    }

    private State walk(ServerLevel level) {
        BlockPos storage = this.npc.getStoragePos();
        IItemHandler handler = handlerAt(level, storage);
        if (storage == null || handler == null) {
            return enterWait(level, State.NO_STORAGE);
        }
        this.npc.getLookControl().setLookAt(storage.getX() + 0.5D, storage.getY() + 0.5D, storage.getZ() + 0.5D);
        switch (this.approach.approach(level, storage, REACH)) {
            case MOVING -> {
                return State.WALKING;
            }
            case FAILED -> {
                return enterWait(level, State.UNREACHABLE);
            }
            case ARRIVED -> {
            }
        }

        depositAll(handler);
        this.npc.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, storage, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.1F);
        if (this.npc.isInventoryFull()) {
            return enterWait(level, State.FULL);
        }
        clearNotices();
        return State.DONE;
    }

    private State enterWait(ServerLevel level, State state) {
        this.stage = Stage.WAIT;
        this.waitState = state;
        this.nextTry = level.getGameTime() + RETRY_TICKS;
        this.npc.getNavigation().stop();
        switch (state) {
            case FULL -> this.npc.noticeOwnerOnce(NOTICE_FULL, Component.translatable("message.simsekordu.storage_full", this.npc.getNpcName()));
            case NO_STORAGE -> this.npc.noticeOwnerOnce(NOTICE_NONE, Component.translatable("message.simsekordu.inventory_full_no_storage", this.npc.getNpcName()));
            case UNREACHABLE -> this.npc.noticeOwnerOnce(NOTICE_UNREACHABLE, Component.translatable("message.simsekordu.storage_unreachable", this.npc.getNpcName()));
            default -> {
            }
        }
        return state;
    }

    private State waitForStorage(ServerLevel level) {
        long now = level.getGameTime();
        if (now < this.nextTry) {
            return this.waitState;
        }
        this.nextTry = now + RETRY_TICKS;
        if (!this.npc.isInventoryFull()) {
            // The owner emptied the inventory by hand.
            clearNotices();
            return State.DONE;
        }
        BlockPos storage = this.npc.getStoragePos();
        IItemHandler handler = handlerAt(level, storage);
        if (handler != null && canDepositAny(handler)) {
            this.approach.reset();
            this.stage = Stage.WALK;
            return State.WALKING;
        }
        return this.waitState;
    }

    private void clearNotices() {
        this.npc.clearNotice(NOTICE_FULL);
        this.npc.clearNotice(NOTICE_NONE);
        this.npc.clearNotice(NOTICE_UNREACHABLE);
    }

    @Nullable
    private static IItemHandler handlerAt(ServerLevel level, @Nullable BlockPos storage) {
        return storage != null && level.isLoaded(storage) ? CommanderWandItem.findItemHandler(level, storage) : null;
    }

    /** Moves everything except the kept tools into the storage; what does not fit stays in the inventory. */
    private void depositAll(IItemHandler handler) {
        Container inventory = this.npc.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || this.keep.test(stack)) {
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
            if (!stack.isEmpty() && !this.keep.test(stack)
                    && ItemHandlerHelper.insertItemStacked(handler, stack.copy(), true).getCount() < stack.getCount()) {
                return true;
            }
        }
        return false;
    }
}
