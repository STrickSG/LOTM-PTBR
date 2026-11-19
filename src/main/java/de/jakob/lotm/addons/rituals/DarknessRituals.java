package de.jakob.lotm.addons.rituals;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.colonies.Colonists;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItem;
import de.jakob.lotm.dimension.ModDimensions;
import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rituais de avanço da Escuridão. Cada um aceita vários jeitos, de qualquer Caminho:
 * <ul>
 *   <li>5, Bruxo Espiritual: no Mundo Espiritual, achar a informação do seu verdadeiro eu, fundir-se com ela e beber lá.</li>
 *   <li>4, Vigia da Noite: um círculo mágico (velas acesas, redstone, fogo de alma) e o sangue de uma linhagem divina (uma Característica).</li>
 *   <li>3, Bispo do Horror: beber à beira da morte (pouca vida e algo ferindo agora, ou um Totem acabou de salvar).</li>
 *   <li>2, Servo da Ocultação: 3 dias sozinho, no escuro e em silêncio, sem enlouquecer.</li>
 *   <li>1, Cavaleiro do Infortúnio: o infortúnio de um Arcanjo ou de uma civilização é você.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class DarknessRituals {
    private DarknessRituals() {}

    public static final int SELF_MIN = 80, SELF_MAX = 160, SELF_REACH = 3, SELF_HINT_SECONDS = 10;
    public static final int CIRCLE_MIN = 2, CIRCLE_MAX = 5, CIRCLE_MARKS = 8;
    public static final float HORROR_HEALTH = 0.25f;
    public static final int HORROR_HURT_WINDOW = 20 * 5, TOTEM_WINDOW = 20 * 30;
    public static final int ISOLATION_NEEDED = 24000 * 3, ISOLATION_RADIUS = 48, DARK_LIGHT = 4, GRACE_SECONDS = 10;
    public static final float SANE = 0.5f;
    public static final int ANGEL_WINDOW = 24000, CIVILIZATION_PEOPLE = 30, CIVILIZATION_WINDOW = 24000 * 7, ACTION_WINDOW = 20 * 3;

    private static final String SELF_X = "LotmDarkSelfX", SELF_Y = "LotmDarkSelfY", SELF_Z = "LotmDarkSelfZ", SELF_MERGED = "LotmDarkSelfMerged",
            SELF_LAST = "LotmDarkSelfLast", LAST_HURT = "LotmDarkLastHurt", TOTEM = "LotmDarkTotem", ISOLATION = "LotmDarkIsolation",
            GRACE = "LotmDarkGrace", BROKEN = "LotmDarkBroken", ANGEL_FELL = "LotmDarkAngelFell", LAST_ACTION = "LotmDarkLastAction",
            CIVILIZATIONS = "LotmDarkCivilizations", CIVILIZATION_DONE = "LotmDarkCivilizationDone";
    private static final int COLOR = 0x8E7CC3, BAD = 0xFF7A7A;

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Está na Sequência de onde se faz o ritual da Sequência {@code ritualSequence} da Escuridão */
    private static boolean preparing(Player player, int ritualSequence) {
        return BeyonderData.isBeyonder(player) && "darkness".equals(BeyonderData.getPathway(player))
                && BeyonderData.getSequence(player) == ritualSequence + 1;
    }

    private static long now(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    private static boolean isPerson(Entity entity) {
        return entity instanceof AbstractEntityCitizen || entity instanceof AbstractVillager || entity instanceof Player
                || entity instanceof BeyonderNPCEntity;
    }

    private static float sanity(Player player) {
        return player.getData(ModAttachments.SANITY_COMPONENT).getSanity();
    }

    // ------------------------------------------------------------------ 5, Bruxo Espiritual: o verdadeiro eu

    private static boolean inSpiritWorld(Player player) {
        return player.level().dimension().equals(ModDimensions.SPIRIT_WORLD_DIMENSION_KEY);
    }

    /** Fundiu-se com a informação do seu verdadeiro eu, e ainda está no Mundo Espiritual */
    public static boolean mergedWithSelf(ServerPlayer player) {
        return inSpiritWorld(player) && data(player).getBoolean(SELF_MERGED);
    }

    private static BlockPos truePos(CompoundTag tag) {
        return new BlockPos(tag.getInt(SELF_X), tag.getInt(SELF_Y), tag.getInt(SELF_Z));
    }

    private static void tickSelf(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (!inSpiritWorld(player)) return;
        if (tag.getBoolean(SELF_MERGED)) return;
        if (!tag.contains(SELF_X)) {
            // a informação sobre você mesmo fica em algum lugar longe daqui
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            int distance = SELF_MIN + player.getRandom().nextInt(SELF_MAX - SELF_MIN + 1);
            tag.putInt(SELF_X, player.getBlockX() + (int) (Math.cos(angle) * distance));
            tag.putInt(SELF_Y, player.getBlockY());
            tag.putInt(SELF_Z, player.getBlockZ() + (int) (Math.sin(angle) * distance));
            tag.putDouble(SELF_LAST, distance);
            tell(player, Component.translatable("lotmcraft.darkness.self_felt").withColor(COLOR));
            return;
        }
        BlockPos target = truePos(tag);
        double horizontal = Math.sqrt(Math.pow(player.getX() - target.getX() - .5, 2) + Math.pow(player.getZ() - target.getZ() - .5, 2));
        if (horizontal <= SELF_REACH) {
            tag.putBoolean(SELF_MERGED, true);
            tell(player, Component.translatable("lotmcraft.darkness.self_merged").withColor(COLOR));
            player.serverLevel().sendParticles(player, ParticleTypes.END_ROD, true, target.getX() + .5, player.getY() + 1, target.getZ() + .5, 60, .6, 1, .6, .05);
            return;
        }
        // perto o bastante para ver: uma luz que não é de ninguém
        if (horizontal <= 64) {
            player.serverLevel().sendParticles(player, ParticleTypes.END_ROD, true, target.getX() + .5, player.getY() + 1.2, target.getZ() + .5, 8, .3, .6, .3, .01);
        }
        if (player.tickCount % (20 * SELF_HINT_SECONDS) < 20) {
            double last = tag.getDouble(SELF_LAST);
            String band = horizontal > 120 ? "far" : horizontal > 60 ? "away" : horizontal > 20 ? "near" : "close";
            String trend = horizontal < last - 2 ? "warmer" : horizontal > last + 2 ? "colder" : "same";
            tag.putDouble(SELF_LAST, horizontal);
            tell(player, Component.translatable("lotmcraft.darkness.self_" + band)
                    .append(Component.translatable("lotmcraft.darkness.self_" + trend)).withColor(COLOR));
        }
    }

    /** Adivinhar no Mundo Espiritual (de qualquer Caminho) mostra para onde ir */
    private static void divine(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (!inSpiritWorld(player) || !tag.contains(SELF_X) || tag.getBoolean(SELF_MERGED)) return;
        BlockPos target = truePos(tag);
        double dx = target.getX() + .5 - player.getX(), dz = target.getZ() + .5 - player.getZ();
        String direction = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
        player.displayClientMessage(Component.translatable("lotmcraft.darkness.self_divined",
                Component.translatable("lotmcraft.darkness.dir_" + direction), (int) Math.sqrt(dx * dx + dz * dz)).withColor(COLOR), false);
    }

    // ------------------------------------------------------------------ 4, Vigia da Noite: o círculo e o sangue

    private static boolean mark(BlockState state) {
        if (state.is(BlockTags.CANDLES)) return state.getValue(CandleBlock.LIT);
        if (state.is(Blocks.SOUL_CAMPFIRE)) return state.getValue(CampfireBlock.LIT);
        return state.is(Blocks.REDSTONE_WIRE) || state.is(Blocks.SOUL_LANTERN) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)
                || state.is(Blocks.SOUL_FIRE);
    }

    /** Marcas do círculo em volta de você (de 2 a 5 blocos), espalhadas pelos quatro lados */
    public static int circleMarks(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        int count = 0;
        boolean[] sides = new boolean[4];
        for (int dx = -CIRCLE_MAX; dx <= CIRCLE_MAX; dx++) {
            for (int dz = -CIRCLE_MAX; dz <= CIRCLE_MAX; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < CIRCLE_MIN || d > CIRCLE_MAX + .5) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    if (!mark(level.getBlockState(center.offset(dx, dy, dz)))) continue;
                    count++;
                    sides[(dx >= 0 ? 1 : 0) + (dz >= 0 ? 2 : 0)] = true;
                    break;
                }
            }
        }
        for (boolean side : sides) if (!side) return 0;
        return count;
    }

    private static boolean divineBlood(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof BeyonderCharacteristicItem) return true;
        }
        return false;
    }

    public static boolean circleDrawn(ServerPlayer player) {
        return divineBlood(player) && circleMarks(player) >= CIRCLE_MARKS;
    }

    // ------------------------------------------------------------------ 3, Bispo do Horror: diante da morte

    /** À beira da morte agora: pouca vida e algo ferindo há pouco, ou um Totem acabou de salvá-lo */
    public static boolean facingDeath(ServerPlayer player) {
        CompoundTag tag = data(player);
        long time = now(player.serverLevel());
        if (tag.contains(TOTEM) && time - tag.getLong(TOTEM) <= TOTEM_WINDOW) return true;
        return player.getHealth() <= player.getMaxHealth() * HORROR_HEALTH && tag.contains(LAST_HURT) && time - tag.getLong(LAST_HURT) <= HORROR_HURT_WINDOW;
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0 && preparing(player, 3)) {
            data(player).putLong(LAST_HURT, now(player.serverLevel()));
        }
    }

    @SubscribeEvent
    public static void onTotem(LivingUseTotemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && preparing(player, 3)) {
            data(player).putLong(TOTEM, now(player.serverLevel()));
            tell(player, Component.translatable("lotmcraft.darkness.faced_death").withColor(COLOR));
        }
    }

    // ------------------------------------------------------------------ 2, Servo da Ocultação: isolado, no escuro, em silêncio

    public static int isolatedTicks(ServerPlayer player) {
        return data(player).getInt(ISOLATION);
    }

    public static boolean concealed(ServerPlayer player) {
        return isolatedTicks(player) >= ISOLATION_NEEDED && sanity(player) >= SANE;
    }

    /** Algo quebrou o isolamento: falou, mexeu com alguém, alguém mexeu com você */
    private static void breakIsolation(ServerPlayer player, String why) {
        CompoundTag tag = data(player);
        if (tag.getInt(ISOLATION) > 0 && tag.getInt(ISOLATION) < ISOLATION_NEEDED) {
            player.displayClientMessage(Component.translatable("lotmcraft.darkness.isolation_broken",
                    Component.translatable("lotmcraft.darkness.why_" + why)).withColor(BAD), false);
        }
        if (tag.getInt(ISOLATION) < ISOLATION_NEEDED) tag.putInt(ISOLATION, 0);
        tag.putInt(GRACE, 0);
    }

    private static String isolationProblem(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (player.isSleeping()) return "sleep";
        if (level.getMaxLocalRawBrightness(player.blockPosition()) > DARK_LIGHT) return "light";
        boolean company = !level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ISOLATION_RADIUS),
                e -> e != player && e.isAlive() && isPerson(e) && !(e instanceof Player p && p.isSpectator())).isEmpty();
        return company ? "company" : null;
    }

    private static void tickIsolation(ServerPlayer player) {
        CompoundTag tag = data(player);
        int ticks = tag.getInt(ISOLATION);
        if (ticks >= ISOLATION_NEEDED) return;
        String problem = isolationProblem(player);
        if (problem != null) {
            // um pouco de folga (comer, passar por uma luz), mas não muito
            int grace = tag.getInt(GRACE) + 1;
            tag.putInt(GRACE, grace);
            if (grace > GRACE_SECONDS) breakIsolation(player, problem);
            return;
        }
        tag.putInt(GRACE, 0);
        ticks += 20;
        tag.putInt(ISOLATION, ticks);
        if (ticks >= ISOLATION_NEEDED) tell(player, Component.translatable("lotmcraft.darkness.isolation_done").withColor(COLOR));
        else if (ticks % 2400 == 0) tell(player, Component.translatable("lotmcraft.darkness.isolation_progress", ticks * 100 / ISOLATION_NEEDED).withColor(COLOR));
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (preparing(event.getPlayer(), 2)) breakIsolation(event.getPlayer(), "chat");
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && preparing(player, 2) && isPerson(event.getTarget())) breakIsolation(player, "touch");
    }

    // ------------------------------------------------------------------ 1, Cavaleiro do Infortúnio

    /** O infortúnio de um Arcanjo (há pouco) ou de uma civilização é você */
    public static boolean misfortuneBrought(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (tag.getBoolean(CIVILIZATION_DONE)) return true;
        return tag.contains(ANGEL_FELL) && now(player.serverLevel()) - tag.getLong(ANGEL_FELL) <= ANGEL_WINDOW;
    }

    private static void angelMisfortune(ServerPlayer player) {
        data(player).putLong(ANGEL_FELL, now(player.serverLevel()));
        tell(player, Component.translatable("lotmcraft.darkness.angel_misfortune").withColor(COLOR));
    }

    private static boolean archangel(LivingEntity entity) {
        return BeyonderData.isBeyonder(entity) && BeyonderData.getSequence(entity) <= 1;
    }

    /** Quem feriu cada Arcanjo (se ele cair, o infortúnio dele foi você) */
    private static final Map<UUID, Set<UUID>> ANGEL_HURT_BY = new HashMap<>();

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            if (preparing(player, 2) && isPerson(target)) breakIsolation(player, "touch");
            if (preparing(player, 1) && archangel(target)) ANGEL_HURT_BY.computeIfAbsent(target.getUUID(), k -> new HashSet<>()).add(player.getUUID());
        }
        if (target instanceof ServerPlayer player && preparing(player, 2) && event.getSource().getEntity() != null
                && isPerson(event.getSource().getEntity())) breakIsolation(player, "touch");
    }

    /** O seu Azar (de poção, flecha ou poder) num Arcanjo */
    @SubscribeEvent
    public static void onBadLuck(MobEffectEvent.Added event) {
        if (!event.getEffectInstance().getEffect().equals(MobEffects.UNLUCK) || !archangel(event.getEntity())) return;
        if (event.getEffectSource() instanceof ServerPlayer player && preparing(player, 1)) angelMisfortune(player);
    }

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String id = event.getAbility().getId();
        data(player).putLong(LAST_ACTION, now(player.serverLevel()));
        // 5: adivinhar no Mundo Espiritual
        if (preparing(player, 5) && (id.contains("divination") || id.contains("prophecy") || id.contains("spirit_vision")
                || id.contains("dowsing") || id.contains("clairvoyance"))) divine(player);
        // 1: um poder de infortúnio perto de um Arcanjo
        if (preparing(player, 1) && (id.contains("misfortune") || id.contains("curse") || id.contains("calamity"))) {
            boolean angelNear = !player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32),
                    e -> e != player && e.isAlive() && archangel(e)).isEmpty();
            if (angelNear) angelMisfortune(player);
        }
    }

    /** O jogador que causou essa morte: a fonte, ou quem usou um poder por perto agora há pouco */
    private static ServerPlayer culprit(ServerLevel level, Entity source, LivingEntity dead) {
        if (source instanceof ServerPlayer player) return player;
        if (source != null) return null;
        for (ServerPlayer player : level.players()) {
            if (preparing(player, 1) && player.distanceTo(dead) <= 64 && now(level) - data(player).getLong(LAST_ACTION) <= ACTION_WINDOW) return player;
        }
        return null;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        // um Arcanjo que você feriu caiu
        Set<UUID> hurtBy = ANGEL_HURT_BY.remove(dead.getUUID());
        if (hurtBy != null) {
            for (UUID id : hurtBy) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null && preparing(player, 1)) angelMisfortune(player);
            }
        }
        // uma civilização que você está arruinando: colônia ou aldeia
        if (!(dead instanceof AbstractEntityCitizen) && !(dead instanceof net.minecraft.world.entity.npc.Villager)) return;
        ServerPlayer player = culprit(level, event.getSource().getEntity(), dead);
        if (player == null || !preparing(player, 1)) return;
        String key;
        int population;
        var citizen = Colonists.data(dead);
        if (citizen != null) {
            key = "colony:" + citizen.getColony().getID();
            population = citizen.getColony().getCitizenManager().getCurrentCitizenCount();
        } else {
            // a mesma aldeia: a até 96 blocos de onde o infortúnio dela começou
            key = null;
            CompoundTag known = data(player).getCompound(CIVILIZATIONS);
            for (String k : known.getAllKeys()) {
                CompoundTag c = known.getCompound(k);
                if (k.startsWith("village:") && c.getString("dim").equals(level.dimension().location().toString())
                        && Math.hypot(c.getInt("x") - dead.getBlockX(), c.getInt("z") - dead.getBlockZ()) <= 96) { key = k; break; }
            }
            if (key == null) key = "village:" + level.dimension().location() + ":" + dead.getBlockX() + ":" + dead.getBlockZ();
            // moradores da aldeia: aldeões (o comerciante ambulante só está de passagem)
            population = level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, dead.getBoundingBox().inflate(96), Entity::isAlive).size();
        }
        CompoundTag all = data(player).getCompound(CIVILIZATIONS);
        CompoundTag civ = all.getCompound(key);
        long time = now(level);
        if (!civ.contains("start") || time - civ.getLong("start") > CIVILIZATION_WINDOW) {
            civ = new CompoundTag();
            civ.putLong("start", time);
            // o tamanho dela quando o infortúnio começou (contando quem acabou de morrer)
            civ.putInt("size", population + 1);
            civ.putString("dim", level.dimension().location().toString());
            civ.putInt("x", dead.getBlockX());
            civ.putInt("z", dead.getBlockZ());
        }
        civ.putInt("dead", civ.getInt("dead") + 1);
        all.put(key, civ);
        data(player).put(CIVILIZATIONS, all);
        int size = civ.getInt("size"), deaths = civ.getInt("dead");
        if (size < CIVILIZATION_PEOPLE) return;
        int needed = (size + 1) / 2;
        if (deaths >= needed) {
            data(player).putBoolean(CIVILIZATION_DONE, true);
            tell(player, Component.translatable("lotmcraft.darkness.civilization_done").withColor(COLOR));
        } else if (deaths % 5 == 0) {
            tell(player, Component.translatable("lotmcraft.darkness.civilization_progress", deaths, needed).withColor(COLOR));
        }
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (preparing(player, 5)) tickSelf(player);
        if (preparing(player, 2)) tickIsolation(player);
    }
}
