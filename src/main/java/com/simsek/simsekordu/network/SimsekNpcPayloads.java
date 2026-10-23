package com.simsek.simsekordu.network;

import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.data.Job;
import com.simsek.simsekordu.entity.SimsekNpc;
import com.simsek.simsekordu.menu.SimsekNpcMenu;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** GUI -> server packets of the NPC menu. Every handler re-validates ownership and distance. */
@EventBusSubscriber(modid = SimsekOrdu.MODID)
public final class SimsekNpcPayloads {
    public static final int MAX_NAME_LENGTH = 16;
    private static final String NETWORK_VERSION = "1";

    private SimsekNpcPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_VERSION)
                .playToServer(SetJobPayload.TYPE, SetJobPayload.STREAM_CODEC, SimsekNpcPayloads::handleSetJob)
                .playToServer(SetWaitingPayload.TYPE, SetWaitingPayload.STREAM_CODEC, SimsekNpcPayloads::handleSetWaiting)
                .playToServer(RenamePayload.TYPE, RenamePayload.STREAM_CODEC, SimsekNpcPayloads::handleRename);
    }

    /** Returns the NPC if {@code player} owns it and is within menu range, otherwise null. */
    @Nullable
    private static SimsekNpc findControlledNpc(Player player, int entityId) {
        if (player.level().getEntity(entityId) instanceof SimsekNpc npc
                && npc.isAlive()
                && npc.isOwnedBy(player)
                && npc.distanceToSqr(player) <= SimsekNpcMenu.MAX_DISTANCE * SimsekNpcMenu.MAX_DISTANCE) {
            return npc;
        }
        return null;
    }

    private static void handleSetJob(SetJobPayload payload, IPayloadContext context) {
        SimsekNpc npc = findControlledNpc(context.player(), payload.entityId());
        Job[] jobs = Job.values();
        if (npc != null && payload.jobId() >= 0 && payload.jobId() < jobs.length) {
            npc.setJob(jobs[payload.jobId()]);
        }
    }

    private static void handleSetWaiting(SetWaitingPayload payload, IPayloadContext context) {
        SimsekNpc npc = findControlledNpc(context.player(), payload.entityId());
        if (npc != null) {
            npc.setWaiting(payload.waiting());
        }
    }

    private static void handleRename(RenamePayload payload, IPayloadContext context) {
        SimsekNpc npc = findControlledNpc(context.player(), payload.entityId());
        if (npc == null) {
            return;
        }
        String name = sanitizeName(payload.name());
        if (!name.isEmpty()) {
            npc.setNpcName(name);
        }
    }

    public static String sanitizeName(String raw) {
        StringBuilder cleaned = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c >= ' ' && c != 127 && c != '§') {
                cleaned.append(c);
            }
        }
        String name = cleaned.toString().strip();
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH).strip() : name;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SimsekOrdu.MODID, path);
    }

    public record SetJobPayload(int entityId, int jobId) implements CustomPacketPayload {
        public static final Type<SetJobPayload> TYPE = new Type<>(id("set_job"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetJobPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetJobPayload::entityId,
                ByteBufCodecs.VAR_INT, SetJobPayload::jobId,
                SetJobPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetWaitingPayload(int entityId, boolean waiting) implements CustomPacketPayload {
        public static final Type<SetWaitingPayload> TYPE = new Type<>(id("set_waiting"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetWaitingPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetWaitingPayload::entityId,
                ByteBufCodecs.BOOL, SetWaitingPayload::waiting,
                SetWaitingPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RenamePayload(int entityId, String name) implements CustomPacketPayload {
        public static final Type<RenamePayload> TYPE = new Type<>(id("rename"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RenamePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RenamePayload::entityId,
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), RenamePayload::name,
                RenamePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
