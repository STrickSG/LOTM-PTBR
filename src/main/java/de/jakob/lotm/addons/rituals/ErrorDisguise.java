package de.jakob.lotm.addons.rituals;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.jakob.lotm.addons.colonies.Colonists;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.item.custom.DisguiseClothesItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * O disfarce do Cavalo de Troia do Destino (Erro, ritual da Sequência 2): sem poderes, só roupas e um nome.
 * Quem vive a vida de outro não fala no chat, não aparece no Tab e as mensagens do servidor não o entregam.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class ErrorDisguise {
    private ErrorDisguise() {}

    private static final int COLOR_BAD = 0xFF7A7A;
    /** Comandos que mandam mensagem com o seu nome */
    private static final Set<String> TALK_COMMANDS = Set.of("me", "msg", "tell", "w", "say", "teammsg", "tm");

    // ------------------------------------------------------------------ aparência

    /** Como desenhar esta pessoa: tipo de criatura e a aparência dela */
    public static CompoundTag lookOf(LivingEntity target) {
        CompoundTag look = new CompoundTag();
        look.putString("type", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
        if (target instanceof AbstractEntityCitizen citizen) {
            var data = citizen.getEntityData();
            look.putString("model", data.get(AbstractEntityCitizen.DATA_MODEL));
            look.putInt("texture", data.get(AbstractEntityCitizen.DATA_TEXTURE));
            look.putInt("female", data.get(AbstractEntityCitizen.DATA_IS_FEMALE));
            look.putString("style", data.get(AbstractEntityCitizen.DATA_STYLE));
            look.putString("suffix", data.get(AbstractEntityCitizen.DATA_TEXTURE_SUFFIX));
            look.putString("meta", data.get(AbstractEntityCitizen.DATA_RENDER_METADATA));
            look.putBoolean("child", data.get(AbstractEntityCitizen.DATA_IS_CHILD));
        } else {
            look.put("nbt", target.saveWithoutId(new CompoundTag()));
        }
        return look;
    }

    /** Guardado no jogador: as roupas que ele está vestindo agora (nome e aparência) */
    private static final String WORN = "LotmDisguiseWorn";

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static boolean hasClothes(Player player, String name) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof DisguiseClothesItem && player.getUUID().equals(DisguiseClothesItem.owner(stack))
                    && name.equals(DisguiseClothesItem.disguiseName(stack))) return true;
        }
        return false;
    }

    /** Está vestido com as roupas desta pessoa (e ainda as tem: perdê-las é o mesmo que tirá-las) */
    public static boolean wearing(Player player, String name) {
        CompoundTag worn = persisted(player).getCompound(WORN);
        if (!name.equals(worn.getString("name"))) return false;
        if (hasClothes(player, name)) return true;
        if (player instanceof ServerPlayer serverPlayer) takeOff(serverPlayer);
        return false;
    }

    /** Usar as roupas: veste; usar de novo: tira */
    public static void toggle(ServerPlayer player, ItemStack stack) {
        String name = DisguiseClothesItem.disguiseName(stack);
        if (!player.getUUID().equals(DisguiseClothesItem.owner(stack))) {
            player.displayClientMessage(Component.translatable("lotmcraft.error.clothes_not_yours", name).withColor(COLOR_BAD), true);
            return;
        }
        if (name.equals(persisted(player).getCompound(WORN).getString("name"))) {
            takeOff(player);
            player.displayClientMessage(Component.translatable("lotmcraft.error.clothes_off", name), true);
        } else {
            CompoundTag worn = new CompoundTag();
            worn.putString("name", name);
            worn.put("look", DisguiseClothesItem.look(stack));
            persisted(player).put(WORN, worn);
            sync(player);
            player.displayClientMessage(Component.translatable("lotmcraft.error.clothes_on", name), true);
        }
    }

    static void takeOff(ServerPlayer player) {
        if (!persisted(player).contains(WORN)) return;
        persisted(player).remove(WORN);
        sync(player);
    }

    /** Conta para todos que estão vendo o jogador (e para ele mesmo) como ele aparece */
    private static void sync(ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, packetFor(player));
    }

    private static DisguiseSyncPacket packetFor(ServerPlayer player) {
        CompoundTag worn = persisted(player).getCompound(WORN);
        return new DisguiseSyncPacket(player.getUUID(), worn.getString("name"), worn.getCompound("look"));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher
                && persisted(target).contains(WORN)) {
            watcher.connection.send(packetFor(target));
        }
    }

    // ------------------------------------------------------------------ a pessoa volta

    /** A identidade acabou: a pessoa volta para a vida dela e as roupas perdem o sentido */
    static void restore(ServerPlayer player, String name, int colonyId, int citizenId, CompoundTag body, String bodyDimension) {
        if (citizenId >= 0) {
            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                if (colony.getID() == colonyId) colony.getTravellingManager().finishTravellingFor(citizenId);
            }
        } else if (body != null && !body.isEmpty()) {
            MinecraftServer server = player.server;
            ServerLevel level = bodyDimension.isEmpty() ? player.serverLevel()
                    : server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(bodyDimension)));
            if (level != null) {
                Entity back = EntityType.loadEntityRecursive(body, level, e -> e);
                if (back != null) level.addFreshEntity(back);
            }
        }
        takeOff(player);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof DisguiseClothesItem && player.getUUID().equals(DisguiseClothesItem.owner(stack))
                    && name.equals(DisguiseClothesItem.disguiseName(stack))) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        showInTab(player);
    }

    // ------------------------------------------------------------------ Tab

    public static void hideFromTab(ServerPlayer player) {
        broadcastListed(player, false);
    }

    public static void showInTab(ServerPlayer player) {
        broadcastListed(player, true);
    }

    /** Só muda se o jogador aparece na lista do Tab; ele continua existindo para o jogo (pele, nome, entidade) */
    private static void broadcastListed(ServerPlayer player, boolean listed) {
        ClientboundPlayerInfoUpdatePacket packet = listedPacket(player, listed);
        if (packet == null) return;
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) other.connection.send(packet);
    }

    private static ClientboundPlayerInfoUpdatePacket listedPacket(ServerPlayer player, boolean listed) {
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), List.of(player));
        if (listed) return packet;
        try {
            ClientboundPlayerInfoUpdatePacket.Entry entry = packet.entries().getFirst();
            ClientboundPlayerInfoUpdatePacket.Entry hidden = new ClientboundPlayerInfoUpdatePacket.Entry(entry.profileId(), entry.profile(), false,
                    entry.latency(), entry.gameMode(), entry.displayName(), entry.chatSession());
            for (Field field : ClientboundPlayerInfoUpdatePacket.class.getDeclaredFields()) {
                if (field.getType() == List.class) {
                    field.setAccessible(true);
                    field.set(packet, List.of(hidden));
                }
            }
            return packet;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOTMCraft.LOGGER.warn("Não deu para esconder {} da lista do Tab", player.getScoreboardName(), e);
            return null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joined)) return;
        // quem entra não vê os disfarçados no Tab; o próprio disfarçado continua escondido ao voltar
        for (ServerPlayer other : joined.server.getPlayerList().getPlayers()) {
            if (other != joined && ErrorRituals.disguised(other)) {
                ClientboundPlayerInfoUpdatePacket packet = listedPacket(other, false);
                if (packet != null) joined.connection.send(packet);
            }
        }
        if (ErrorRituals.disguised(joined)) hideFromTab(joined);
        if (persisted(joined).contains(WORN)) joined.connection.send(packetFor(joined));
    }

    // ------------------------------------------------------------------ clicar no disfarçado

    /**
     * Quem clica em alguém disfarçado vê o que veria clicando na pessoa de verdade:
     * a janela do colono (os dados dele continuam na colônia enquanto ele "viaja"); aldeão balança a cabeça.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClickDisguised(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Player target) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (!persisted(target).contains(WORN)) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getEntity() instanceof ServerPlayer clicker) || !(target instanceof ServerPlayer disguised)) return;
        int[] citizen = ErrorRituals.disguisedCitizen(disguised);
        if (citizen != null) {
            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                if (colony.getID() != citizen[0]) continue;
                var data = colony.getCitizenManager().getCivilian(citizen[1]);
                if (!(data instanceof com.minecolonies.api.colony.ICitizenData citizenData)) return;
                if (!colony.getPermissions().hasPermission(clicker, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS)) return;
                new com.minecolonies.core.network.messages.client.colony.ColonyViewCitizenViewMessage((com.minecolonies.core.colony.Colony) colony, citizenData).sendToPlayer(clicker);
                clicker.connection.send(new DisguiseCitizenWindowPacket(colony.getID(), colony.getDimension().location().toString(), citizenData.getId()));
                return;
            }
        } else {
            disguised.level().playSound(null, disguised.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------ na colônia daquele colono

    /** Disfarçado (e vestido) de um colono desta colônia */
    public static boolean disguisedIn(Player player, int colonyId) {
        int[] citizen = ErrorRituals.disguisedCitizen(player);
        return citizen != null && citizen[0] == colonyId && wearing(player, ErrorRituals.disguiseName(player));
    }

    /** No instante em que o disfarçado abre um baú (ou outro container) da colônia daquele colono */
    private static boolean openingChest;

    public static boolean openingChest() {
        return openingChest;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlockStart(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        openingChest = false;
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)) return;
        IColony colony = Colonists.colonyAt(event.getLevel(), event.getPos());
        if (colony == null || !disguisedIn(player, colony.getID())) return;
        var level = event.getLevel();
        var pos = event.getPos();
        var state = level.getBlockState(pos);
        var blockEntity = level.getBlockEntity(pos);
        boolean container = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, null) != null;
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            if (container) break;
            container = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, side) != null;
        }
        openingChest = container;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRightClickBlockEnd(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        openingChest = false;
    }

    // ------------------------------------------------------------------ chat

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        if (!ErrorRituals.disguised(event.getPlayer())) return;
        event.setCanceled(true);
        event.getPlayer().displayClientMessage(Component.translatable("lotmcraft.error.identity_silent", ErrorRituals.disguiseName(event.getPlayer())).withColor(COLOR_BAD), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        var source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || !ErrorRituals.disguised(player)) return;
        String input = event.getParseResults().getReader().getString().strip();
        if (input.startsWith("/")) input = input.substring(1);
        String command = input.split(" ", 2)[0].toLowerCase();
        if (!TALK_COMMANDS.contains(command)) return;
        event.setCanceled(true);
        player.displayClientMessage(Component.translatable("lotmcraft.error.identity_silent", ErrorRituals.disguiseName(player)).withColor(COLOR_BAD), true);
    }

    // ------------------------------------------------------------------ mensagens do servidor

    /**
     * Mensagens do servidor que citam alguém disfarçado (progresso, morte, entrada e saída) não são enviadas:
     * você não é mais você, é outro agora.
     */
    public static boolean exposes(Component message) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ErrorRituals.disguised(player)) continue;
            if (mentions(message, ErrorRituals.disguiseName(player)) || mentions(message, player.getGameProfile().getName())) return true;
        }
        return false;
    }

    private static boolean mentions(Component message, String name) {
        if (name == null || name.isEmpty()) return false;
        if (message.getContents() instanceof TranslatableContents translatable) {
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component component && (component.getString().equals(name) || mentions(component, name))) return true;
                if (arg instanceof String text && text.equals(name)) return true;
            }
        }
        for (Component sibling : message.getSiblings()) if (mentions(sibling, name)) return true;
        return false;
    }
}
