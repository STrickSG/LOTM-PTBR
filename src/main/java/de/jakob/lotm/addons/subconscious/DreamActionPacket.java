package de.jakob.lotm.addons.subconscious;

import de.jakob.lotm.LOTMCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Cliente -> servidor: o sonho escolhido no menu para esta mente */
public record DreamActionPacket(UUID sleeper, String action) implements CustomPacketPayload {
    public static final Type<DreamActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "dream_action"));

    public static final StreamCodec<ByteBuf, DreamActionPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DreamActionPacket::sleeper,
            ByteBufCodecs.STRING_UTF8, DreamActionPacket::action,
            DreamActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DreamActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) SubconsciousSea.influence(player, packet.sleeper(), packet.action());
        });
    }
}
