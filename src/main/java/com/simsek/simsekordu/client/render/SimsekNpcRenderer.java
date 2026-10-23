package com.simsek.simsekordu.client.render;

import com.simsek.simsekordu.entity.SimsekNpc;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.resources.ResourceLocation;

public class SimsekNpcRenderer extends MobRenderer<SimsekNpc, VillagerModel<SimsekNpc>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");

    public SimsekNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        // Shows the tool in the main hand (e.g. a miner's pickaxe) in front of the crossed arms.
        this.addLayer(new CrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(SimsekNpc entity) {
        return TEXTURE;
    }
}
