package com.simsek.simsekordu.item;

import com.simsek.simsekordu.SimsekOrdu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Left clicking a block with the wand selects corner 1 instead of breaking the block. */
@EventBusSubscriber(modid = SimsekOrdu.MODID)
public final class WandEvents {
    private WandEvents() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof CommanderWandItem)) {
            return;
        }
        event.setCanceled(true);
        // The server fires this before its own reach check, so verify the distance here.
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && !event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player
                && player.canInteractWithBlock(event.getPos(), 1.0D)) {
            if (player.isSecondaryUseActive()) {
                CommanderWandItem.clearSelection(stack, player);
            } else {
                CommanderWandItem.selectCorner(stack, player, 1, event.getPos());
            }
        }
    }
}
