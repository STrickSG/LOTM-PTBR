package de.jakob.lotm.addons.subconscious;

import de.jakob.lotm.LOTMCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Servidor -> cliente: abrir o menu de sonhos desta mente (nome, ofício, felicidade, habilidades, o que dá para sonhar) */
public record DreamMindPacket(CompoundTag mind) implements CustomPacketPayload {
    public static final Type<DreamMindPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "dream_mind"));

    public static final StreamCodec<ByteBuf, DreamMindPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, DreamMindPacket::mind, DreamMindPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DreamMindPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DreamScreen.open(packet.mind()));
    }
}
