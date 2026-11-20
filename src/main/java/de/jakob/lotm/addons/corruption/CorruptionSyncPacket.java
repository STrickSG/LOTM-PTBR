package de.jakob.lotm.addons.corruption;

import de.jakob.lotm.LOTMCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Servidor -> cliente: a corrupção atual do jogador, para a barra da Introspecção */
public record CorruptionSyncPacket(float value) implements CustomPacketPayload {
    public static final Type<CorruptionSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "corruption_sync"));
    public static final StreamCodec<ByteBuf, CorruptionSyncPacket> STREAM_CODEC = ByteBufCodecs.FLOAT.map(CorruptionSyncPacket::new, CorruptionSyncPacket::value);

    /** O valor no cliente */
    public static float clientValue;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CorruptionSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> clientValue = packet.value());
    }
}
