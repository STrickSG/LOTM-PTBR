package de.jakob.lotm.network.packets.toServer;

import com.mojang.datafixers.util.Pair;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.events.HonorificNamesEventHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;
import de.jakob.lotm.util.LotmText;

/**
 * Sent by the client when the player responds to a pending prayer from the Honorific Names menu.
 *
 * @param senderUUID UUID of the player who sent the prayer (the one who typed the honorific name in chat)
 * @param teleport   true → teleport the sender to the target; false → queue a message transfer
 */
public record HonorificNamesRespondPacket(UUID senderUUID, int action) implements CustomPacketPayload {
    public static final int MESSAGE = 0, TELEPORT = 1, GRANT = 2, IGNORE = 3;

    public boolean teleport() {
        return action == TELEPORT;
    }

    public static final CustomPacketPayload.Type<HonorificNamesRespondPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "honorific_names_respond"));

    public static final StreamCodec<FriendlyByteBuf, HonorificNamesRespondPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeUUID(pkt.senderUUID());
                        buf.writeVarInt(pkt.action());
                    },
                    buf -> new HonorificNamesRespondPacket(buf.readUUID(), buf.readVarInt())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HonorificNamesRespondPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.flow().getReceptionSide().isServer()) return;

            ServerPlayer target = (ServerPlayer) context.player();
            UUID targetUUID = target.getUUID();
            UUID senderUUID = pkt.senderUUID();

            // Validate that this prayer is still pending
            Pair<UUID, UUID> key = new Pair<>(targetUUID, senderUUID);
            if (!HonorificNamesEventHandler.answerState.contains(key)) {
                return;
            }

            // o pedido de um fiel da Igreja: atender ou ignorar
            var petition = HonorificNamesEventHandler.getPendingPrayers(targetUUID).stream()
                    .filter(p -> p.senderUUID().equals(senderUUID) && p.isPetition()).findFirst().orElse(null);
            if (petition != null) {
                if (pkt.action() == GRANT || pkt.action() == IGNORE)
                    de.jakob.lotm.addons.church.ChurchPrayers.answer(target, petition, pkt.action() == GRANT);
                return;
            }

            ServerPlayer sender = target.server.getPlayerList().getPlayer(senderUUID);
            if (sender == null) {
                HonorificNamesEventHandler.answerState.remove(key);
                HonorificNamesEventHandler.removePendingPrayer(targetUUID, senderUUID);
                return;
            }

            if (pkt.teleport()) {
                // Teleport target (prayer receiver) to sender's (the one who prayed) location
                target.teleportTo(
                        sender.serverLevel(),
                        sender.getX(), sender.getY(), sender.getZ(),
                        sender.getYRot(), sender.getXRot()
                );
            } else {
                // Queue message transfer: target's next chat message goes to sender
                HonorificNamesEventHandler.isInTransferring.put(targetUUID, senderUUID);
                de.jakob.lotm.util.helper.MessageUtil.actionBar(target, 
                        net.minecraft.network.chat.Component.literal(LotmText.f("lotmcraft.text.your_next_message_will_be_sent", "Your next message will be sent to %s.", sender.getName().getString()))
                                .withStyle(net.minecraft.ChatFormatting.GREEN)
                );
            }

            HonorificNamesEventHandler.answerState.remove(key);
            HonorificNamesEventHandler.removePendingPrayer(targetUUID, senderUUID);
        });
    }
}
