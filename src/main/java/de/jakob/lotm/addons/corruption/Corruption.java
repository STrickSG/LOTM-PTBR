package de.jakob.lotm.addons.corruption;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.colonies.Colonists;
import de.jakob.lotm.beyonders.acting.ActingTask;
import de.jakob.lotm.beyonders.acting.ActingTaskRegistry;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A corrupção de um Beyonder (0 a 1), guardada no jogador e mostrada na Introspecção.
 * <ul>
 *   <li>Sobe com o tempo: a característica corrompe sozinha, mais rápido quanto mais alta a Sequência e quanto mais
 *   características acumuladas no corpo.</li>
 *   <li>A atuação segura: atuar enquanto a poção ainda é digerida baixa a corrupção. Atuar demais a faz subir (atuar com
 *   a digestão já cheia, ou muitas vezes em poucos minutos).</li>
 *   <li>As coisas mundanas seguram: dormir, comer, negociar, colher, criar animais, conversar com aldeões e colonos. Cada
 *   uma tem um tempo de espera e não conta se for atuação do Caminho do jogador (o Plantador colhe atuando).</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class Corruption {
    private Corruption() {}

    private static final String KEY = "LotmCorruption";
    private static final int TICK = 200;

    // ------------------------------------------------------------------ valor

    public static float get(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getFloat(KEY);
    }

    public static void set(ServerPlayer player, float value) {
        float v = Math.max(0f, Math.min(1f, value));
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        float old = persisted.getFloat(KEY);
        persisted.putFloat(KEY, v);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        if (Math.abs(old - v) > 1e-6f) sync(player);
    }

    public static void add(ServerPlayer player, float amount) {
        if (!BeyonderData.isBeyonder(player)) return;
        set(player, get(player) + amount);
    }

    private static void sync(ServerPlayer player) {
        PacketHandler.sendToPlayer(player, new CorruptionSyncPacket(get(player)));
    }

    /** Quanto as características acumuladas pesam: cada cópia extra, mais quanto mais alta a Sequência dela */
    public static float stackWeight(int[] stacks) {
        if (stacks == null) return 0f;
        float total = 0f;
        for (int seq = 0; seq < stacks.length; seq++) if (stacks[seq] > 0) total += stacks[seq] * (0.04f + 0.02f * (9 - Math.min(9, seq)));
        return total;
    }

    /** Quanto sobe por dia de jogo (20 minutos) */
    public static float dailyGrowth(LivingEntity entity) {
        return dailyGrowth(BeyonderData.getSequence(entity), BeyonderData.getCharStacks(entity));
    }

    public static float dailyGrowth(int seq, int[] stacks) {
        float base = 0.01f + 0.004f * (9 - Math.max(0, Math.min(9, seq)));
        return base * (1f + 4f * stackWeight(stacks));
    }

    // ------------------------------------------------------------------ o tempo

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % TICK != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!BeyonderData.isBeyonder(player)) {
                if (get(player) > 0) set(player, 0);
                continue;
            }
            if (player.isSpectator()) continue;
            add(player, dailyGrowth(player) * TICK / 24000f);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    // ------------------------------------------------------------------ a atuação

    private static final Map<UUID, Deque<Integer>> RECENT_ACTING = new HashMap<>();

    /** Chamado quando uma atuação dá digestão. digestionBefore: a digestão antes dela. */
    public static void onActing(ServerPlayer player, float digestionBefore, float amount) {
        int now = player.server.getTickCount();
        Deque<Integer> recent = RECENT_ACTING.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        while (!recent.isEmpty() && now - recent.peekFirst() > 20 * 60 * 5) recent.pollFirst();
        recent.addLast(now);
        if (digestionBefore >= 0.999f) {
            add(player, 0.01f);                         // atuar o que já foi digerido: exagero
        } else {
            add(player, -Math.min(0.03f, 0.005f + amount * 0.25f));
        }
        if (recent.size() > 15) add(player, 0.005f);    // muitas vezes em poucos minutos
    }

    // ------------------------------------------------------------------ as coisas mundanas

    private static final Map<UUID, Map<String, Integer>> COOLDOWNS = new HashMap<>();

    /** Uma coisa mundana: baixa a corrupção, se não estiver em espera e se não for atuação do Caminho do jogador */
    private static void mundane(Player p, String activity, String actingId, float relief, int cooldown) {
        if (!(p instanceof ServerPlayer player) || !BeyonderData.isBeyonder(player)) return;
        if (actingId != null && isActing(player, actingId)) return;
        int now = player.server.getTickCount();
        Map<String, Integer> map = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Integer until = map.get(activity);
        if (until != null && now < until) return;
        map.put(activity, now + cooldown);
        add(player, -relief);
    }

    /** Se esta tarefa é atuação do Caminho e Sequência do jogador */
    private static boolean isActing(Player player, String taskId) {
        List<ActingTask> tasks = ActingTaskRegistry.getTasksFor(BeyonderData.getPathway(player), BeyonderData.getSequence(player));
        for (ActingTask task : tasks) if (task.getId().equals(taskId)) return true;
        return false;
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (event.wakeImmediately()) return;
        mundane(event.getEntity(), "sleep", "sleep_in_bed", 0.03f, 20 * 60 * 10);
    }

    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var food = event.getItem().get(net.minecraft.core.component.DataComponents.FOOD);
        if (food == null) return;
        String path = BuiltInRegistries.ITEM.getKey(event.getItem().getItem()).getPath();
        mundane(player, "eat", "eat_" + path, 0.005f, 20 * 60);
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        mundane(event.getEntity(), "trade", "trade_with_villager", 0.01f, 20 * 60 * 2);
    }

    @SubscribeEvent
    public static void onHarvest(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof CropBlock crop && crop.isMaxAge(event.getState()))
            mundane(event.getPlayer(), "harvest", "harvest_crop", 0.002f, 20 * 10);
    }

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() != null) mundane(event.getCausedByPlayer(), "breed", "breed_animals", 0.01f, 20 * 60 * 2);
    }

    @SubscribeEvent
    public static void onTalk(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof AbstractVillager || Colonists.isAnyCitizen(event.getTarget()))
            mundane(event.getEntity(), "talk", "interact_with_villager", 0.005f, 20 * 60);
    }
}
