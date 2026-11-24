package de.jakob.lotm.addons.characteristics;

import de.jakob.lotm.LOTMCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: o Deus começa o Ritual de Ascensão (botão da Introspecção) */
public record RequestAscensionPacket() implements CustomPacketPayload {
    public static final Type<RequestAscensionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "request_ascension"));
    public static final StreamCodec<ByteBuf, RequestAscensionPacket> STREAM_CODEC = StreamCodec.unit(new RequestAscensionPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestAscensionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) GreatOldOnes.requestAscension(player);
        });
    }
}
