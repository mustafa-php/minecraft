package com.simsek.simsekordu.registry;

import com.simsek.simsekordu.SimsekOrdu;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class ModTags {
    /** Animals a hunter in animal mode may kill (data/simsekordu/tags/entity_type/huntable_animals.json). */
    public static final TagKey<EntityType<?>> HUNTABLE_ANIMALS =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(SimsekOrdu.MODID, "huntable_animals"));

    private ModTags() {
    }
}
