package de.jakob.lotm.addons.rituals;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.dimension.DeepSpacePlanets;
import de.jakob.lotm.dimension.ModDimensions;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rituais de avanço do Caminho da Porta (wiki da novel):
 * <ul>
 *   <li>5, Viajante: coordenadas em 4 pontos do Mundo Espiritual, muito longe uns dos outros (Magnetitas a 1000 blocos).</li>
 *   <li>4, Feiticeiro dos Segredos: selar para sempre uma criatura hostil de nível semideus, sem ajuda de fora.</li>
 *   <li>3, Errante: registrar num livro 3 projeções do Mundo Astral (estruturas do Mundo Espiritual).</li>
 *   <li>2, Andarilho dos Planos: deixar 9 lendas fora do planeta (livros assinados em atris no Espaço Profundo).</li>
 *   <li>1, Chave das Estrelas: pousar num planeta pesado do Espaço Profundo e criar uma ligação mística com ele.</li>
 * </ul>
 * O ritual de cada Sequência é feito por quem ainda está na Sequência de cima; as mensagens só aparecem para ele.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class DoorRituals {
    private DoorRituals() {}

    public static final int COORDINATES_NEEDED = 4, COORDINATES_DISTANCE = 1000;
    public static final int RECORDS_NEEDED = 3;
    public static final int LEGENDS_NEEDED = 9, LEGENDS_DISTANCE = 500;
    public static final int STAR_TICKS_NEEDED = 20 * 60 * 5;

    private static final ResourceKey<Level> DEEP_SPACE = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "deep_space"));

    // dados do jogador (sobrevivem à morte)
    private static final String COORDS = "LotmDoorCoords", LEGENDS = "LotmDoorLegends", SEALED = "LotmDoorSealed",
            STAR_STONE = "LotmDoorStarStone", STAR_PLANET = "LotmDoorStarPlanet", STAR_PROGRESS = "LotmDoorStarProgress",
            STAR_DONE = "LotmDoorStarConnected";
    // dados da criatura e do livro
    private static final String SEALED_BY = "LotmSealedBy", SEALED_AT = "LotmSealedAt", HURT_BY = "LotmHurtByPlayers";
    private static final String RECORD_OWNER = "LotmAstralOwner", RECORDS = "LotmAstralRecords";

    /** Poderes que prendem e contam como selo (do Caminho, gravados pelo Escriba ou de um Artefato Selado) */
    private static final Set<String> SEAL_ABILITIES = Set.of("sealing_ability", "spatial_cage_ability",
            "imprison_ability", "confinement_ability", "flame_cage_ability", "petrification_ability");

    private static final int COLOR = 0xB894FF;

    // ------------------------------------------------------------------ util

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Está na Sequência de onde se faz o ritual da Sequência {@code ritualSequence} da Porta */
    private static boolean preparing(Player player, int ritualSequence) {
        return BeyonderData.isBeyonder(player) && "door".equals(BeyonderData.getPathway(player))
                && BeyonderData.getSequence(player) == ritualSequence + 1;
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    /** No Mundo Espiritual a barra de ação mostra as coordenadas do Mundo de Cima (passiva da Porta), então vai para o chat */
    private static void tellChat(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }

    private static void addPos(CompoundTag tag, String key, BlockPos pos) {
        ListTag list = tag.getList(key, Tag.TAG_LONG);
        for (Tag t : list) if (((LongTag) t).getAsLong() == pos.asLong()) return;
        if (list.size() >= 64) list.remove(0);
        list.add(LongTag.valueOf(pos.asLong()));
        tag.put(key, list);
    }

    private static List<BlockPos> positions(CompoundTag tag, String key) {
        List<BlockPos> result = new ArrayList<>();
        for (Tag t : tag.getList(key, Tag.TAG_LONG)) result.add(BlockPos.of(((LongTag) t).getAsLong()));
        return result;
    }

    /** Maior grupo de pontos com todos a pelo menos {@code min} blocos uns dos outros (na horizontal), até {@code need} */
    static int spread(List<BlockPos> points, double min, int need) {
        int n = Math.min(points.size(), 48);
        boolean[][] far = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double dx = points.get(i).getX() - points.get(j).getX(), dz = points.get(i).getZ() - points.get(j).getZ();
                far[i][j] = dx * dx + dz * dz >= min * min;
            }
        }
        return best(far, new ArrayList<>(), 0, n, need);
    }

    private static int best(boolean[][] far, List<Integer> chosen, int start, int n, int need) {
        int result = chosen.size();
        if (result >= need) return need;
        for (int i = start; i < n && result < need; i++) {
            boolean ok = true;
            for (int c : chosen) if (!far[c][i]) { ok = false; break; }
            if (!ok) continue;
            chosen.add(i);
            result = Math.max(result, best(far, chosen, i + 1, n, need));
            chosen.remove(chosen.size() - 1);
        }
        return result;
    }

    // ------------------------------------------------------------------ 5, Viajante: coordenadas no Mundo Espiritual

    /** Quantas das Magnetitas do jogador no Mundo Espiritual estão a 1000 blocos umas das outras (até 4) */
    public static int coordinates(ServerPlayer player) {
        ServerLevel spirit = player.server.getLevel(ModDimensions.SPIRIT_WORLD_DIMENSION_KEY);
        if (spirit == null) return 0;
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : positions(data(player), COORDS)) if (spirit.getBlockState(pos).is(Blocks.LODESTONE)) valid.add(pos);
        return spread(valid, COORDINATES_DISTANCE, COORDINATES_NEEDED);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getPlacedBlock().is(Blocks.LODESTONE)) return;
        BlockPos pos = event.getPos().immutable();
        if (level.dimension().equals(ModDimensions.SPIRIT_WORLD_DIMENSION_KEY)) {
            addPos(data(player), COORDS, pos);
            if (preparing(player, 5)) {
                tell(player, Component.translatable("lotmcraft.door.coordinate", coordinates(player), COORDINATES_NEEDED).withColor(COLOR));
            }
        } else if (level.dimension().equals(DEEP_SPACE)) {
            DeepSpacePlanets.Planet planet = DeepSpacePlanets.nearest(pos.getX(), pos.getZ());
            if (planet != null && planet.contains(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 4)) {
                CompoundTag tag = data(player);
                tag.putLong(STAR_STONE, pos.asLong());
                tag.putLong(STAR_PLANET, planet.key());
                if (preparing(player, 1)) tell(player, Component.translatable("lotmcraft.door.star_mark").withColor(COLOR));
            }
        }
    }

    // ------------------------------------------------------------------ 4, Feiticeiro dos Segredos: selar uma criatura semideus

    public static boolean sealed(ServerPlayer player) {
        return data(player).getBoolean(SEALED);
    }

    public static boolean isSealed(LivingEntity entity) {
        return entity.getPersistentData().hasUUID(SEALED_BY);
    }

    /** Criatura de nível semideus (Beyonder de Sequência 4 ou 3) hostil ao jogador */
    private static boolean hostileDemigod(LivingEntity entity, Player player) {
        if (entity instanceof Player || !entity.isAlive() || isSealed(entity) || !BeyonderData.isBeyonder(entity)) return false;
        int sequence = BeyonderData.getSequence(entity);
        if (sequence < 3 || sequence > 4) return false;
        if (entity instanceof BeyonderNPCEntity npc && npc.isHostile()) return true;
        if (entity instanceof Mob mob && mob.getTarget() == player) return true;
        return entity instanceof Enemy;
    }

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SEAL_ABILITIES.contains(event.getAbility().getId())) return;
        if (!preparing(player, 4)) return;
        ServerLevel level = player.serverLevel();
        Map<LivingEntity, Vec3> candidates = new HashMap<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32))) {
            if (hostileDemigod(entity, player)) candidates.put(entity, entity.position());
        }
        if (candidates.isEmpty()) return;
        String ability = event.getAbility().getId();
        // o Confinamento ergue uma caixa de barreiras de 6 blocos em volta do ponto mirado (o mesmo que ele usa)
        Vec3 confinement = de.jakob.lotm.util.helper.AbilityUtil.getTargetLocation(player, 12, 2f);
        // o poder age agora; logo depois vê quem ficou preso de verdade
        ServerScheduler.scheduleDelayed(15, () -> candidates.forEach((entity, start) -> {
            if (entity.isAlive() && !isSealed(entity) && held(entity, ability, confinement)) trySeal(entity, player);
        }), level);
    }

    /** A criatura está presa pelo poder: com o efeito que ele deixa, ou dentro da caixa do Confinamento */
    private static boolean held(LivingEntity entity, String ability, Vec3 confinement) {
        if (ability.equals("petrification_ability")) return entity.hasEffect(ModEffects.PETRIFICATION);
        if (ability.equals("confinement_ability")) {
            Vec3 p = entity.position();
            return Math.abs(p.x - confinement.x) < 5.5 && Math.abs(p.y - confinement.y) < 5.5 && Math.abs(p.z - confinement.z) < 5.5;
        }
        // Aprisionar, Selamento, Jaula Espacial e Jaula de Chamas deixam a criatura sem conseguir andar
        MobEffectInstance slow = entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return slow != null && slow.getAmplifier() >= 3;
    }

    private static void trySeal(LivingEntity entity, ServerPlayer player) {
        // menos ajuda de fora, melhor: se outro jogador feriu a criatura, o selo não vale
        for (Tag t : entity.getPersistentData().getList(HURT_BY, Tag.TAG_INT_ARRAY)) {
            if (!NbtUtils.loadUUID(t).equals(player.getUUID())) {
                tell(player, Component.translatable("lotmcraft.door.seal_helped").withColor(0xFF7A7A));
                return;
            }
        }
        CompoundTag tag = entity.getPersistentData();
        tag.putUUID(SEALED_BY, player.getUUID());
        Vec3 pos = entity.position();
        ListTag at = new ListTag();
        at.add(net.minecraft.nbt.DoubleTag.valueOf(pos.x));
        at.add(net.minecraft.nbt.DoubleTag.valueOf(pos.y));
        at.add(net.minecraft.nbt.DoubleTag.valueOf(pos.z));
        tag.put(SEALED_AT, at);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
            mob.setPersistenceRequired();
        }
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setDeltaMovement(Vec3.ZERO);
        spawnChains(entity);
        ServerLevel level = (ServerLevel) entity.level();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1, pos.z, 120, .6, 1, .6, .2);
        level.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y + 1, pos.z, 80, .8, 1.2, .8, .5);
        level.playSound(null, entity.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, .6f, 1.4f);
        data(player).putBoolean(SEALED, true);
        tell(player, Component.translatable("lotmcraft.door.sealed", entity.getType().getDescription()).withColor(COLOR));
    }

    // ------------------------------------------------------------------ correntes do selo

    private static final String CHAIN_TAG = "lotm_seal_chain";
    private static final int DOOR_COLOR = 0x89F5F5;
    /** Efeito visual do Selamento da Porta (Photon) mostrado para quem está perto de cada criatura selada */
    private static final Map<UUID, Map<UUID, UUID>> SEAL_FX = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Correntes de verdade (blocos de corrente) prendendo a criatura selada: quatro correntes presas no chão em volta
     * dela, puxando até o peito, e duas voltas de corrente no corpo (cintura e peito).
     */
    private static void spawnChains(LivingEntity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        float h = entity.getBbHeight(), w = entity.getBbWidth();
        String own = CHAIN_TAG + "_" + entity.getUUID();
        // correntes do chão até o peito
        float out = 2.0f, in = w * .45f, chest = h * .6f;
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + 90 * i);
            float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
            chain(level, entity, own, new org.joml.Vector3f(cx * out, .05f, cz * out), new org.joml.Vector3f(cx * in, chest, cz * in));
        }
        // voltas em volta do corpo (octógono)
        for (float y : new float[]{h * .38f, h * .68f}) {
            float r = w * .62f;
            for (int i = 0; i < 8; i++) {
                double a0 = Math.toRadians(22.5 + 45 * i), a1 = Math.toRadians(22.5 + 45 * (i + 1));
                chain(level, entity, own, new org.joml.Vector3f((float) Math.cos(a0) * r, y, (float) Math.sin(a0) * r),
                        new org.joml.Vector3f((float) Math.cos(a1) * r, y, (float) Math.sin(a1) * r));
            }
        }
    }

    /** Uma corrente reta de {@code from} até {@code to} (relativo aos pés da criatura), feita de blocos de corrente */
    private static void chain(ServerLevel level, LivingEntity entity, String own, org.joml.Vector3f from, org.joml.Vector3f to) {
        org.joml.Vector3f dir = new org.joml.Vector3f(to).sub(from);
        float length = dir.length();
        if (length < 1e-3f) return;
        dir.normalize();
        int pieces = Math.max(1, Math.round(length));
        float piece = length / pieces;
        org.joml.Quaternionf rot = new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), dir);
        org.joml.Vector3f scale = new org.joml.Vector3f(1f, piece, 1f);
        for (int i = 0; i < pieces; i++) {
            org.joml.Vector3f center = new org.joml.Vector3f(dir).mul(piece * (i + .5f)).add(from);
            // gira em volta do centro do bloco: translação = centro - rotação(escala * meio do bloco)
            org.joml.Vector3f translation = rot.transform(new org.joml.Vector3f(.5f * scale.x, .5f * scale.y, .5f * scale.z)).negate().add(center);
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "minecraft:block_display");
            tag.put("block_state", NbtUtils.writeBlockState(Blocks.CHAIN.defaultBlockState()));
            CompoundTag transformation = new CompoundTag();
            transformation.put("translation", floats(translation.x, translation.y, translation.z));
            transformation.put("left_rotation", floats(rot.x, rot.y, rot.z, rot.w));
            transformation.put("scale", floats(scale.x, scale.y, scale.z));
            transformation.put("right_rotation", floats(0, 0, 0, 1));
            tag.put("transformation", transformation);
            // cor do Caminho da Porta: contorno brilhando e corrente sempre iluminada
            tag.putBoolean("Glowing", true);
            tag.putInt("glow_color_override", DOOR_COLOR);
            CompoundTag brightness = new CompoundTag();
            brightness.putInt("sky", 15);
            brightness.putInt("block", 15);
            tag.put("brightness", brightness);
            ListTag tags = new ListTag();
            tags.add(StringTag.valueOf(CHAIN_TAG));
            tags.add(StringTag.valueOf(own));
            tag.put("Tags", tags);
            net.minecraft.world.entity.Entity display = net.minecraft.world.entity.EntityType.loadEntityRecursive(tag, level, e -> {
                e.moveTo(entity.getX(), entity.getY(), entity.getZ(), 0, 0);
                return e;
            });
            if (display != null) level.addFreshEntity(display);
        }
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(net.minecraft.nbt.FloatTag.valueOf(v));
        return list;
    }

    /** Mostra o efeito do Selamento da Porta para quem chega perto e o tira de quem se afasta */
    private static void updateSealFx(LivingEntity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        Map<UUID, UUID> shown = SEAL_FX.computeIfAbsent(entity.getUUID(), k -> new java.util.concurrent.ConcurrentHashMap<>());
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) < 48 * 48 && !shown.containsKey(player.getUUID())) {
                UUID fx = UUID.randomUUID();
                de.jakob.lotm.network.PacketHandler.sendToPlayer(player, new de.jakob.lotm.network.packets.toClient.PlayCancellablePhotonBlockEffectPacket(
                        "sealing_effect", pos, entity.getX() - (pos.getX() + .5), entity.getY() + entity.getBbHeight() / 2 - (pos.getY() + .5), entity.getZ() - (pos.getZ() + .5),
                        1.1, null, -1, false, true, null, fx));
                shown.put(player.getUUID(), fx);
            }
        }
        shown.entrySet().removeIf(e -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (player != null && player.level() == level && player.distanceToSqr(entity) < 64 * 64) return false;
            if (player != null) de.jakob.lotm.network.PacketHandler.sendToPlayer(player, new de.jakob.lotm.network.packets.toClient.CancelPhotonBlockEffectPacket(e.getValue()));
            return true;
        });
    }

    // quem sai do jogo perde os efeitos no cliente; ao voltar, recebe de novo
    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        for (Map<UUID, UUID> shown : SEAL_FX.values()) shown.remove(event.getEntity().getUUID());
    }

    // se a criatura selada sumir (comando, vazio), as correntes somem junto
    @SubscribeEvent
    public static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !(event.getLevel() instanceof ServerLevel level) || !isSealed(entity)) return;
        Map<UUID, UUID> shown = SEAL_FX.remove(entity.getUUID());
        if (shown != null) shown.forEach((playerId, fx) -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) de.jakob.lotm.network.PacketHandler.sendToPlayer(player, new de.jakob.lotm.network.packets.toClient.CancelPhotonBlockEffectPacket(fx));
        });
        if (entity.getRemovalReason() == null || !entity.getRemovalReason().shouldDestroy()) return;
        String own = CHAIN_TAG + "_" + entity.getUUID();
        for (net.minecraft.world.entity.Display.BlockDisplay d : level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class,
                entity.getBoundingBox().inflate(4))) {
            if (d.getTags().contains(own)) d.discard();
        }
    }

    // a criatura selada fica presa para sempre: parada, sem IA e sem levar dano
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide || !isSealed(entity)) return;
        ListTag at = entity.getPersistentData().getList(SEALED_AT, Tag.TAG_DOUBLE);
        if (at.size() == 3) {
            double x = at.getDouble(0), y = at.getDouble(1), z = at.getDouble(2);
            if (entity.distanceToSqr(x, y, z) > 0.01) entity.teleportTo(x, y, z);
        }
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity instanceof Mob mob && !mob.isNoAi()) mob.setNoAi(true);
        if (entity.tickCount % 10 == 0) {
            ((ServerLevel) entity.level()).sendParticles(de.jakob.lotm.particle.ModParticles.STAR.get(), entity.getX(), entity.getY() + entity.getBbHeight() / 2,
                    entity.getZ(), 4, entity.getBbWidth() * .7, entity.getBbHeight() * .45, entity.getBbWidth() * .7, .02);
        }
        if (entity.tickCount % 40 == 0) updateSealFx(entity);
        if (entity.tickCount % 20 == 0) {
            ((ServerLevel) entity.level()).sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                    6, entity.getBbWidth() * .6, entity.getBbHeight() * .5, entity.getBbWidth() * .6, .3);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (isSealed(entity)) {
            event.setCanceled(true);
            return;
        }
        // guarda quem feriu a criatura (para saber se houve ajuda de fora no selo)
        if (!(entity instanceof Player) && event.getSource().getEntity() instanceof Player attacker) {
            ListTag list = entity.getPersistentData().getList(HURT_BY, Tag.TAG_INT_ARRAY);
            for (Tag t : list) if (NbtUtils.loadUUID(t).equals(attacker.getUUID())) return;
            if (list.size() < 8) list.add(NbtUtils.createUUID(attacker.getUUID()));
            entity.getPersistentData().put(HURT_BY, list);
        }
    }

    // ------------------------------------------------------------------ 3, Errante: registrar projeções do Mundo Astral

    /** Quantas projeções diferentes estão registradas no livro do jogador que tem mais registros */
    public static int records(ServerPlayer player) {
        int best = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(Items.WRITABLE_BOOK) && !stack.is(Items.WRITTEN_BOOK)) continue;
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!tag.hasUUID(RECORD_OWNER) || !tag.getUUID(RECORD_OWNER).equals(player.getUUID())) continue;
            best = Math.max(best, tag.getList(RECORDS, Tag.TAG_STRING).size());
        }
        return best;
    }

    // agachado + clique direito com o livro e pena dentro de uma estrutura do Mundo Espiritual
    @SubscribeEvent
    public static void onUseBook(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack book = event.getItemStack();
        if (!book.is(Items.WRITABLE_BOOK) || !player.isShiftKeyDown() || !event.getLevel().dimension().equals(ModDimensions.SPIRIT_WORLD_DIMENSION_KEY)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(player instanceof ServerPlayer serverPlayer) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!preparing(serverPlayer, 3)) {
            tellChat(serverPlayer, Component.translatable("lotmcraft.believers.not_worthy").withColor(0xFF7A7A));
            return;
        }
        // as construções do próprio Mundo Espiritual, ou uma estrutura comum do Minecraft que esteja nele
        String record = null;
        de.jakob.lotm.dimension.spirit_world.SpiritWorldStructureIndex.Entry built =
                de.jakob.lotm.dimension.spirit_world.SpiritWorldStructureIndex.at(level, serverPlayer.blockPosition());
        if (built != null) {
            record = built.id();
        } else {
            StructureStart start = level.structureManager().getStructureWithPieceAt(serverPlayer.blockPosition(), holder -> true);
            if (start != null && start.isValid()) {
                ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
                record = id + "@" + start.getChunkPos().toLong();
            }
        }
        if (record == null) {
            tellChat(serverPlayer, Component.translatable("lotmcraft.door.no_projection").withColor(0xFF7A7A));
            return;
        }

        CompoundTag tag = book.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(RECORD_OWNER) && !tag.getUUID(RECORD_OWNER).equals(serverPlayer.getUUID())) {
            tellChat(serverPlayer, Component.translatable("lotmcraft.door.not_your_book").withColor(0xFF7A7A));
            return;
        }
        ListTag records = tag.getList(RECORDS, Tag.TAG_STRING);
        for (Tag t : records) {
            if (t.getAsString().equals(record)) {
                tellChat(serverPlayer, Component.translatable("lotmcraft.door.already_recorded", records.size(), RECORDS_NEEDED).withColor(COLOR));
                return;
            }
        }
        records.add(StringTag.valueOf(record));
        tag.putUUID(RECORD_OWNER, serverPlayer.getUUID());
        tag.put(RECORDS, records);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        book.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("lotmcraft.door.records_lore", records.size(), RECORDS_NEEDED).withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.translatable("lotmcraft.door.records_owner", serverPlayer.getName().getString()).withStyle(ChatFormatting.GRAY))));
        level.sendParticles(ParticleTypes.ENCHANT, serverPlayer.getX(), serverPlayer.getY() + 1.2, serverPlayer.getZ(), 40, .5, .6, .5, .6);
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        tellChat(serverPlayer, Component.translatable("lotmcraft.door.recorded", records.size(), RECORDS_NEEDED).withColor(COLOR));
    }

    // ------------------------------------------------------------------ 2, Andarilho dos Planos: lendas fora do planeta

    /** Quantas lendas do jogador (livro assinado por ele num atril do Espaço Profundo) estão a 500 blocos umas das outras */
    public static int legends(ServerPlayer player) {
        ServerLevel space = player.server.getLevel(DEEP_SPACE);
        if (space == null) return 0;
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : positions(data(player), LEGENDS)) if (isLegend(space, pos, player)) valid.add(pos);
        return spread(valid, LEGENDS_DISTANCE, LEGENDS_NEEDED);
    }

    private static boolean isLegend(ServerLevel level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) || !lectern.hasBook()) return false;
        WrittenBookContent content = lectern.getBook().get(DataComponents.WRITTEN_BOOK_CONTENT);
        return content != null && content.author().equals(player.getName().getString());
    }

    @SubscribeEvent
    public static void onUseLectern(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(DEEP_SPACE) || !event.getItemStack().is(Items.WRITTEN_BOOK)) return;
        BlockPos pos = event.getPos().immutable();
        if (!level.getBlockState(pos).is(Blocks.LECTERN)) return;
        ServerScheduler.scheduleDelayed(2, () -> {
            if (!isLegend(level, pos, player)) return;
            addPos(data(player), LEGENDS, pos);
            if (preparing(player, 2)) tell(player, Component.translatable("lotmcraft.door.legend", legends(player), LEGENDS_NEEDED).withColor(COLOR));
        }, level);
    }

    // ------------------------------------------------------------------ 1, Chave das Estrelas: o planeta pesado

    public static boolean starConnected(ServerPlayer player) {
        return data(player).getBoolean(STAR_DONE);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(DEEP_SPACE)) return;
        ServerLevel level = player.serverLevel();
        DeepSpacePlanets.Planet planet = DeepSpacePlanets.nearest(player.getX(), player.getZ());
        if (planet == null) return;
        double dx = planet.x() - player.getX(), dz = planet.z() - player.getZ();
        if (dx * dx + dz * dz > 400 * 400) return;

        // o planeta gira e emite sinais: luzes que rodam em volta dele (acompanhando o anel) e feixes de estrelas nos polos
        long time = level.getGameTime();
        double cx = planet.x() + .5, cy = planet.y() + .5, cz = planet.z() + .5;
        if (time % 3 == 0) {
            double turn = time * 0.025;
            for (int band = 0; band < 2; band++) {
                double ring = band == 0 ? planet.ringOuter() + 3 : planet.radius() + 4;
                int points = band == 0 ? 48 : 28;
                for (int i = 0; i < points; i++) {
                    double a = (band == 0 ? turn : -turn * 1.6) + i * Math.PI * 2 / points;
                    level.sendParticles(player, band == 0 ? ParticleTypes.END_ROD : de.jakob.lotm.particle.ModParticles.STAR.get(), true,
                            cx + Math.cos(a) * ring, cy + (band == 0 ? 0 : Math.sin(a * 3) * 2), cz + Math.sin(a) * ring, 1, 0, 0, 0, 0);
                }
            }
        }
        if (time % 40 == 0) {
            // sinal: feixe de estrelas saindo dos dois polos
            for (int k = 0; k < 24; k++) {
                level.sendParticles(player, de.jakob.lotm.particle.ModParticles.STAR.get(), true, cx, cy + planet.radius() + 2 + k * 1.5, cz, 4, .15, .4, .15, .01);
                level.sendParticles(player, de.jakob.lotm.particle.ModParticles.STAR.get(), true, cx, cy - planet.radius() - 2 - k * 1.5, cz, 4, .15, .4, .15, .01);
            }
            level.sendParticles(player, ParticleTypes.END_ROD, true, cx, cy + planet.radius() + 18, cz, 40, .4, 14, .4, .02);
            level.sendParticles(player, ParticleTypes.END_ROD, true, cx, cy - planet.radius() - 18, cz, 40, .4, 14, .4, .02);
        }

        // ligação mística: pousado no planeta, com a sua Magnetita nele
        if (time % 20 != 0) return;
        CompoundTag tag = data(player);
        if (tag.getBoolean(STAR_DONE) || tag.getLong(STAR_PLANET) != planet.key() || !tag.contains(STAR_STONE)) return;
        if (!player.onGround() || !planet.contains(player.getX(), player.getY(), player.getZ(), 4)) return;
        if (!level.getBlockState(BlockPos.of(tag.getLong(STAR_STONE))).is(Blocks.LODESTONE)) return;
        int progress = tag.getInt(STAR_PROGRESS) + 20;
        tag.putInt(STAR_PROGRESS, progress);
        if (progress >= STAR_TICKS_NEEDED) {
            tag.putBoolean(STAR_DONE, true);
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 150, 1, 2, 1, .2);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, .8f);
            if (preparing(player, 1)) tell(player, Component.translatable("lotmcraft.door.star_done").withColor(COLOR));
        } else if (preparing(player, 1)) {
            tell(player, Component.translatable("lotmcraft.door.star_progress", progress * 100 / STAR_TICKS_NEEDED).withColor(COLOR));
        }
    }
}
