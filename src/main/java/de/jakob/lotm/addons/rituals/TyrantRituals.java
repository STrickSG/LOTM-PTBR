package de.jakob.lotm.addons.rituals;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.colonies.Colonists;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AdvancementUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rituais de avanço do Tirano. Cada um aceita vários jeitos, de qualquer Caminho:
 * <ul>
 *   <li>5, Cantor do Oceano: beber "na barriga" do mar: dentro de um Monumento Oceânico ou numa câmara fechada sob as ondas.</li>
 *   <li>4, Enterrador do Cataclismo: causar um terremoto e um tsunami e beber no meio deles (poderes ou explosões; as debaixo d'água viram tsunami).</li>
 *   <li>3, Rei do Mar: uma torre de pedra do fundo do mar até a superfície e nenhuma criatura forte de pé em volta, 3 delas vencidas por você.</li>
 *   <li>2, Calamidade: 20 pessoas atingidas por um desastre seu; depois ele para, e você bebe diante dos que sobraram.</li>
 *   <li>1, Deus do Trovão: com raios, atrapalhar a ascensão de 3 Anjos de Sequência 2 (um de Sequência 1 vale os 3).</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class TyrantRituals {
    private TyrantRituals() {}

    public static final int CHAMBER_DEPTH = 8;
    public static final int CATACLYSM_WINDOW = 20 * 60, CATACLYSM_RADIUS = 64, CATACLYSM_EXPLOSIONS = 5;
    public static final int TOWER_SEARCH = 24, TOWER_MIN_WATER = 8, SEA_RADIUS = 96, SEA_KILLS = 3, SEA_WINDOW = 24000 * 3;
    public static final int CALAMITY_PEOPLE = 20, CALAMITY_WINDOW = 20 * 60 * 10, CALM_TICKS = 20 * 60, CALM_DRINK = 20 * 60 * 5,
            WITNESSES = 10, WITNESS_RADIUS = 48, ALONE_RADIUS = 64, ACTION_WINDOW = 20 * 3;
    public static final int ANGELS_NEEDED = 3;

    private static final String LAST_ACTION = "LotmTyrLastAction", SEA_KILLS_TAG = "LotmTyrSeaKills", CALAMITY = "LotmTyrCalamity",
            CALAMITY_LAST = "LotmTyrCalamityLast", CALAMITY_START = "LotmTyrCalamityStart", ANGELS = "LotmTyrAngels";
    private static final int COLOR = 0x5FA8E8;

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Está na Sequência de onde se faz o ritual da Sequência {@code ritualSequence} do Tirano */
    private static boolean preparing(Player player, int ritualSequence) {
        return BeyonderData.isBeyonder(player) && "tyrant".equals(BeyonderData.getPathway(player))
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

    private static String personKey(LivingEntity entity) {
        UUID id = Colonists.id(entity);
        return (id != null ? id : entity.getUUID()).toString();
    }

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        LivingEntity user = event.getEntity();
        if (!(user.level() instanceof ServerLevel level)) return;
        String id = event.getAbility().getId();
        boolean quake = id.contains("earthquake") || id.contains("quake") || id.contains("tremor") || id.contains("collapse");
        boolean wave = id.contains("tsunami") || id.contains("flood") || id.contains("tide") || id.contains("wrath_of_the_ocean")
                || id.contains("torrential") || id.contains("deluge");
        if (quake || wave) cataclysm(level, user.position(), user, quake, wave);
        if (user instanceof ServerPlayer player) data(player).putLong(LAST_ACTION, now(level));
    }

    /** O jogador que causou isso: a fonte, quem montou nela, ou quem usou um poder por perto agora há pouco */
    private static ServerPlayer culprit(ServerLevel level, Entity source, Vec3 at, int ritualSequence) {
        if (source instanceof ServerPlayer player) return player;
        if (source instanceof LightningBolt bolt && bolt.getCause() != null) return bolt.getCause();
        if (source != null && source.getControllingPassenger() instanceof ServerPlayer rider) return rider;
        if (source != null) return null;
        for (ServerPlayer player : level.players()) {
            if (preparing(player, ritualSequence) && player.position().distanceTo(at) <= CATACLYSM_RADIUS
                    && now(level) - data(player).getLong(LAST_ACTION) <= ACTION_WINDOW) return player;
        }
        return null;
    }

    // ------------------------------------------------------------------ 5, Cantor do Oceano: na barriga do mar

    /** Dentro de um Monumento Oceânico, ou numa câmara fechada no fundo do mar (sem ver o céu, com mar por cima) */
    public static boolean inSeaBelly(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        var monument = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(BuiltinStructures.OCEAN_MONUMENT);
        if (monument != null && level.structureManager().getStructureWithPieceAt(pos, monument).isValid()) return true;
        if (!level.getBiome(pos).is(BiomeTags.IS_OCEAN) || level.canSeeSky(pos.above())) return false;
        // água de verdade acima do teto: o mar inteiro por cima
        int water = 0;
        for (int y = pos.getY() + 2; y < level.getSeaLevel() + 1 && water < CHAMBER_DEPTH; y++) {
            if (level.getFluidState(pos.atY(y)).isSource()) water++;
        }
        return water >= CHAMBER_DEPTH && pos.getY() < level.getSeaLevel() - CHAMBER_DEPTH;
    }

    // ------------------------------------------------------------------ 4, Enterrador do Cataclismo: terremoto e tsunami

    private record Shock(long time, Vec3 at, UUID by, boolean quake, boolean wave, boolean power) {}

    private static final Map<String, Deque<Shock>> SHOCKS = new HashMap<>();

    private static Deque<Shock> shocks(ServerLevel level) {
        Deque<Shock> list = SHOCKS.computeIfAbsent(level.dimension().location().toString(), k -> new ArrayDeque<>());
        long time = now(level);
        while (!list.isEmpty() && time - list.peekFirst().time() > CATACLYSM_WINDOW) list.pollFirst();
        return list;
    }

    private static void cataclysm(ServerLevel level, Vec3 at, Entity by, boolean quake, boolean wave) {
        shocks(level).addLast(new Shock(now(level), at, by == null ? null : by.getUUID(), quake, wave, true));
    }

    /** Explosões abalam a terra; debaixo d'água, também levantam o mar */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Vec3 at = event.getExplosion().center();
        Entity by = event.getExplosion().getIndirectSourceEntity();
        boolean underwater = !level.getFluidState(BlockPos.containing(at)).isEmpty();
        shocks(level).addLast(new Shock(now(level), at, by == null ? null : by.getUUID(), true, underwater, false));
    }

    /** No meio de um terremoto e de um tsunami causados por você, nos últimos 60 segundos */
    public static boolean amidstCataclysm(ServerPlayer player) {
        int quakes = 0, waves = 0;
        boolean quakePower = false, wavePower = false;
        for (Shock shock : shocks(player.serverLevel())) {
            if (!player.getUUID().equals(shock.by()) || shock.at().distanceTo(player.position()) > CATACLYSM_RADIUS) continue;
            if (shock.quake()) { quakes++; quakePower |= shock.power(); }
            if (shock.wave()) { waves++; wavePower |= shock.power(); }
        }
        return (quakePower || quakes >= CATACLYSM_EXPLOSIONS) && (wavePower || waves >= CATACLYSM_EXPLOSIONS);
    }

    // ------------------------------------------------------------------ 3, Rei do Mar: a torre e o mar subjugado

    private static boolean stoneLike(BlockState state) {
        return state.is(Tags.Blocks.STONES) || state.is(Tags.Blocks.COBBLESTONES) || state.is(BlockTags.STONE_BRICKS)
                || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.PRISMARINE) || state.is(Blocks.PRISMARINE_BRICKS)
                || state.is(Blocks.DARK_PRISMARINE) || state.is(Blocks.SMOOTH_STONE) || state.is(Blocks.POLISHED_ANDESITE)
                || state.is(Blocks.POLISHED_DIORITE) || state.is(Blocks.POLISHED_GRANITE) || state.is(Blocks.POLISHED_DEEPSLATE)
                || state.is(Blocks.DEEPSLATE_BRICKS) || state.is(Blocks.DEEPSLATE_TILES) || state.is(Blocks.MOSSY_COBBLESTONE);
    }

    /** Uma torre de pedra perto de você: do fundo do mar até acima da superfície, com pelo menos 8 blocos dentro d'água */
    public static BlockPos tower(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int sea = level.getSeaLevel();
        BlockPos center = player.blockPosition();
        for (int dx = -TOWER_SEARCH; dx <= TOWER_SEARCH; dx++) {
            for (int dz = -TOWER_SEARCH; dz <= TOWER_SEARCH; dz++) {
                int x = center.getX() + dx, z = center.getZ() + dz;
                BlockPos top = new BlockPos(x, sea, z);
                if (!stoneLike(level.getBlockState(top)) || !level.getBiome(top).is(BiomeTags.IS_OCEAN)) continue;
                // desce pela torre; ao lado dela precisa ter mar
                int y = sea - 1, wet = 0;
                while (y > level.getMinBuildHeight() && stoneLike(level.getBlockState(new BlockPos(x, y, z)))) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getFluidState(p.north()).isSource() || level.getFluidState(p.south()).isSource()
                            || level.getFluidState(p.east()).isSource() || level.getFluidState(p.west()).isSource()) wet++;
                    y--;
                }
                if (wet >= TOWER_MIN_WATER) return top;
            }
        }
        return null;
    }

    private static boolean powerful(LivingEntity entity) {
        if (entity instanceof Player) return false;
        return entity instanceof ElderGuardian || entity instanceof Guardian || entity instanceof Drowned
                || (BeyonderData.isBeyonder(entity) && !(entity instanceof AbstractEntityCitizen));
    }

    private static int seaKillsNear(ServerPlayer player, BlockPos at) {
        ListTag kills = data(player).getList(SEA_KILLS_TAG, Tag.TAG_COMPOUND);
        long time = now(player.serverLevel());
        int count = 0;
        for (int i = 0; i < kills.size(); i++) {
            CompoundTag kill = kills.getCompound(i);
            if (time - kill.getLong("t") > SEA_WINDOW || !kill.getString("dim").equals(player.level().dimension().location().toString())) continue;
            if (at.distSqr(new BlockPos(kill.getInt("x"), kill.getInt("y"), kill.getInt("z"))) <= (double) SEA_RADIUS * SEA_RADIUS) {
                count += kill.getInt("w");
            }
        }
        return count;
    }

    /** A torre de pé, nenhuma criatura forte viva em volta e pelo menos 3 vencidas por você ali */
    public static boolean seaSubdued(ServerPlayer player) {
        BlockPos top = tower(player);
        if (top == null) return false;
        if (!player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(top).inflate(SEA_RADIUS),
                e -> e.isAlive() && powerful(e)).isEmpty()) return false;
        return seaKillsNear(player, top) >= SEA_KILLS;
    }

    // ------------------------------------------------------------------ 2, Calamidade: o desastre e o fim dele

    private static boolean disasterDamage(DamageSource source) {
        return source.is(ModDamageTypes.LIGHTNING) || source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.DAMAGES_HELMET)
                // poderes (de qualquer Caminho) e Artefatos Selados: os danos do próprio mod
                || source.typeHolder().unwrapKey().map(k -> k.location().getNamespace().equals(LOTMCraft.MOD_ID)).orElse(false);
    }

    public static int calamityVictims(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (now(player.serverLevel()) - tag.getLong(CALAMITY_START) > CALAMITY_WINDOW + CALM_TICKS + CALM_DRINK) return 0;
        return tag.getCompound(CALAMITY).size();
    }

    /** O desastre atingiu 20 pessoas, parou há pelo menos 1 minuto (e há no máximo 5), você está diante de 10 delas e sozinho */
    public static boolean calamityHalted(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (calamityVictims(player) < CALAMITY_PEOPLE) return false;
        long quiet = now(player.serverLevel()) - tag.getLong(CALAMITY_LAST);
        if (quiet < CALM_TICKS || quiet > CALM_TICKS + CALM_DRINK) return false;
        CompoundTag victims = tag.getCompound(CALAMITY);
        int witnesses = 0;
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WITNESS_RADIUS),
                e -> e.isAlive() && e != player && isPerson(e))) {
            if (victims.contains(personKey(e))) witnesses++;
        }
        boolean alone = player.serverLevel().getPlayers(p -> p != player && p.distanceTo(player) <= ALONE_RADIUS && !p.isSpectator()).isEmpty();
        return witnesses >= WITNESSES && alone;
    }

    // ------------------------------------------------------------------ 1, Deus do Trovão: raios na ascensão dos Anjos

    public static int angelsDisrupted(ServerPlayer player) {
        CompoundTag angels = data(player).getCompound(ANGELS);
        int total = 0;
        for (String key : angels.getAllKeys()) total += angels.getInt(key);
        return total;
    }

    private static boolean lightning(DamageSource source) {
        return source.is(ModDamageTypes.LIGHTNING) || source.is(DamageTypeTags.IS_LIGHTNING) || source.getDirectEntity() instanceof LightningBolt;
    }

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) return;
        DamageSource source = event.getSource();
        Entity from = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();

        // 2: gente atingida por um desastre seu
        if (isPerson(target) && disasterDamage(source)) {
            ServerPlayer player = culprit(level, from, target.position(), 2);
            if (player != null && player != target && preparing(player, 2)) {
                CompoundTag tag = data(player);
                long time = now(level);
                if (time - tag.getLong(CALAMITY_LAST) > CALM_TICKS || time - tag.getLong(CALAMITY_START) > CALAMITY_WINDOW) {
                    // um desastre novo
                    tag.put(CALAMITY, new CompoundTag());
                    tag.putLong(CALAMITY_START, time);
                }
                CompoundTag victims = tag.getCompound(CALAMITY);
                boolean fresh = !victims.contains(personKey(target));
                victims.putBoolean(personKey(target), true);
                tag.put(CALAMITY, victims);
                tag.putLong(CALAMITY_LAST, time);
                if (fresh) {
                    int count = victims.size();
                    tell(player, Component.translatable(count >= CALAMITY_PEOPLE ? "lotmcraft.tyrant.calamity_peak" : "lotmcraft.tyrant.calamity_progress",
                            count, CALAMITY_PEOPLE).withColor(COLOR));
                }
            }
        }

        // 1: um raio seu na ascensão de um Anjo
        if (lightning(source) && BeyonderData.isBeyonder(target)) {
            ServerPlayer player = culprit(level, from, target.position(), 1);
            if (player == null || player == target || !preparing(player, 1)) return;
            int sequence = BeyonderData.getSequence(target);
            boolean rising = target instanceof Player ? AdvancementUtil.isAdvancing(target.getUUID()) : true;
            if (sequence > 2 || !rising) return;
            CompoundTag angels = data(player).getCompound(ANGELS);
            String key = target.getUUID().toString();
            if (angels.contains(key)) return;
            angels.putInt(key, sequence <= 1 ? ANGELS_NEEDED : 1);
            data(player).put(ANGELS, angels);
            tell(player, Component.translatable("lotmcraft.tyrant.angel_disrupted", Math.min(angelsDisrupted(player), ANGELS_NEEDED), ANGELS_NEEDED).withColor(COLOR));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !powerful(dead)) return;
        Entity from = event.getSource().getEntity();
        ServerPlayer player = culprit(level, from, dead.position(), 3);
        if (player == null || !preparing(player, 3)) return;
        CompoundTag kill = new CompoundTag();
        kill.putLong("t", now(level));
        kill.putString("dim", level.dimension().location().toString());
        kill.putInt("x", dead.getBlockX());
        kill.putInt("y", dead.getBlockY());
        kill.putInt("z", dead.getBlockZ());
        // o Guardião Ancião e os Beyonders fortes valem pelos três
        boolean mighty = dead instanceof ElderGuardian || (BeyonderData.isBeyonder(dead) && BeyonderData.getSequence(dead) <= 4);
        kill.putInt("w", mighty ? SEA_KILLS : 1);
        ListTag kills = data(player).getList(SEA_KILLS_TAG, Tag.TAG_COMPOUND);
        kills.add(kill);
        while (kills.size() > 64) kills.remove(0);
        data(player).put(SEA_KILLS_TAG, kills);
        tell(player, Component.translatable("lotmcraft.tyrant.sea_kill", Math.min(seaKillsNear(player, dead.blockPosition()), SEA_KILLS), SEA_KILLS).withColor(COLOR));
    }
}
