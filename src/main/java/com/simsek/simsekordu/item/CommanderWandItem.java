package com.simsek.simsekordu.item;

import com.simsek.simsekordu.data.Job;
import com.simsek.simsekordu.data.MiningRegion;
import com.simsek.simsekordu.entity.SimsekNpc;
import com.simsek.simsekordu.entity.goal.HuntHelper;
import com.simsek.simsekordu.registry.ModDataComponents;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Selects a mining region (left click = corner 1, right click = corner 2), a storage chest
 * (shift + right click a block) and hands both to a soldier (shift + right click the soldier).
 */
public class CommanderWandItem extends Item {
    public CommanderWandItem(Properties properties) {
        super(properties);
    }

    public static WandSelection getSelection(ItemStack stack, ResourceKey<Level> dimension) {
        WandSelection selection = stack.get(ModDataComponents.WAND_SELECTION.get());
        return selection == null ? WandSelection.empty(dimension) : selection.forDimension(dimension);
    }

    /** Never break blocks with the wand: left click is used to select a corner. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /** Shift + right click into the air (no block in reach) makes the player's position the hunting centre. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            selectHuntCenter(stack, serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Runs before the clicked block's own interaction, so corners can also be set on chests, doors and the like. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isSecondaryUseActive()) {
            selectStorage(stack, player, context.getClickedPos());
        } else {
            selectCorner(stack, player, 2, context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof SimsekNpc npc) || !npc.isOwnedBy(player)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (!player.isSecondaryUseActive()) {
            serverPlayer.displayClientMessage(Component.translatable("message.simsekordu.wand.assign_hint"), true);
            return InteractionResult.SUCCESS;
        }
        assignTask(stack, serverPlayer, npc);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (isSelected && level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player
                && level.getGameTime() % 20 == 0) {
            WandSelection selection = getSelection(stack, serverLevel.dimension());
            if (!selection.isEmpty()) {
                WandVisuals.showSelection(player, selection);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WandSelection selection = stack.get(ModDataComponents.WAND_SELECTION.get());
        tooltip.add(selectionLine("item.simsekordu.commander_wand.corner1", selection == null ? Optional.empty() : selection.corner1()));
        tooltip.add(selectionLine("item.simsekordu.commander_wand.corner2", selection == null ? Optional.empty() : selection.corner2()));
        tooltip.add(selectionLine("item.simsekordu.commander_wand.storage", selection == null ? Optional.empty() : selection.storage()));
        tooltip.add(selectionLine("item.simsekordu.commander_wand.hunt_center", selection == null ? Optional.empty() : selection.huntCenter()));
        tooltip.add(Component.translatable("item.simsekordu.commander_wand.help.corners").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.simsekordu.commander_wand.help.storage").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.simsekordu.commander_wand.help.hunt").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.simsekordu.commander_wand.help.assign").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component selectionLine(String key, Optional<BlockPos> pos) {
        Component value = pos.map(p -> (Component) Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ()))
                .orElse(Component.translatable("item.simsekordu.commander_wand.unset"));
        return Component.translatable(key, value).withStyle(ChatFormatting.GRAY);
    }

    /** Sets corner {@code corner} (1 or 2) to {@code pos}. Also used by the left click handler. */
    public static void selectCorner(ItemStack stack, ServerPlayer player, int corner, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        WandSelection selection = getSelection(stack, level.dimension());
        Optional<BlockPos> current = corner == 1 ? selection.corner1() : selection.corner2();
        if (current.isPresent() && current.get().equals(pos)) {
            // Clicks repeat while a button is held down; nothing changes, so stay quiet.
            return;
        }
        Optional<BlockPos> other = corner == 1 ? selection.corner2() : selection.corner1();
        if (other.isPresent()) {
            MiningRegion candidate = MiningRegion.of(pos, other.get());
            if (!candidate.fitsLimit()) {
                player.displayClientMessage(Component.translatable("message.simsekordu.wand.too_large",
                        candidate.sizeX(), candidate.sizeY(), candidate.sizeZ(), MiningRegion.MAX_SIZE)
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        WandSelection updated = corner == 1 ? selection.withCorner1(pos) : selection.withCorner2(pos);
        stack.set(ModDataComponents.WAND_SELECTION.get(), updated);
        Optional<MiningRegion> region = updated.region();
        if (region.isPresent()) {
            player.displayClientMessage(Component.translatable("message.simsekordu.wand.region_set",
                    corner, pos.getX(), pos.getY(), pos.getZ(), region.get().sizeX(), region.get().sizeY(), region.get().sizeZ())
                    .withStyle(ChatFormatting.GREEN), true);
            WandVisuals.showRegion(player, region.get());
        } else {
            player.displayClientMessage(Component.translatable("message.simsekordu.wand.corner_set",
                    corner, pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GREEN), true);
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, corner == 1 ? 1.2F : 1.6F);
        WandVisuals.showCorner(player, corner, pos);
    }

    /** Makes the player's block position the hunting centre. */
    public static void selectHuntCenter(ItemStack stack, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        WandSelection selection = getSelection(stack, level.dimension());
        if (selection.huntCenter().isPresent() && selection.huntCenter().get().equals(pos)) {
            // Right click repeats while the button is held; nothing changed, so stay quiet.
            return;
        }
        stack.set(ModDataComponents.WAND_SELECTION.get(), selection.withHuntCenter(pos));
        player.displayClientMessage(Component.translatable("message.simsekordu.wand.hunt_center_set",
                pos.getX(), pos.getY(), pos.getZ(), HuntHelper.RADIUS).withStyle(ChatFormatting.GOLD), true);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
        WandVisuals.showHuntArea(player, pos);
    }

    public static void selectStorage(ItemStack stack, ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (findItemHandler(level, pos) == null) {
            player.displayClientMessage(Component.translatable("message.simsekordu.wand.not_storage").withStyle(ChatFormatting.RED), true);
            return;
        }
        WandSelection selection = getSelection(stack, level.dimension());
        if (selection.storage().isPresent() && selection.storage().get().equals(pos)) {
            return;
        }
        stack.set(ModDataComponents.WAND_SELECTION.get(), selection.withStorage(pos));
        player.displayClientMessage(Component.translatable("message.simsekordu.wand.storage_set",
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA), true);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
        WandVisuals.showStorage(player, pos);
    }

    /** Clears the selection (shift + left click). Does nothing if there is nothing selected. */
    public static void clearSelection(ItemStack stack, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (getSelection(stack, level.dimension()).isEmpty()) {
            return;
        }
        stack.set(ModDataComponents.WAND_SELECTION.get(), WandSelection.empty(level.dimension()));
        player.displayClientMessage(Component.translatable("message.simsekordu.wand.cleared"), true);
    }

    @Nullable
    public static IItemHandler findItemHandler(ServerLevel level, BlockPos pos) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        return handler != null ? handler : level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
    }

    /**
     * Hands the selection to {@code npc}: a hunter gets the hunting centre, a miner the mining region, anyone else
     * whatever is selected. The storage block, if selected, is shared by both tasks.
     */
    private static void assignTask(ItemStack stack, ServerPlayer player, SimsekNpc npc) {
        ServerLevel level = player.serverLevel();
        WandSelection selection = getSelection(stack, level.dimension());
        Job job = npc.getJob();
        BlockPos storage = selection.storage().orElse(null);
        boolean assigned = false;

        if (job != Job.HUNTER) {
            Optional<MiningRegion> region = selection.region();
            if (region.isPresent()) {
                if (!region.get().fitsLimit()) {
                    player.displayClientMessage(Component.translatable("message.simsekordu.wand.too_large",
                            region.get().sizeX(), region.get().sizeY(), region.get().sizeZ(), MiningRegion.MAX_SIZE)
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                npc.assignMiningTask(region.get(), storage);
                player.sendSystemMessage(Component.translatable("message.simsekordu.task_assigned",
                        npc.getNpcName(), region.get().sizeX(), region.get().sizeY(), region.get().sizeZ(), npc.getMiningRemaining()));
                assigned = true;
            } else if (job == Job.MINER) {
                player.displayClientMessage(Component.translatable("message.simsekordu.wand.no_region").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        if (job != Job.MINER) {
            Optional<BlockPos> center = selection.huntCenter();
            if (center.isPresent()) {
                npc.assignHuntTask(center.get(), storage);
                player.sendSystemMessage(Component.translatable("message.simsekordu.hunt_assigned", npc.getNpcName(), HuntHelper.RADIUS,
                        center.get().getX(), center.get().getY(), center.get().getZ(),
                        Component.translatable(npc.getHuntMode().getTranslationKey())));
                assigned = true;
            } else if (job == Job.HUNTER) {
                player.displayClientMessage(Component.translatable("message.simsekordu.wand.no_hunt_center").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        if (!assigned) {
            player.displayClientMessage(Component.translatable("message.simsekordu.wand.nothing_selected").withStyle(ChatFormatting.RED), true);
            return;
        }

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, npc.getX(), npc.getY() + npc.getBbHeight() * 0.7D, npc.getZ(),
                10, 0.4D, 0.5D, 0.4D, 0.0D);
        level.playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (npc.getStoragePos() == null) {
            player.sendSystemMessage(Component.translatable("message.simsekordu.task_no_storage", npc.getNpcName())
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (job != Job.MINER && job != Job.HUNTER) {
            player.sendSystemMessage(Component.translatable("message.simsekordu.task_no_job", npc.getNpcName())
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
