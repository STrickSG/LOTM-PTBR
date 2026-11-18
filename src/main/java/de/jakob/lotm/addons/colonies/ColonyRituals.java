package de.jakob.lotm.addons.colonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.constant.BuildingConstants;
import com.minecolonies.core.blocks.huts.BlockHutTownHall;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.rituals.FoolRituals;
import de.jakob.lotm.attachments.MarionetteComponent;
import de.jakob.lotm.attachments.ModAttachments;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Colônias nos rituais do Tolo.
 * <ul>
 *   <li>Invocador de Milagres (Tolo 2): reativar uma colônia abandonada (as que nascem no mundo) traz a história de volta ao presente.</li>
 *   <li>Cidade das Marionetes (Atendente de Mistérios, Tolo 1): agachado + clique direito, de mão vazia, numa marionete sua com nome,
 *       dentro da sua colônia: ela vira um colono que continua sendo sua marionete (sem lutar). Monstros ganham forma humana,
 *       com a skin de um Beyonder Errante.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class ColonyRituals {
    private static final String ABANDONED_HALL = "LotmAbandonedTownHall";
    private static final int COLOR = 0xB894FF;

    private ColonyRituals() {}

    /** Chamado no setup comum: o MineColonies já está carregado (o LOTM PTBR carrega depois dele) */
    public static void register() {
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyCreatedModEvent.class, ColonyRituals::onColonyCreated);
    }

    // ------------------------------------------------------------------ colônia abandonada

    // A prefeitura de uma colônia abandonada (gerada no mundo) fica desativada até alguém reivindicá-la.
    // Guarda qual prefeitura o jogador abriu, porque a reativação apaga essa marca antes de a colônia nascer.
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BlockHutTownHall)) return;
        BlockEntity entity = event.getLevel().getBlockEntity(event.getPos());
        if (!(entity instanceof AbstractTileEntityColonyBuilding hut)) return;
        List<String> tags = hut.getPositionedTags().getOrDefault(BlockPos.ZERO, List.of());
        if (tags.contains(BuildingConstants.DEACTIVATED)) {
            persisted(player).putLong(ABANDONED_HALL, event.getPos().asLong());
        }
    }

    private static void onColonyCreated(ColonyCreatedModEvent event) {
        IColony colony = event.getColony();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        UUID owner = colony.getPermissions().getOwner();
        if (server == null || owner == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player == null) return;
        CompoundTag tag = persisted(player);
        if (!tag.contains(ABANDONED_HALL) || tag.getLong(ABANDONED_HALL) != colony.getCenter().asLong()) return;
        tag.remove(ABANDONED_HALL);
        FoolRituals.restoredColony(player, colony.getName());
    }

    // ------------------------------------------------------------------ Cidade das Marionetes

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof LivingEntity marionette) || event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return;
        if (!marionette.hasData(ModAttachments.MARIONETTE_COMPONENT)) return;
        MarionetteComponent component = marionette.getData(ModAttachments.MARIONETTE_COMPONENT);
        if (!component.isMarionette() || !player.getStringUUID().equals(component.getControllerUUID())) return;
        IColony colony = Colonists.colonyAt(marionette.level(), marionette.blockPosition());
        if (colony == null || !player.getUUID().equals(colony.getPermissions().getOwner())) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(player instanceof ServerPlayer serverPlayer) || !(marionette.level() instanceof ServerLevel level)) return;

        if (!marionette.hasCustomName()) {
            serverPlayer.displayClientMessage(Component.translatable("lotmcraft.colony.marionette_no_name").withColor(0xFF7A7A), true);
            return;
        }
        if (colony.getCitizenManager().getCurrentCitizenCount() >= colony.getCitizenManager().getMaxCitizens()) {
            serverPlayer.displayClientMessage(Component.translatable("lotmcraft.colony.marionette_no_home").withColor(0xFF7A7A), true);
            return;
        }

        String name = marionette.getCustomName().getString().strip();
        String skin = skinFor(marionette);
        ICitizenData citizen = colony.getCitizenManager().createAndRegisterCivilianData();
        citizen.setName(name);
        data(serverPlayer.server).put(citizen.getUUID(), player.getUUID(), skin);
        colony.getCitizenManager().spawnOrCreateCivilian(citizen, level, List.of(marionette.blockPosition()), true);

        level.sendParticles(ParticleTypes.ENCHANT, marionette.getX(), marionette.getY(1), marionette.getZ(), 60, .4, .8, .4, .6);
        level.sendParticles(ParticleTypes.END_ROD, marionette.getX(), marionette.getY(.6), marionette.getZ(), 15, .3, .5, .3, .02);
        level.playSound(null, marionette.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, .8f, 1.5f);
        player.getData(ModAttachments.MARIONETTE_OWNER_COMPONENT).removeMarionette(marionette.getUUID());
        marionette.discard();
        citizen.getEntity().ifPresent(entity -> bindThreads(entity, serverPlayer.server));
        serverPlayer.displayClientMessage(Component.translatable("lotmcraft.colony.marionette_citizen", name, colony.getName()).withColor(COLOR), true);
    }

    // Skins dos Beyonders Errantes (sem as de personagens da novel)
    private static final String[] HUMAN_SKINS = {
            "steampunk_1", "steampunk_2", "mage", "sorcerer", "medieval_guy", "gentleman",
            "victorian_1", "victorian_2", "victorian_3", "victorian_4", "victorian_5", "victorian_6", "victorian_7", "victorian_8",
            "victorian_9", "victorian_10", "victorian_11", "victorian_12", "victorian_13", "victorian_14", "victorian_15", "victorian_16"};

    /**
     * Aparência do colono: um Beyonder Errante continua com a própria skin; monstros e outras criaturas ganham forma humana
     * (skin de Beyonder Errante); aldeões ficam com a aparência normal de colono.
     */
    private static String skinFor(LivingEntity marionette) {
        if (marionette instanceof de.jakob.lotm.entity.custom.BeyonderNPCEntity npc) return npc.getSkinName();
        if (marionette instanceof net.minecraft.world.entity.npc.AbstractVillager) return "";
        return HUMAN_SKINS[marionette.getRandom().nextInt(HUMAN_SKINS.length)];
    }

    /**
     * O colono continua sendo marionete do dono: conta como marionete dele (menu, limite, Atendente de Mistérios),
     * mas não luta nem segue ninguém; vive e trabalha na colônia.
     */
    private static void bindThreads(Entity entity, MinecraftServer server) {
        if (!(entity instanceof EntityCitizen citizen) || citizen.getCitizenData() == null) return;
        Data data = data(server);
        UUID controller = data.controllers.get(citizen.getCitizenData().getUUID());
        if (controller == null) return;
        MarionetteComponent component = citizen.getData(ModAttachments.MARIONETTE_COMPONENT);
        component.setMarionette(true);
        component.setControllerUUID(controller.toString());
        component.setShouldAttack(false);
        component.setCurrentMode(MarionetteComponent.MarionetteMode.STAY);
        ServerPlayer owner = server.getPlayerList().getPlayer(controller);
        if (owner != null) owner.getData(ModAttachments.MARIONETTE_OWNER_COMPONENT).addMarionette(citizen.getUUID());
        String skin = data.skins.getOrDefault(citizen.getCitizenData().getUUID(), "");
        if (!skin.isEmpty()) PacketDistributor.sendToPlayersTrackingEntity(citizen, new MarionetteSkinPacket(citizen.getId(), skin));
    }

    // o MineColonies recria a entidade do colono quando ela some (chunk, morte com cemitério...): os fios voltam junto
    @SubscribeEvent
    public static void onCitizenJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntityCitizen citizen) || citizen.getServer() == null) return;
        MinecraftServer server = citizen.getServer();
        // os dados do colono são ligados à entidade logo depois de ela entrar no mundo
        de.jakob.lotm.util.scheduling.ServerScheduler.scheduleDelayed(2, () -> bindThreads(citizen, server));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof EntityCitizen citizen) || citizen.getCitizenData() == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        String skin = data(player.server).skins.getOrDefault(citizen.getCitizenData().getUUID(), "");
        if (!skin.isEmpty()) PacketDistributor.sendToPlayer(player, new MarionetteSkinPacket(citizen.getId(), skin));
    }

    @SubscribeEvent
    public static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof EntityCitizen) MarionetteSkinPacket.forget(event.getEntity().getId());
    }

    /** Nomes das marionetes do jogador que moram como colonos na colônia dele */
    public static List<String> marionetteColonists(ServerPlayer player) {
        List<String> names = new ArrayList<>();
        IColony colony = Colonists.ownedBy(player.getUUID());
        if (colony == null) return names;
        Data data = data(player.server);
        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            if (player.getUUID().equals(data.controllers.get(citizen.getUUID()))) names.add(citizen.getName());
        }
        return names;
    }

    /** O colono é uma marionete deste jogador */
    public static boolean isMarionetteColonist(MinecraftServer server, UUID citizen, UUID controller) {
        return controller.equals(data(server).controllers.get(citizen));
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "lotmcraft_marionette_colonists");
    }

    /** Colono (identidade fixa) -> jogador que controla os fios dele, e a skin humana (vazia = aparência de colono) */
    private static final class Data extends SavedData {
        private final Map<UUID, UUID> controllers = new HashMap<>();
        private final Map<UUID, String> skins = new HashMap<>();

        void put(UUID citizen, UUID controller, String skin) {
            controllers.put(citizen, controller);
            skins.put(citizen, skin);
            setDirty();
        }

        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data data = new Data();
            CompoundTag controllers = tag.contains("Controllers") ? tag.getCompound("Controllers") : tag;
            for (String citizen : controllers.getAllKeys()) {
                if (controllers.hasUUID(citizen)) data.controllers.put(UUID.fromString(citizen), controllers.getUUID(citizen));
            }
            CompoundTag skins = tag.getCompound("Skins");
            for (String citizen : skins.getAllKeys()) data.skins.put(UUID.fromString(citizen), skins.getString(citizen));
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            CompoundTag controllersTag = new CompoundTag();
            controllers.forEach((citizen, controller) -> controllersTag.putUUID(citizen.toString(), controller));
            tag.put("Controllers", controllersTag);
            CompoundTag fixed = new CompoundTag();
            skins.forEach((citizen, skin) -> fixed.putString(citizen.toString(), skin));
            tag.put("Skins", fixed);
            return tag;
        }
    }
}
