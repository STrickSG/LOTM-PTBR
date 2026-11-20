package de.jakob.lotm.beyonders.abilities.common;

import de.jakob.lotm.attachments.AllyComponent;
import de.jakob.lotm.beyonders.abilities.abyss.MaliceSeed.MaliceSeedAbility;
import de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbilites.Phantom.PhantomDetectionUtil;
import de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbilites.Shapeshifter.SpiritDisguiseUtil;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.beyonders.abilities.darkness.RequiemAbility;
import de.jakob.lotm.beyonders.abilities.visionary.handlers.VisionaryHandler;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.visionary.handlers.VisionaryLoosingControlHandler;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.PlayPhotonEntityEffectPacket;
import de.jakob.lotm.network.packets.toClient.SyncSpiritVisionAbilityPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.mixin.EntityAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Vector3f;

import java.util.*;

public class SpiritVisionAbility extends ToggleAbility {
    /** Alcance por sequência (índice = sequência): o Vidente começa enxergando de perto */
    private static final int[] RANGE = {64, 60, 54, 48, 40, 32, 24, 20, 14, 10};

    /** Cresce com a sequência e um pouco com a digestão */
    public static int range(ServerPlayer player) {
        int seq = Math.clamp(BeyonderData.getSequence(player), 0, 9);
        float digestion = Math.clamp(BeyonderData.getDigestionProgress(player), 0f, 1f);
        return Math.round(RANGE[seq] * (0.85f + 0.15f * digestion));
    }

    /** A cor do contorno que cada ser tem agora, para quem está olhando */
    private final Map<UUID, Map<Entity, PlayerTeam>> outlined = new HashMap<>();
    private static final String MALICE_TEAM = "lotm_malice_black";
    private static final PlayerTeam MALICE_TEAM_OBJ = new PlayerTeam(new Scoreboard(), MALICE_TEAM);
    // Aura branca = pacífico, vermelha = agressivo, amarela = neutro
    private static final PlayerTeam PEACEFUL_TEAM = new PlayerTeam(new Scoreboard(), "lotm_sv_peaceful");
    private static final PlayerTeam HOSTILE_TEAM = new PlayerTeam(new Scoreboard(), "lotm_sv_hostile");
    private static final PlayerTeam NEUTRAL_TEAM = new PlayerTeam(new Scoreboard(), "lotm_sv_neutral");
    private static final PlayerTeam[] ALL_TEAMS = {MALICE_TEAM_OBJ, PEACEFUL_TEAM, HOSTILE_TEAM, NEUTRAL_TEAM};
    static {
        MALICE_TEAM_OBJ.setColor(ChatFormatting.BLACK);
        PEACEFUL_TEAM.setColor(ChatFormatting.WHITE);
        HOSTILE_TEAM.setColor(ChatFormatting.RED);
        NEUTRAL_TEAM.setColor(ChatFormatting.YELLOW);
    }
    public SpiritVisionAbility(String id) {
        super(id);

        canBeCopied = false;
        canBeUsedByNPC = false;
        doesNotIncreaseDigestion = true;
        cannotBeStolen = true;
        canBeReplicated = false;
        canBeUsedInArtifact = false;
        autoClear = false;
        tickRate = 1;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        Map<String, Integer> reqs = new HashMap(
                Map.of(
                        "fool", 9,
                        "door", 7,
                        "hermit", 9,
                        "demoness", 7,
                        "mother", 8,
                        "wheel_of_fortune", 9,
                        "darkness", 7,
                        "abyss", 9,
                        "red_priest", 8
                ));

        for (String pathway : BeyonderData.pathways) {
            if (!reqs.containsKey(pathway))
                reqs.put(pathway, 5);
        }
        return reqs;
    }
    public void forceStop(ServerLevel level, LivingEntity entity) {
        if (isActiveForEntity(entity)) cancel(level, entity);
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (!level.isClientSide && RequiemAbility.blocksSpiritVision(entity)) {
            forceStop((ServerLevel) level, entity);
            return;
        }
        super.onAbilityUse(level, entity);
    }
    @Override
    public void start(Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            if (entity instanceof ServerPlayer player) {
                PacketHandler.sendToPlayer(player, new SyncSpiritVisionAbilityPacket(true, -1));
                AllyUtil.syncAllyData(player);
            }
            return;
        }

