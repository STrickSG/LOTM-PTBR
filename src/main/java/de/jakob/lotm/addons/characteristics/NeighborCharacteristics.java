package de.jakob.lotm.addons.characteristics;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.corruption.Corruption;
import de.jakob.lotm.addons.corruption.LostToCorruption;
import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItemHandler;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AdvancementUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Características de Sequência 1 de caminhos vizinhos, guardadas no corpo de um Anjo (Seq 1).
 * Não há limite de quantas, mas cada uma pesa na corrupção como uma extra de Sequência 1.
 * Juntar o conjunto inteiro do grupo (3 do próprio caminho e 3 de cada vizinho) é a sobrecarga:
 * perde o controle na hora, a corrupção vai a 100% e só volta quando as extras saírem do corpo
 * (pela Autoridade Angelical, a única coisa que ainda dá para usar) ou quando o matarem.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class NeighborCharacteristics {
    private NeighborCharacteristics() {}

    private static final String KEY = "LotmNeighborChars", OVERLOAD = "LotmCharOverload";
    /** Quantas de Sequência 1 de cada caminho fecham o conjunto */
    public static final int FULL = 3;
    /** Cada característica de Sequência 1 de um vizinho pesa isto na corrupção */
    public static final float WEIGHT = 0.10f;

    private static CompoundTag persisted(Player p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG); }
    private static void save(Player p, CompoundTag persisted) { p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted); }

    public static Map<String, Integer> all(Player p) {
        Map<String, Integer> out = new LinkedHashMap<>();
        CompoundTag tag = persisted(p).getCompound(KEY);
        for (String k : tag.getAllKeys()) if (tag.getInt(k) > 0) out.put(k, tag.getInt(k));
        return out;
    }

    /** Os caminhos vizinhos de que guarda alguma (no cliente, o que foi sincronizado do próprio jogador) */
    public static Set<String> pathwaysOf(Player p) {
        if (p.level().isClientSide()) {
            return NeighborCharsSyncPacket.clientPathways;   // sem tocar em classes de cliente (servidor dedicado)
        }
        return all(p).keySet();
    }

    public static void sync(ServerPlayer player) {
        de.jakob.lotm.network.PacketHandler.sendToPlayer(player, new NeighborCharsSyncPacket(java.util.List.copyOf(all(player).keySet())));
    }

    @SubscribeEvent
    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    public static int get(Player p, String pathway) { return persisted(p).getCompound(KEY).getInt(pathway); }

    public static int total(Player p) {
        int n = 0;
        for (int v : all(p).values()) n += v;
        return n;
    }

    private static void set(Player p, String pathway, int value) {
        CompoundTag persisted = persisted(p);
        CompoundTag tag = persisted.getCompound(KEY);
        if (value <= 0) tag.remove(pathway); else tag.putInt(pathway, value);
        persisted.put(KEY, tag);
        save(p, persisted);
        if (p instanceof ServerPlayer sp) sync(sp);
    }

    /** Só um Anjo (Seq 1) pode guardar a de Sequência 1 de um caminho vizinho, e só se não houver Deus desse caminho */
    public static boolean canAbsorb(ServerPlayer player, String pathway) {
        if (BeyonderData.getSequence(player) != 1) return false;
        String own = BeyonderData.getPathway(player);
        if (own.equals(pathway) || !AdvancementUtil.neighbors(own).contains(pathway)) return false;
        var map = BeyonderData.playerMap;
        return map == null || map.count(pathway, 0) == 0;
    }

    public static void add(ServerPlayer player, String pathway) {
        set(player, pathway, get(player, pathway) + 1);
        Corruption.set(player, Corruption.get(player));   // aplica o novo piso
        checkOverload(player);
    }

    /** Tira uma do corpo e devolve o item (vazio se não havia) */
    public static ItemStack remove(ServerPlayer player, String pathway) {
        int have = get(player, pathway);
        if (have <= 0) return ItemStack.EMPTY;
        var item = BeyonderCharacteristicItemHandler.selectCharacteristicOfPathwayAndSequence(pathway, 1);
        if (item == null) return ItemStack.EMPTY;
        set(player, pathway, have - 1);
        return new ItemStack(item);
    }

    public static float weight(Player p) { return total(p) * WEIGHT; }

    // ------------------------------------------------------------------ sobrecarga

    public static boolean isOverloaded(Player p) { return persisted(p).getBoolean(OVERLOAD); }

    private static void setOverloaded(Player p, boolean on) {
        CompoundTag persisted = persisted(p);
        if (on) persisted.putBoolean(OVERLOAD, true); else persisted.remove(OVERLOAD);
        save(p, persisted);
    }

    /** O conjunto inteiro do grupo: 3 do próprio caminho e 3 de cada vizinho */
    public static boolean hasFullSet(ServerPlayer player) {
        if (BeyonderData.getSequence(player) != 1) return false;
        String own = BeyonderData.getPathway(player);
        Set<String> neighbors = AdvancementUtil.neighbors(own);
        if (neighbors.isEmpty()) return false;
        if (1 + BeyonderData.getCharStack(player, 1) < FULL) return false;
        for (String n : neighbors) if (get(player, n) < FULL) return false;
        return true;
    }

    public static void checkOverload(ServerPlayer player) {
        if (isOverloaded(player) || !hasFullSet(player)) return;
        setOverloaded(player, true);
        player.sendSystemMessage(Component.translatable("lotmcraft.characteristics.overload").withColor(0xB0306A));
        Corruption.set(player, 1f);
        LostToCorruption.lose(player);
    }

    /** Ainda pode usar a Autoridade Angelical para tirar as características do corpo */
    public static boolean mayUseWhileOverloaded(net.minecraft.world.entity.LivingEntity entity, String abilityId) {
        return entity instanceof Player p && isOverloaded(p) && "angel_authority_ability".equals(abilityId);
    }

    /** Morto em sobrecarga: as características dos vizinhos se desprendem do corpo e caem no chão */
    public static void onDeathOverloaded(ServerPlayer player) {
        if (!isOverloaded(player)) return;
        for (var e : all(player).entrySet()) {
            var item = BeyonderCharacteristicItemHandler.selectCharacteristicOfPathwayAndSequence(e.getKey(), 1);
            if (item == null) continue;
            for (int i = 0; i < e.getValue(); i++) {
                ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), new ItemStack(item));
                drop.setDefaultPickUpDelay();
                player.level().addFreshEntity(drop);
            }
            set(player, e.getKey(), 0);
        }
        setOverloaded(player, false);
    }

    /** Quando o conjunto deixa de estar completo (tirou as extras), volta a ser quem era */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (!isOverloaded(player)) return;
        if (!hasFullSet(player)) {
            setOverloaded(player, false);
            LostToCorruption.recover(player);
            player.sendSystemMessage(Component.translatable("lotmcraft.characteristics.overload_end").withColor(0xB9E3FF));
        }
    }
}
