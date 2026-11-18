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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
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
    /** Reputação com o aldeão para ele dar tudo de boa vontade (negociar bastante, ser Herói da Vila...) */
    public static final int VILLAGER_DEVOTION = 30;
    public static final int ORDER_MIN_CITIZENS = 10;
    public static final double COLLAPSED_HAPPINESS = 3.0;
    public static final long PEACE_TICKS = 24000;
    public static final int IDENTITY_TICKS_NEEDED = 24000 * 6;
    public static final int CITY_CITIZENS = 20, CLOCKS_NEEDED = 12, DISORDER_TICKS_NEEDED = 24000 * 7;

    private static final String DREAMS = "LotmErrorDreams", HOSTS = "LotmErrorHosts", HOSTS_OTHERS = "LotmErrorHostsOthers", LAST_VIOLENCE = "LotmErrorLastViolence",
            IDENTITY = "LotmErrorIdentity", IDENTITY_COLONY = "LotmErrorIdentityColony",
            IDENTITY_HOME = "LotmErrorIdentityHome", IDENTITY_DIMENSION = "LotmErrorIdentityDimension", IDENTITY_TICKS = "LotmErrorIdentityTicks",
            IDENTITY_DONE = "LotmErrorIdentityDone", DISORDER_KEY = "LotmErrorDisorderCity", DISORDER_SEEN = "LotmErrorDisorderSeen", DISORDER_NAME = "LotmErrorDisorderName", DISORDER_TICKS = "LotmErrorDisorderTicks",
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

    // ------------------------------------------------------------------ pessoas
    // Vale gente de todo tipo: colonos, aldeões, Beyonders Errantes e outros jogadores.

    private static boolean isPerson(Entity entity) {
        return entity instanceof EntityCitizen citizen ? citizen.getCitizenData() != null
                : entity instanceof AbstractVillager || entity instanceof BeyonderNPCEntity || entity instanceof Player;
    }

    /** Identidade fixa: a do colono nos dados da colônia, a da entidade para os outros */
    private static UUID personId(Entity entity) {
        UUID citizen = Colonists.id(entity);
        return citizen != null ? citizen : entity.getUUID();
    }

    private static String nameOf(Entity entity) {
        return entity instanceof EntityCitizen citizen && citizen.getCitizenData() != null ? citizen.getCitizenData().getName() : entity.getName().getString();
    }

    /** Dormindo de verdade, ou adormecido por um poder */
    private static boolean asleep(LivingEntity entity) {
        if (entity instanceof EntityCitizen citizen && citizen.getCitizenSleepHandler().isAsleep()) return true;
        return entity.isSleeping() || entity.hasEffect(ModEffects.ASLEEP);
    }

    // ------------------------------------------------------------------ interações
    // O Erro é uma falha, um bug: faz os rituais na colônia dos outros, mesmo sem permissão. Por isso estes eventos rodam
    // antes da proteção de colônia do MineColonies e fazem a ação eles mesmos.

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof LivingEntity target) || !isPerson(target)) return;
        Player player = event.getEntity();
        if (target == player) return;
        ItemStack held = player.getMainHandItem();
        boolean dream = preparing(player, 5) && player.isShiftKeyDown() && held.isEmpty();
        boolean host = preparing(player, 4) && player.isShiftKeyDown() && held.isEmpty() && !(target instanceof Player);
        boolean identity = preparing(player, 2) && held.is(Items.NAME_TAG) && held.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                && (target instanceof EntityCitizen || target instanceof AbstractVillager || target instanceof BeyonderNPCEntity);
        if (!dream && !host && !identity) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (dream) enterDream(serverPlayer, target);
        else if (host) askOffering(serverPlayer, target);
        else takeIdentity(serverPlayer, target, held);
    }

    // ------------------------------------------------------------------ 5, Ladrão de Sonhos

    /** Poderes de sonho de qualquer Caminho: quem os usa entra no sonho de quem estiver dormindo por perto */
    private static final Set<String> DREAM_ABILITIES = Set.of("nightmare_ability", "dream_traversal_ability", "dream_weave_ability",
            "dream_maze_ability", "nightmare_spectator_ability", "midnight_poem_ability");

    private static void enterDream(ServerPlayer player, LivingEntity sleeper) {
        String name = nameOf(sleeper);
        if (!asleep(sleeper)) {
            tell(player, Component.translatable("lotmcraft.error.dream_awake", name).withColor(BAD));
            return;
        }
        markDreams(player, List.of(sleeper));
        tell(player, Component.translatable("lotmcraft.error.dream_entered", name, sleepingDreams(player)).withColor(COLOR));
    }

    private static void markDreams(ServerPlayer player, List<LivingEntity> sleepers) {
        CompoundTag tag = data(player);
        Set<UUID> dreams = uuids(tag, DREAMS);
        ServerLevel level = player.serverLevel();
        for (LivingEntity sleeper : sleepers) {
            dreams.add(personId(sleeper));
            level.sendParticles(ParticleTypes.WITCH, sleeper.getX(), sleeper.getY() + .6, sleeper.getZ(), 25, .4, .3, .4, .02);
        }
        if (!sleepers.isEmpty()) level.playSound(null, sleepers.get(0).blockPosition(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.PLAYERS, .6f, .5f);
        putUuids(tag, DREAMS, dreams);
    }

    private static void dreamPowers(ServerPlayer player, String ability) {
        if (!preparing(player, 5) || !DREAM_ABILITIES.contains(ability)) return;
        // o poder age agora; um instante depois, quem estiver dormindo por perto tem você no sonho
        ServerScheduler.scheduleDelayed(15, () -> {
            if (!player.isAlive()) return;
            List<LivingEntity> sleepers = new ArrayList<>();
            for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24))) {
                if (entity != player && isPerson(entity) && asleep(entity)) sleepers.add(entity);
            }
            if (sleepers.isEmpty()) return;
            markDreams(player, sleepers);
            tell(player, Component.translatable("lotmcraft.error.dream_entered", nameOf(sleepers.get(0)), sleepingDreams(player)).withColor(COLOR));
        }, player.serverLevel());
    }

    /** Pessoas em cujo sonho você entrou e que estão dormindo agora, a até 48 blocos */
    public static int sleepingDreams(ServerPlayer player) {
        Set<UUID> dreams = uuids(data(player), DREAMS);
        int count = 0;
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DREAM_RADIUS))) {
            if (entity != player && isPerson(entity) && asleep(entity) && dreams.contains(personId(entity))) count++;
        }
        return count;
    }

    public static boolean inDreams(ServerPlayer player) {
        return sleepingDreams(player) >= DREAMS_NEEDED;
    }

    // ------------------------------------------------------------------ 4, Parasita

    /** Dá tudo de boa vontade: colono feliz, aldeão que gosta muito de você, ou quem já é seu aliado (convencido, enganado...) */
    private static boolean willing(ServerPlayer player, LivingEntity target) {
        if (AllyUtil.areAllies(player, target)) return true;
        if (target instanceof EntityCitizen citizen && citizen.getCitizenData() != null) {
            ICitizenData data = citizen.getCitizenData();
            return data.getCitizenHappinessHandler().getHappiness(data.getColony(), data) >= WILLING_HAPPINESS;
        }
        return target instanceof Villager villager && villager.getPlayerReputation(player) >= VILLAGER_DEVOTION;
    }

    private static void askOffering(ServerPlayer player, LivingEntity target) {
        String name = nameOf(target);
        CompoundTag tag = data(player);
        // colonos são conferidos pelos dados da colônia; os outros, pela lista de mortos
        String key = target instanceof EntityCitizen ? HOSTS : HOSTS_OTHERS;
        Set<UUID> hosts = uuids(tag, key);
        UUID id = personId(target);
        if (hosts.contains(id)) {
            tell(player, Component.translatable("lotmcraft.error.host_already", name).withColor(COLOR));
            return;
        }
        if (!willing(player, target)) {
            target.playSound(SoundEvents.VILLAGER_NO, 1f, 1f);
            tell(player, Component.translatable("lotmcraft.error.host_unwilling", name).withColor(BAD));
            return;
        }
        // tudo o que ele tem passa para você
        List<ItemStack> given = new ArrayList<>();
        if (target instanceof EntityCitizen citizen) {
            var inventory = citizen.getCitizenData().getInventory();
            for (int slot = 0; slot < inventory.getSlots(); slot++) given.add(inventory.extractItem(slot, inventory.getStackInSlot(slot).getCount(), false));
        } else if (target instanceof AbstractVillager villager) {
            for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) given.add(villager.getInventory().removeItemNoUpdate(slot));
        }
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (target instanceof EntityCitizen) break; // o equipamento do colono já está no inventário dele
            given.add(target.getItemBySlot(slot).copy());
            target.setItemSlot(slot, ItemStack.EMPTY);
        }
        for (ItemStack stack : given) if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
        hosts.add(id);
        putUuids(tag, key, hosts);
        if (!(target instanceof EntityCitizen) && target instanceof net.minecraft.world.entity.Mob mob) mob.setPersistenceRequired();
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + 1, target.getZ(), 12, .3, .4, .3, .02);
        level.playSound(null, target.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1f, .8f);
        tell(player, Component.translatable("lotmcraft.error.host_offered", name, livingHosts(player), HOSTS_NEEDED).withColor(COLOR));
    }

    /** Quem se ofereceu e ainda está vivo */
    public static int livingHosts(ServerPlayer player) {
        Set<UUID> colonists = Colonists.allIds();
        Set<UUID> dead = deadHosts(player.server);
        int count = 0;
        for (UUID id : uuids(data(player), HOSTS)) if (colonists.contains(id)) count++;
        for (UUID id : uuids(data(player), HOSTS_OTHERS)) if (!dead.contains(id)) count++;
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
        if (!isPerson(event.getEntity())) return;
        UUID id = personId(event.getEntity());
        for (String key : new String[]{HOSTS, HOSTS_OTHERS}) {
            Set<UUID> hosts = uuids(tag, key);
            if (!hosts.remove(id)) continue;
            putUuids(tag, key, hosts);
            if (preparing(player, 4)) tell(player, Component.translatable("lotmcraft.error.host_lost", event.getEntity().getName()).withColor(BAD));
        }
    }

    // quem morre não sustenta mais ninguém (aldeões e Beyonders Errantes; colonos são vistos pelos dados da colônia)
    @SubscribeEvent
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getServer() == null || entity instanceof EntityCitizen || !(entity instanceof AbstractVillager || entity instanceof BeyonderNPCEntity)) return;
        DeadHosts dead = entity.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(DeadHosts::new, DeadHosts::load, null), "lotmcraft_error_dead");
        dead.add(entity.getUUID());
    }

    private static Set<UUID> deadHosts(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(DeadHosts::new, DeadHosts::load, null), "lotmcraft_error_dead").ids;
    }

    private static final class DeadHosts extends SavedData {
        private final Set<UUID> ids = new java.util.LinkedHashSet<>();

        void add(UUID id) {
            ids.add(id);
            while (ids.size() > 4096) ids.remove(ids.iterator().next());
            setDirty();
        }

        static DeadHosts load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            DeadHosts data = new DeadHosts();
            for (Tag t : tag.getList("Dead", Tag.TAG_INT_ARRAY)) data.ids.add(NbtUtils.loadUUID(t));
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            ids.forEach(id -> list.add(NbtUtils.createUUID(id)));
            tag.put("Dead", list);
            return tag;
        }
    }

    // ------------------------------------------------------------------ 3, Mentor do Engano

    /** A ordem de uma região desmoronou e você não feriu ninguém no último dia */
    public static boolean orderCollapsed(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (tag.contains(LAST_VIOLENCE) && now(player) - tag.getLong(LAST_VIOLENCE) < PEACE_TICKS) return false;
        IColony colony = Colonists.colonyAt(player.level(), player.blockPosition());
        if (colony != null && colony.getCitizenManager().getCurrentCitizenCount() >= ORDER_MIN_CITIZENS
                && colony.getOverallHappiness() <= COLLAPSED_HAPPINESS) return true;
        return villageCollapsed(player);
    }

    /** Vila comum: as regras dela são camas e postos de trabalho; quase todos os aldeões sem casa e sem trabalho */
    private static boolean villageCollapsed(ServerPlayer player) {
        List<Villager> villagers = player.serverLevel().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(48), v -> !v.isBaby());
        if (villagers.size() < ORDER_MIN_CITIZENS) return false;
        int lost = 0;
        for (Villager villager : villagers) {
            var brain = villager.getBrain();
            if (brain.getMemory(MemoryModuleType.HOME).isEmpty() && brain.getMemory(MemoryModuleType.JOB_SITE).isEmpty()) lost++;
        }
        return lost * 10 >= villagers.size() * 8;
    }

    // ------------------------------------------------------------------ 2, Cavalo de Troia do Destino

    /** Tomar o lugar de alguém (colono, aldeão ou Beyonder Errante) com uma etiqueta com o nome dele */
    private static void takeIdentity(ServerPlayer player, LivingEntity target, ItemStack nameTag) {
        String name = nameOf(target);
        if (!target.hasCustomName() && !(target instanceof EntityCitizen) || !nameTag.getHoverName().getString().strip().equalsIgnoreCase(name.strip())) {
            tell(player, Component.translatable("lotmcraft.error.identity_wrong_name", name).withColor(BAD));
            return;
        }
        CompoundTag tag = data(player);
        tag.putString(IDENTITY, name);
        IColony colony = target instanceof EntityCitizen citizen ? citizen.getCitizenData().getColony() : Colonists.colonyAt(target.level(), target.blockPosition());
        if (colony != null) {
            tag.putInt(IDENTITY_COLONY, colony.getID());
            tag.remove(IDENTITY_HOME);
        } else {
            // fora de uma colônia, o lar dele é onde ele dormia (ou onde estava), num raio de 48 blocos
            BlockPos home = target instanceof Villager villager
                    ? villager.getBrain().getMemory(MemoryModuleType.HOME).map(net.minecraft.core.GlobalPos::pos).orElse(target.blockPosition())
                    : target.blockPosition();
            tag.remove(IDENTITY_COLONY);
            tag.putLong(IDENTITY_HOME, home.asLong());
            tag.putString(IDENTITY_DIMENSION, target.level().dimension().location().toString());
        }
        tag.putInt(IDENTITY_TICKS, 0);
        tag.remove(IDENTITY_DONE);
        if (!player.getAbilities().instabuild) nameTag.shrink(1);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX(), target.getY() + 1, target.getZ(), 30, .3, .6, .3, .02);
        level.playSound(null, target.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1f, 1f);
        // ele some do mundo; você fica no lugar dele
        if (target instanceof EntityCitizen citizen) citizen.getCitizenData().getColony().getCitizenManager().removeCivilian(citizen.getCitizenData());
        if (target.isAlive()) target.discard();
        player.refreshDisplayName();
        tell(player, Component.translatable("lotmcraft.error.identity_taken", name).withColor(COLOR));
    }

    /** Está vivendo entre os do outro: dentro da colônia dele, ou a até 48 blocos do lar dele */
    private static boolean amongTheirPeople(ServerPlayer player, CompoundTag tag) {
        if (tag.contains(IDENTITY_COLONY)) {
            IColony colony = Colonists.colonyAt(player.level(), player.blockPosition());
            return colony != null && colony.getID() == tag.getInt(IDENTITY_COLONY);
        }
        if (!tag.contains(IDENTITY_HOME) || !player.level().dimension().location().toString().equals(tag.getString(IDENTITY_DIMENSION))) return false;
        return BlockPos.of(tag.getLong(IDENTITY_HOME)).distSqr(player.blockPosition()) <= 48 * 48;
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (event.getEntity().level().isClientSide) return;
        CompoundTag tag = data(event.getEntity());
        if (tag.contains(IDENTITY)) event.setDisplayname(Component.literal(tag.getString(IDENTITY)));
    }

    // usar qualquer poder entre os dele entrega o disfarce; poderes de sonho servem ao Ladrão de Sonhos
    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        dreamPowers(player, event.getAbility().getId());
        CompoundTag tag = data(player);
        if (!tag.contains(IDENTITY) || tag.getBoolean(IDENTITY_DONE)) return;
        if (!amongTheirPeople(player, tag)) return;
        String name = tag.getString(IDENTITY);
        dropIdentity(player);
        tell(player, Component.translatable("lotmcraft.error.identity_exposed", name).withColor(BAD));
    }

    private static void dropIdentity(ServerPlayer player) {
        CompoundTag tag = data(player);
        tag.remove(IDENTITY);
        tag.remove(IDENTITY_COLONY);
        tag.remove(IDENTITY_HOME);
        tag.remove(IDENTITY_DIMENSION);
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
            if (amongTheirPeople(player, tag)) {
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

    /** Uma cidade com 12 relógios: colônia de 20 colonos ou mais, ou vila comum com 20 aldeões ou mais */
    private record City(String key, String name, List<ItemFrame> clocks, List<LivingEntity> people) {}

    private static City findCity(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (colony.getWorld() != level || colony.getCitizenManager().getCurrentCitizenCount() < CITY_CITIZENS) continue;
            if (!level.isLoaded(colony.getCenter())) continue;
            List<ItemFrame> found = framedClocks(level, colony);
            if (found.size() < CLOCKS_NEEDED) continue;
            List<LivingEntity> people = new ArrayList<>();
            for (ICitizenData data : colony.getCitizenManager().getCitizens()) {
                data.getEntity().ifPresent(e -> { if (e.isAlive()) people.add(e); });
            }
            return new City("colony:" + colony.getID(), colony.getName(), found, people);
        }
        // vila comum perto de você
        List<ItemFrame> near = level.getEntitiesOfClass(ItemFrame.class, player.getBoundingBox().inflate(96), f -> f.getItem().is(Items.CLOCK));
        if (near.size() < CLOCKS_NEEDED) return null;
        double x = 0, z = 0;
        for (ItemFrame f : near) { x += f.getX(); z += f.getZ(); }
        BlockPos center = BlockPos.containing(x / near.size(), player.getY(), z / near.size());
        List<LivingEntity> villagers = new ArrayList<>(level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(center).inflate(64, 48, 64)));
        if (villagers.size() < CITY_CITIZENS) return null;
        return new City("village:" + (center.getX() >> 6) + "," + (center.getZ() >> 6), Component.translatable("lotmcraft.error.village").getString(), near, villagers);
    }

    private static void tickDisorder(ServerPlayer player, CompoundTag tag) {
        ServerLevel level = player.serverLevel();
        City city = findCity(player);
        int ticks = tag.getInt(DISORDER_TICKS);
        if (city == null) {
            // a cidade carregada (com você por perto) perdeu relógios: a desordem acabou e a contagem recomeça
            if (ticks > 0 && tag.contains(DISORDER_KEY) && tag.getString(DISORDER_KEY).equals(tag.getString(DISORDER_SEEN))) {
                tag.putInt(DISORDER_TICKS, 0);
                tell(player, Component.translatable("lotmcraft.error.disorder_broken", tag.getString(DISORDER_NAME)).withColor(BAD));
            }
            tag.remove(DISORDER_SEEN);
            return;
        }
        tag.putString(DISORDER_SEEN, city.key());
        if (!city.key().equals(tag.getString(DISORDER_KEY))) {
            tag.putString(DISORDER_KEY, city.key());
            ticks = 0;
        }
        tag.putString(DISORDER_NAME, city.name());
        ticks += 100;
        tag.putInt(DISORDER_TICKS, ticks);
        disorderEffects(level, city.people(), city.clocks());
        if (ticks >= DISORDER_TICKS_NEEDED) {
            tag.putBoolean(DISORDER_DONE, true);
            tell(player, Component.translatable("lotmcraft.error.disorder_done", city.name()).withColor(COLOR));
        } else if (ticks % 2400 == 0) {
            tell(player, Component.translatable("lotmcraft.error.disorder_progress", city.name(), ticks * 100 / DISORDER_TICKS_NEEDED).withColor(COLOR));
        }
    }

    // o tempo da cidade se embaralha: os moradores ficam lentos ou rápidos ao acaso, e os relógios soltam faíscas
    private static void disorderEffects(ServerLevel level, List<LivingEntity> people, List<ItemFrame> clocks) {
        for (int i = 0; i < Math.min(4, people.size()); i++) {
            LivingEntity person = people.get(level.random.nextInt(people.size()));
            if (level.random.nextBoolean()) TimeHandler.stealTime(person, 160);
            else TimeHandler.giftTime(person, 160);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, person.getX(), person.getY() + 1, person.getZ(), 15, .3, .5, .3, .02);
        }
        for (ItemFrame frame : clocks) {
            level.sendParticles(ParticleTypes.ENCHANT, frame.getX(), frame.getY(), frame.getZ(), 6, .2, .2, .2, .4);
        }
    }
}
