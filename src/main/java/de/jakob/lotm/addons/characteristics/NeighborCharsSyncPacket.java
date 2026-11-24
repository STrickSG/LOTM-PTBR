package de.jakob.lotm.addons.characteristics;

import de.jakob.lotm.LOTMCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Set;

/** Servidor -> cliente: de quais caminhos vizinhos o jogador guarda características de Sequência 1 (para as habilidades) */
public record NeighborCharsSyncPacket(List<String> pathways) implements CustomPacketPayload {
    public static final Type<NeighborCharsSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "neighbor_chars_sync"));
    public static final StreamCodec<ByteBuf, NeighborCharsSyncPacket> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(NeighborCharsSyncPacket::new, NeighborCharsSyncPacket::pathways);

    /** O que o cliente sabe do próprio jogador */
    public static volatile Set<String> clientPathways = Set.of();
    public static volatile java.util.Map<String, Integer> clientCounts = java.util.Map.of();
    /** As Unicidades de vizinhos que o jogador guarda, e se ele é um Grande Antigo */
    public static volatile java.util.List<String> clientUniquenesses = java.util.List.of();
    public static volatile boolean clientGreatOldOne = false, clientAscensionReady = false;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NeighborCharsSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            java.util.List<String> uniq = new java.util.ArrayList<>();
            boolean goo = false, ready = false;
            for (String s : packet.pathways()) {
                if (s.startsWith("#uniq:")) { uniq.add(s.substring(6)); continue; }
                if (s.equals("#goo")) { goo = true; continue; }
                if (s.equals("#ready")) { ready = true; continue; }
                int i = s.indexOf('=');
                if (i > 0) counts.put(s.substring(0, i), Integer.parseInt(s.substring(i + 1)));
            }
            clientCounts = counts;
            clientPathways = Set.copyOf(counts.keySet());
            clientUniquenesses = java.util.List.copyOf(uniq);
            clientGreatOldOne = goo;
            clientAscensionReady = ready;
        });
    }
}
