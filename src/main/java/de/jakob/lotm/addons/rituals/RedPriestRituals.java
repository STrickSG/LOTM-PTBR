package de.jakob.lotm.addons.rituals;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.colonies.ColonyConquest;
import de.jakob.lotm.addons.colonies.Colonists;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AllyUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Rituais de avanço do Sacerdote Vermelho (Caçador). Cada um aceita vários jeitos, de qualquer Caminho:
 * <ul>
 *   <li>5, Ceifador: beber diante de um alvo mais forte que você, capturado (preso de qualquer forma) e vivo.</li>
 *   <li>4, Cavaleiro de Sangue de Ferro: 30 companheiros fortes ao seu lado: guardas da sua colônia, aliados ou subordinados.</li>
 *   <li>3, Bispo da Guerra: vencer uma guerra contra uma força poderosa: invasões do MineColonies, invasões de aldeia...</li>
 *   <li>2, Bruxo do Clima: mudar o clima por conta própria (poder, Artefato Selado ou item), sem mais ninguém por perto.</li>
 *   <li>1, Conquistador: conquistar colônias que somem 100 colonos ou mais (ver {@link ColonyConquest}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class RedPriestRituals {
    private RedPriestRituals() {}

    public static final int CAPTURE_RADIUS = 8;
    public static final int TEAM_NEEDED = 30, TEAM_RADIUS = 64, STRONG_ARMOR = 15;
    public static final double FRIENDLY_HAPPINESS = 7.0;
    public static final int WAR_KILLS = 20, WAR_RADIUS = 96, WAR_LULL = 20 * 60 * 10, WAR_DRINK_WINDOW = 24000;
    public static final int WEATHER_ACTION_WINDOW = 20 * 10, WEATHER_DRINK_WINDOW = 20 * 60, WEATHER_ALONE_RADIUS = 64;

    private static final String WAR_COUNT = "LotmRedWarKills", WAR_LAST = "LotmRedWarLast", WAR_WON = "LotmRedWarWon",
            LAST_ACTION = "LotmRedLastAction", WEATHER_AT = "LotmRedWeatherAt";
    private static final int COLOR = 0xE0533A, BAD = 0xFF7A7A;

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Está na Sequência de onde se faz o ritual da Sequência {@code ritualSequence} do Sacerdote Vermelho */
    public static boolean preparing(Player player, int ritualSequence) {
        return BeyonderData.isBeyonder(player) && "red_priest".equals(BeyonderData.getPathway(player))
                && BeyonderData.getSequence(player) == ritualSequence + 1;
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    // ------------------------------------------------------------------ 5, Ceifador

    /** Preso de qualquer forma: sem conseguir andar, petrificado, conquistado, adormecido por um poder... */
    public static boolean held(LivingEntity entity) {
        if (entity.hasEffect(ModEffects.PETRIFICATION) || entity.hasEffect(ModEffects.CONQUERED) || entity.hasEffect(ModEffects.ASLEEP)) return true;
        MobEffectInstance slow = entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow != null && slow.getAmplifier() >= 3) return true;
        var speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getValue() <= 0.001;
    }

    /** Um alvo mais forte que você (Sequência menor), vivo e capturado, a até 8 blocos: ele assiste, com medo */
    public static boolean captured(ServerPlayer player) {
        int mine = BeyonderData.getSequence(player);
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CAPTURE_RADIUS))) {
            if (entity == player || !entity.isAlive() || !BeyonderData.isBeyonder(entity) || BeyonderData.getSequence(entity) >= mine) continue;
            if (!held(entity)) continue;
            player.serverLevel().sendParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() + 1.2, entity.getZ(), 25, .3, .5, .3, .02);
            entity.playSound(SoundEvents.WITCH_CELEBRATE, .6f, .5f);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 4, Cavaleiro de Sangue de Ferro

    private static boolean isTeammate(ServerPlayer player, LivingEntity entity) {
        if (entity instanceof EntityCitizen citizen && citizen.getCitizenData() != null) {
            ICitizenData data = citizen.getCitizenData();
            IColony colony = data.getColony();
            boolean mine = colony != null && player.getUUID().equals(colony.getPermissions().getOwner());
            boolean guard = data.getJob() instanceof AbstractJobGuard<?>;
            return (mine && guard || AllyUtil.areAllies(player, entity))
                    && data.getCitizenHappinessHandler().getHappiness(colony, data) >= FRIENDLY_HAPPINESS;
        }
        if (AllyUtil.areAllies(player, entity)) return true;
        var subordinate = entity.getData(de.jakob.lotm.attachments.ModAttachments.SUBORDINATE_COMPONENT);
        return subordinate.isSubordinate() && player.getStringUUID().equals(subordinate.getControllerUUID());
    }

    /** Forte: armadura de ferro completa ou melhor, ou um Beyonder */
    private static boolean strong(LivingEntity entity) {
        return entity.getArmorValue() >= STRONG_ARMOR || BeyonderData.isBeyonder(entity);
    }

    public static int strongTeam(ServerPlayer player) {
        int count = 0;
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(TEAM_RADIUS))) {
            if (entity != player && entity.isAlive() && isTeammate(player, entity) && strong(entity)) count++;
        }
        return count;
    }

    public static boolean teamFormed(ServerPlayer player) {
        return strongTeam(player) >= TEAM_NEEDED;
    }

    // ------------------------------------------------------------------ 3, Bispo da Guerra

    private static boolean isInvader(Entity entity) {
        return entity instanceof AbstractEntityMinecoloniesRaider || entity instanceof Raider;
    }

    // cada invasor que cai perto de você conta para a guerra, venha a morte de você, dos seus guardas ou dos seus aliados
    @SubscribeEvent
    public static void onInvaderDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!isInvader(dead) || !(dead.level() instanceof ServerLevel level)) return;
        for (ServerPlayer player : level.players()) {
            if (!preparing(player, 3) || player.distanceToSqr(dead) > WAR_RADIUS * WAR_RADIUS) continue;
            CompoundTag tag = data(player);
            long time = now(player);
            int kills = time - tag.getLong(WAR_LAST) > WAR_LULL ? 0 : tag.getInt(WAR_COUNT);
            tag.putInt(WAR_COUNT, kills + 1);
            tag.putLong(WAR_LAST, time);
            if ((kills + 1) % 5 == 0) tell(player, Component.translatable("lotmcraft.red_priest.war_progress", kills + 1).withColor(COLOR));
        }
    }

    public static boolean warWon(ServerPlayer player) {
        CompoundTag tag = data(player);
        return tag.contains(WAR_WON) && now(player) - tag.getLong(WAR_WON) <= WAR_DRINK_WINDOW;
    }

    // ------------------------------------------------------------------ 2, Bruxo do Clima

    // o que você fez por conta própria: usar um poder (do Caminho, de outro ou de um Artefato Selado) ou um item
    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && preparing(player, 2)) data(player).putLong(LAST_ACTION, now(player));
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && preparing(player, 2)) data(player).putLong(LAST_ACTION, now(player));
    }

    private static long weatherCommandTick = Long.MIN_VALUE;

    // o comando /weather não é você mudando o céu
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString();
        if (input.startsWith("/")) input = input.substring(1);
        if (input.startsWith("weather")) weatherCommandTick = event.getParseResults().getContext().getSource().getServer().overworld().getGameTime();
    }

    /** Chamado pelo mixin quando algo muda o clima (não a troca natural, que não passa por ali) */
    public static void weatherChanged(ServerLevel level) {
        long time = level.getServer().overworld().getGameTime();
        if (time - weatherCommandTick <= 1) return;
        for (ServerPlayer player : level.players()) {
            if (!preparing(player, 2)) continue;
            CompoundTag tag = data(player);
            if (!tag.contains(LAST_ACTION) || time - tag.getLong(LAST_ACTION) > WEATHER_ACTION_WINDOW) continue;
            // sem ajuda externa: nenhum outro jogador por perto
            boolean alone = level.players().stream().noneMatch(other -> other != player && !other.isSpectator()
                    && other.distanceToSqr(player) <= WEATHER_ALONE_RADIUS * WEATHER_ALONE_RADIUS);
            if (!alone) continue;
            tag.putLong(WEATHER_AT, time);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 2, player.getZ(), 60, 1.5, 1.5, 1.5, .2);
            level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, .8f, 1.3f);
            tell(player, Component.translatable("lotmcraft.red_priest.weather_obeys").withColor(COLOR));
        }
    }

    public static boolean weatherBent(ServerPlayer player) {
        CompoundTag tag = data(player);
        return tag.contains(WEATHER_AT) && now(player) - tag.getLong(WEATHER_AT) <= WEATHER_DRINK_WINDOW;
    }

    // ------------------------------------------------------------------ guerra vencida

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0 || !preparing(player, 3)) return;
        CompoundTag tag = data(player);
        if (tag.getInt(WAR_COUNT) < WAR_KILLS) return;
        // a guerra acabou quando não sobra nenhum invasor por perto: vitória
        boolean enemiesLeft = !player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WAR_RADIUS),
                e -> e.isAlive() && isInvader(e)).isEmpty();
        if (enemiesLeft) return;
        tag.putLong(WAR_WON, now(player));
        tag.putInt(WAR_COUNT, 0);
        player.serverLevel().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 80, 1, 1.5, 1, .05);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1f, 1.2f);
        tell(player, Component.translatable("lotmcraft.red_priest.war_won").withColor(COLOR));
    }

    // ------------------------------------------------------------------ 1, Conquistador

    public static boolean nationConquered(ServerPlayer player) {
        return ColonyConquest.conqueredPopulation(player) >= ColonyConquest.NATION_NEEDED;
    }
}
