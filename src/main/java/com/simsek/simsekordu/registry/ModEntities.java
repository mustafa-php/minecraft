package com.simsek.simsekordu.registry;

import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.entity.SimsekNpc;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SimsekOrdu.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SimsekNpc>> SIMSEK_NPC =
            ENTITY_TYPES.register("simsek_npc", () -> EntityType.Builder
                    .of(SimsekNpc::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("simsek_npc"));
}
