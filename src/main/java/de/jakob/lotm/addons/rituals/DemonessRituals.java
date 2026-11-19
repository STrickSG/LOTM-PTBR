package de.jakob.lotm.addons.rituals;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.colonies.Colonists;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.dimension.ModDimensions;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.entity.ModEntities;
import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.entity.custom.MirrorSelfEntity;
import de.jakob.lotm.beyonders.artifacts.SealedArtifactItem;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rituais de avanço da Demônia. Cada um aceita vários jeitos, de qualquer Caminho:
 * <ul>
 *   <li>5, Demônia da Aflição: queimar sem parar por 3 minutos, sem proteção contra o fogo, e beber com a mente firme.</li>
 *   <li>4, Demônia do Desespero: deixar doentes 30 pessoas em até 3 dias (qualquer doença, de qualquer fonte sua).</li>
 *   <li>3, Demônia da Perpetuidade: o seu reflexo no Mundo do Espelho: vencido só por você, encantado, ou um dia em paz.</li>
 *   <li>2, Demônia da Catástrofe: 40 mortes em volta em 10 minutos, com você participando, e beber durante o desastre.</li>
 *   <li>1, Demônia do Apocalipse: fim de uma era (Dragão do Fim, virada de século), um apocalipse em curso ou a queda
 *   de um ser de Sequência 1 ou 0 que você feriu.</li>
 * </ul>
 * O ritual de cada Sequência é feito por quem ainda está na Sequência de cima.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class DemonessRituals {
    private DemonessRituals() {}

    public static final int BURN_SECONDS = 180, BURN_DRINK_WINDOW = 20 * 60;
    public static final float BURN_SANITY = 0.5f;
    public static final int PLAGUE_NEEDED = 30, PLAGUE_WINDOW = 24000 * 3, ACTION_WINDOW = 20 * 3, ATTRIBUTION_RADIUS = 24;
    public static final int MIRROR_PEACE_NEEDED = 24000, MIRROR_PEACE_RADIUS = 16;
    public static final int DISASTER_DEATHS = 40, DISASTER_OWN = 5, DISASTER_RADIUS = 128, DISASTER_WINDOW = 20 * 60 * 10;
    public static final int ERA_WINDOW = 24000, DEITY_WINDOW = 24000, APOCALYPSE_WINDOW = 20 * 60 * 10;

    private static final String BURN = "LotmDemBurnSeconds", BURN_DONE = "LotmDemBurnDoneAt", PLAGUE = "LotmDemPlague",
            LAST_ACTION = "LotmDemLastAction", MIRROR = "LotmDemMirrorDone", MIRROR_SELF = "LotmDemMirrorSelf",
            DEITY_FELL = "LotmDemDeityFellAt";
    private static final int COLOR = 0xC74B8F, BAD = 0xFF7A7A;

    private static final Set<Holder<MobEffect>> DISEASES = Set.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.HUNGER,
            ModEffects.MENTAL_PLAGUE, ModEffects.CORRODED);

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Está na Sequência de onde se faz o ritual da Sequência {@code ritualSequence} da Demônia */
    private static boolean preparing(Player player, int ritualSequence) {
        return BeyonderData.isBeyonder(player) && "demoness".equals(BeyonderData.getPathway(player))
                && BeyonderData.getSequence(player) == ritualSequence + 1;
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    /** Gente: colonos, aldeões, jogadores e Beyonders Errantes */
    private static boolean isPerson(Entity entity) {
        return entity instanceof AbstractEntityCitizen || entity instanceof AbstractVillager || entity instanceof Player
                || entity instanceof BeyonderNPCEntity;
    }

    private static float sanity(Player player) {
        return player.getData(ModAttachments.SANITY_COMPONENT).getSanity();
    }

    // ------------------------------------------------------------------ 5, Demônia da Aflição: queimar sem parar

    public static boolean burnedEnough(ServerPlayer player) {
        CompoundTag tag = data(player);
        boolean burnedOut = tag.getInt(BURN) >= BURN_SECONDS && player.isOnFire();
        boolean justNow = tag.contains(BURN_DONE) && now(player) - tag.getLong(BURN_DONE) <= BURN_DRINK_WINDOW;
        return (burnedOut || justNow) && sanity(player) >= BURN_SANITY;
    }

    /** Fogo de verdade, sem substitutos: nada de Resistência ao Fogo nem imunidade */
    private static boolean truelyBurning(ServerPlayer player) {
        return player.isOnFire() && !player.hasEffect(MobEffects.FIRE_RESISTANCE) && !player.fireImmune() && !player.isCreative();
    }

    private static void tickBurning(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (!truelyBurning(player)) {
            if (tag.getInt(BURN) > 0 && tag.getInt(BURN) < BURN_SECONDS) {
                tell(player, Component.translatable("lotmcraft.demoness.burn_lost").withColor(BAD));
            }
            tag.remove(BURN);
            return;
        }
        int seconds = tag.getInt(BURN) + 1;
        tag.putInt(BURN, seconds);
        if (seconds == BURN_SECONDS) {
            tag.putLong(BURN_DONE, now(player));
            tell(player, Component.translatable("lotmcraft.demoness.burn_done").withColor(COLOR));
        } else if (seconds < BURN_SECONDS && seconds % 15 == 0) {
            tell(player, Component.translatable("lotmcraft.demoness.burn_progress", seconds, BURN_SECONDS).withColor(COLOR));
        }
    }

    // ------------------------------------------------------------------ 4, Demônia do Desespero: uma praga

    public static int infected(ServerPlayer player) {
        return data(player).getCompound(PLAGUE).size();
    }

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        String id = event.getAbility().getId();
        // 1: o poder Apocalipse, de quem for (jogador ou Beyonder Errante), marca um apocalipse em curso por perto
        if (id.equals("apocalypse_ability") && event.getEntity().level() instanceof ServerLevel level) {
            markApocalypse(level, event.getEntity().position(), level.getServer().overworld().getGameTime());
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        data(player).putLong(LAST_ACTION, now(player));
        // 3: qualquer poder de encanto no seu reflexo o deixa apaixonado
        if (preparing(player, 3) && (id.contains("charm") || id.contains("seduc") || id.contains("infatuat"))) {
            MirrorSelfEntity self = reflection(player);
            if (self != null && self.getState() == MirrorSelfEntity.WILD && self.distanceTo(player) <= MIRROR_PEACE_RADIUS) {
                finishMirror(player, self, MirrorSelfEntity.INFATUATED, "lotmcraft.demoness.mirror_infatuated");
            }
        }
    }

    @SubscribeEvent
    public static void onDisease(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !isPerson(target) || !DISEASES.contains(event.getEffectInstance().getEffect())) return;
        ServerPlayer source = null;
        Entity from = event.getEffectSource();
        if (from instanceof ServerPlayer player) source = player;
        else if (from != null && from.getControllingPassenger() instanceof ServerPlayer rider) source = rider;
        if (source == null) {
            // poderes e artefatos nem sempre dizem quem foi: quem usou um poder por perto agora há pouco
            for (Player nearby : target.level().getEntitiesOfClass(Player.class, target.getBoundingBox().inflate(ATTRIBUTION_RADIUS))) {
                if (nearby instanceof ServerPlayer sp && preparing(sp, 4) && now(sp) - data(sp).getLong(LAST_ACTION) <= ACTION_WINDOW) {
                    source = sp;
                    break;
                }
            }
        }
        if (source == null || source == target || !preparing(source, 4)) return;
        CompoundTag plague = data(source).getCompound(PLAGUE);
        long time = now(source);
        // quem adoeceu há mais de 3 dias já não conta
        for (String key : Set.copyOf(plague.getAllKeys())) if (time - plague.getLong(key) > PLAGUE_WINDOW) plague.remove(key);
        UUID id = Colonists.id(target);
        String key = (id != null ? id : target.getUUID()).toString();
        boolean fresh = !plague.contains(key);
        plague.putLong(key, time);
        data(source).put(PLAGUE, plague);
        if (fresh) {
            int count = plague.size();
            tell(source, Component.translatable(count >= PLAGUE_NEEDED ? "lotmcraft.demoness.plague_done" : "lotmcraft.demoness.plague_progress",
                    count, PLAGUE_NEEDED).withColor(COLOR));
        }
    }

    // ------------------------------------------------------------------ 3, Demônia da Perpetuidade: o eu do espelho

    public static boolean mirrorDone(ServerPlayer player) {
        return data(player).getBoolean(MIRROR);
    }

    private static MirrorSelfEntity reflection(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (!tag.hasUUID(MIRROR_SELF)) return null;
        ServerLevel mirror = player.server.getLevel(ModDimensions.MIRROR_WORLD_DIMENSION_KEY);
        if (mirror == null) return null;
        return mirror.getEntity(tag.getUUID(MIRROR_SELF)) instanceof MirrorSelfEntity self && self.isAlive() ? self : null;
    }

    private static void tickMirror(ServerPlayer player) {
        if (!player.level().dimension().equals(ModDimensions.MIRROR_WORLD_DIMENSION_KEY) || mirrorDone(player)) return;
        MirrorSelfEntity self = reflection(player);
        if (self == null) {
            // o seu reflexo aparece diante de você, com a sua força e o que você veste
            self = ModEntities.MIRROR_SELF.get().create(player.serverLevel());
            if (self == null) return;
            Vec3 at = player.position().add(player.getLookAngle().multiply(4, 0, 4));
            self.moveTo(at.x, player.getY(), at.z, player.getYRot() + 180, 0);
            self.setOwner(player.getUUID());
            self.setCustomName(Component.translatable("entity.lotmcraft.mirror_self.of", player.getName()));
            self.setCustomNameVisible(true);
            var maxHealth = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (maxHealth != null) maxHealth.setBaseValue(Math.max(20, player.getMaxHealth()));
            self.setHealth(self.getMaxHealth());
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.MAINHAND) {
                    self.setItemSlot(slot, player.getItemBySlot(slot).copy());
                    self.setDropChance(slot, 0f);
                }
            }
            BeyonderData.setBeyonder(self, "demoness", BeyonderData.getSequence(player));
            player.serverLevel().addFreshEntity(self);
            data(player).putUUID(MIRROR_SELF, self.getUUID());
            tell(player, Component.translatable("lotmcraft.demoness.mirror_found").withColor(COLOR));
            return;
        }
        // reconciliação: um dia em paz ao lado dele, sem nunca atacá-lo
        if (self.getState() == MirrorSelfEntity.WILD && !self.attackedByOwner && self.distanceTo(player) <= MIRROR_PEACE_RADIUS) {
            self.peaceTicks += 20;
            if (self.peaceTicks >= MIRROR_PEACE_NEEDED) finishMirror(player, self, MirrorSelfEntity.RECONCILED, "lotmcraft.demoness.mirror_reconciled");
            else if (self.peaceTicks % 2400 == 0) {
                tell(player, Component.translatable("lotmcraft.demoness.mirror_peace", self.peaceTicks * 100 / MIRROR_PEACE_NEEDED).withColor(COLOR));
            }
        }
    }

    private static void finishMirror(ServerPlayer player, MirrorSelfEntity self, int state, String message) {
        self.setState(state);
        self.setHealth(self.getMaxHealth());
        data(player).putBoolean(MIRROR, true);
        tell(player, Component.translatable(message).withColor(COLOR));
    }

    /** Vencer o reflexo: só com o seu poder. Quem ajuda (ou um Artefato Selado) estraga a vitória */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onReflectionHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof MirrorSelfEntity self) || self.level().isClientSide) return;
        Entity attacker = event.getSource().getEntity();
        UUID owner = self.getOwner();
        if (attacker == null || owner == null) return;
        if (!attacker.getUUID().equals(owner)) {
            self.helped = true;
            return;
        }
        if (!(attacker instanceof ServerPlayer player)) return;
        self.attackedByOwner = true;
        if (player.getMainHandItem().getItem() instanceof SealedArtifactItem || player.getOffhandItem().getItem() instanceof SealedArtifactItem) {
            self.helped = true;
        }
        if (self.getState() != MirrorSelfEntity.WILD || !preparing(player, 3)) return;
        if (event.getAmount() >= self.getHealth() - 1) {
            if (self.helped) {
                tell(player, Component.translatable("lotmcraft.demoness.mirror_helped").withColor(BAD));
                return;
            }
            event.setCanceled(true);
            finishMirror(player, self, MirrorSelfEntity.ENSLAVED, "lotmcraft.demoness.mirror_enslaved");
        }
    }

    // ------------------------------------------------------------------ 2, Demônia da Catástrofe / 1, apocalipse

    private record Death(long time, boolean own) {}

    /** Mortes recentes em volta de cada Demônia que prepara o ritual (só na memória: a janela é de 10 minutos) */
    private static final Map<UUID, Deque<Death>> DEATHS = new HashMap<>();
    /** Onde o poder Apocalipse foi usado por último em cada mundo */
    private static final Map<String, Vec3> APOCALYPSE_AT = new HashMap<>();
    private static final Map<String, Long> APOCALYPSE_TIME = new HashMap<>();
    /** Quem feriu cada ser de Sequência 1 ou 0 (para a queda de uma divindade) */
    private static final Map<UUID, Set<UUID>> DEITY_HURT_BY = new HashMap<>();

    private static void markApocalypse(ServerLevel level, Vec3 pos, long time) {
        String key = level.dimension().location().toString();
        APOCALYPSE_AT.put(key, pos);
        APOCALYPSE_TIME.put(key, time);
    }

    private static Deque<Death> recentDeaths(ServerPlayer player) {
        Deque<Death> deaths = DEATHS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        long time = now(player);
        while (!deaths.isEmpty() && time - deaths.peekFirst().time() > DISASTER_WINDOW) deaths.pollFirst();
        return deaths;
    }

    /** Um desastre em curso: 40 mortes em volta nos últimos 10 minutos, 5 delas suas */
    public static boolean disasterActive(ServerPlayer player) {
        Deque<Death> deaths = recentDeaths(player);
        long own = deaths.stream().filter(Death::own).count();
        return deaths.size() >= DISASTER_DEATHS && own >= DISASTER_OWN;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        Entity killer = event.getSource().getEntity();
        for (ServerPlayer player : level.players()) {
            if (player == dead || !(preparing(player, 2) || preparing(player, 1))) continue;
            if (player.distanceTo(dead) > DISASTER_RADIUS) continue;
            boolean own = killer == player || (killer != null && killer.getUUID().equals(player.getUUID()));
            Deque<Death> deaths = recentDeaths(player);
            deaths.addLast(new Death(now(player), own));
            if (deaths.size() % 10 == 0) {
                tell(player, Component.translatable("lotmcraft.demoness.disaster_progress", deaths.size(), DISASTER_DEATHS).withColor(COLOR));
            }
        }
        // 1: fim de uma era
        if (dead instanceof EnderDragon) EraSavedData.markEraEnd(level.getServer());
        // 1: a queda de uma divindade
        Set<UUID> hurtBy = DEITY_HURT_BY.remove(dead.getUUID());
        if (hurtBy != null) {
            for (UUID id : hurtBy) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null && preparing(player, 1)) {
                    data(player).putLong(DEITY_FELL, now(player));
                    tell(player, Component.translatable("lotmcraft.demoness.deity_fell").withColor(COLOR));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDeityHurt(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !(event.getSource().getEntity() instanceof ServerPlayer player) || !preparing(player, 1)) return;
        if (!BeyonderData.isBeyonder(target) || BeyonderData.getSequence(target) > 1) return;
        DEITY_HURT_BY.computeIfAbsent(target.getUUID(), k -> new HashSet<>()).add(player.getUUID());
    }

    public static boolean apocalypseNow(ServerPlayer player) {
        CompoundTag tag = data(player);
        long time = now(player);
        // a queda de uma divindade que você ajudou a derrubar
        if (tag.contains(DEITY_FELL) && time - tag.getLong(DEITY_FELL) <= DEITY_WINDOW) return true;
        // o fim de uma era: o Dragão do Fim caiu há pouco, ou o mundo vira um século (dia 100, 200...)
        if (EraSavedData.eraEndedWithin(player.getServer(), ERA_WINDOW)) return true;
        long day = player.serverLevel().getDayTime() / 24000;
        if (day > 0 && day % 100 == 0) return true;
        // sinais do apocalipse: um desastre em curso, ou o poder Apocalipse usado por perto
        if (disasterActive(player)) return true;
        String key = player.level().dimension().location().toString();
        Vec3 at = APOCALYPSE_AT.get(key);
        return at != null && time - APOCALYPSE_TIME.getOrDefault(key, 0L) <= APOCALYPSE_WINDOW && at.distanceTo(player.position()) <= DISASTER_RADIUS;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (preparing(player, 5)) tickBurning(player);
        if (preparing(player, 3)) tickMirror(player);
    }

    /** Uma lista simples guardada no mundo: quando o Dragão do Fim caiu pela última vez */
    public static final class EraSavedData extends net.minecraft.world.level.saveddata.SavedData {
        private static final String NAME = "lotmcraft_demoness_era";
        private long endedAt = Long.MIN_VALUE;

        private static EraSavedData get(net.minecraft.server.MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(EraSavedData::new,
                    (tag, provider) -> {
                        EraSavedData data = new EraSavedData();
                        if (tag.contains("EndedAt")) data.endedAt = tag.getLong("EndedAt");
                        return data;
                    }, null), NAME);
        }

        static void markEraEnd(net.minecraft.server.MinecraftServer server) {
            EraSavedData data = get(server);
            data.endedAt = server.overworld().getGameTime();
            data.setDirty();
        }

        static boolean eraEndedWithin(net.minecraft.server.MinecraftServer server, long window) {
            EraSavedData data = get(server);
            return data.endedAt != Long.MIN_VALUE && server.overworld().getGameTime() - data.endedAt <= window;
        }

        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            tag.putLong("EndedAt", endedAt);
            return tag;
        }
    }
}
