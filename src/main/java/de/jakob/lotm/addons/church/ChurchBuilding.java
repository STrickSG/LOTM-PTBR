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
    /** O dia (do mundo) da última pregação do Deus neste templo: uma por dia */
    private long lastPreachDay = -1;

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

    /**
     * As regras das Catedrais: uma Catedral (nível 4) por colônia; a Catedral Sagrada (nível 5) é uma só por Deus,
     * e só na colônia do próprio Deus. Recusa a melhoria com a mensagem quando a regra não deixa.
     */
    @Override
    public void requestUpgrade(net.minecraft.world.entity.player.Player player, BlockPos builder) {
        String refusal = upgradeRefusal(getBuildingLevel() + 1);
        if (refusal != null) {
            if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(refusal).withColor(0xFF7A7A));
            return;
        }
        super.requestUpgrade(player, builder);
    }

    /** A chave da mensagem de recusa, ou null se pode melhorar para este nível */
    public String upgradeRefusal(int next) {
        if (next == 4) {
            for (var other : getColony().getServerBuildingManager().getBuildings().values()) {
                if (other == this || !(other instanceof ChurchBuilding church)) continue;
                if (church.getBuildingLevel() >= 4 || church.getBuildingLevel() == 3 && church.isPendingConstruction())
                    return "lotmcraft.church.rule.one_cathedral";
            }
        }
        if (next == 5) {
            if (god == null) return "lotmcraft.church.rule.sacred_needs_god";
            if (!god.equals(getColony().getPermissions().getOwner())) return "lotmcraft.church.rule.sacred_own_colony";
            MinecraftServer server = getColony().getWorld().getServer();
            if (server != null) for (ChurchBuilding church : Churches.temples(server, god)) {
                if (church == this) continue;
                if (church.getBuildingLevel() >= 5 || church.getBuildingLevel() == 4 && church.isPendingConstruction())
                    return "lotmcraft.church.rule.one_sacred";
            }
        }
        return null;
    }

    /** O Deus tem uma Catedral Sagrada pronta nesta colônia (onde ficam os Santos, os Anjos e o Papa) */
    public static boolean hasSacredCathedral(IColony colony, UUID god) {
        if (colony == null || god == null) return false;
        for (var b : colony.getServerBuildingManager().getBuildings().values())
            if (b instanceof ChurchBuilding church && god.equals(church.getGod()) && church.getBuildingLevel() >= 5) return true;
        return false;
    }

    public UUID getGod() {
        return god;
    }

    public long getLastPreachDay() {
        return lastPreachDay;
    }

    public void setLastPreachDay(long day) {
        lastPreachDay = day;
        markDirty();
    }

    public void setGod(UUID god) {
        this.god = god;
        markDirty();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        super.deserializeNBT(provider, tag);
        god = tag.hasUUID("lotmGod") ? tag.getUUID("lotmGod") : null;
        lastPreachDay = tag.contains("lotmPreach") ? tag.getLong("lotmPreach") : -1;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = super.serializeNBT(provider);
        if (god != null) tag.putUUID("lotmGod", god);
        tag.putLong("lotmPreach", lastPreachDay);
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
            buf.writeUtf(info == null ? "" : info.nature());
            buf.writeUtf(info == null ? "" : info.pathway());
            buf.writeVarInt(server == null ? 0 : Churches.temples(server, god).size());
        }
    }

    /** O que o cliente sabe do templo, para o menu */
    public static class View extends AbstractBuildingView {
        public UUID god;
        public String godName = "", churchName = "", kind = Churches.CHURCH, pathway = "", nature = "";
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
                nature = buf.readUtf();
                pathway = buf.readUtf();
                temples = buf.readVarInt();
            } else {
                god = null;
                godName = churchName = pathway = nature = "";
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
