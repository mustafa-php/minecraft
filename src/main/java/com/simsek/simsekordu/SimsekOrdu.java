package com.simsek.simsekordu;

import com.simsek.simsekordu.entity.SimsekNpc;
import com.simsek.simsekordu.registry.ModEntities;
import com.simsek.simsekordu.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(SimsekOrdu.MODID)
public class SimsekOrdu {
    public static final String MODID = "simsekordu";

    public SimsekOrdu(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::addCreativeTabContents);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SIMSEK_NPC.get(), SimsekNpc.createAttributes().build());
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.SIMSEK_NPC_SPAWN_EGG);
        }
    }
}
