package de.jakob.lotm.addons.colonies;

import de.jakob.lotm.LOTMCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor -> cliente: este colono é uma marionete-monstro e aparece com a skin de um Beyonder Errante
 * (o Tolo deixa as marionetes-monstro com forma humana). Skin vazia = aparência normal de colono.
 */
public record MarionetteSkinPacket(int entityId, String skin) implements CustomPacketPayload {
    public static final Type<MarionetteSkinPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "marionette_colonist_skin"));

    public static final StreamCodec<ByteBuf, MarionetteSkinPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MarionetteSkinPacket::entityId,
            ByteBufCodecs.STRING_UTF8, MarionetteSkinPacket::skin,
            MarionetteSkinPacket::new);

    /** Skins por id de entidade, só no cliente */
    private static final Map<Integer, ResourceLocation> SKINS = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MarionetteSkinPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.skin().isEmpty()) SKINS.remove(packet.entityId());
            else SKINS.put(packet.entityId(), ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/npc/" + packet.skin() + ".png"));
        });
    }

    public static ResourceLocation skinOf(int entityId) {
        return SKINS.get(entityId);
    }

    public static void forget(int entityId) {
        SKINS.remove(entityId);
    }
}
