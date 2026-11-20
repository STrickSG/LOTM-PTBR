package de.jakob.lotm.util.helper;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.CancelPhotonBlockEffectPacket;
import de.jakob.lotm.network.packets.toClient.CancelPhotonEntityEffectPacket;
import de.jakob.lotm.network.packets.toClient.PlayCancellablePhotonBlockEffectPacket;
import de.jakob.lotm.network.packets.toClient.PlayPhotonBlockEffectPacket;
import de.jakob.lotm.network.packets.toClient.PlayPhotonEntityEffectPacket;
import de.jakob.lotm.LOTMCraft;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class FxHandler {

    private static final double RANGE = 160;

    private FxHandler() {
    }

    public static void play(ServerLevel level, String fx, Vec3 pos, double scale) {
        play(level, fx, pos, scale, null);
    }

    public static void play(ServerLevel level, String fx, Vec3 pos, double scale, Quaternionf rotation) {
        BlockPos blockPos = BlockPos.containing(pos);
        PacketHandler.sendToNearbyPlayers(new PlayPhotonBlockEffectPacket(fx, blockPos,
                pos.x - blockPos.getX() - 0.5, pos.y - blockPos.getY() - 0.5, pos.z - blockPos.getZ() - 0.5,
                scale, rotation, -1, false, true, null), level, pos, RANGE);
    }

    /** Rotation that turns an effect authored along +Y towards {@code direction}. */
    public static Quaternionf pointing(Vec3 direction) {
        Vec3 dir = direction.normalize();
        return new Quaternionf().rotationTo(0, 1, 0, (float) dir.x, (float) dir.y, (float) dir.z);
    }

    /** Rotation that turns an effect authored facing +Z (local Y up) along an entity's view. */
    public static Quaternionf facing(Entity entity, float extraYawDegrees) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-entity.getYRot()))
                .rotateX((float) Math.toRadians(entity.getXRot()))
                .rotateY((float) Math.toRadians(extraYawDegrees));
    }

    /** Plays an effect for a single player only, e.g. weather close around them. */
    public static void playFor(ServerPlayer player, String fx, Vec3 pos, double scale) {
        BlockPos blockPos = BlockPos.containing(pos);
        PacketHandler.sendToPlayer(player, new PlayPhotonBlockEffectPacket(fx, blockPos,
                pos.x - blockPos.getX() - 0.5, pos.y - blockPos.getY() - 0.5, pos.z - blockPos.getZ() - 0.5,
                scale, null, -1, false, true, null));
    }

    public static UUID playCancellable(ServerLevel level, String fx, Vec3 pos, double scale) {
        return playCancellable(level, fx, pos, scale, null);
    }

    public static UUID playCancellable(ServerLevel level, String fx, Vec3 pos, double scale, Quaternionf rotation) {
        UUID id = UUID.randomUUID();
        BlockPos blockPos = BlockPos.containing(pos);
        PacketHandler.sendToNearbyPlayers(new PlayCancellablePhotonBlockEffectPacket(fx, blockPos,
                pos.x - blockPos.getX() - 0.5, pos.y - blockPos.getY() - 0.5, pos.z - blockPos.getZ() - 0.5,
                scale, rotation, -1, false, true, null, id), level, pos, RANGE);
        return id;
    }

    /** Um efeito duradouro: quem chega perto depois de ele ter sido criado também passa a vê-lo */
    private record Persistent(ServerLevel level, Vec3 pos, PlayCancellablePhotonBlockEffectPacket packet, Set<UUID> viewers) {}

    private static final Map<UUID, Persistent> PERSISTENT = new HashMap<>();

    /**
     * Como {@link #playCancellable}, mas para efeitos que ficam no mundo por muito tempo: quem estava longe quando ele
     * foi criado recebe o efeito ao chegar perto, e quem se afasta demais deixa de recebê-lo. Precisa de {@link #cancel}.
     */
    public static UUID playPersistent(ServerLevel level, String fx, Vec3 pos, double scale, Quaternionf rotation) {
        UUID id = UUID.randomUUID();
        BlockPos blockPos = BlockPos.containing(pos);
        PlayCancellablePhotonBlockEffectPacket packet = new PlayCancellablePhotonBlockEffectPacket(fx, blockPos,
                pos.x - blockPos.getX() - 0.5, pos.y - blockPos.getY() - 0.5, pos.z - blockPos.getZ() - 0.5,
                scale, rotation, -1, false, true, null, id);
        Persistent persistent = new Persistent(level, pos, packet, new HashSet<>());
        PERSISTENT.put(id, persistent);
        syncViewers(persistent);
        return id;
    }

    private static void syncViewers(Persistent fx) {
        for (ServerPlayer player : fx.level().players()) {
            if (!fx.viewers().contains(player.getUUID()) && player.position().distanceTo(fx.pos()) <= RANGE) {
                PacketHandler.sendToPlayer(player, fx.packet());
                fx.viewers().add(player.getUUID());
            }
        }
        for (UUID viewer : new ArrayList<>(fx.viewers())) {
            ServerPlayer player = fx.level().getServer().getPlayerList().getPlayer(viewer);
            boolean here = player != null && player.level() == fx.level() && player.position().distanceTo(fx.pos()) <= RANGE + 32;
            if (here) continue;
            fx.viewers().remove(viewer);
            if (player != null) PacketHandler.sendToPlayer(player, new CancelPhotonBlockEffectPacket(fx.packet().effectUUID()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PERSISTENT.isEmpty() || event.getServer().getTickCount() % 20 != 0) return;
        for (Persistent fx : new ArrayList<>(PERSISTENT.values())) syncViewers(fx);
    }

    /** Rotation that turns an effect authored facing +Z towards {@code direction}, staying upright. */
    public static Quaternionf yawTowards(Vec3 direction) {
        return new Quaternionf().rotateY((float) Math.atan2(direction.x, direction.z));
    }

    public static void cancel(ServerLevel level, UUID id) {
        if (id != null) PERSISTENT.remove(id);
        if (id != null) PacketHandler.sendToAllPlayersInSameLevel(new CancelPhotonBlockEffectPacket(id), level);
    }

    public static UUID playOnEntity(ServerLevel level, String fx, Entity entity, double yOffset, double scale) {
        UUID id = UUID.randomUUID();
        PacketHandler.sendToNearbyPlayers(new PlayPhotonEntityEffectPacket(fx, entity.getId(), 0, yOffset, 0, scale,
                null, false, true, id, false), level, entity.position(), RANGE);
        return id;
    }

    /** Plays an effect centred on the entity, sized to its height (authored for a 1.8 block tall body). */
    public static UUID playOnBody(ServerLevel level, String fx, Entity entity) {
        return playOnEntity(level, fx, entity, entity.getBbHeight() * 0.5, Math.max(0.6, entity.getBbHeight() / 1.8));
    }

    public static void cancelOnEntity(ServerLevel level, UUID id) {
        if (id != null) PacketHandler.sendToAllPlayersInSameLevel(new CancelPhotonEntityEffectPacket(id), level);
    }
}
