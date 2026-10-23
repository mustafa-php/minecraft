package com.simsek.simsekordu.client;

import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.client.render.SimsekNpcRenderer;
import com.simsek.simsekordu.client.screen.SimsekNpcScreen;
import com.simsek.simsekordu.registry.ModEntities;
import com.simsek.simsekordu.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = SimsekOrdu.MODID, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SIMSEK_NPC.get(), SimsekNpcRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SIMSEK_NPC.get(), SimsekNpcScreen::new);
    }
}
