package de.jakob.lotm.addons.characteristics;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.sefirah.SefirahHandler;
import de.jakob.lotm.entity.custom.uniqueness.UniquenessEntity;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AdvancementUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * As Unicidades dos caminhos vizinhos e o Grande Antigo.
 * <ul>
 *   <li>Só um Deus (Seq 0) pega a Unicidade de um caminho vizinho; ela não muda o caminho, mas pesa na corrupção.</li>
 *   <li>Grande Antigo: o Deus que abriga a Sefirah do grupo, as Unicidades dos vizinhos e pelo menos uma característica
 *   de Sequência 1 de cada vizinho. Só um por grupo. Quem trocou de caminho não consegue.</li>
 *   <li>Ao morrer, as Unicidades dos vizinhos se soltam e voltam ao mundo; o título acaba.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class GreatOldOnes {
    private GreatOldOnes() {}

    private static final String KEY = "LotmNeighborUniq", TITLE = "LotmGreatOldOne";
    /** Cada Unicidade de um vizinho pesa isto na corrupção */
    public static final float WEIGHT = 0.15f;
    private static final int COLOR = 0xD9B36A;

    /** Sefirah -> os caminhos do grupo que ela representa */
    private static final Map<String, Set<String>> SEFIRAH_GROUPS = Map.of(
            "sefirah_castle", Set.of("fool", "error", "door"),
            "brood_hive", Set.of("mother", "moon"));

    // ------------------------------------------------------------------ no corpo

    private static CompoundTag persisted(Player p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG); }
    private static void save(Player p, CompoundTag persisted) { p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted); }

    public static Set<String> uniquenesses(Player p) {
        Set<String> out = new LinkedHashSet<>();
        for (Tag t : persisted(p).getList(KEY, Tag.TAG_STRING)) out.add(t.getAsString());
        return out;
    }

    private static void setUniquenesses(Player p, Set<String> set) {
        CompoundTag persisted = persisted(p);
        ListTag list = new ListTag();
        for (String s : set) list.add(StringTag.valueOf(s));
        if (list.isEmpty()) persisted.remove(KEY); else persisted.put(KEY, list);
        save(p, persisted);
    }

    public static boolean isGreatOldOne(Player p) { return persisted(p).getBoolean(TITLE); }

    private static void setTitle(Player p, boolean on) {
        CompoundTag persisted = persisted(p);
        if (on) persisted.putBoolean(TITLE, true); else persisted.remove(TITLE);
        save(p, persisted);
    }

    public static float weight(Player p) { return uniquenesses(p).size() * WEIGHT; }

    /** Só o Deus pega a Unicidade de um caminho vizinho */
    public static boolean canTake(Player player, String pathway) {
        if (BeyonderData.getSequence(player) != 0) return false;
        String own = BeyonderData.getPathway(player);
        return !own.equalsIgnoreCase(pathway) && AdvancementUtil.neighbors(own).contains(pathway);
    }

    public static void take(ServerPlayer player, String pathway) {
        Set<String> set = uniquenesses(player);
        set.add(pathway);
        setUniquenesses(player, set);
        data(player.server).holders.put(pathway, player.getUUID());
        data(player.server).setDirty();
        de.jakob.lotm.addons.corruption.Corruption.set(player, de.jakob.lotm.addons.corruption.Corruption.get(player));   // o novo piso
        NeighborCharacteristics.sync(player);
        check(player);
    }

    /** Alguém (mesmo desconectado) guarda esta Unicidade como a de um vizinho */
    public static boolean isHeld(MinecraftServer server, String pathway) {
        return data(server).holders.containsKey(pathway);
    }

    // ------------------------------------------------------------------ o Grande Antigo

    private static String groupKey(String own) {
        Set<String> group = new TreeSet<>(AdvancementUtil.neighbors(own));
        group.add(own);
        return String.join("+", group);
    }

    /** O que falta para ser Grande Antigo (null se nada falta) */
    public static String missing(ServerPlayer player) {
        if (BeyonderData.getSequence(player) != 0) return "seq";
        String own = BeyonderData.getPathway(player);
        Set<String> neighbors = AdvancementUtil.neighbors(own);
        if (neighbors.isEmpty()) return "group";
        if (BeyonderData.hasSwitchedPathway(player)) return "switched";
        Set<String> sefirahGroup = SEFIRAH_GROUPS.get(SefirahHandler.getClaimedSefirot(player));
        if (sefirahGroup == null || !sefirahGroup.contains(own)) return "sefirah";
        Set<String> held = uniquenesses(player);
        for (String n : neighbors) {
            if (!held.contains(n)) return "uniqueness:" + n;
            if (NeighborCharacteristics.get(player, n) < 1) return "characteristic:" + n;
        }
        UUID other = data(player.server).titles.get(groupKey(own));
        if (other != null && !other.equals(player.getUUID())) return "taken";
        return null;
    }

    public static void check(ServerPlayer player) {
        boolean ready = missing(player) == null;
        boolean has = isGreatOldOne(player);
        if (ready == has) return;
        String group = groupKey(BeyonderData.getPathway(player));
        setTitle(player, ready);
        if (ready) {
            data(player.server).titles.put(group, player.getUUID());
            for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.translatable("lotmcraft.great_old_one.ascended", player.getName()).withColor(COLOR));
                p.playNotifySound(SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.MASTER, 0.6f, 0.6f);
            }
        } else {
            data(player.server).titles.remove(group, player.getUUID());
            player.sendSystemMessage(Component.translatable("lotmcraft.great_old_one.lost").withColor(0xB0306A));
        }
        data(player.server).setDirty();
        NeighborCharacteristics.sync(player);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 7) return;
        if (BeyonderData.getSequence(player) != 0 && !isGreatOldOne(player) && uniquenesses(player).isEmpty()) return;
        check(player);
    }

    /** Morto: as Unicidades dos vizinhos se soltam e voltam ao mundo, e o título acaba */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        Set<String> held = uniquenesses(player);
        if (held.isEmpty() && !isGreatOldOne(player)) return;
        Data data = data(player.server);
        for (String pathway : held) data.holders.remove(pathway, player.getUUID());
        setUniquenesses(player, Set.of());
        if (isGreatOldOne(player)) {
            setTitle(player, false);
            data.titles.values().remove(player.getUUID());
        }
        data.setDirty();
        var at = player.position();
        for (String pathway : held) UniquenessEntity.trySpawn(level, at, pathway);
        NeighborCharacteristics.sync(player);
    }

    // ------------------------------------------------------------------ dados do mundo

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "lotmcraft_great_old_ones");
    }

    private static final class Data extends SavedData {
        /** Unicidade de vizinho -> quem a guarda */
        private final Map<String, UUID> holders = new HashMap<>();
        /** grupo -> o Grande Antigo */
        private final Map<String, UUID> titles = new HashMap<>();

        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            CompoundTag h = tag.getCompound("Holders"), t = tag.getCompound("Titles");
            for (String k : h.getAllKeys()) d.holders.put(k, h.getUUID(k));
            for (String k : t.getAllKeys()) d.titles.put(k, t.getUUID(k));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            CompoundTag h = new CompoundTag(), t = new CompoundTag();
            holders.forEach(h::putUUID);
            titles.forEach(t::putUUID);
            tag.put("Holders", h);
            tag.put("Titles", t);
            return tag;
        }
    }
}
