package de.jakob.lotm.beyonders.abilities.door.passives;

import de.jakob.lotm.LOTMCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Servidor -> cliente: coordenadas no Mundo de Cima da Percepção do Mundo Espiritual (Porta 5).
 * Mostradas num canto da tela, não na barra de ação, para não esconder as outras mensagens.
 */
public record SpiritCoordinatesPacket(int x, int y, int z) implements CustomPacketPayload {
    public static final Type<SpiritCoordinatesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "spirit_world_coordinates"));

    public static final StreamCodec<ByteBuf, SpiritCoordinatesPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpiritCoordinatesPacket::x,
            ByteBufCodecs.VAR_INT, SpiritCoordinatesPacket::y,
            ByteBufCodecs.VAR_INT, SpiritCoordinatesPacket::z,
            SpiritCoordinatesPacket::new);

    /** Último valor recebido e quando (System.currentTimeMillis), só no cliente */
    static volatile SpiritCoordinatesPacket last;
    static volatile long receivedAt;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpiritCoordinatesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            last = packet;
            receivedAt = System.currentTimeMillis();
        });
    }
}
