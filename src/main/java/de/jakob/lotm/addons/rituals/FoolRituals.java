package de.jakob.lotm.addons.rituals;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.HistoricalVoidComponent;
import de.jakob.lotm.attachments.MarionetteComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.dimension.ModDimensions;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;

/**
 * Rituais de avanço do Caminho do Tolo (wiki da novel):
 * <ul>
 *   <li>5, Marionetista: beber a poção em meio ao canto de sereias (Canção da Sereia a até 20 blocos, 1 minuto).</li>
 *   <li>4, Feiticeiro Bizarro: matar um semideus num grande espetáculo, diante de muitos espectadores, e beber no fim.</li>
 *   <li>3, Erudito de Outrora: ficar separado da realidade e virar história (3 dias seguidos no Vazio Histórico).</li>
 *   <li>2, Invocador de Milagres: trazer de volta ao presente um pedaço da história que ficou para trás.</li>
 *   <li>1, Atendente de Mistérios: uma cidade só de marionetes, cada uma com o seu destino (50 marionetes com nome, 7 dias).</li>
 * </ul>
 * O ritual de cada Sequência é feito por quem ainda está na Sequência de cima; as mensagens só aparecem para ele.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class FoolRituals {
    private FoolRituals() {}

    public static final int SONG_WINDOW = 20 * 60;
    public static final int AUDIENCE_NEEDED = 30, SHOW_WINDOW = 20 * 60;
    public static final int HISTORY_TICKS_NEEDED = 24000 * 7;
    public static final int MARIONETTES_NEEDED = 50, TOWN_RADIUS = 48, TOWN_TICKS_NEEDED = 24000 * 7;
    public static final int FORGOTTEN_AGE = 24000 * 5;

    private static final String SONG = "LotmFoolSirenHeard", SHOW_TIME = "LotmFoolShowTime", SHOW_AUDIENCE = "LotmFoolShowAudience",
            HISTORY = "LotmFoolHistoryTicks", HISTORY_DONE = "LotmFoolBecameHistory", FORGOTTEN = "LotmFoolForgotten",
            MIRACLE_DONE = "LotmFoolMiracle", TOWN = "LotmFoolTownTicks", TOWN_DONE = "LotmFoolTownDone";
    private static final int COLOR = 0xB894FF;

    // ------------------------------------------------------------------ util

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Está na Sequência de onde se faz o ritual da Sequência {@code ritualSequence} do Tolo */
    private static boolean preparing(Player player, int ritualSequence) {
        return BeyonderData.isBeyonder(player) && "fool".equals(BeyonderData.getPathway(player))
                && BeyonderData.getSequence(player) == ritualSequence + 1;
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    // ------------------------------------------------------------------ 5, Marionetista: canto de sereia

    public static boolean heardSong(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (!tag.contains(SONG) || now(player) - tag.getLong(SONG) > SONG_WINDOW) return false;
        // condição escondida: um corpo despedaçado não aguenta (mais da metade da vida)
        if (player.getHealth() <= player.getMaxHealth() / 2) {
            player.displayClientMessage(Component.translatable("lotmcraft.fool.hidden.song").withColor(0xFF7A7A), false);
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        if (!"siren_song_ability".equals(event.getAbility().getId()) || !(event.getEntity().level() instanceof ServerLevel level)) return;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(event.getEntity()) > 20 * 20) continue;
            data(player).putLong(SONG, now(player));
            if (preparing(player, 5)) tell(player, Component.translatable("lotmcraft.fool.song_heard").withColor(COLOR));
        }
    }

    // ------------------------------------------------------------------ 4, Feiticeiro Bizarro: o grande espetáculo

    public static boolean performed(ServerPlayer player) {
        CompoundTag tag = data(player);
        return tag.getInt(SHOW_AUDIENCE) >= AUDIENCE_NEEDED && tag.contains(SHOW_TIME) && now(player) - tag.getLong(SHOW_TIME) <= SHOW_WINDOW;
    }

    /** Plateia em volta: aldeão vale 1, jogador vale 5 e jogador do Visionário vale 15 */
    static int audience(ServerLevel level, LivingEntity stage, Player performer) {
        int points = 0;
        for (LivingEntity watcher : level.getEntitiesOfClass(LivingEntity.class, stage.getBoundingBox().inflate(32))) {
            if (watcher == performer || watcher == stage || !watcher.isAlive()) continue;
            if (de.jakob.lotm.addons.colonies.Colonists.isAnyCitizen(watcher)) points += 1;
            else if (watcher instanceof Player p && !p.isSpectator()) {
                points += BeyonderData.isBeyonder(p) && "visionary".equals(BeyonderData.getPathway(p)) ? 15 : 5;
            }
        }
        return points;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;

        // 4: o golpe final num semideus diante da plateia
        if (event.getSource().getEntity() instanceof ServerPlayer killer && preparing(killer, 4)
                && BeyonderData.isBeyonder(dead) && BeyonderData.getSequence(dead) >= 3 && BeyonderData.getSequence(dead) <= 4) {
            int points = audience(level, dead, killer);
            CompoundTag tag = data(killer);
            tag.putLong(SHOW_TIME, now(killer));
            tag.putInt(SHOW_AUDIENCE, points);
            if (points >= AUDIENCE_NEEDED) {
                level.sendParticles(ParticleTypes.FIREWORK, dead.getX(), dead.getY() + 1.5, dead.getZ(), 60, 1, 1.5, 1, .15);
                level.playSound(null, dead.blockPosition(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1f, 1f);
                tell(killer, Component.translatable("lotmcraft.fool.show_done", points, AUDIENCE_NEEDED).withColor(COLOR));
            } else {
                tell(killer, Component.translatable("lotmcraft.fool.show_small", points, AUDIENCE_NEEDED).withColor(0xFF7A7A));
            }
        }

        // 2: uma criatura marcada no Vazio Histórico deixou de existir no presente
        if (!(dead instanceof Player)) {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                if (!player.hasData(ModAttachments.HISTORICAL_MARKED_ENTITIES_COMPONENT)) continue;
                for (CompoundTag marked : player.getData(ModAttachments.HISTORICAL_MARKED_ENTITIES_COMPONENT).getMarkedEntities()) {
                    CompoundTag nbt = marked.getCompound("EntityNBT");
                    if (nbt.hasUUID("UUID") && nbt.getUUID("UUID").equals(dead.getUUID())) {
                        ListTag list = data(player).getList(FORGOTTEN, Tag.TAG_COMPOUND);
                        if (list.size() >= 32) list.remove(0);
                        CompoundTag entry = new CompoundTag();
                        entry.putUUID("Id", dead.getUUID());
                        entry.putLong("Died", now(player));
                        list.add(entry);
                        data(player).put(FORGOTTEN, list);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 3, Erudito de Outrora: virar história

    public static boolean becameHistory(ServerPlayer player) {
        return data(player).getBoolean(HISTORY_DONE);
    }

    // ------------------------------------------------------------------ 1, Atendente de Mistérios: cidade de marionetes

    public static boolean townDone(ServerPlayer player) {
        return data(player).getBoolean(TOWN_DONE);
    }

    /** Marionetes do jogador, com nome (o destino de cada uma), a até 48 blocos dele */
    static int namedMarionettes(ServerPlayer player) {
        return townNames(player)[0];
    }

    /** [marionetes com nome único, marionetes com nome repetido] (condição escondida: cada uma com um destino próprio) */
    static int[] townNames(ServerPlayer player) {
        String owner = player.getStringUUID();
        // marionetes soltas: a até 48 blocos de você ou dentro da sua colônia (a Cidade das Marionetes)
        java.util.Map<java.util.UUID, String> town = new java.util.HashMap<>();
        com.minecolonies.api.colony.IColony colony = de.jakob.lotm.addons.colonies.Colonists.ownedBy(player.getUUID());
        java.util.List<Entity> candidates = new java.util.ArrayList<>(player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(TOWN_RADIUS)));
        if (colony != null && colony.getWorld() instanceof ServerLevel colonyLevel) {
            for (Entity e : colonyLevel.getAllEntities()) {
                if (e instanceof LivingEntity && colony.isCoordInColony(colonyLevel, e.blockPosition())) candidates.add(e);
            }
        }
        for (Entity e : candidates) {
            // marionetes-colono são contadas pelos dados da colônia logo abaixo (mesmo com o chunk descarregado)
            if (!(e instanceof LivingEntity living) || de.jakob.lotm.addons.colonies.Colonists.isAnyCitizen(living)
                    || !living.hasCustomName() || !living.hasData(ModAttachments.MARIONETTE_COMPONENT)) continue;
            MarionetteComponent m = living.getData(ModAttachments.MARIONETTE_COMPONENT);
            if (m.isMarionette() && owner.equals(m.getControllerUUID())) town.put(living.getUUID(), living.getCustomName().getString());
        }
        java.util.Map<String, Integer> names = new java.util.HashMap<>();
        for (String name : town.values()) names.merge(name.strip().toLowerCase(), 1, Integer::sum);
        // marionetes que viraram colonos da sua colônia
        for (String name : de.jakob.lotm.addons.colonies.ColonyRituals.marionetteColonists(player)) names.merge(name.strip().toLowerCase(), 1, Integer::sum);
        int unique = 0, repeated = 0;
        for (int n : names.values()) { if (n == 1) unique++; else repeated += n; }
        return new int[]{unique, repeated};
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        CompoundTag tag = data(player);

        // 3: cada segundo no Vazio Histórico conta; sair zera
        if (!tag.getBoolean(HISTORY_DONE)) {
            if (player.level().dimension().equals(ModDimensions.HISTORICAL_VOID_DIMENSION_KEY)) {
                int ticks = tag.getInt(HISTORY) + 20;
                tag.putInt(HISTORY, ticks);
                if (ticks >= HISTORY_TICKS_NEEDED) {
                    tag.putBoolean(HISTORY_DONE, true);
                    player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 120, 1, 1.5, 1, .5);
                    if (preparing(player, 3)) tell(player, Component.translatable("lotmcraft.fool.history_done").withColor(COLOR));
                } else if (preparing(player, 3) && ticks % 1200 == 0) {
                    tell(player, Component.translatable("lotmcraft.fool.history_progress", ticks * 100 / HISTORY_TICKS_NEEDED).withColor(COLOR));
                }
            } else if (tag.getInt(HISTORY) > 0) {
                tag.putInt(HISTORY, 0);
            }
        }

        // 1: a cidade de marionetes tem de durar um dia inteiro
        if (!tag.getBoolean(TOWN_DONE) && preparing(player, 1) && player.tickCount % 100 == 0) {
            int[] town = townNames(player);
            int count = town[0];
            if (town[1] > 0 && player.tickCount % 1200 == 0) {
                tell(player, Component.translatable("lotmcraft.fool.hidden.town").withColor(0xFF7A7A));
            }
            int ticks = tag.getInt(TOWN);
            if (count >= MARIONETTES_NEEDED) {
                ticks += 100;
                tag.putInt(TOWN, ticks);
                if (ticks >= TOWN_TICKS_NEEDED) {
                    tag.putBoolean(TOWN_DONE, true);
                    tell(player, Component.translatable("lotmcraft.fool.town_done").withColor(COLOR));
                } else if (ticks % 1200 == 0) {
                    tell(player, Component.translatable("lotmcraft.fool.town_progress", count, MARIONETTES_NEEDED, ticks * 100 / TOWN_TICKS_NEEDED).withColor(COLOR));
                }
            } else if (ticks > 0 && count > 0) {
                // a cidade se desfez (marionetes a menos): recomeça
                tag.putInt(TOWN, 0);
                tell(player, Component.translatable("lotmcraft.fool.town_broken", count, MARIONETTES_NEEDED).withColor(0xFF7A7A));
            }
        }
    }

    // condição escondida: quem fala com o presente ainda pertence a ele
    @SubscribeEvent
    public static void onChat(net.neoforged.neoforge.event.ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        CompoundTag tag = data(player);
        if (tag.getBoolean(HISTORY_DONE) || tag.getInt(HISTORY) <= 0) return;
        if (!player.level().dimension().equals(ModDimensions.HISTORICAL_VOID_DIMENSION_KEY)) return;
        tag.putInt(HISTORY, 0);
        if (preparing(player, 3)) tell(player, Component.translatable("lotmcraft.fool.hidden.history").withColor(0xFF7A7A));
    }

    // sair do jogo ou morrer tira da névoa da história: a contagem do Erudito de Outrora zera
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CompoundTag tag = data(event.getEntity());
        if (!tag.getBoolean(HISTORY_DONE)) tag.putInt(HISTORY, 0);
    }

    // ------------------------------------------------------------------ 2, Invocador de Milagres: a história volta ao presente

    public static boolean miracleDone(ServerPlayer player) {
        return data(player).getBoolean(MIRACLE_DONE);
    }

    /** Outra forma do milagre: reativar uma colônia abandonada traz a história dela de volta ao presente */
    public static void restoredColony(ServerPlayer player, String colonyName) {
        if (!preparing(player, 2) || miracleDone(player)) return;
        data(player).putBoolean(MIRACLE_DONE, true);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 120, 3, 2, 3, .08);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.2f);
        tell(player, Component.translatable("lotmcraft.fool.miracle_colony", colonyName).withColor(COLOR));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = entity.getPersistentData();
        if (!tag.getBoolean("VoidSummoned") || !tag.hasUUID("VoidOriginalUUID") || !tag.hasUUID("VoidSummonOwner")) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tag.getUUID("VoidSummonOwner"));
        if (owner == null || !preparing(owner, 2)) return;
        UUID original = tag.getUUID("VoidOriginalUUID");
        long died = Long.MIN_VALUE;
        for (Tag t : data(owner).getList(FORGOTTEN, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            if (entry.hasUUID("Id") && entry.getUUID("Id").equals(original)) died = entry.getLong("Died");
        }
        if (died == Long.MIN_VALUE) return;
        // condição escondida: tem que ter ficado para trás de verdade (morta há pelo menos 5 dias)
        if (now(owner) - died < FORGOTTEN_AGE) {
            tell(owner, Component.translatable("lotmcraft.fool.hidden.miracle").withColor(0xFF7A7A));
            return;
        }
        // depois que a invocação terminar de registrar o tempo e o custo, ela vira parte do presente de vez
        ServerScheduler.scheduleDelayed(2, () -> {
            if (!entity.isAlive()) return;
            long summonTime = tag.getLong("VoidSummonTime");
            int cost = Math.max(1, tag.getInt("VoidSlotCost"));
            tag.remove("VoidSummoned");
            tag.remove("VoidSummonTime");
            tag.remove("VoidDrainPerSecond");
            tag.remove("VoidSlotCost");
            HistoricalVoidComponent voidData = owner.getData(ModAttachments.HISTORICAL_VOID_COMPONENT.get());
            voidData.summonedCount = Math.max(0, voidData.summonedCount - cost);
            voidData.activeSummonTimes.remove(summonTime);
            data(owner).putBoolean(MIRACLE_DONE, true);
            level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + 1, entity.getZ(), 80, .6, 1, .6, .1);
            level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.2f);
            tell(owner, Component.translatable("lotmcraft.fool.miracle_done", entity.getName()).withColor(COLOR));
        }, level);
    }
}
