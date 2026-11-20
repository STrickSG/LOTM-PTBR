package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Um templo: o prédio da Igreja (ou Seita) numa colônia. O templo é consagrado a um Deus (um jogador de Sequência 3
 * ou acima); a organização em si (nome, tipo) fica em {@link Churches}, e um Deus pode ter templos em várias colônias.
 */
public class ChurchBuilding extends AbstractBuilding {
    public static final String SCHEMATIC = "church";
    private UUID god;

    public ChurchBuilding(IColony colony, BlockPos pos) {
        super(colony, pos);
    }

    @Override
    public String getSchematicName() {
        return SCHEMATIC;
    }

    @Override
    public int getMaxBuildingLevel() {
        return 5;
    }

    public UUID getGod() {
        return god;
    }

    public void setGod(UUID god) {
        this.god = god;
        markDirty();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        super.deserializeNBT(provider, tag);
        god = tag.hasUUID("lotmGod") ? tag.getUUID("lotmGod") : null;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = super.serializeNBT(provider);
        if (god != null) tag.putUUID("lotmGod", god);
        return tag;
    }

    @Override
    public void serializeToView(RegistryFriendlyByteBuf buf, boolean fullSync) {
        super.serializeToView(buf, fullSync);
        MinecraftServer server = getColony().getWorld().getServer();
        Churches.Info info = god == null || server == null ? null : Churches.get(server).info(god);
        buf.writeBoolean(god != null);
        if (god != null) {
            buf.writeUUID(god);
            buf.writeUtf(Churches.godName(server, god));
            buf.writeUtf(info == null ? "" : info.name());
            buf.writeUtf(info == null ? Churches.CHURCH : info.kind());
            buf.writeUtf(info == null ? "" : info.pathway());
            buf.writeVarInt(server == null ? 0 : Churches.temples(server, god).size());
        }
    }

    /** O que o cliente sabe do templo, para o menu */
    public static class View extends AbstractBuildingView {
        public UUID god;
        public String godName = "", churchName = "", kind = Churches.CHURCH, pathway = "";
        public int temples;

        public View(IColonyView colony, BlockPos pos) {
            super(colony, pos);
        }

        @Override
        public void deserialize(RegistryFriendlyByteBuf buf) {
            super.deserialize(buf);
            if (buf.readBoolean()) {
                god = buf.readUUID();
                godName = buf.readUtf();
                churchName = buf.readUtf();
                kind = buf.readUtf();
                pathway = buf.readUtf();
                temples = buf.readVarInt();
            } else {
                god = null;
                godName = churchName = pathway = "";
                kind = Churches.CHURCH;
                temples = 0;
            }
        }

        @Override
        public void openGui(boolean shouldOpenInv) {
            ChurchClient.open(this);
        }

        /** A janela padrão do MineColonies (construir, melhorar, reparar) */
        public void openColonyWindow() {
            super.openGui(false);
        }

        @Override
        public com.ldtteam.blockui.views.BOWindow getWindow() {
            return new com.minecolonies.core.client.gui.WindowHutMinPlaceholder<>(this);
        }
    }
}
