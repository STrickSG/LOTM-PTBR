package de.jakob.lotm.addons.colonies;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.constant.BuildingConstants;
import com.minecolonies.core.blocks.huts.BlockHutTownHall;
import com.mojang.authlib.GameProfile;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reinos de NPC: parte das colônias abandonadas que o MineColonies gera no mundo nasce habitada, com um dono que não é
 * jogador, prédios reativados, colonos e guardas. São as nações que o Conquistador (Sacerdote Vermelho 1) pode conquistar
 * quando joga sozinho. As outras continuam abandonadas (servem ao Invocador de Milagres, Tolo 2).
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class NpcKingdoms {
    private NpcKingdoms() {}

    /** Em cada 100 colônias abandonadas geradas, quantas nascem como reino */
    public static final int KINGDOM_CHANCE = 50;
    private static final int MIN_PEOPLE = 10, MAX_PEOPLE = 24, SCAN_CHUNKS = 6;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (!(entity.getBlockState().getBlock() instanceof BlockHutTownHall) || !(entity instanceof AbstractTileEntityColonyBuilding hut)) continue;
            if (!hut.getPositionedTags().getOrDefault(BlockPos.ZERO, List.of()).contains(BuildingConstants.DEACTIVATED)) continue;
            BlockPos pos = entity.getBlockPos().immutable();
            Data data = data(level);
            if (!data.seen.add(pos.asLong())) continue;
            data.setDirty();
            if (Math.floorMod(level.getSeed() ^ pos.asLong() * 0x9E3779B97F4A7C15L, 100) >= KINGDOM_CHANCE) continue;
            // espera o resto da colônia (outros chunks) carregar antes de dar vida a ela
            ServerScheduler.scheduleDelayed(60, () -> found(level, pos), level);
        }
    }

    private static void found(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AbstractTileEntityColonyBuilding hall)) return;
        if (!hall.getPositionedTags().getOrDefault(BlockPos.ZERO, List.of()).contains(BuildingConstants.DEACTIVATED)) return;
        if (IColonyManager.getInstance().getColonyByPosFromWorld(level, pos) != null) return;

        if (hall instanceof com.minecolonies.core.tileentities.TileEntityColonyBuilding building) building.reactivate();
        String pack = hall.getStructurePack() == null ? "Medieval Oak" : hall.getStructurePack().getName();
        String ruler = "Reino" + Long.toHexString(pos.asLong() & 0xFFFFFF);
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("lotmcraft:kingdom:" + pos.asLong()).getBytes(StandardCharsets.UTF_8)), ruler);
        var owner = FakePlayerFactory.get(level, profile);
        owner.moveTo(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        IColony colony = IColonyManager.getInstance().createColony(level, pos, owner, ruler, pack);
        if (colony == null) return;
        colony.getServerBuildingManager().addNewBuilding(hall, level);

        // os outros prédios da colônia abandonada voltam a funcionar (casas, torres de guarda...)
        ChunkPos center = new ChunkPos(pos);
        for (int cx = -SCAN_CHUNKS; cx <= SCAN_CHUNKS; cx++) {
            for (int cz = -SCAN_CHUNKS; cz <= SCAN_CHUNKS; cz++) {
                if (!level.hasChunk(center.x + cx, center.z + cz)) continue;
                for (BlockEntity entity : new ArrayList<>(level.getChunk(center.x + cx, center.z + cz).getBlockEntities().values())) {
                    if (entity == hall || !(entity instanceof AbstractTileEntityColonyBuilding hut)) continue;
                    if (!hut.getPositionedTags().getOrDefault(BlockPos.ZERO, List.of()).contains(BuildingConstants.DEACTIVATED)) continue;
                    if (!colony.isCoordInColony(level, entity.getBlockPos())) continue;
                    try {
                        if (hut instanceof com.minecolonies.core.tileentities.TileEntityColonyBuilding building) building.reactivate();
                        colony.getServerBuildingManager().addNewBuilding(hut, level);
                    } catch (Exception e) {
                        LOTMCraft.LOGGER.warn("Reino de NPC: prédio em {} não voltou a funcionar", entity.getBlockPos(), e);
                    }
                }
            }
        }

        // gente: o reino nasce habitado
        colony.getCitizenManager().calculateMaxCitizens();
        int people = Math.max(MIN_PEOPLE, Math.min(MAX_PEOPLE, colony.getCitizenManager().getMaxCitizens()));
        colony.getCitizenManager().setMaxCitizens(Math.max(people, colony.getCitizenManager().getMaxCitizens()));
        for (int i = colony.getCitizenManager().getCurrentCitizenCount(); i < people; i++) {
            ICitizenData citizen = colony.getCitizenManager().createAndRegisterCivilianData();
            colony.getCitizenManager().spawnOrCreateCivilian(citizen, level, List.of(pos.above()), true);
        }
        // defensores do reino: golens de ferro que guardam a prefeitura (além dos guardas, se o reino tiver torres)
        int golems = 3 + people / 6;
        for (int i = 0; i < golems; i++) {
            var golem = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(level);
            if (golem == null) continue;
            double angle = Math.PI * 2 * i / golems;
            BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    pos.offset((int) (Math.cos(angle) * 8), 0, (int) (Math.sin(angle) * 8)));
            golem.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, level.random.nextFloat() * 360, 0);
            golem.setPersistenceRequired();
            golem.getPersistentData().putInt(DEFENDER, colony.getID());
            level.addFreshEntity(golem);
        }
        // o nome do reino vem da família de um dos seus moradores
        List<ICitizenData> citizens = colony.getCitizenManager().getCitizens();
        if (!citizens.isEmpty()) {
            String[] parts = citizens.get(level.random.nextInt(citizens.size())).getName().split(" ");
            colony.setName("Reino de " + parts[parts.length - 1]);
        }
        data(level).kingdoms.add(colony.getID());
        data(level).setDirty();
        LOTMCraft.LOGGER.info("Reino de NPC fundado: {} em {} ({} moradores)", colony.getName(), pos, colony.getCitizenManager().getCurrentCitizenCount());
    }

    /** Marca dos golens que defendem um reino (id da colônia) */
    public static final String DEFENDER = "LotmKingdomDefender";

    public static boolean isDefender(net.minecraft.world.entity.Entity entity, IColony colony) {
        return entity.getPersistentData().contains(DEFENDER) && entity.getPersistentData().getInt(DEFENDER) == colony.getID();
    }

    public static boolean isKingdom(ServerLevel level, IColony colony) {
        return data(level).kingdoms.contains(colony.getID());
    }

    private static Data data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "lotmcraft_npc_kingdoms");
    }

    private static final class Data extends SavedData {
        private final Set<Long> seen = new HashSet<>();
        private final Set<Integer> kingdoms = new HashSet<>();

        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data data = new Data();
            for (Tag t : tag.getList("Seen", Tag.TAG_LONG)) data.seen.add(((LongTag) t).getAsLong());
            for (int id : tag.getIntArray("Kingdoms")) data.kingdoms.add(id);
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            var list = new net.minecraft.nbt.ListTag();
            seen.forEach(p -> list.add(LongTag.valueOf(p)));
            tag.put("Seen", list);
            tag.putIntArray("Kingdoms", kingdoms.stream().mapToInt(Integer::intValue).toArray());
            return tag;
        }
    }
}
