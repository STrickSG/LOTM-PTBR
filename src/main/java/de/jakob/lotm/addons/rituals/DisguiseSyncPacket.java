package de.jakob.lotm.addons.rituals;

import de.jakob.lotm.LOTMCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor -> cliente: este jogador está (ou não) vestido com as roupas de outra pessoa.
 * Nome vazio = sem disfarce. O cliente desenha o jogador como essa pessoa.
 */
public record DisguiseSyncPacket(UUID player, String name, CompoundTag look) implements CustomPacketPayload {
    public static final Type<DisguiseSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "error_disguise"));

    public static final StreamCodec<ByteBuf, DisguiseSyncPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DisguiseSyncPacket::player,
            ByteBufCodecs.STRING_UTF8, DisguiseSyncPacket::name,
            ByteBufCodecs.COMPOUND_TAG, DisguiseSyncPacket::look,
            DisguiseSyncPacket::new);

    public record Look(String name, CompoundTag look) {}

    /** Só no cliente: quem está disfarçado de quem */
    static final Map<UUID, Look> DISGUISED = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DisguiseSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.name().isEmpty()) DISGUISED.remove(packet.player());
            else DISGUISED.put(packet.player(), new Look(packet.name(), packet.look()));
        });
    }
}
