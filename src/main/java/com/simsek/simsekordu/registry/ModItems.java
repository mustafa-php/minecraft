package com.simsek.simsekordu.registry;

import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.item.CommanderWandItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SimsekOrdu.MODID);

    public static final DeferredItem<DeferredSpawnEggItem> SIMSEK_NPC_SPAWN_EGG =
            ITEMS.register("simsek_npc_spawn_egg", () -> new DeferredSpawnEggItem(
                    ModEntities.SIMSEK_NPC, 0x556B2F, 0x7B4A2B, new Item.Properties()));

    public static final DeferredItem<CommanderWandItem> COMMANDER_WAND =
            ITEMS.register("commander_wand", () -> new CommanderWandItem(new Item.Properties().stacksTo(1)));
}
