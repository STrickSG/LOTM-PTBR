package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.util.BeyonderData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: consagrar o templo a si mesmo (fundando a Igreja/Seita com o nome escolhido) ou renomear */
public record ChurchActionPacket(BlockPos temple, String action, String name, String kind) implements CustomPacketPayload {
    public static final Type<ChurchActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "church_action"));
    public static final StreamCodec<ByteBuf, ChurchActionPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ChurchActionPacket::temple,
            ByteBufCodecs.STRING_UTF8, ChurchActionPacket::action,
            ByteBufCodecs.STRING_UTF8, ChurchActionPacket::name,
            ByteBufCodecs.STRING_UTF8, ChurchActionPacket::kind,
            ChurchActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChurchActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) act(player, packet);
        });
    }

    private static void act(ServerPlayer player, ChurchActionPacket packet) {
        if (player.distanceToSqr(packet.temple().getCenter()) > 64 * 64) return;
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), packet.temple());
        if (colony == null || !(colony.getServerBuildingManager().getBuilding(packet.temple()) instanceof ChurchBuilding temple)) return;
        Churches churches = Churches.get(player.server);
        String name = packet.name().strip();
        if (name.length() > Churches.MAX_NAME) name = name.substring(0, Churches.MAX_NAME);

        switch (packet.action()) {
            case "consecrate" -> {
                if (!BeyonderData.isBeyonder(player) || BeyonderData.getSequence(player) > Churches.MAX_SEQUENCE) {
                    say(player, Component.translatable("lotmcraft.church.not_worthy", Churches.MAX_SEQUENCE), 0xFF7A7A);
                    return;
                }
                if (temple.getGod() != null && !temple.getGod().equals(player.getUUID())) {
                    say(player, Component.translatable("lotmcraft.church.already_consecrated"), 0xFF7A7A);
                    return;
                }
                if (churches.info(player.getUUID()) == null) {
                    if (name.isEmpty()) {
                        say(player, Component.translatable("lotmcraft.church.need_name"), 0xFF7A7A);
                        return;
                    }
                    String kind = Churches.SECT.equals(packet.kind()) ? Churches.SECT : Churches.CHURCH;
                    churches.found(player, name, kind, BeyonderData.getPathway(player));
                }
                temple.setGod(player.getUUID());
                player.serverLevel().sendParticles(ParticleTypes.END_ROD, packet.temple().getX() + 0.5, packet.temple().getY() + 1.2, packet.temple().getZ() + 0.5, 40, 0.5, 0.8, 0.5, 0.05);
                player.serverLevel().playSound(null, packet.temple(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.3f);
                say(player, Component.translatable("lotmcraft.church.consecrated", churches.info(player.getUUID()).name()), 0xFFE29A);
            }
            case "rename" -> {
                if (!player.getUUID().equals(temple.getGod()) || name.isEmpty()) return;
                churches.rename(player.getUUID(), name);
                Churches.temples(player.server, player.getUUID()).forEach(ChurchBuilding::markDirty);
                say(player, Component.translatable("lotmcraft.church.renamed", name), 0xFFE29A);
            }
            default -> { }
        }
    }

    private static void say(ServerPlayer player, Component text, int color) {
        player.displayClientMessage(text.copy().withColor(color), true);
    }
}
