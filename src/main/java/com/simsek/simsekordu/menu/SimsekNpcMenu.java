package com.simsek.simsekordu.menu;

import com.simsek.simsekordu.entity.SimsekNpc;
import com.simsek.simsekordu.registry.ModMenus;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SimsekNpcMenu extends AbstractContainerMenu {
    /** Maximum distance (in blocks) between the player and the NPC for the menu to stay open. */
    public static final double MAX_DISTANCE = 8.0D;

    public static final int PANEL_WIDTH = 200;
    /** Left edge of every slot column; centers the 9-slot rows in the panel. */
    public static final int SLOT_X = (PANEL_WIDTH - 9 * 18) / 2 + 1;
    public static final int NPC_SLOT_Y = 29;
    public static final int PLAYER_INV_Y = 84;
    public static final int HOTBAR_Y = 142;

    private static final int NPC_SLOTS = SimsekNpc.INVENTORY_SIZE;
    private static final int PLAYER_SLOTS_END = NPC_SLOTS + 36;

    @Nullable
    private final SimsekNpc npc;
    /** Client-side UI state: slots are only shown (and clickable) on the inventory tab. */
    private boolean inventoryTabActive;

    /** Client constructor: the NPC is looked up by the entity id written in {@code SimsekNpc#openMenu}. */
    public SimsekNpcMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory,
                playerInventory.player.level().getEntity(buf.readVarInt()) instanceof SimsekNpc found ? found : null);
    }

    public SimsekNpcMenu(int containerId, Inventory playerInventory, @Nullable SimsekNpc npc) {
        super(ModMenus.SIMSEK_NPC.get(), containerId);
        this.npc = npc;
        Container npcInventory = npc != null ? npc.getInventory() : new SimpleContainer(NPC_SLOTS);

        for (int i = 0; i < NPC_SLOTS; i++) {
            addSlot(new TabSlot(npcInventory, i, SLOT_X + i * 18, NPC_SLOT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new TabSlot(playerInventory, col + row * 9 + 9, SLOT_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new TabSlot(playerInventory, col, SLOT_X + col * 18, HOTBAR_Y));
        }

        if (npc != null && !playerInventory.player.level().isClientSide) {
            npc.addMenuUser();
        }
    }

    @Nullable
    public SimsekNpc getNpc() {
        return this.npc;
    }

    public boolean isInventoryTabActive() {
        return this.inventoryTabActive;
    }

    public void setInventoryTabActive(boolean active) {
        this.inventoryTabActive = active;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.npc != null && !player.level().isClientSide) {
            this.npc.removeMenuUser();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.npc == null || !this.npc.isAlive() || this.npc.distanceToSqr(player) > MAX_DISTANCE * MAX_DISTANCE) {
            return false;
        }
        return this.npc.isOwnedBy(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < NPC_SLOTS) {
                if (!moveItemStackTo(stack, NPC_SLOTS, PLAYER_SLOTS_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, NPC_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    private class TabSlot extends Slot {
        TabSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return SimsekNpcMenu.this.inventoryTabActive;
        }
    }
}
