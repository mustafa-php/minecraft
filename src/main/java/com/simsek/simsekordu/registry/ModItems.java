package com.simsek.simsekordu.registry;

import com.simsek.simsekordu.SimsekOrdu;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SimsekOrdu.MODID);

    public static final DeferredItem<DeferredSpawnEggItem> SIMSEK_NPC_SPAWN_EGG =
            ITEMS.register("simsek_npc_spawn_egg", () -> new DeferredSpawnEggItem(
                    ModEntities.SIMSEK_NPC, 0x4B5320, 0xC9A227, new Item.Properties()));
}
