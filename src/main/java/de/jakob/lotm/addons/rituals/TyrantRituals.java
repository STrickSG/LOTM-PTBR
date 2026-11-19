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
 *   <li>5, Cantor do Oceano: beber "na barriga" do mar: dentro de um Monumento Oceânico ou numa cápsula de obsidiana num oceano.</li>
 *   <li>4, Enterrador do Cataclismo: causar um terremoto e um tsunami e beber no meio deles (poderes ou explosões; as debaixo d'água viram tsunami).</li>
 *   <li>3, Rei do Mar: um pilar maciço de pedra do fundo do mar até 10 blocos acima da água, e nenhuma criatura forte de pé em volta, 3 delas vencidas por você.</li>
 *   <li>2, Calamidade: 20 pessoas atingidas por um desastre seu; depois ele para, e você bebe diante dos que sobraram.</li>
 *   <li>1, Deus do Trovão: com raios, atrapalhar a ascensão de 3 Anjos de Sequência 2 (um de Sequência 1 vale os 3).</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class TyrantRituals {
    private TyrantRituals() {}

    public static final int CAPSULE_SIZE = 4;
    public static final int CATACLYSM_WINDOW = 20 * 60, CATACLYSM_RADIUS = 64, CATACLYSM_EXPLOSIONS = 5;
    public static final int TOWER_SEARCH = 24, TOWER_RADIUS = 16, TOWER_MAX_BLOCKS = 20000, TOWER_ABOVE_WATER = 10, TOWER_LAYER_MIN = 9;
    public static final float TOWER_STONE_SHARE = 0.7f;
    public static final int SEA_RADIUS = 96, SEA_KILLS = 3, SEA_WINDOW = 24000 * 3;
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

    /** Dentro de um Monumento Oceânico, ou fechado numa cápsula de obsidiana em qualquer bioma de oceano (abafa a voz do mar) */
    public static boolean inSeaBelly(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        var monument = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(BuiltinStructures.OCEAN_MONUMENT);
        if (monument != null && level.structureManager().getStructureWithPieceAt(pos, monument).isValid()) return true;
        if (!level.getBiome(pos).is(BiomeTags.IS_OCEAN)) return false;
        // em todas as direções, dos pés e da cabeça, o primeiro bloco sólido é obsidiana
        for (BlockPos from : new BlockPos[]{pos, pos.above()}) {
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                if ((from == pos && dir == net.minecraft.core.Direction.UP) || (from != pos && dir == net.minecraft.core.Direction.DOWN)) continue;
                if (!obsidianWall(level, from, dir)) return false;
            }
        }
        return true;
    }

    private static boolean obsidianWall(ServerLevel level, BlockPos from, net.minecraft.core.Direction dir) {
        for (int i = 1; i <= CAPSULE_SIZE; i++) {
            BlockState state = level.getBlockState(from.relative(dir, i));
            if (state.isAir()) continue;
            return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN);
        }
        return false;
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

    /** Família da pedra: pedras, pedregulhos, tijolos, ardósia, andesito, prismarinho... inclusive escadas, lajes e muros */
    static boolean stoneLike(BlockState state) {
        if (state.is(Tags.Blocks.STONES) || state.is(Tags.Blocks.COBBLESTONES) || state.is(BlockTags.STONE_BRICKS)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)) return true;
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("redstone") || path.contains("glowstone") || path.contains("lodestone") || path.contains("button")
                || path.contains("pressure_plate")) return false;
        for (String word : STONE_WORDS) if (path.contains(word)) return true;
        return false;
    }

    private static final String[] STONE_WORDS = {"stone", "deepslate", "andesite", "diorite", "granite", "tuff", "blackstone",
            "basalt", "prismarine", "brick", "obsidian"};

    private static boolean water(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).isSource() && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
    }

    /**
     * Uma torre de pedra perto de você, num oceano: um pilar maciço (sem vão por dentro), de qualquer formato, do leito do mar
     * até pelo menos 10 blocos acima da água, com pelo menos 9 blocos de pedra em cada andar e 70% de pedra no todo.
     * Enfeites de qualquer material por fora fazem parte da build.
     */
    public static BlockPos tower(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        // um ponto de partida: pedra com água ao lado, perto de você
        BlockPos seed = null;
        search:
        for (int r = 0; r <= TOWER_SEARCH; r++) {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                for (int dy = 8; dy >= -40; dy--) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!stoneLike(level.getBlockState(p))) continue;
                    if (!level.getBiome(p).is(BiomeTags.IS_OCEAN)) continue;
                    for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        if (water(level, p.relative(dir)) && !water(level, p.relative(dir).above())) { seed = p; break search; }
                    }
                }
            }
        }
        if (seed == null) return null;

        // a torre: a pedra ligada ao ponto de partida (sem se espalhar pelo leito)
        java.util.Set<BlockPos> tower = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(seed);
        tower.add(seed);
        while (!queue.isEmpty() && tower.size() < TOWER_MAX_BLOCKS) {
            BlockPos p = queue.poll();
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos n = p.relative(dir);
                if (Math.abs(n.getX() - seed.getX()) > TOWER_RADIUS || Math.abs(n.getZ() - seed.getZ()) > TOWER_RADIUS) continue;
                if (tower.contains(n) || !stoneLike(level.getBlockState(n))) continue;
                tower.add(n);
                queue.add(n);
            }
        }
        int surface = seed.getY();
        int top = tower.stream().mapToInt(BlockPos::getY).max().orElse(surface);
        if (top < surface + TOWER_ABOVE_WATER) return null;

        // de cima até o leito: cada andar maciço, com pedra suficiente; embaixo da água, até tocar o fundo
        int stone = 0, other = 0;
        boolean floor = false;
        for (int y = top; y > level.getMinBuildHeight(); y--) {
            final int yy = y;
            java.util.List<BlockPos> layer = tower.stream().filter(p -> p.getY() == yy).toList();
            if (layer.isEmpty()) break;
            if (y <= top - 1 && layer.size() < TOWER_LAYER_MIN) return null;
            if (hollow(level, layer, tower)) return null;
            stone += layer.size();
            other += decorations(level, layer, tower);
            // o andar já está abaixo da água e nenhum lado dele dá para a água: chegou ao leito
            if (y < surface && layer.stream().noneMatch(p -> net.minecraft.core.Direction.Plane.HORIZONTAL.stream().anyMatch(d -> water(level, p.relative(d))))) {
                floor = true;
                break;
            }
        }
        if (!floor) return null;
        if (stone < TOWER_STONE_SHARE * (stone + other)) return null;
        return new BlockPos(seed.getX(), top, seed.getZ());
    }

    /** Um vão por dentro do andar: ar ou água cercados de pedra por todos os lados (o pilar tem que ser maciço) */
    private static boolean hollow(ServerLevel level, java.util.List<BlockPos> layer, java.util.Set<BlockPos> tower) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, y = layer.get(0).getY();
        for (BlockPos p : layer) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        // tudo o que não é a torre e se alcança de fora da caixa é "fora"; o resto é miolo
        java.util.Set<Long> outside = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int x = minX - 1; x <= maxX + 1; x++) { queue.add(new int[]{x, minZ - 1}); queue.add(new int[]{x, maxZ + 1}); }
        for (int z = minZ - 1; z <= maxZ + 1; z++) { queue.add(new int[]{minX - 1, z}); queue.add(new int[]{maxX + 1, z}); }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (c[0] < minX - 1 || c[0] > maxX + 1 || c[1] < minZ - 1 || c[1] > maxZ + 1) continue;
            long key = ((long) c[0] << 32) ^ (c[1] & 0xffffffffL);
            if (!outside.add(key) || tower.contains(new BlockPos(c[0], y, c[1]))) continue;
            queue.add(new int[]{c[0] + 1, c[1]}); queue.add(new int[]{c[0] - 1, c[1]});
            queue.add(new int[]{c[0], c[1] + 1}); queue.add(new int[]{c[0], c[1] - 1});
        }
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            BlockPos p = new BlockPos(x, y, z);
            long key = ((long) x << 32) ^ (z & 0xffffffffL);
            if (tower.contains(p) || outside.contains(key)) continue;
            BlockState state = level.getBlockState(p);
            if (state.isAir() || !state.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    /** Enfeites grudados no andar (qualquer bloco que não é pedra, nem ar, nem água) */
    private static int decorations(ServerLevel level, java.util.List<BlockPos> layer, java.util.Set<BlockPos> tower) {
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        for (BlockPos p : layer) {
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(dir);
                if (tower.contains(n) || !seen.add(n)) continue;
                BlockState state = level.getBlockState(n);
                if (!state.isAir() && state.getFluidState().isEmpty()) seen.add(n.immutable());
                else seen.remove(n);
            }
        }
        return seen.size();
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

        // 1: um raio seu na ascensão de um Anjo (poderes e artefatos)
        if (lightning(source) && BeyonderData.isBeyonder(target)) {
            ServerPlayer player = culprit(level, from, target.position(), 1);
            if (player != null) angelStruck(player, target);
        }
    }

    /** Raios comuns (tridente com Canalização, raios chamados): quem chamou o raio é quem o causou */
    @SubscribeEvent
    public static void onStruck(net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof LivingEntity target && BeyonderData.isBeyonder(target)
                && event.getLightning().getCause() != null) {
            angelStruck(event.getLightning().getCause(), target);
        }
    }

    private static void angelStruck(ServerPlayer player, LivingEntity target) {
        if (player == target || !preparing(player, 1)) return;
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
