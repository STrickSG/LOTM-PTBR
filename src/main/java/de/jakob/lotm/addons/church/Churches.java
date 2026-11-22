package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * As Igrejas e Seitas do mundo: uma por Deus (um jogador), com o nome que ele escolheu, o tipo e o Caminho em que foi
 * fundada. Os templos são os prédios {@link ChurchBuilding} consagrados a esse Deus, em qualquer colônia.
 */
public final class Churches extends SavedData {
    public static final String CHURCH = "church", SECT = "sect";
    /** Sequência mínima para ter uma Igreja: 3 ou maior (número menor) */
    public static final int MAX_SEQUENCE = 3;
    public static final int MAX_NAME = 40;

    /** Natureza da organização escondida: a Seita (sombria, com sacrifícios) ou a Ordem (neutra, com reuniões secretas); vazia na Igreja */
    public static final String DARK = "dark", CALM = "calm";

    public record Info(String name, String kind, String pathway, String nature) {
        public boolean isSect() { return SECT.equals(kind); }
        public boolean isDark() { return isSect() && DARK.equals(nature); }
        /** A Ordem: escondida como a Seita, porém neutra */
        public boolean isOrder() { return isSect() && !DARK.equals(nature); }

        /** Que Beyonders podem ser membros: a Igreja aceita todos; a Seita, só o próprio caminho; a Ordem, também os vizinhos */
        public boolean accepts(String beyonderPathway) {
            if (!isSect() || beyonderPathway == null || beyonderPathway.equals(pathway)) return true;
            return isOrder() && de.jakob.lotm.util.helper.AdvancementUtil.neighbors(pathway).contains(beyonderPathway);
        }
    }

    private final Map<UUID, Info> churches = new HashMap<>();
    private final Map<UUID, String> godNames = new HashMap<>();

    public static Churches get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Churches::new, Churches::load, null), "lotmcraft_churches");
    }

    public Info info(UUID god) {
        return churches.get(god);
    }

    public void found(ServerPlayer god, String name, String kind, String pathway, String nature) {
        churches.put(god.getUUID(), new Info(name, kind, pathway, SECT.equals(kind) ? (DARK.equals(nature) ? DARK : CALM) : ""));
        godNames.put(god.getUUID(), god.getName().getString());
        setDirty();
    }

    public void rename(UUID god, String name) {
        Info info = churches.get(god);
        if (info == null) return;
        churches.put(god, new Info(name, info.kind(), info.pathway(), info.nature()));
        setDirty();
    }

    /** O nome do Deus, mesmo que ele esteja desconectado */
    public static String godName(MinecraftServer server, UUID god) {
        if (server == null || god == null) return "";
        ServerPlayer online = server.getPlayerList().getPlayer(god);
        if (online != null) return online.getName().getString();
        return get(server).godNames.getOrDefault(god, "?");
    }

    /** Todos os templos consagrados a este Deus, em todas as colônias */
    public static List<ChurchBuilding> temples(MinecraftServer server, UUID god) {
        List<ChurchBuilding> list = new ArrayList<>();
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
                if (building instanceof ChurchBuilding temple && god.equals(temple.getGod())) list.add(temple);
            }
        }
        return list;
    }

    static Churches load(CompoundTag tag, HolderLookup.Provider provider) {
        Churches data = new Churches();
        CompoundTag list = tag.getCompound("Churches");
        for (String key : list.getAllKeys()) {
            CompoundTag c = list.getCompound(key);
            data.churches.put(UUID.fromString(key), new Info(c.getString("Name"), c.getString("Kind"), c.getString("Pathway"),
                    SECT.equals(c.getString("Kind")) ? (c.getString("Nature").isEmpty() ? CALM : c.getString("Nature")) : ""));
        }
        CompoundTag names = tag.getCompound("GodNames");
        for (String key : names.getAllKeys()) data.godNames.put(UUID.fromString(key), names.getString(key));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag list = new CompoundTag();
        churches.forEach((god, info) -> {
            CompoundTag c = new CompoundTag();
            c.putString("Name", info.name());
            c.putString("Kind", info.kind());
            c.putString("Pathway", info.pathway());
            c.putString("Nature", info.nature());
            list.put(god.toString(), c);
        });
        tag.put("Churches", list);
        CompoundTag names = new CompoundTag();
        godNames.forEach((god, name) -> names.putString(god.toString(), name));
        tag.put("GodNames", names);
        return tag;
    }
}
