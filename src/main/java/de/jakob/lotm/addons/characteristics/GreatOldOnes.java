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
 *   <li>Ritual de Ascensão: com tudo reunido, o Deus começa a Ascensão dentro da própria Sefirah (pela Introspecção).
 *   Dura 5 minutos, o servidor inteiro fica sabendo e a corrupção sobe; se morrer, sair da Sefirah, perder um requisito
 *   ou perder o controle, falha e as Unicidades dos vizinhos voltam ao mundo. Ao fim, ganha o nome do grupo
 *   (Senhor dos Mistérios, Escuridão Eterna...).</li>
 *   <li>Só um Deus (Seq 0) pega a Unicidade de um caminho vizinho; ela não muda o caminho, mas pesa na corrupção.</li>
 *   <li>Grande Antigo: o Deus que abriga a Sefirah do grupo, as Unicidades dos vizinhos e pelo menos uma característica
 *   de Sequência 1 de cada vizinho. Só um por grupo. Quem trocou de caminho não consegue.</li>
 *   <li>Ao morrer, as Unicidades dos vizinhos se soltam e voltam ao mundo; o título acaba.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class GreatOldOnes {
    private GreatOldOnes() {}

    private static final String KEY = "LotmNeighborUniq", TITLE = "LotmGreatOldOne", ASCENSION = "LotmAscension", READY = "LotmAscensionReady";
    /** A Ascensão dura 5 minutos */
    public static final int ASCENSION_TICKS = 20 * 60 * 5;
    /** Quanto a corrupção sobe por segundo durante a Ascensão */
    private static final float ASCENSION_CORRUPTION = 0.001f;

    /** Caminho -> o Grande Antigo do grupo (a chave do nome) */
    private static final Map<String, String> GROUP_NAMES = Map.ofEntries(
            Map.entry("fool", "lord_of_mysteries"), Map.entry("error", "lord_of_mysteries"), Map.entry("door", "lord_of_mysteries"),
            Map.entry("visionary", "god_almighty"), Map.entry("sun", "god_almighty"), Map.entry("tyrant", "god_almighty"),
            Map.entry("white_tower", "god_almighty"), Map.entry("hanged_man", "god_almighty"),
            Map.entry("darkness", "eternal_darkness"), Map.entry("death", "eternal_darkness"), Map.entry("twilight_giant", "eternal_darkness"),
            Map.entry("red_priest", "calamity_of_destruction"), Map.entry("demoness", "calamity_of_destruction"),
            Map.entry("hermit", "demon_of_knowledge"), Map.entry("paragon", "demon_of_knowledge"),
            Map.entry("mother", "goddess_of_origin"), Map.entry("moon", "goddess_of_origin"),
            Map.entry("abyss", "father_of_devils"), Map.entry("chained", "father_of_devils"),
            Map.entry("wheel_of_fortune", "key_of_light"));

    /** O nome do Grande Antigo do grupo deste caminho (ex.: Senhor dos Mistérios) */
    public static Component titleOf(String pathway) {
        String id = GROUP_NAMES.get(pathway);
        return id == null ? Component.translatable("lotm.characteristics.great_old_one") : Component.translatable("lotmcraft.great_old_one.name." + id);
    }
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

    /** Confere o título (some se faltar um requisito) e se a Ascensão está liberada (para o botão da Introspecção) */
    public static void check(ServerPlayer player) {
        String missing = missing(player);
        boolean changed = false;
        if (isGreatOldOne(player) && missing != null) {
            data(player.server).titles.values().remove(player.getUUID());
            data(player.server).setDirty();
            setTitle(player, false);
            player.sendSystemMessage(Component.translatable("lotmcraft.great_old_one.lost", titleOf(BeyonderData.getPathway(player))).withColor(0xB0306A));
            changed = true;
        }
        boolean ready = missing == null && !isGreatOldOne(player);
        CompoundTag persisted = persisted(player);
        if (persisted.getBoolean(READY) != ready) {
            if (ready) persisted.putBoolean(READY, true); else persisted.remove(READY);
            save(player, persisted);
            changed = true;
        }
        if (changed) NeighborCharacteristics.sync(player);
    }

    public static boolean isReady(Player p) { return persisted(p).getBoolean(READY); }

    // ------------------------------------------------------------------ o Ritual de Ascensão

    public static int ascensionTicks(Player p) { return persisted(p).getInt(ASCENSION); }

    private static void setAscension(Player p, int ticks) {
        CompoundTag persisted = persisted(p);
        if (ticks > 0) persisted.putInt(ASCENSION, ticks); else persisted.remove(ASCENSION);
        save(p, persisted);
    }

    /** O Deus pede a Ascensão (botão da Introspecção) */
    public static void requestAscension(ServerPlayer player) {
        if (isGreatOldOne(player) || ascensionTicks(player) > 0) return;
        String missing = missing(player);
        if (missing != null) {
            player.displayClientMessage(Component.translatable("lotmcraft.great_old_one.not_ready"), true);
            return;
        }
        String sefirah = SefirahHandler.getClaimedSefirot(player);
        if (!SefirahHandler.isInSefirot(player, sefirah)) {
            player.displayClientMessage(Component.translatable("lotmcraft.great_old_one.inside_sefirah"), true);
            return;
        }
        setAscension(player, ASCENSION_TICKS);
        Component title = titleOf(BeyonderData.getPathway(player));
        int color = BeyonderData.pathwayInfos.containsKey(BeyonderData.getPathway(player)) ? BeyonderData.pathwayInfos.get(BeyonderData.getPathway(player)).color() : COLOR;
        for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.translatable("lotmcraft.great_old_one.ascension_start", player.getName(), title).withColor(color));
            p.playNotifySound(SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.MASTER, 0.8f, 0.5f);
        }
        player.closeContainer();
    }

    private static void ascensionTick(ServerPlayer player) {
        int left = ascensionTicks(player);
        if (left <= 0) return;
        ServerLevel level = player.serverLevel();
        String pathway = BeyonderData.getPathway(player);
        // de pé no ar, sem habilidades, envolto na cor do caminho
        player.setDeltaMovement(new net.minecraft.world.phys.Vec3(0, 0.02, 0));
        player.hurtMarked = true;
        player.getData(de.jakob.lotm.attachments.ModAttachments.DISABLED_ABILITIES_COMPONENT).disableAbilityUsageForTime("ascension", 20, player);
        int c = BeyonderData.pathwayInfos.containsKey(pathway) ? BeyonderData.pathwayInfos.get(pathway).color() : COLOR;
        var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), 2.5f);
        de.jakob.lotm.util.helper.ParticleUtil.spawnSphereParticles(level, dust, player.position().add(0, player.getBbHeight() / 2, 0), 2.0, 70);
        if (left % 120 == 0)
            de.jakob.lotm.rendering.effectRendering.EffectManager.playMovableEffect(de.jakob.lotm.rendering.effectRendering.EffectIds.BEAMS_OF_LIGHT, level, player,
                    de.jakob.lotm.rendering.effectRendering.EffectParams.ofDuration(120));

        if (left % 20 == 0) {
            // a cada segundo: confere os requisitos e o lugar, e a corrupção sobe
            String fail = missing(player);
            if (fail == null && !SefirahHandler.isInSefirot(player, SefirahHandler.getClaimedSefirot(player))) fail = "left_sefirah";
            float corruption = de.jakob.lotm.addons.corruption.Corruption.get(player) + ASCENSION_CORRUPTION;
            de.jakob.lotm.addons.corruption.Corruption.set(player, Math.min(1f, corruption));
            if (fail == null && corruption >= 1f) fail = "lost_control";
            if (fail != null) {
                failAscension(player, fail);
                return;
            }
        }

        setAscension(player, left - 1);
        if (left - 1 == 0) {
            setTitle(player, true);
            data(player.server).titles.put(groupKey(pathway), player.getUUID());
            data(player.server).setDirty();
            check(player);
            NeighborCharacteristics.sync(player);
            Component title = titleOf(pathway);
            for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.translatable("lotmcraft.great_old_one.ascended", player.getName(), title).withColor(c));
                p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, net.minecraft.sounds.SoundSource.MASTER, 1f, 0.6f);
            }
        }
    }

    /** A Ascensão falhou: as Unicidades dos vizinhos se soltam e voltam ao mundo */
    private static void failAscension(ServerPlayer player, String reason) {
        setAscension(player, 0);
        LOTMCraft.LOGGER.info("Ascensão de {} falhou: {}", player.getName().getString(), reason);
        releaseUniquenesses(player);
        Component title = titleOf(BeyonderData.getPathway(player));
        for (ServerPlayer p : player.server.getPlayerList().getPlayers())
            p.sendSystemMessage(Component.translatable("lotmcraft.great_old_one.ascension_failed", player.getName(), title).withColor(0xB0306A));
        if ("lost_control".equals(reason)) de.jakob.lotm.addons.corruption.LostToCorruption.lose(player);
        check(player);
    }

    /** Na falha da Ascensão, as Unicidades dos vizinhos se soltam e fogem para longe, cada uma para um lado (300 a 800 blocos) */
    private static void releaseUniquenesses(ServerPlayer player) {
        Set<String> held = uniquenesses(player);
        if (held.isEmpty()) return;
        Data data = data(player.server);
        for (String pathway : held) data.holders.remove(pathway, player.getUUID());
        data.setDirty();
        setUniquenesses(player, Set.of());
        ServerLevel overworld = player.server.overworld();
        var from = player.level() == overworld ? player.position() : net.minecraft.world.phys.Vec3.atBottomCenterOf(overworld.getSharedSpawnPos());
        var random = player.getRandom();
        for (String pathway : held) {
            double angle = random.nextDouble() * Math.PI * 2, dist = 300 + random.nextDouble() * 500;
            int x = (int) (from.x + Math.cos(angle) * dist), z = (int) (from.z + Math.sin(angle) * dist);
            overworld.getChunk(x >> 4, z >> 4);   // carrega o lugar para ela aparecer
            int y = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            UniquenessEntity.trySpawn(overworld, new net.minecraft.world.phys.Vec3(x + 0.5, y + 1, z + 0.5), pathway);
            LOTMCraft.LOGGER.info("Unicidade {} fugiu para {} {} {}", pathway, x, y + 1, z);
        }
        de.jakob.lotm.addons.corruption.Corruption.set(player, de.jakob.lotm.addons.corruption.Corruption.get(player));
        NeighborCharacteristics.sync(player);
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        // sair do jogo interrompe a Ascensão sem castigo (como a Apoteose)
        if (event.getEntity() instanceof ServerPlayer player && ascensionTicks(player) > 0) setAscension(player, 0);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ascensionTicks(player) > 0) ascensionTick(player);
        if (player.tickCount % 40 != 7) return;
        if (BeyonderData.getSequence(player) != 0 && !isGreatOldOne(player) && uniquenesses(player).isEmpty()) return;
        check(player);
    }

    /** Morto: as Unicidades dos vizinhos se soltam e voltam ao mundo, a Ascensão falha e o título acaba */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        Set<String> held = uniquenesses(player);
        boolean ascending = ascensionTicks(player) > 0;
        if (held.isEmpty() && !isGreatOldOne(player) && !ascending) return;
        if (ascending) {
            setAscension(player, 0);
            Component title = titleOf(BeyonderData.getPathway(player));
            for (ServerPlayer p : player.server.getPlayerList().getPlayers())
                p.sendSystemMessage(Component.translatable("lotmcraft.great_old_one.ascension_failed", player.getName(), title).withColor(0xB0306A));
        }
        Data data = data(player.server);
        if (isGreatOldOne(player)) {
            setTitle(player, false);
            data.titles.values().remove(player.getUUID());
            data.setDirty();
        }
        // morreu no Mundo Normal: as Unicidades caem ali mesmo
        for (String pathway : held) data.holders.remove(pathway, player.getUUID());
        data.setDirty();
        setUniquenesses(player, Set.of());
        ServerLevel overworld = player.server.overworld();
        var at = level == overworld ? player.position() : net.minecraft.world.phys.Vec3.atBottomCenterOf(overworld.getSharedSpawnPos());
        for (String pathway : held) UniquenessEntity.trySpawn(overworld, at, pathway);
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
