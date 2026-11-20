package de.jakob.lotm.addons.church;

import de.jakob.lotm.LOTMCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Servidor -> cliente: abrir o menu do templo nesta posição (usado pelo altar) */
public record OpenChurchPacket(BlockPos temple) implements CustomPacketPayload {
    public static final Type<OpenChurchPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "open_church"));
    public static final StreamCodec<ByteBuf, OpenChurchPacket> STREAM_CODEC = BlockPos.STREAM_CODEC.map(OpenChurchPacket::new, OpenChurchPacket::temple);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenChurchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ChurchClient.openAt(packet.temple()));
    }
}
