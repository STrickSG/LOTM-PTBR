package de.jakob.lotm.addons.colonies;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.rituals.Believers;
import de.jakob.lotm.addons.rituals.RedPriestRituals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Conquista de colônias (ritual do Conquistador, Sacerdote Vermelho 1). Quem está na Sequência 2 do Sacerdote Vermelho:
 * <ol>
 *   <li>ataca os guardas de uma colônia que não é sua: é uma declaração de guerra (ele vira inimigo da colônia);</li>
 *   <li>com nenhum guarda dela vivo perto da prefeitura, finca um estandarte qualquer perto dela.</li>
 * </ol>
 * A colônia conquistada obedece (o conquistador vira oficial), reverencia (os colonos viram fiéis dele) e teme (os colonos
 * tremem perto dele). Um jogador retoma a própria colônia matando o conquistador dentro dela.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class ColonyConquest {
    private ColonyConquest() {}

    public static final int NATION_NEEDED = 100, GUARD_RADIUS = 48, BANNER_RADIUS = 16;
    private static final int COLOR = 0xE0533A, BAD = 0xFF7A7A;

    private static boolean canConquer(Player player) {
        return RedPriestRituals.preparing(player, 1);
    }

    private static boolean foreign(IColony colony, Player player) {
        return !player.getUUID().equals(colony.getPermissions().getOwner());
    }

    /** A colônia que este defensor protege: guardas dela ou golens de um reino de NPC */
    private static IColony defendedColony(net.minecraft.world.entity.Entity target) {
        if (target instanceof EntityCitizen citizen && citizen.getCitizenData() != null && citizen.getCitizenData().getJob() instanceof AbstractJobGuard<?>) {
            return citizen.getCitizenData().getColony();
        }
        if (target.getPersistentData().contains(NpcKingdoms.DEFENDER)) {
            int id = target.getPersistentData().getInt(NpcKingdoms.DEFENDER);
            for (IColony colony : IColonyManager.getInstance().getAllColonies()) if (colony.getID() == id) return colony;
        }
        return null;
    }

    // atacar os defensores de uma colônia é declarar guerra a ela: o MineColonies passa a deixar os dois lados lutarem
    private static boolean attackingDefender = false;

    /** Durante o golpe num defensor em guerra declarada, a proteção da colônia deixa (ver o mixin de permissões) */
    public static boolean attackingDefender() {
        return attackingDefender;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !canConquer(player)) return;
        IColony colony = defendedColony(event.getTarget());
        if (colony == null || !foreign(colony, player)) return;
        attackingDefender = true;
        if (colony.getPermissions().getRank(player).isHostile()) return;
        colony.getPermissions().addPlayer(player.getUUID(), player.getName().getString(), colony.getPermissions().getRankHostile());
        player.displayClientMessage(Component.translatable("lotmcraft.conquest.war_declared", colony.getName()).withColor(COLOR), true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onAttackDone(AttackEntityEvent event) {
        attackingDefender = false;
    }

    /** Defensores da colônia ainda vivos perto da prefeitura: guardas e, nos reinos de NPC, os golens */
    private static int defenders(IColony colony) {
        int count = 0;
        for (ICitizenData data : colony.getCitizenManager().getCitizens()) {
            if (!(data.getJob() instanceof AbstractJobGuard<?>)) continue;
            var entity = data.getEntity();
            if (entity.isPresent() && entity.get().isAlive() && entity.get().blockPosition().distSqr(colony.getCenter()) <= GUARD_RADIUS * GUARD_RADIUS) count++;
        }
        if (colony.getWorld() instanceof ServerLevel level) {
            count += level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(colony.getCenter()).inflate(GUARD_RADIUS),
                    e -> e.isAlive() && NpcKingdoms.isDefender(e, colony)).size();
        }
        return count;
    }

    // fincar um estandarte perto da prefeitura: a proteção da colônia não impede (é uma conquista)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlaceBanner(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (!(held.getItem() instanceof BannerItem) || !canConquer(player)) return;
        IColony colony = Colonists.colonyAt(event.getLevel(), event.getPos());
        if (colony == null || !foreign(colony, player) || event.getPos().distSqr(colony.getCenter()) > BANNER_RADIUS * BANNER_RADIUS) return;
        event.setCanceled(true);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (conqueror(serverPlayer.server, colony) != null && player.getUUID().equals(conqueror(serverPlayer.server, colony))) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        int left = defenders(colony);
        if (left > 0) {
            serverPlayer.displayClientMessage(Component.translatable("lotmcraft.conquest.defenders", colony.getName(), left).withColor(BAD), true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        placingConquestBanner = true;
        InteractionResult result;
        try {
            result = held.useOn(new UseOnContext(player, event.getHand(), event.getHitVec()));
        } finally {
            placingConquestBanner = false;
        }
        event.setCancellationResult(result);
        if (result.consumesAction()) conquer(serverPlayer, colony);
    }

    private static boolean placingConquestBanner = false;

    /** Enquanto o estandarte da conquista é fincado, a proteção da colônia deixa (ver o mixin de permissões) */
    public static boolean placingConquestBanner() {
        return placingConquestBanner;
    }

    private static void conquer(ServerPlayer player, IColony colony) {
        data(player.server).put(colony.getID(), player.getUUID());
        // obedece: o conquistador manda na colônia
        colony.getPermissions().addPlayer(player.getUUID(), player.getName().getString(), colony.getPermissions().getRankOfficer());
        // reverencia: todos os colonos passam a ser fiéis dele
        Believers.claimByConquest(player, colony);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 120, 2, 1.5, 2, .05);
        level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 2f, .8f);
        player.displayClientMessage(Component.translatable("lotmcraft.conquest.done", colony.getName(), conqueredPopulation(player), NATION_NEEDED).withColor(COLOR), true);
    }

    // um jogador retoma a própria colônia matando o conquistador dentro dela
    @SubscribeEvent
    public static void onConquerorDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead) || !(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        IColony colony = Colonists.colonyAt(dead.level(), dead.blockPosition());
        if (colony == null || !dead.getUUID().equals(conqueror(dead.server, colony))) return;
        if (!killer.getUUID().equals(colony.getPermissions().getOwner()) && !colony.getPermissions().isColonyMember(killer)) return;
        data(dead.server).remove(colony.getID());
        colony.getPermissions().removePlayer(dead.getUUID());
        killer.displayClientMessage(Component.translatable("lotmcraft.conquest.liberated", colony.getName()).withColor(COLOR), true);
    }

    // teme: perto do conquistador, os colonos das colônias dele tremem
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0) return;
        Data data = data(player.server);
        if (!data.conquests.containsValue(player.getUUID())) return;
        for (EntityCitizen citizen : player.serverLevel().getEntitiesOfClass(EntityCitizen.class, player.getBoundingBox().inflate(12))) {
            if (citizen.getCitizenData() == null || citizen.getCitizenData().getColony() == null) continue;
            if (!player.getUUID().equals(data.conquests.get(citizen.getCitizenData().getColony().getID()))) continue;
            player.serverLevel().sendParticles(ParticleTypes.SPLASH, citizen.getX(), citizen.getY() + 1.6, citizen.getZ(), 6, .2, .2, .2, 0);
        }
    }

    public static UUID conqueror(MinecraftServer server, IColony colony) {
        return data(server).conquests.get(colony.getID());
    }

    /** Colonos de todas as colônias conquistadas pelo jogador (a dele não conta) */
    public static int conqueredPopulation(ServerPlayer player) {
        Data data = data(player.server);
        int total = 0;
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (player.getUUID().equals(data.conquests.get(colony.getID())) && foreign(colony, player)) {
                total += colony.getCitizenManager().getCurrentCitizenCount();
            }
        }
        return total;
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "lotmcraft_conquests");
    }

    /** Colônia (id) -> quem a conquistou */
    private static final class Data extends SavedData {
        private final Map<Integer, UUID> conquests = new HashMap<>();

        void put(int colony, UUID conqueror) {
            conquests.put(colony, conqueror);
            setDirty();
        }

        void remove(int colony) {
            if (conquests.remove(colony) != null) setDirty();
        }

        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data data = new Data();
            for (String key : tag.getAllKeys()) data.conquests.put(Integer.parseInt(key), tag.getUUID(key));
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            conquests.forEach((colony, conqueror) -> tag.putUUID(String.valueOf(colony), conqueror));
            return tag;
        }
    }
}
