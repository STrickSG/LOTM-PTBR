package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.network.PacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Servidor -> cliente: a sanidade de quem a visão está olhando (o cliente não recebe a sanidade dos outros) */
public record VisionTargetPacket(int entityId, float sanity) implements CustomPacketPayload {
    public static final Type<VisionTargetPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "vision_target"));
    public static final StreamCodec<ByteBuf, VisionTargetPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VisionTargetPacket::entityId,
            ByteBufCodecs.FLOAT, VisionTargetPacket::sanity,
            VisionTargetPacket::new);

    /** Última sanidade recebida e de quem */
    public static volatile int clientEntity = -1;
    public static volatile float clientSanity = 1f;

    public static void send(ServerPlayer viewer, LivingEntity target) {
        if (target == null || viewer.tickCount % 5 != 0) return;
        PacketHandler.sendToPlayer(viewer, new VisionTargetPacket(target.getId(), target.getData(ModAttachments.SANITY_COMPONENT).getSanity()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VisionTargetPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            clientEntity = packet.entityId();
            clientSanity = packet.sanity();
        });
    }
}
