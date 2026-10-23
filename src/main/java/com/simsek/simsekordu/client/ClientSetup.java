package com.simsek.simsekordu.client;

import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.client.render.SimsekNpcRenderer;
import com.simsek.simsekordu.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = SimsekOrdu.MODID, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SIMSEK_NPC.get(), SimsekNpcRenderer::new);
    }
}
