package de.jakob.lotm.dimension.spirit_world;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.dimension.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Onde ficam as construções grandes do Mundo Espiritual (Pavilhão de Lã, Farol de Cristal, Altar do Vazio...).
 * Elas são montadas bloco a bloco pelo gerador e o Minecraft não as conhece como estruturas, então o gerador
 * avisa aqui e as posições ficam salvas no mundo (usado pelo ritual do Errante, Porta 3).
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class SpiritWorldStructureIndex {
    public record Entry(String name, int x, int y, int z, int radius) {
        public boolean contains(BlockPos pos) {
            double dx = pos.getX() - x, dz = pos.getZ() - z;
            return dx * dx + dz * dz <= (radius + 3) * (radius + 3) && Math.abs(pos.getY() - y) <= 16;
        }

        public String id() {
            return name + "@" + x + "," + z;
        }
    }

    // o gerador roda fora da linha principal: as construções novas esperam aqui até o próximo tick do servidor
    private static final ConcurrentLinkedQueue<Entry> PENDING = new ConcurrentLinkedQueue<>();

    private SpiritWorldStructureIndex() {}

    static void record(String name, BlockPos center, int radius) {
        PENDING.add(new Entry(name, center.getX(), center.getY(), center.getZ(), radius));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        ServerLevel level = event.getServer().getLevel(ModDimensions.SPIRIT_WORLD_DIMENSION_KEY);
        if (level == null) return;
        Data data = data(level);
        Entry e;
        while ((e = PENDING.poll()) != null) data.add(e);
    }

    /** A construção do Mundo Espiritual em que a posição está, ou null */
    public static Entry at(ServerLevel level, BlockPos pos) {
        Data data = data(level);
        int cx = Math.floorDiv(pos.getX(), 64), cz = Math.floorDiv(pos.getZ(), 64);
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                Entry e = data.byCell.get(cellKey(x, z));
                if (e != null && e.contains(pos)) return e;
            }
        }
        return null;
    }

    /** Todas as construções já geradas */
    public static java.util.Collection<Entry> all(ServerLevel level) {
        return java.util.List.copyOf(data(level).byCell.values());
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "lotmcraft_spirit_structures");
    }

    private static final class Data extends SavedData {
        private final Map<Long, Entry> byCell = new HashMap<>();

        void add(Entry e) {
            byCell.put(cellKey(Math.floorDiv(e.x, 64), Math.floorDiv(e.z, 64)), e);
            setDirty();
        }

        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data data = new Data();
            for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                Entry e = new Entry(c.getString("name"), c.getInt("x"), c.getInt("y"), c.getInt("z"), c.getInt("r"));
                data.byCell.put(cellKey(Math.floorDiv(e.x, 64), Math.floorDiv(e.z, 64)), e);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            for (Entry e : byCell.values()) {
                CompoundTag c = new CompoundTag();
                c.putString("name", e.name);
                c.putInt("x", e.x);
                c.putInt("y", e.y);
                c.putInt("z", e.z);
                c.putInt("r", e.radius);
                list.add(c);
            }
            tag.put("entries", list);
            return tag;
        }
    }
}
