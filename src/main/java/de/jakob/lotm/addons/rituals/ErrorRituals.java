package de.jakob.lotm.addons.rituals;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.colonies.Colonists;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.beyonders.abilities.error.handler.TimeHandler;
import de.jakob.lotm.util.BeyonderData;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Rituais de avanço do Erro, com os colonos do MineColonies:
 * <ul>
 *   <li>5, Ladrão de Sonhos: estar nos sonhos de 30 colonos que dormem na hora de beber.</li>
 *   <li>4, Parasita: 9 colonos oferecem, de boa vontade, tudo o que têm para sustentar você.</li>
 *   <li>3, Mentor do Engano: sem violência, a ordem de uma colônia desmorona (felicidade geral muito baixa).</li>
 *   <li>2, Cavalo de Troia do Destino: tomar o lugar de um colono e viver como ele, sem poderes, por 6 dias.</li>
 *   <li>1, Verme do Tempo: 12 relógios em molduras deixam uma cidade (colônia de 20 colonos) em desordem temporal por 7 dias.</li>
 * </ul>
 * O ritual de cada Sequência é feito por quem ainda está na Sequência de cima; as mensagens só aparecem para ele.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class ErrorRituals {
    private ErrorRituals() {}

    public static final int DREAMS_NEEDED = 30, DREAM_RADIUS = 48;
    public static final int HOSTS_NEEDED = 9;
    public static final double WILLING_HAPPINESS = 7.0;
    public static final int ORDER_MIN_CITIZENS = 10;
    public static final double COLLAPSED_HAPPINESS = 3.0;
    public static final long PEACE_TICKS = 24000;
    public static final int IDENTITY_TICKS_NEEDED = 24000 * 6;
    public static final int CITY_CITIZENS = 20, CLOCKS_NEEDED = 12, DISORDER_TICKS_NEEDED = 24000 * 7;

    private static final String DREAMS = "LotmErrorDreams", HOSTS = "LotmErrorHosts", LAST_VIOLENCE = "LotmErrorLastViolence",
            IDENTITY = "LotmErrorIdentity", IDENTITY_COLONY = "LotmErrorIdentityColony", IDENTITY_TICKS = "LotmErrorIdentityTicks",
            IDENTITY_DONE = "LotmErrorIdentityDone", DISORDER_COLONY = "LotmErrorDisorderColony", DISORDER_TICKS = "LotmErrorDisorderTicks",
            DISORDER_DONE = "LotmErrorDisorderDone";
    private static final int COLOR = 0x9D6BFF, BAD = 0xFF7A7A;

    // ------------------------------------------------------------------ util

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Está na Sequência de onde se faz o ritual da Sequência {@code ritualSequence} do Erro */
    private static boolean preparing(Player player, int ritualSequence) {
        return BeyonderData.isBeyonder(player) && "error".equals(BeyonderData.getPathway(player))
                && BeyonderData.getSequence(player) == ritualSequence + 1;
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    private static Set<UUID> uuids(CompoundTag tag, String key) {
        Set<UUID> set = new HashSet<>();
        for (Tag t : tag.getList(key, Tag.TAG_INT_ARRAY)) set.add(NbtUtils.loadUUID(t));
        return set;
    }

    private static void putUuids(CompoundTag tag, String key, Set<UUID> set) {
        ListTag list = new ListTag();
        set.forEach(id -> list.add(NbtUtils.createUUID(id)));
        tag.put(key, list);
    }

    private static IColony colonyById(int id) {
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (colony.getID() == id) return colony;
        }
        return null;
    }

    // ------------------------------------------------------------------ interações com colonos
    // O Erro é uma falha, um bug: faz os rituais na colônia dos outros, mesmo sem permissão. Por isso estes eventos rodam
    // antes da proteção de colônia do MineColonies e fazem a ação eles mesmos.

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof EntityCitizen citizen)) return;
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        boolean dream = preparing(player, 5) && player.isShiftKeyDown() && held.isEmpty();
        boolean host = preparing(player, 4) && player.isShiftKeyDown() && held.isEmpty();
        boolean identity = preparing(player, 2) && held.is(Items.NAME_TAG) && held.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (!dream && !host && !identity) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(player instanceof ServerPlayer serverPlayer) || citizen.getCitizenData() == null) return;
        if (dream) enterDream(serverPlayer, citizen);
        else if (host) askOffering(serverPlayer, citizen);
        else takeIdentity(serverPlayer, citizen, held);
    }

    // ------------------------------------------------------------------ 5, Ladrão de Sonhos

    private static void enterDream(ServerPlayer player, EntityCitizen citizen) {
        String name = citizen.getCitizenData().getName();
        if (!citizen.getCitizenSleepHandler().isAsleep()) {
            tell(player, Component.translatable("lotmcraft.error.dream_awake", name).withColor(BAD));
            return;
        }
        CompoundTag tag = data(player);
        Set<UUID> dreams = uuids(tag, DREAMS);
        if (dreams.add(citizen.getCitizenData().getUUID())) putUuids(tag, DREAMS, dreams);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.WITCH, citizen.getX(), citizen.getY() + .6, citizen.getZ(), 25, .4, .3, .4, .02);
        level.playSound(null, citizen.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.PLAYERS, .6f, .5f);
        tell(player, Component.translatable("lotmcraft.error.dream_entered", name, sleepingDreams(player)).withColor(COLOR));
    }

    /** Colonos em cujo sonho você entrou e que estão dormindo agora, a até 48 blocos */
    public static int sleepingDreams(ServerPlayer player) {
        Set<UUID> dreams = uuids(data(player), DREAMS);
        int count = 0;
        for (EntityCitizen citizen : player.serverLevel().getEntitiesOfClass(EntityCitizen.class, player.getBoundingBox().inflate(DREAM_RADIUS))) {
            if (citizen.getCitizenData() != null && citizen.getCitizenSleepHandler().isAsleep() && dreams.contains(citizen.getCitizenData().getUUID())) count++;
        }
        return count;
    }

    public static boolean inDreams(ServerPlayer player) {
        return sleepingDreams(player) >= DREAMS_NEEDED;
    }

    // ------------------------------------------------------------------ 4, Parasita

    private static void askOffering(ServerPlayer player, EntityCitizen citizen) {
        ICitizenData citizenData = citizen.getCitizenData();
        IColony colony = citizenData.getColony();
        String name = citizenData.getName();
        CompoundTag tag = data(player);
        Set<UUID> hosts = uuids(tag, HOSTS);
        if (hosts.contains(citizenData.getUUID())) {
            tell(player, Component.translatable("lotmcraft.error.host_already", name).withColor(COLOR));
            return;
        }
        if (citizenData.getCitizenHappinessHandler().getHappiness(colony, citizenData) < WILLING_HAPPINESS) {
            citizen.playSound(SoundEvents.VILLAGER_NO, 1f, 1f);
            tell(player, Component.translatable("lotmcraft.error.host_unwilling", name).withColor(BAD));
            return;
        }
        // tudo o que ele tem passa para você
        var inventory = citizenData.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.extractItem(slot, inventory.getStackInSlot(slot).getCount(), false);
            if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
        }
        hosts.add(citizenData.getUUID());
        putUuids(tag, HOSTS, hosts);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SCULK_SOUL, citizen.getX(), citizen.getY() + 1, citizen.getZ(), 12, .3, .4, .3, .02);
        level.playSound(null, citizen.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1f, .8f);
        tell(player, Component.translatable("lotmcraft.error.host_offered", name, livingHosts(player), HOSTS_NEEDED).withColor(COLOR));
    }

    /** Colonos que se ofereceram e ainda estão vivos */
    public static int livingHosts(ServerPlayer player) {
        Set<UUID> existing = Colonists.allIds();
        int count = 0;
        for (UUID id : uuids(data(player), HOSTS)) if (existing.contains(id)) count++;
        return count;
    }

    public static boolean sustained(ServerPlayer player) {
        return livingHosts(player) >= HOSTS_NEEDED;
    }

    // ferir quem se ofereceu: ele retira a oferta. Qualquer ferida conta como violência (Mentor do Engano)
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity() == player) return;
        CompoundTag tag = data(player);
        tag.putLong(LAST_VIOLENCE, now(player));
        UUID id = Colonists.id(event.getEntity());
        if (id == null) return;
        Set<UUID> hosts = uuids(tag, HOSTS);
        if (hosts.remove(id)) {
            putUuids(tag, HOSTS, hosts);
            if (preparing(player, 4)) tell(player, Component.translatable("lotmcraft.error.host_lost", event.getEntity().getName()).withColor(BAD));
        }
    }

    // ------------------------------------------------------------------ 3, Mentor do Engano

    /** Está numa colônia cuja ordem desmoronou, sem ter ferido ninguém no último dia */
    public static boolean orderCollapsed(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (tag.contains(LAST_VIOLENCE) && now(player) - tag.getLong(LAST_VIOLENCE) < PEACE_TICKS) return false;
        IColony colony = Colonists.colonyAt(player.level(), player.blockPosition());
        return colony != null && colony.getCitizenManager().getCurrentCitizenCount() >= ORDER_MIN_CITIZENS
                && colony.getOverallHappiness() <= COLLAPSED_HAPPINESS;
    }

    // ------------------------------------------------------------------ 2, Cavalo de Troia do Destino

    private static void takeIdentity(ServerPlayer player, EntityCitizen citizen, ItemStack nameTag) {
        ICitizenData citizenData = citizen.getCitizenData();
        String name = citizenData.getName();
        if (!nameTag.getHoverName().getString().strip().equalsIgnoreCase(name.strip())) {
            tell(player, Component.translatable("lotmcraft.error.identity_wrong_name", name).withColor(BAD));
            return;
        }
        IColony colony = citizenData.getColony();
        CompoundTag tag = data(player);
        tag.putString(IDENTITY, name);
        tag.putInt(IDENTITY_COLONY, colony.getID());
        tag.putInt(IDENTITY_TICKS, 0);
        tag.remove(IDENTITY_DONE);
        if (!player.getAbilities().instabuild) nameTag.shrink(1);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, citizen.getX(), citizen.getY() + 1, citizen.getZ(), 30, .3, .6, .3, .02);
        level.playSound(null, citizen.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1f, 1f);
        // ele some do mundo; você fica no lugar dele
        colony.getCitizenManager().removeCivilian(citizenData);
        if (citizen.isAlive()) citizen.discard();
        player.refreshDisplayName();
        tell(player, Component.translatable("lotmcraft.error.identity_taken", name).withColor(COLOR));
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (event.getEntity().level().isClientSide) return;
        CompoundTag tag = data(event.getEntity());
        if (tag.contains(IDENTITY)) event.setDisplayname(Component.literal(tag.getString(IDENTITY)));
    }

    // usar qualquer poder dentro da colônia entrega o disfarce
    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag tag = data(player);
        if (!tag.contains(IDENTITY) || tag.getBoolean(IDENTITY_DONE)) return;
        IColony colony = Colonists.colonyAt(player.level(), player.blockPosition());
        if (colony == null || colony.getID() != tag.getInt(IDENTITY_COLONY)) return;
        String name = tag.getString(IDENTITY);
        dropIdentity(player);
        tell(player, Component.translatable("lotmcraft.error.identity_exposed", name).withColor(BAD));
    }

    private static void dropIdentity(ServerPlayer player) {
        CompoundTag tag = data(player);
        tag.remove(IDENTITY);
        tag.remove(IDENTITY_COLONY);
        tag.remove(IDENTITY_TICKS);
        tag.remove(IDENTITY_DONE);
        player.refreshDisplayName();
    }

    public static boolean identityTrusted(ServerPlayer player) {
        CompoundTag tag = data(player);
        return tag.contains(IDENTITY) && tag.getBoolean(IDENTITY_DONE);
    }

    // ------------------------------------------------------------------ 1, Verme do Tempo

    public static boolean disorderDone(ServerPlayer player) {
        return data(player).getBoolean(DISORDER_DONE);
    }

    // Verme do Tempo: pendurar molduras e pôr relógios nelas dentro de qualquer colônia, sem permissão

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHangFrame(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (!preparing(player, 1) || !(held.is(Items.ITEM_FRAME) || held.is(Items.GLOW_ITEM_FRAME))) return;
        if (Colonists.colonyAt(event.getLevel(), event.getPos()) == null) return;
        event.setCanceled(true);
        InteractionResult result = held.useOn(new UseOnContext(player, event.getHand(), event.getHitVec()));
        event.setCancellationResult(result);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFrameSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof ItemFrame frame) || !clockIntoFrame(event.getEntity(), frame, event.getHand())) return;
        event.setCanceled(true);
        event.setCancellationResult(frame.interact(event.getEntity(), event.getHand()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFrame(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ItemFrame frame) || !clockIntoFrame(event.getEntity(), frame, event.getHand())) return;
        event.setCanceled(true);
        event.setCancellationResult(frame.interact(event.getEntity(), event.getHand()));
    }

    private static boolean clockIntoFrame(Player player, ItemFrame frame, InteractionHand hand) {
        return preparing(player, 1) && frame.getItem().isEmpty() && player.getItemInHand(hand).is(Items.CLOCK)
                && Colonists.colonyAt(frame.level(), frame.blockPosition()) != null;
    }

    private static List<ItemFrame> framedClocks(ServerLevel level, IColony colony) {
        List<ItemFrame> frames = new ArrayList<>();
        for (ItemFrame frame : level.getEntities(EntityTypeTest.forClass(ItemFrame.class), f -> f.getItem().is(Items.CLOCK))) {
            if (colony.isCoordInColony(level, frame.blockPosition())) frames.add(frame);
        }
        return frames;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 100 != 0) return;
        CompoundTag tag = data(player);

        // 2: depois de avançar (ou fora do Erro 3) a identidade emprestada fica para trás
        if (tag.contains(IDENTITY) && !preparing(player, 2)) dropIdentity(player);
        // 2: vivendo como o outro, dentro da colônia dele
        if (tag.contains(IDENTITY) && !tag.getBoolean(IDENTITY_DONE)) {
            IColony colony = Colonists.colonyAt(player.level(), player.blockPosition());
            if (colony != null && colony.getID() == tag.getInt(IDENTITY_COLONY)) {
                int ticks = tag.getInt(IDENTITY_TICKS) + 100;
                tag.putInt(IDENTITY_TICKS, ticks);
                if (ticks >= IDENTITY_TICKS_NEEDED) {
                    tag.putBoolean(IDENTITY_DONE, true);
                    if (preparing(player, 2)) tell(player, Component.translatable("lotmcraft.error.identity_done", tag.getString(IDENTITY)).withColor(COLOR));
                } else if (preparing(player, 2) && ticks % 2400 == 0) {
                    tell(player, Component.translatable("lotmcraft.error.identity_progress", tag.getString(IDENTITY), ticks * 100 / IDENTITY_TICKS_NEEDED).withColor(COLOR));
                }
            }
        }

        // 1: uma cidade em desordem temporal
        if (!tag.getBoolean(DISORDER_DONE) && preparing(player, 1)) tickDisorder(player, tag);
    }

    private static void tickDisorder(ServerPlayer player, CompoundTag tag) {
        ServerLevel level = player.serverLevel();
        IColony city = null;
        List<ItemFrame> clocks = List.of();
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (colony.getWorld() != level || colony.getCitizenManager().getCurrentCitizenCount() < CITY_CITIZENS) continue;
            if (!level.isLoaded(colony.getCenter())) continue;
            List<ItemFrame> found = framedClocks(level, colony);
            if (found.size() >= CLOCKS_NEEDED) { city = colony; clocks = found; break; }
        }
        int ticks = tag.getInt(DISORDER_TICKS);
        if (city == null) {
            // a cidade carregada perdeu relógios: a desordem acabou e a contagem recomeça
            if (ticks > 0 && tag.contains(DISORDER_COLONY)) {
                IColony previous = colonyById(tag.getInt(DISORDER_COLONY));
                if (previous != null && previous.getWorld() == level && level.isLoaded(previous.getCenter())) {
                    tag.putInt(DISORDER_TICKS, 0);
                    tell(player, Component.translatable("lotmcraft.error.disorder_broken", previous.getName()).withColor(BAD));
                }
            }
            return;
        }
        if (!tag.contains(DISORDER_COLONY) || tag.getInt(DISORDER_COLONY) != city.getID()) {
            tag.putInt(DISORDER_COLONY, city.getID());
            ticks = 0;
        }
        ticks += 100;
        tag.putInt(DISORDER_TICKS, ticks);
        disorderEffects(level, city, clocks);
        if (ticks >= DISORDER_TICKS_NEEDED) {
            tag.putBoolean(DISORDER_DONE, true);
            tell(player, Component.translatable("lotmcraft.error.disorder_done", city.getName()).withColor(COLOR));
        } else if (ticks % 2400 == 0) {
            tell(player, Component.translatable("lotmcraft.error.disorder_progress", city.getName(), ticks * 100 / DISORDER_TICKS_NEEDED).withColor(COLOR));
        }
    }

    // o tempo da cidade se embaralha: colonos ficam lentos ou rápidos ao acaso, e os relógios soltam faíscas
    private static void disorderEffects(ServerLevel level, IColony city, List<ItemFrame> clocks) {
        List<EntityCitizen> citizens = new ArrayList<>();
        for (ICitizenData data : city.getCitizenManager().getCitizens()) {
            data.getEntity().ifPresent(e -> { if (e instanceof EntityCitizen c && c.isAlive()) citizens.add(c); });
        }
        for (int i = 0; i < Math.min(4, citizens.size()); i++) {
            EntityCitizen citizen = citizens.get(level.random.nextInt(citizens.size()));
            if (level.random.nextBoolean()) TimeHandler.stealTime(citizen, 160);
            else TimeHandler.giftTime(citizen, 160);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, citizen.getX(), citizen.getY() + 1, citizen.getZ(), 15, .3, .5, .3, .02);
        }
        for (ItemFrame frame : clocks) {
            level.sendParticles(ParticleTypes.ENCHANT, frame.getX(), frame.getY(), frame.getZ(), 6, .2, .2, .2, .4);
        }
    }
}