        entity.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1, 1);
    }

    private final DustParticleOptions dust = new DustParticleOptions(new Vector3f(255, 255, 255), 2f);

    private final HashMap<UUID, Set<Entity>> glowingEntities = new HashMap<>();

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (level.isClientSide()) {
            return;
        }

        if (!(entity instanceof ServerPlayer player))
            return;

        int range = range(player);
        LivingEntity lookedAt = AbilityUtil.getTargetEntity(entity, range, 1.2f, false, true);

        if (lookedAt != null) {
            if (VisionaryHandler.shouldStayInvisible(BeyonderData.getSequence(entity), lookedAt)|| PhantomDetectionUtil.isHiddenFromSpiritVision(entity, lookedAt))
                return;
        }

        PacketHandler.sendToPlayer(player, new PlayPhotonEntityEffectPacket("spirit_vision", player.getId(), 0, 0, 0, 1, null, true, true, UUID.randomUUID(), true));

        PacketHandler.sendToPlayer(player, new SyncSpiritVisionAbilityPacket(true, lookedAt == null ? -1 : lookedAt.getId()));
        de.jakob.lotm.network.packets.toClient.VisionTargetPacket.send(player, lookedAt);
        AllyComponent allyComponent = entity.getData(ModAttachments.ALLY_COMPONENT);

        if (lookedAt != null) {
            if (!allyComponent.isAlly(lookedAt.getUUID())) {
                if (shouldLooseControl(entity, lookedAt)) {
                    VisionaryLoosingControlHandler.applyEffect(lookedAt, entity, this);
                    return;
                }
            }
        }

        entity.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION, 20 * 25, 1, false, false, false));

        List<LivingEntity> nearbyEntities = AbilityUtil.getNearbyEntities(entity, (ServerLevel) level, entity.getEyePosition(), range, false, true)
                .stream()
                .filter(nearbyEntity -> {
                    return !VisionaryHandler.shouldStayInvisible(BeyonderData.getSequence(entity), nearbyEntity) && !PhantomDetectionUtil.isHiddenFromSpiritVision(entity, nearbyEntity);
                })
                .toList();


        updateOutlines(player, nearbyEntities);
        for (LivingEntity nearbyEntity : nearbyEntities) {
            setGlowingForPlayer(nearbyEntity, (ServerPlayer) entity, true);
        }

        // quem saiu do alcance para de brilhar
        Set<Entity> glowing = glowingEntities.computeIfAbsent(entity.getUUID(), k -> new HashSet<>());
        for (Entity e : glowing) if (!nearbyEntities.contains(e)) setGlowingForPlayer(e, player, false);
        glowing.clear();
        glowing.addAll(nearbyEntities);

    }

    public static boolean shouldLooseControl(LivingEntity player, LivingEntity target) {
        int playerSeq = BeyonderData.getSequence(player);
        int targetSeq = BeyonderData.getSequence(target);

        if (player.getData(ModAttachments.ALLY_COMPONENT.get()).isAlly(target.getUUID()))
            return false;
        if (SpiritDisguiseUtil.isFooledByDisguise(player, target)) return false;

        return AbilityUtil.isTargetSignificantlyStronger(player, target);
    }

    public static void setGlowingForPlayer(Entity entity, ServerPlayer player, boolean glowing) {
        EntityDataAccessor<Byte> FLAGS = EntityAccessor.getSharedFlagsId();


        // Current flags from the entity
        byte flags = entity.getEntityData().get(FLAGS);

        if (glowing) {
            flags |= 0x40; // glowing bit
        } else {
            flags &= ~0x40; // clear glowing bit
        }

        // Build a list of data values (only the one we care about)
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
        values.add(SynchedEntityData.DataValue.create(FLAGS, flags));

        // Send metadata update ONLY to that player
        ClientboundSetEntityDataPacket packet =
                new ClientboundSetEntityDataPacket(entity.getId(), values);
        player.connection.send(packet);
    }

    @Override
    public void stop(Level level, LivingEntity entity) {
        if (level.isClientSide) {
            entity.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1, 1);
        } else {
            if (!(entity instanceof ServerPlayer player))
                return;

            player.removeEffect(MobEffects.NIGHT_VISION);

            if (glowingEntities.containsKey(entity.getUUID()))
                glowingEntities.get(entity.getUUID()).forEach(e -> setGlowingForPlayer(e, player, false));
            glowingEntities.remove(entity.getUUID());
            // apagar os times apaga os contornos coloridos junto
            if (outlined.remove(entity.getUUID()) != null)
                for (PlayerTeam team : ALL_TEAMS) player.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(team));

            PacketHandler.sendToPlayer(player, new SyncSpiritVisionAbilityPacket(false, -1));

            clearArtifactScaling(entity);
        }
    }

    private int getRandomColor() {
        Random random = new Random();
        int alpha = 0xFF; // Full opacity
        int red = random.nextInt(256);   // 0 to 255
        int green = random.nextInt(256); // 0 to 255
        int blue = random.nextInt(256);  // 0 to 255

        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    @Override
    protected float getSpiritualityCost() {
        return .5f;
    }
    /** Branco = pacífico, vermelho = agressivo (ou com raiva), amarelo = neutro; a semente da malícia continua negra */
    private static PlayerTeam teamFor(ServerPlayer viewer, LivingEntity e) {
        if (MaliceSeedAbility.canObserverSeeSeed(viewer, e)) return MALICE_TEAM_OBJ;
        if (e instanceof net.minecraft.world.entity.NeutralMob n) return n.isAngry() ? HOSTILE_TEAM : NEUTRAL_TEAM;
        if (e instanceof net.minecraft.world.entity.monster.Enemy) return HOSTILE_TEAM;
        if (e instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null) return HOSTILE_TEAM;
        if (e instanceof net.minecraft.world.entity.player.Player) return NEUTRAL_TEAM;
        return PEACEFUL_TEAM;
    }

    private void updateOutlines(ServerPlayer viewer, List<LivingEntity> nearby) {
        Map<Entity, PlayerTeam> previous = outlined.get(viewer.getUUID());
        if (previous == null) {
            previous = new HashMap<>();
            outlined.put(viewer.getUUID(), previous);
            for (PlayerTeam team : ALL_TEAMS) viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        }
        Map<Entity, PlayerTeam> current = new HashMap<>();
        for (LivingEntity e : nearby) current.put(e, teamFor(viewer, e));
        for (Map.Entry<Entity, PlayerTeam> en : current.entrySet()) {
            // entrar num time novo já tira do antigo no cliente
            if (previous.get(en.getKey()) != en.getValue())
                viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(en.getValue(), en.getKey().getScoreboardName(), ClientboundSetPlayerTeamPacket.Action.ADD));
        }
        for (Map.Entry<Entity, PlayerTeam> en : previous.entrySet()) {
            if (!current.containsKey(en.getKey()))
                viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(en.getValue(), en.getKey().getScoreboardName(), ClientboundSetPlayerTeamPacket.Action.REMOVE));
        }
        previous.clear();
        previous.putAll(current);
    }

}
