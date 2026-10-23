package com.simsek.simsekordu.registry;

import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.menu.SimsekNpcMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, SimsekOrdu.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<SimsekNpcMenu>> SIMSEK_NPC =
            MENU_TYPES.register("simsek_npc", () -> IMenuTypeExtension.create(SimsekNpcMenu::new));
}
