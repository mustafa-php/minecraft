package com.simsek.simsekordu.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.entity.SimsekNpc;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = SimsekOrdu.MODID)
public class SimsekCommands {
    private static final double TARGET_RANGE = 16.0D;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(SimsekOrdu.MODID)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("xp")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(SimsekCommands::giveXp))));
    }

    private static int giveXp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(context, "amount");

        SimsekNpc npc = findLookedAtNpc(player);
        if (npc == null) {
            source.sendFailure(Component.translatable("commands.simsekordu.xp.no_target"));
            return 0;
        }

        npc.addXp(amount);
        source.sendSuccess(() -> Component.translatable("commands.simsekordu.xp.success", amount, npc.getDisplayName()), true);
        return amount;
    }

    private static SimsekNpc findLookedAtNpc(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 reach = player.getViewVector(1.0F).scale(TARGET_RANGE);
        AABB searchBox = player.getBoundingBox().expandTowards(reach).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, start, start.add(reach), searchBox,
                entity -> entity instanceof SimsekNpc && entity.isAlive() && !entity.isSpectator(),
                TARGET_RANGE * TARGET_RANGE);
        return hit == null ? null : (SimsekNpc) hit.getEntity();
    }
}
