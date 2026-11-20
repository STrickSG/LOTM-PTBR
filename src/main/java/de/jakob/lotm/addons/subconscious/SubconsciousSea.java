package de.jakob.lotm.addons.subconscious;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.happiness.ExpirationBasedHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.colonies.Colonists;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * O Mar do Subconsciente Coletivo (Visionário). Uma dimensão: um mar escuro e parado, onde as mentes de quem dorme
 * aparecem como ilhas, no mesmo lugar em que a pessoa dorme no mundo de verdade.
 * <ul>
 *   <li>Entrar: um Visionário de Sequência 5 ou menor que dorme numa cama sonha para dentro do mar; qualquer Beyonder
 *   entra usando, agachado, um poder de sonho de qualquer Caminho.</li>
 *   <li>Sair: afundar até o fundo do mar (acordar), ou usar de novo, agachado, um poder de sonho.</li>
 *   <li>Lá, anda-se por cima da água; agachado, afunda.</li>
 *   <li>Clicar na chama de uma mente abre as influências (uma por pessoa por noite): paz, pesadelo e sonhos que
 *   ensinam (+1 nível em duas habilidades do colono).</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class SubconsciousSea {
    private SubconsciousSea() {}

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "subconscious_sea"));

    public static final int MIND_RADIUS = 96, SLEEP_TICKS = 60, ISLAND_SPACING = 11, REACH = 10;
    private static final int S = SubconsciousSeaChunkGenerator.SURFACE + 1;
    private static final String ORIGIN_DIM = "LotmSeaOriginDim", ORIGIN_X = "LotmSeaOriginX", ORIGIN_Y = "LotmSeaOriginY",
            ORIGIN_Z = "LotmSeaOriginZ", ORIGIN_COLONY = "LotmSeaOriginColony", SLEPT = "LotmSeaSlept", TAG = "lotm_mind", TAG_PREFIX = "lotm_mind_";
    /** Marcas deixadas nas mentes (para os rituais do Visionário) */
    public static final String MARKS = "LotmSeaMarks", NIGHT_DREAMS = "LotmSeaNightDreams";
    private static final int COLOR = 0x9C88D8, BAD = 0xFF7A7A;

    public static boolean inSea(Player player) {
        return player.level().dimension().equals(DIMENSION);
    }

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ------------------------------------------------------------------ andar sobre o mar

    /** A água segura quem está na superfície; agachado, afunda devagar */
    @SubscribeEvent
    public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!inSea(player) || player.isSpectator() || player.getAbilities().flying) return;
        // o movimento do jogador é calculado no cliente; o servidor só não cobra a queda
        if (!player.level().isClientSide) {
            player.fallDistance = 0;
            return;
        }
        if (player.isShiftKeyDown()) return;
        BlockPos feet = player.blockPosition();
        boolean water = player.level().getFluidState(feet).isSource() || player.level().getFluidState(feet.below()).isSource();
        if (!water) return;
        Vec3 motion = player.getDeltaMovement();
        if (player.getY() < S - 0.05) {
            player.setDeltaMovement(motion.x, Math.max(motion.y, Math.min(0.35, (S - player.getY()) * 0.4)), motion.z);
        } else if (motion.y < 0) {
            player.setDeltaMovement(motion.x, 0, motion.z);
            player.setOnGround(true);
            player.fallDistance = 0;
        }
    }

    // ------------------------------------------------------------------ entrar e sair

    public static void enter(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel sea = server.getLevel(DIMENSION);
        if (sea == null || inSea(player)) return;
        CompoundTag tag = data(player);
        tag.putString(ORIGIN_DIM, player.level().dimension().location().toString());
        tag.putDouble(ORIGIN_X, player.getX());
        tag.putDouble(ORIGIN_Y, player.getY());
        tag.putDouble(ORIGIN_Z, player.getZ());
        // dormindo dentro de uma colônia, só as mentes dela aparecem
        var colony = Colonists.colonyAt(player.level(), player.blockPosition());
        if (colony != null) tag.putInt(ORIGIN_COLONY, colony.getID());
        else tag.remove(ORIGIN_COLONY);
        if (player.isSleeping()) player.stopSleeping();
        player.teleportTo(sea, player.getX(), S + 0.5, player.getZ(), player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable("lotmcraft.sea.entered").withColor(COLOR), false);
        sea.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, .5f);
    }

    public static void leave(ServerPlayer player) {
        if (!inSea(player)) return;
        CompoundTag tag = data(player);
        ServerLevel back = null;
        if (tag.contains(ORIGIN_DIM)) {
            back = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(ORIGIN_DIM))));
        }
        if (back == null) {
            back = player.server.overworld();
            BlockPos spawn = back.getSharedSpawnPos();
            player.teleportTo(back, spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, player.getYRot(), player.getXRot());
        } else {
            player.teleportTo(back, tag.getDouble(ORIGIN_X), tag.getDouble(ORIGIN_Y), tag.getDouble(ORIGIN_Z), player.getYRot(), player.getXRot());
        }
        player.displayClientMessage(Component.translatable("lotmcraft.sea.woke").withColor(COLOR), true);
    }

    /** Um Visionário de Sequência 5 ou menor que dorme numa cama sonha para dentro do mar */
    private static boolean dreamer(ServerPlayer player) {
        return BeyonderData.isBeyonder(player) && "visionary".equals(BeyonderData.getPathway(player)) && BeyonderData.getSequence(player) <= 5;
    }

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) return;
        String id = event.getAbility().getId();
        if (!(id.contains("dream") || id.contains("nightmare") || id.contains("sleep") || id.contains("midnight"))) return;
        if (inSea(player)) leave(player);
        else enter(player);
    }

    // ------------------------------------------------------------------ as ilhas das mentes

    private record Mind(UUID sleeper, ResourceKey<Level> realDimension, BlockPos center, String kind) {}

    /** Mentes à mostra agora, por quem dorme */
    private static final Map<UUID, Mind> MINDS = new HashMap<>();
    /** Quem já foi influenciado nesta noite (dia do mundo) */
    private static final Map<UUID, Long> INFLUENCED = new HashMap<>();

    private static boolean asleep(LivingEntity entity) {
        if (entity instanceof EntityCitizen citizen) return citizen.getCitizenSleepHandler().isAsleep() || citizen.isSleeping();
        return entity.isSleeping();
    }

    private static boolean person(Entity entity) {
        return (entity instanceof EntityCitizen || entity instanceof Villager || entity instanceof Player) && !marionette(entity);
    }

    /** Marionetes não têm mente própria: não aparecem no mar */
    private static boolean marionette(Entity entity) {
        return entity instanceof LivingEntity living && living.hasData(ModAttachments.MARIONETTE_COMPONENT)
                && living.getData(ModAttachments.MARIONETTE_COMPONENT).isMarionette();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // sonhar para dentro do mar
        CompoundTag tag = data(player);
        if (player.isSleeping() && dreamer(player)) {
            int slept = tag.getInt(SLEPT) + 1;
            tag.putInt(SLEPT, slept);
            if (slept >= SLEEP_TICKS) {
                tag.remove(SLEPT);
                enter(player);
            }
        } else if (tag.contains(SLEPT)) tag.remove(SLEPT);

        if (!inSea(player)) return;
        // afundar até o fundo é acordar
        if (player.getY() <= SubconsciousSeaChunkGenerator.FLOOR + 2.5) {
            leave(player);
            return;
        }
        if (player.tickCount % 40 == 0) refreshMinds(player);
        if (player.tickCount % 10 == 0) ambience(player);
    }

    /** Partículas no ar do mar: lembranças soltas */
    private static void ambience(ServerPlayer player) {
        ServerLevel sea = player.serverLevel();
        sea.sendParticles(player, ParticleTypes.END_ROD, false, player.getX(), S + 3, player.getZ(), 3, 12, 3, 12, 0.005);
        sea.sendParticles(player, ParticleTypes.WITCH, false, player.getX(), S + 1, player.getZ(), 2, 10, 1, 10, 0);
        for (Mind mind : MINDS.values()) {
            if (mind.center().distToCenterSqr(player.position()) > 64 * 64) continue;
            sea.sendParticles(player, ParticleTypes.SOUL_FIRE_FLAME, false, mind.center().getX() + .5, S + 1.2, mind.center().getZ() + .5, 1, .1, .3, .1, 0.005);
        }
    }

    /** Mostra as ilhas de quem dorme perto da sua origem, e desfaz as de quem acordou */
    private static void refreshMinds(ServerPlayer player) {
        ServerLevel sea = player.serverLevel();
        CompoundTag tag = data(player);
        if (!tag.contains(ORIGIN_DIM)) return;
        ServerLevel real = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(ORIGIN_DIM))));
        if (real == null) return;
        AABB area = new AABB(player.getX() - MIND_RADIUS, real.getMinBuildHeight(), player.getZ() - MIND_RADIUS,
                player.getX() + MIND_RADIUS, real.getMaxBuildHeight(), player.getZ() + MIND_RADIUS);
        Set<UUID> sleeping = new HashSet<>();
        java.util.List<LivingEntity> sleepers = new java.util.ArrayList<>();
        com.minecolonies.api.colony.IColony colony = tag.contains(ORIGIN_COLONY)
                ? com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByWorld(tag.getInt(ORIGIN_COLONY), real) : null;
        if (colony != null) {
            // numa colônia, só os colonos dela
            for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
                citizen.getEntity().ifPresent(e -> { if (e.isAlive() && person(e) && asleep(e)) sleepers.add(e); });
            }
        } else {
            sleepers.addAll(real.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && person(e) && e != player && asleep(e)));
        }
        for (LivingEntity entity : sleepers) {
            sleeping.add(entity.getUUID());
            if (!MINDS.containsKey(entity.getUUID())) raise(sea, real, entity);
        }
        // quem acordou (ou saiu de perto) perde a ilha
        for (Mind mind : new java.util.ArrayList<>(MINDS.values())) {
            if (!mind.realDimension().equals(real.dimension()) || sleeping.contains(mind.sleeper())) continue;
            if (colony == null && Math.hypot(mind.center().getX() - player.getX(), mind.center().getZ() - player.getZ()) > MIND_RADIUS) continue;
            sink(sea, mind);
        }
        // ilhas antigas, de antes de o servidor fechar
        for (Entity e : sea.getEntities((Entity) null, player.getBoundingBox().inflate(MIND_RADIUS, 16, MIND_RADIUS), e -> e.getTags().contains(TAG))) {
            UUID id = null;
            for (String t : e.getTags()) if (t.startsWith(TAG_PREFIX)) id = UUID.fromString(t.substring(TAG_PREFIX.length()));
            if (id != null && !MINDS.containsKey(id)) {
                sink(sea, new Mind(id, real.dimension(), e.blockPosition().atY(S), ""));
            }
        }
    }

    private static String kindOf(LivingEntity entity) {
        ICitizenData citizen = Colonists.data(entity);
        if (citizen != null) return "colonist";
        return entity instanceof Villager ? "villager" : "player";
    }

    private static String jobKey(LivingEntity entity) {
        ICitizenData citizen = Colonists.data(entity);
        if (citizen != null && citizen.getJob() != null) return citizen.getJob().getJobRegistryEntry().getTranslationKey();
        if (entity instanceof Villager villager) {
            ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
            return "entity.minecraft.villager." + key.getPath();
        }
        return citizen != null ? "lotmcraft.sea.no_job" : "lotmcraft.sea.dreamer";
    }

    private static Item memoryOf(LivingEntity entity) {
        String job = jobKey(entity);
        String[][] table = {{"farmer", "WHEAT"}, {"fisher", "FISHING_ROD"}, {"miner", "IRON_PICKAXE"}, {"quarrier", "IRON_PICKAXE"},
                {"lumberjack", "IRON_AXE"}, {"knight", "IRON_SWORD"}, {"guard", "IRON_SWORD"}, {"ranger", "BOW"}, {"archer", "BOW"},
                {"baker", "BREAD"}, {"cook", "COOKED_BEEF"}, {"chef", "COOKED_BEEF"}, {"builder", "BRICKS"}, {"delivery", "CHEST"},
                {"smelter", "IRON_INGOT"}, {"blacksmith", "ANVIL"}, {"weaponsmith", "ANVIL"}, {"armorer", "IRON_CHESTPLATE"},
                {"healer", "GOLDEN_APPLE"}, {"cleric", "GOLDEN_APPLE"}, {"librarian", "BOOK"}, {"teacher", "BOOK"}, {"pupil", "BOOK"},
                {"researcher", "BOOK"}, {"cartographer", "MAP"}, {"shepherd", "WHITE_WOOL"}, {"butcher", "PORKCHOP"},
                {"florist", "POPPY"}, {"beekeeper", "HONEYCOMB"}, {"mason", "STONE_BRICKS"}, {"stonemason", "STONE_BRICKS"},
                {"fletcher", "ARROW"}, {"leatherworker", "LEATHER"}, {"toolsmith", "IRON_SHOVEL"}, {"no_job", "PAPER"}, {"dreamer", "WRITABLE_BOOK"}};
        for (String[] row : table) {
            if (job.contains(row[0])) return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(row[1].toLowerCase()));
        }
        return Items.EMERALD;
    }

    /** Lugar para a ilha: onde a pessoa dorme, afastando um pouco das outras ilhas */
    private static BlockPos spot(LivingEntity entity) {
        BlockPos at = new BlockPos(entity.getBlockX(), S, entity.getBlockZ());
        for (int tries = 0; tries < 12; tries++) {
            BlockPos candidate = at;
            boolean free = MINDS.values().stream().noneMatch(m -> Math.hypot(m.center().getX() - candidate.getX(), m.center().getZ() - candidate.getZ()) < ISLAND_SPACING);
            if (free) return at;
            double angle = tries * 2.4;
            at = at.offset((int) (Math.cos(angle) * ISLAND_SPACING), 0, (int) (Math.sin(angle) * ISLAND_SPACING));
        }
        return at;
    }

    /** A mente sobe do fundo como uma ilha: a lembrança que mais pesa gira sobre ela, com o nome e o ofício */
    private static void raise(ServerLevel sea, ServerLevel real, LivingEntity sleeper) {
        BlockPos c = spot(sleeper);
        Mind mind = new Mind(sleeper.getUUID(), real.dimension(), c, kindOf(sleeper));
        MINDS.put(sleeper.getUUID(), mind);
        Random rnd = new Random(sleeper.getUUID().getLeastSignificantBits());
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz) + rnd.nextDouble() * .6;
            if (d > 4.8) continue;
            int depth = (int) (5 - d);
            for (int y = 0; y <= depth; y++) {
                var b = y == 0 ? (d > 4 ? Blocks.AMETHYST_BLOCK : (rnd.nextInt(3) == 0 ? Blocks.MOSS_BLOCK : Blocks.CALCITE)) : (y < 2 ? Blocks.CALCITE : Blocks.TUFF);
                sea.setBlockAndUpdate(c.offset(dx, -1 - y, dz), b.defaultBlockState());
            }
            if (d <= 4 && rnd.nextInt(7) == 0) sea.setBlockAndUpdate(c.offset(dx, 0, dz), Blocks.SMALL_AMETHYST_BUD.defaultBlockState());
            else if (d <= 3.5 && rnd.nextInt(9) == 0) sea.setBlockAndUpdate(c.offset(dx, 0, dz), Blocks.MOSS_CARPET.defaultBlockState());
        }
        // a chama da mente: clicar nela abre as influências
        sea.setBlockAndUpdate(c, Blocks.END_ROD.defaultBlockState());
        Entity item = EntityType.ITEM_DISPLAY.create(sea);
        if (item != null) {
            item.moveTo(c.getX() + .5, c.getY() + 2.3, c.getZ() + .5);
            CompoundTag t = new CompoundTag();
            CompoundTag stack = new CompoundTag();
            stack.putString("id", BuiltInRegistries.ITEM.getKey(memoryOf(sleeper)).toString());
            stack.putInt("count", 1);
            t.put("item", stack);
            t.putString("billboard", "vertical");
            t.put("transformation", transform(1.4f));
            t.put("brightness", brightness());
            item.load(item.saveWithoutId(new CompoundTag()).merge(t));
            tag(item, sleeper.getUUID());
            sea.addFreshEntity(item);
        }
        Entity text = EntityType.TEXT_DISPLAY.create(sea);
        if (text != null) {
            text.moveTo(c.getX() + .5, c.getY() + 3.6, c.getZ() + .5);
            CompoundTag t = new CompoundTag();
            String name = sleeper.getName().getString().replace("\\", "").replace("\"", "");
            t.putString("text", "{\"text\":\"" + name + "\",\"color\":\"#E8DDFF\",\"extra\":[{\"text\":\"\\n\"},{\"translate\":\"" + jobKey(sleeper)
                    + "\",\"color\":\"#9C88D8\",\"italic\":true}]}");
            t.putString("billboard", "center");
            t.putInt("background", 0x40100A26);
            t.put("transformation", transform(1.3f));
            t.put("brightness", brightness());
            text.load(text.saveWithoutId(new CompoundTag()).merge(t));
            tag(text, sleeper.getUUID());
            sea.addFreshEntity(text);
        }
        sea.sendParticles(ParticleTypes.END_ROD, c.getX() + .5, c.getY() + 1, c.getZ() + .5, 30, 2, .5, 2, .02);
        sea.playSound(null, c, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1f, .7f);
    }

    private static void tag(Entity e, UUID sleeper) {
        e.addTag(TAG);
        e.addTag(TAG_PREFIX + sleeper);
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    private static CompoundTag transform(float scale) {
        CompoundTag tf = new CompoundTag();
        tf.put("scale", floats(scale, scale, scale));
        tf.put("translation", floats(0, 0, 0));
        tf.put("left_rotation", floats(0, 0, 0, 1));
        tf.put("right_rotation", floats(0, 0, 0, 1));
        return tf;
    }

    private static CompoundTag brightness() {
        CompoundTag br = new CompoundTag();
        br.putInt("sky", 15);
        br.putInt("block", 15);
        return br;
    }

    /** A ilha afunda de volta: o mar volta a ser só mar */
    private static void sink(ServerLevel sea, Mind mind) {
        MINDS.remove(mind.sleeper());
        BlockPos c = mind.center();
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) for (int dy = -7; dy <= 0; dy++) {
            BlockPos p = c.offset(dx, dy, dz);
            if (sea.getBlockState(p).isAir() && p.getY() >= S) continue;
            sea.setBlock(p, (p.getY() < S ? Blocks.WATER : Blocks.AIR).defaultBlockState(), 2);
        }
        for (Entity e : sea.getEntities((Entity) null, new AABB(c).inflate(3, 6, 3), e -> e.getTags().contains(TAG_PREFIX + mind.sleeper()))) e.discard();
        sea.sendParticles(ParticleTypes.SPLASH, c.getX() + .5, c.getY(), c.getZ() + .5, 40, 3, .2, 3, .1);
    }

    // ------------------------------------------------------------------ influenciar uma mente

    private static Mind mindNear(ServerPlayer player, BlockPos pos) {
        for (Mind mind : MINDS.values()) {
            if (mind.center().distSqr(pos) <= 4) return mind;
        }
        return null;
    }

    /** Clicar na chama da mente abre as influências no chat */
    @SubscribeEvent
    public static void onClickMind(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inSea(player) || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.END_ROD)) return;
        Mind mind = mindNear(player, event.getPos());
        if (mind == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        showMenu(player, mind);
    }

    private static final String[] SKILL_NAMES = {"Athletics", "Dexterity", "Strength", "Agility", "Stamina", "Mana", "Adaptability", "Focus",
            "Creativity", "Knowledge", "Intelligence"};

    /** Abre o menu de sonhos desta mente (no estilo do mod) */
    private static void showMenu(ServerPlayer player, Mind mind) {
        ServerLevel real = player.server.getLevel(mind.realDimension());
        Entity body = real == null ? null : real.getEntity(mind.sleeper());
        if (!(body instanceof LivingEntity living)) return;
        CompoundTag info = new CompoundTag();
        info.putUUID("uuid", mind.sleeper());
        info.putString("name", living.getName().getString());
        info.putString("job", jobKey(living));
        info.putString("kind", mind.kind());
        long night = player.server.overworld().getDayTime() / 24000;
        info.putBoolean("tonight", INFLUENCED.getOrDefault(mind.sleeper(), -1L) == night);
        ICitizenData citizen = Colonists.data(living);
        if (citizen != null) {
            info.putString("actions", "peace,nightmare,strength,skill,knowledge,art");
            info.putDouble("happiness", citizen.getCitizenHappinessHandler().getHappiness(citizen.getColony(), citizen));
            CompoundTag skills = new CompoundTag();
            for (String name : SKILL_NAMES) skills.putInt(name, citizen.getCitizenSkillHandler().getLevel(Skill.valueOf(name)));
            info.put("skills", skills);
        } else if (living instanceof Villager villager) {
            info.putString("actions", "peace,nightmare,knowledge");
            info.putString("infoKey", "lotmcraft.sea.screen.villager_level");
            info.putString("infoArg", String.valueOf(villager.getVillagerData().getLevel()));
        } else {
            info.putString("actions", "peace,nightmare");
            info.putString("infoKey", "lotmcraft.sea.screen.sanity");
            info.putString("infoArg", String.format("%.0f%%", living.getData(ModAttachments.SANITY_COMPONENT).getSanity() * 100));
        }
        player.connection.send(new DreamMindPacket(info));
    }

    public static void influence(ServerPlayer player, UUID sleeper, String action) {
        Mind mind = MINDS.get(sleeper);
        if (mind == null || !inSea(player) || Math.hypot(mind.center().getX() - player.getX(), mind.center().getZ() - player.getZ()) > REACH) {
            player.displayClientMessage(Component.translatable("lotmcraft.sea.too_far").withColor(BAD), true);
            return;
        }
        long night = player.server.overworld().getDayTime() / 24000;
        if (INFLUENCED.getOrDefault(sleeper, -1L) == night) {
            player.displayClientMessage(Component.translatable("lotmcraft.sea.already").withColor(BAD), true);
            return;
        }
        ServerLevel real = player.server.getLevel(mind.realDimension());
        Entity body = real == null ? null : real.getEntity(sleeper);
        if (!(body instanceof LivingEntity living) || !asleep(living)) {
            player.displayClientMessage(Component.translatable("lotmcraft.sea.awake").withColor(BAD), true);
            return;
        }
        boolean done = apply(player, living, action);
        if (!done) return;
        INFLUENCED.put(sleeper, night);
        mark(player, sleeper, action, night);
        ServerLevel sea = player.serverLevel();
        BlockPos c = mind.center();
        boolean dark = action.equals("nightmare");
        sea.sendParticles(dark ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD, c.getX() + .5, c.getY() + 1.5, c.getZ() + .5, 40, 1.5, 1, 1.5, .03);
        sea.playSound(null, c, dark ? SoundEvents.WARDEN_HEARTBEAT : SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, dark ? .6f : 1.2f);
        player.displayClientMessage(Component.translatable("lotmcraft.sea.done." + action, body.getName()).withColor(COLOR), false);
    }

    /** O efeito na pessoa de verdade, que dorme */
    private static boolean apply(ServerPlayer player, LivingEntity body, String action) {
        ICitizenData citizen = Colonists.data(body);
        if (citizen != null) {
            switch (action) {
                case "peace" -> citizen.getCitizenHappinessHandler().addModifier(
                        new ExpirationBasedHappinessModifier("lotm_dream_peace", 3.0, new StaticHappinessSupplier(2.0), 3));
                case "nightmare" -> citizen.getCitizenHappinessHandler().addModifier(
                        new ExpirationBasedHappinessModifier("lotm_dream_nightmare", 3.0, new StaticHappinessSupplier(0.0), 3));
                case "strength" -> teach(citizen, Skill.Strength, Skill.Stamina);
                case "skill" -> teach(citizen, Skill.Dexterity, Skill.Agility);
                case "knowledge" -> teach(citizen, Skill.Knowledge, Skill.Intelligence);
                case "art" -> teach(citizen, Skill.Creativity, Skill.Focus);
                default -> { return false; }
            }
            citizen.markDirty(0);
            return true;
        }
        if (body instanceof Villager villager) {
            switch (action) {
                case "peace" -> villager.getGossips().add(player.getUUID(), GossipType.MINOR_POSITIVE, 25);
                case "nightmare" -> villager.getGossips().add(player.getUUID(), GossipType.MINOR_NEGATIVE, 25);
                case "knowledge" -> villager.setVillagerXp(villager.getVillagerXp() + 20);
                default -> { return false; }
            }
            return true;
        }
        if (body instanceof ServerPlayer other) {
            var sanity = other.getData(ModAttachments.SANITY_COMPONENT);
            switch (action) {
                case "peace" -> sanity.setSanityAndSync(sanity.getSanity() + 0.1f, other);
                case "nightmare" -> sanity.setSanityAndSync(sanity.getSanity() - 0.1f, other);
                default -> { return false; }
            }
            return true;
        }
        return false;
    }

    private static void teach(ICitizenData citizen, Skill a, Skill b) {
        citizen.getCitizenSkillHandler().incrementLevel(a, 1);
        citizen.getCitizenSkillHandler().incrementLevel(b, 1);
    }

    /** Guarda as marcas deixadas (mentes diferentes, e o mesmo sonho numa mesma noite) para os rituais */
    private static void mark(ServerPlayer player, UUID sleeper, String action, long night) {
        CompoundTag tag = data(player);
        CompoundTag marks = tag.getCompound(MARKS);
        marks.putLong(sleeper.toString(), night);
        tag.put(MARKS, marks);
        CompoundTag dreams = tag.getCompound(NIGHT_DREAMS);
        if (dreams.getLong("night") != night) {
            dreams = new CompoundTag();
            dreams.putLong("night", night);
        }
        CompoundTag same = dreams.getCompound(action);
        same.putBoolean(sleeper.toString(), true);
        dreams.put(action, same);
        tag.put(NIGHT_DREAMS, dreams);
    }

    /** Quantas mentes diferentes você já marcou */
    public static int marked(Player player) {
        return data(player).getCompound(MARKS).size();
    }

    /** Quantas mentes tiveram o mesmo sonho seu nesta noite (o maior grupo) */
    public static int sameDreamTonight(ServerPlayer player) {
        CompoundTag dreams = data(player).getCompound(NIGHT_DREAMS);
        if (dreams.getLong("night") != player.server.overworld().getDayTime() / 24000) return 0;
        int best = 0;
        for (String key : dreams.getAllKeys()) if (!key.equals("night")) best = Math.max(best, dreams.getCompound(key).size());
        return best;
    }
}
