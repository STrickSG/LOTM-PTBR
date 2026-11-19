package de.jakob.lotm.addons.rituals;

import de.jakob.lotm.LOTMCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Servidor -> cliente: abrir a janela deste colono (quem clicou em alguém disfarçado dele) */
public record DisguiseCitizenWindowPacket(int colonyId, String dimension, int citizenId) implements CustomPacketPayload {
    public static final Type<DisguiseCitizenWindowPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "error_disguise_citizen_window"));

    public static final StreamCodec<ByteBuf, DisguiseCitizenWindowPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DisguiseCitizenWindowPacket::colonyId,
            ByteBufCodecs.STRING_UTF8, DisguiseCitizenWindowPacket::dimension,
            ByteBufCodecs.VAR_INT, DisguiseCitizenWindowPacket::citizenId,
            DisguiseCitizenWindowPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DisguiseCitizenWindowPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ErrorDisguiseRender.openCitizenWindow(packet));
    }
}
