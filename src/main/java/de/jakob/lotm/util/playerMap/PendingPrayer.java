package de.jakob.lotm.util.playerMap;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** petition: vazio para uma prece comum; "heal", "food" ou "protect" para o pedido de um fiel da Igreja */
public record PendingPrayer(UUID senderUUID, String senderName, String senderPathway, int senderSequence,
                             double x, double y, double z, String petition) {

    public PendingPrayer(UUID senderUUID, String senderName, String senderPathway, int senderSequence, double x, double y, double z) {
        this(senderUUID, senderName, senderPathway, senderSequence, x, y, z, "");
    }

    public boolean isPetition() {
        return !petition.isEmpty();
    }

    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_PATHWAY_LENGTH = 64;

    public static PendingPrayer fromNetwork(FriendlyByteBuf buf) {
        return new PendingPrayer(
                buf.readUUID(),
                buf.readUtf(MAX_NAME_LENGTH),
                buf.readUtf(MAX_PATHWAY_LENGTH),
                buf.readInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readUtf(16)
        );
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeUUID(senderUUID);
        buf.writeUtf(senderName, MAX_NAME_LENGTH);
        buf.writeUtf(senderPathway, MAX_PATHWAY_LENGTH);
        buf.writeInt(senderSequence);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeUtf(petition, 16);
    }
}
