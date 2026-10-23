package com.simsek.simsekordu.registry;

import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.item.WandSelection;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SimsekOrdu.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandSelection>> WAND_SELECTION =
            DATA_COMPONENTS.registerComponentType("wand_selection", builder -> builder
                    .persistent(WandSelection.CODEC)
                    .networkSynchronized(WandSelection.STREAM_CODEC));
}
