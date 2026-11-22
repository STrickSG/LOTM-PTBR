package de.jakob.lotm.addons.colonies;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.AIEventTarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIBlockingEventType;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.church.ColonistBeyonders;
import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItemHandler;
import de.jakob.lotm.beyonders.potions.PotionRecipeItemHandler;
import de.jakob.lotm.beyonders.potions.RitualRecipeItemHandler;
import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.gui.custom.trades.BeyonderTradeMenu;
import de.jakob.lotm.item.ModIngredients;
import de.jakob.lotm.item.ModItems;
import de.jakob.lotm.item.PotionIngredient;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * As reuniões de Beyonders dos reinos de NPC. Cada reino tem alguns colonos Beyonders; uma noite por semana eles se
 * reúnem em segredo numa casa da cidade e vendem ingredientes (até a Sequência 4), receitas, pergaminhos de ritual e
 * características, conforme o caminho e a Sequência de cada um. Só vendem a quem traz o convite, que se compra de um
 * Beyonder do reino em qualquer outro momento e diz o dia, a hora e o lugar.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class KingdomGatherings {
    private KingdomGatherings() {}

    /** 20h às 24h */
    public static final long START = 14000, END = 18000;
    /** Preço do convite, em soli */
    public static final int INVITATION_PRICE = 10;
    private static final int[] PRICE = {0, 2560, 1920, 1600, 1100, 400, 200, 100, 30, 10};

    public enum MarketState implements IState { MARKET }

    /** colono vendedor -> as ofertas desta reunião */
    private static final Map<UUID, List<BeyonderNPCEntity.TradeEntry>> STOCK = new HashMap<>();
    /** colônia -> o dia da reunião cujas ofertas estão em STOCK */
    private static final Map<Integer, Long> STOCKED = new HashMap<>();
    private static final Map<EntityCitizen, Boolean> HOOKED = Collections.synchronizedMap(new WeakHashMap<>());

    // ---------------------------------------------------------------- quando e onde

    /** O dia (do mundo) da próxima reunião deste reino, a partir de hoje (a de hoje, se ainda não acabou) */
    public static long nextDay(IColony colony, long dayTime) {
        long day = dayTime / 24000L;
        if (dayTime % 24000L >= END) day++;
        while (Math.floorMod(day + colony.getID() * 3L, 7) != 0) day++;
        return day;
    }

    public static boolean isGatheringNow(IColony colony, long dayTime) {
        long t = dayTime % 24000L;
        return t >= START && t < END && nextDay(colony, dayTime) == dayTime / 24000L;
    }

    /** A casa onde vai ser a reunião: a casa de reunião do reino (com o porão); se ainda não houver, uma casa qualquer */
    public static BlockPos place(IColony colony, long day) {
        if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
            CompoundTag house = data(colony.getWorld().getServer()).houses.get(colony.getID());
            if (house != null) return BlockPos.of(house.getLong("Door"));
        }
        List<IBuilding> houses = new ArrayList<>();
        for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
            String name = b.getSchematicName();
            if (name.contains("citizen") || name.contains("residence") || name.contains("tavern")) houses.add(b);
        }
        if (houses.isEmpty()) return colony.getCenter();
        houses.sort((a, b) -> Long.compare(a.getPosition().asLong(), b.getPosition().asLong()));
        return houses.get((int) Math.floorMod(day * 31 + colony.getID(), houses.size())).getPosition();
    }

    // ---------------------------------------------------------------- os Beyonders do reino

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 100 != 0) return;
        ServerLevel level = server.overworld();
        long time = level.getDayTime();
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (!(colony.getWorld() instanceof ServerLevel world) || !NpcKingdoms.isKingdom(world, colony)) continue;
            seed(server, world, colony);
            buildHouse(server, world, colony);
            long day = time / 24000L;
            if (isGatheringNow(colony, time) && !Long.valueOf(day).equals(STOCKED.get(colony.getID()))) stock(server, colony, day);
        }
    }

    /** Uma vez por reino: 10 a 20% dos colonos são Beyonders, de caminhos variados e Sequências 9 a 4 */
    private static void seed(MinecraftServer server, ServerLevel world, IColony colony) {
        Data data = data(server);
        if (data.seeded.contains(colony.getID())) return;
        List<ICitizenData> citizens = new ArrayList<>(colony.getCitizenManager().getCitizens());
        if (citizens.size() < 4) return;   // espera o reino ter gente
        data.seeded.add(colony.getID());
        data.setDirty();
        Random random = new Random(colony.getID() * 7919L + world.getSeed());
        Collections.shuffle(citizens, random);
        int count = Math.max(2, Math.round(citizens.size() * (0.10f + random.nextFloat() * 0.10f)));
        List<String> pathways = new ArrayList<>();
        for (String p : BeyonderData.implementedPathways) if (PotionRecipeItemHandler.selectRecipeOfPathwayAndSequence(p, 9) != null) pathways.add(p);
        if (pathways.isEmpty()) return;
        int[] seqs = {9, 9, 9, 9, 9, 8, 8, 8, 8, 7, 7, 7, 7, 6, 6, 6, 5, 5, 4, 4};
        for (int i = 0; i < count && i < citizens.size(); i++) {
            ICitizenData c = citizens.get(i);
            if (ColonistBeyonders.info(server, c.getUUID()) != null) continue;
            ColonistBeyonders.makeBeyonder(server, c, pathways.get(random.nextInt(pathways.size())), seqs[random.nextInt(seqs.length)]);
        }
    }

    /** As ofertas de cada Beyonder para a reunião de hoje */
    private static void stock(MinecraftServer server, IColony colony, long day) {
        STOCKED.put(colony.getID(), day);
        Random random = new Random(day * 1013 + colony.getID());
        for (ICitizenData c : colony.getCitizenManager().getCitizens()) {
            ColonistBeyonders.Info info = ColonistBeyonders.info(server, c.getUUID());
            if (info == null) continue;
            List<BeyonderNPCEntity.TradeEntry> list = new ArrayList<>();
            int wanted = 3 + random.nextInt(3);
            for (int tries = 0; tries < 30 && list.size() < wanted; tries++) {
                BeyonderNPCEntity.TradeEntry t = offer(info.pathway(), info.sequence(), random);
                if (t != null && list.stream().noneMatch(o -> o.result().getItem() == t.result().getItem())) list.add(t);
            }
            STOCK.put(c.getUUID(), list);
        }
    }

    /** Uma oferta do caminho do vendedor, de uma Sequência que ele conhece (a dele ou mais baixa) */
    private static BeyonderNPCEntity.TradeEntry offer(String pathway, int own, Random random) {
        int seq = own + random.nextInt(Math.max(1, 10 - own));
        float roll = random.nextFloat();
        Item item;
        int pct;
        if (roll < 0.45f) {
            // ingredientes até a Sequência 4
            List<PotionIngredient> options = new ArrayList<>();
            int s = Math.max(4, seq);
            for (var holder : ModIngredients.ITEMS.getEntries())
                if (holder.get() instanceof PotionIngredient ing && ing.getSequence() == s && ing.isForPathway(pathway)) options.add(ing);
            if (options.isEmpty()) return null;
            PotionIngredient ing = options.get(random.nextInt(options.size()));
            item = ing;
            seq = s;
            pct = ing.isMainIngredient() ? 30 : 15;
        } else if (roll < 0.70f) {
            item = PotionRecipeItemHandler.selectRecipeOfPathwayAndSequence(pathway, seq);
            pct = 33;
        } else if (roll < 0.82f) {
            // pergaminhos de ritual: só há rituais das Sequências 5 a 1
            int s = Math.max(own, Math.min(5, seq));
            if (s > 5) return null;
            item = RitualRecipeItemHandler.ritualScroll(pathway, s);
            seq = s;
            pct = 60;
        } else {
            item = BeyonderCharacteristicItemHandler.selectCharacteristicOfPathwayAndSequence(pathway, seq);
            pct = 100;
        }
        if (item == null) return null;
        long soli = Math.max(1, (long) PRICE[seq] * pct / 100 * (85 + random.nextInt(31)) / 100);
        return priced(new ItemStack(item), soli);
    }

    private static BeyonderNPCEntity.TradeEntry priced(ItemStack result, long soli) {
        int pounds = (int) Math.min(soli / 20, 64);
        int rest = (int) Math.min(soli - pounds * 20L, 64);
        ItemStack a = pounds > 0 ? new ItemStack(ModItems.ONE_POUND.get(), pounds) : new ItemStack(ModItems.ONE_SOLI.get(), rest);
        ItemStack b = pounds > 0 && rest > 0 ? new ItemStack(ModItems.ONE_SOLI.get(), rest) : ItemStack.EMPTY;
        return new BeyonderNPCEntity.TradeEntry(a, b, result);
    }

    // ---------------------------------------------------------------- os vendedores vão à reunião

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntityCitizen citizen)) return;
        if (HOOKED.put(citizen, Boolean.TRUE) != null) return;
        var ai = citizen.getCitizenAI();
        ai.addTransition(new AIEventTarget<IState>(AIBlockingEventType.AI_BLOCKING, () -> placeFor(citizen) != null, () -> attend(citizen), 20));
        ai.addTransition(new TickingTransition<IState>(MarketState.MARKET, () -> placeFor(citizen) == null, () -> CitizenAIState.IDLE, 20));
    }

    /** Onde o Beyonder do reino deve estar agora (ou null) */
    public static BlockPos placeFor(EntityCitizen entity) {
        if (!(entity.level() instanceof ServerLevel level)) return null;
        ICitizenData data = entity.getCitizenData();
        if (data == null || data.getColony() == null || !NpcKingdoms.isKingdom(level, data.getColony())) return null;
        if (!isGatheringNow(data.getColony(), level.getDayTime()) || data.getColony().getRaiderManager().isRaided()) return null;
        if (ColonistBeyonders.info(level.getServer(), data.getUUID()) == null) return null;
        return place(data.getColony(), level.getDayTime() / 24000L);
    }

    private static IState attend(EntityCitizen entity) {
        BlockPos at = placeFor(entity);
        if (at == null) return CitizenAIState.IDLE;
        if (entity.isSleeping()) entity.stopSleeping();
        BlockPos stall = stallFor(entity);
        if (stall == null) {
            if (EntityNavigationUtils.walkToPos(entity, at, 4, true) && entity.getRandom().nextInt(4) == 0)
                EntityNavigationUtils.walkToRandomPosAround(entity, at, 4, 0.6);
            return MarketState.MARKET;
        }
        if (EntityNavigationUtils.walkToPos(entity, stall, 0, true)) {
            entity.getNavigation().stop();
        } else if (entity.level().getDayTime() % 24000L > START + 2000 && !entity.blockPosition().closerThan(stall, 3)) {
            // quem não achou o caminho (porta, escada) chega de qualquer jeito: a reunião não espera
            entity.teleportTo(stall.getX() + 0.5, stall.getY(), stall.getZ() + 0.5);
        }
        return MarketState.MARKET;
    }

    /** A banca deste vendedor no porão: os Beyonders do reino, em ordem, cada um na sua */
    private static BlockPos stallFor(EntityCitizen entity) {
        if (!(entity.level() instanceof ServerLevel level)) return null;
        ICitizenData me = entity.getCitizenData();
        CompoundTag house = data(level.getServer()).houses.get(me.getColony().getID());
        if (house == null) return null;
        long[] spots = house.getLongArray("Stalls");
        if (spots.length == 0) return null;
        List<UUID> sellers = new ArrayList<>();
        for (ICitizenData c : me.getColony().getCitizenManager().getCitizens())
            if (ColonistBeyonders.info(level.getServer(), c.getUUID()) != null) sellers.add(c.getUUID());
        Collections.sort(sellers);
        int i = sellers.indexOf(me.getUUID());
        if (i < 0 || i >= spots.length) return null;   // mais vendedores que bancas: os outros ficam pela sala
        return BlockPos.of(spots[i]);
    }

    // ---------------------------------------------------------------- a casa de reunião do reino

    private static final String[] HOUSES = {"gathering/house1.blueprint", "gathering/house2.blueprint", "gathering/house3.blueprint", "gathering/house4.blueprint"};

    /** Uma vez por reino: escolhe uma das quatro casas e a constrói perto da prefeitura, num lugar plano e livre */
    private static void buildHouse(MinecraftServer server, ServerLevel level, IColony colony) {
        Data data = data(server);
        if (data.houses.containsKey(colony.getID())) return;
        BlockPos hall = colony.getCenter();
        if (!level.isLoaded(hall)) return;
        String path = HOUSES[Math.floorMod(colony.getID() * 7 + (int) (level.getSeed() & 0xFF), HOUSES.length)];
        var blueprint = com.ldtteam.structurize.storage.StructurePacks.getBlueprint("LOTM", path, level.registryAccess());
        if (blueprint == null) return;
        BlockPos offset = blueprint.getPrimaryBlockOffset();
        int sx = blueprint.getSizeX(), sz = blueprint.getSizeZ();
        Random random = new Random(colony.getID() * 31L + level.getSeed());
        double start = random.nextDouble() * Math.PI * 2;
        for (int r : new int[]{30, 38, 46, 54}) for (int k = 0; k < 16; k++) {
            double a = start + Math.PI * 2 * k / 16;
            int ax = hall.getX() + (int) (Math.cos(a) * r), az = hall.getZ() + (int) (Math.sin(a) * r);
            int x0 = ax - offset.getX(), z0 = az - offset.getZ();
            int[][] probes = {{x0, z0}, {x0 + sx - 1, z0}, {x0, z0 + sz - 1}, {x0 + sx - 1, z0 + sz - 1}, {x0 + sx / 2, z0 + sz / 2}};
            boolean ok = true;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            List<Integer> heights = new ArrayList<>();
            for (int[] p : probes) {
                BlockPos q = new BlockPos(p[0], 0, p[1]);
                if (!level.isLoaded(q)) { ok = false; break; }
                int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p[0], p[1]) - 1;
                if (!level.getFluidState(new BlockPos(p[0], h, p[1])).isEmpty()) { ok = false; break; }
                heights.add(h);
                lo = Math.min(lo, h); hi = Math.max(hi, h);
            }
            if (!ok || hi - lo > 4) continue;
            // longe dos prédios da colônia
            for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
                var c = b.getCorners();
                if (c == null || c.getA() == null) continue;
                int bx0 = Math.min(c.getA().getX(), c.getB().getX()) - 3, bx1 = Math.max(c.getA().getX(), c.getB().getX()) + 3;
                int bz0 = Math.min(c.getA().getZ(), c.getB().getZ()) - 3, bz1 = Math.max(c.getA().getZ(), c.getB().getZ()) + 3;
                if (x0 <= bx1 && x0 + sx - 1 >= bx0 && z0 <= bz1 && z0 + sz - 1 >= bz0) { ok = false; break; }
            }
            if (!ok) continue;
            Collections.sort(heights);
            BlockPos anchor = new BlockPos(ax, heights.get(heights.size() / 2), az);
            data.houses.put(colony.getID(), place(level, blueprint, anchor));
            data.setDirty();
            LOTMCraft.LOGGER.info("Reino {}: casa de reunião {} em {}", colony.getName(), path, anchor);
            return;
        }
    }

    /** Põe a casa bloco a bloco e anota a porta e as bancas (o lugar do vendedor, na frente de cada estandarte) */
    private static CompoundTag place(ServerLevel level, com.ldtteam.structurize.blueprints.v1.Blueprint blueprint, BlockPos anchor) {
        BlockPos origin = anchor.subtract(blueprint.getPrimaryBlockOffset());
        int below = blueprint.getPrimaryBlockOffset().getY();
        List<Long> stalls = new ArrayList<>();
        for (var info : blueprint.getBlockInfoAsList()) {
            var state = info.getState();
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
            BlockPos world = origin.offset(info.getPos());
            if ("structurize".equals(id.getNamespace())) {
                if (id.getPath().contains("solid")) level.setBlock(world, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
                continue;
            }
            level.setBlock(world, state, 2);
            if (info.hasTileEntityData()) {
                CompoundTag tile = blueprint.getTileEntityData(world, info.getPos());
                var entity = level.getBlockEntity(world);
                if (tile != null && entity != null) entity.loadWithComponents(tile, level.registryAccess());
            }
            if (info.getPos().getY() < below && state.getBlock() instanceof net.minecraft.world.level.block.WallBannerBlock)
                stalls.add(world.below(2).relative(state.getValue(net.minecraft.world.level.block.WallBannerBlock.FACING)).asLong());
        }
        Collections.sort(stalls);
        CompoundTag tag = new CompoundTag();
        tag.putLong("Door", anchor.above().asLong());
        tag.putLongArray("Stalls", stalls.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    /** O vendedor está na casa de reunião (no porão ou na sala) */
    private static boolean inHouse(IColony colony, long day, BlockPos pos) {
        return pos.closerThan(place(colony, day), 24);
    }

    // ---------------------------------------------------------------- comprar

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof EntityCitizen entity) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (event.getItemStack().getItem() instanceof de.jakob.lotm.beyonders.potions.BeyonderPotion) return;
        ICitizenData data = entity.getCitizenData();
        if (data == null || data.getColony() == null || !(entity.level() instanceof ServerLevel level) || !NpcKingdoms.isKingdom(level, data.getColony())) return;
        ColonistBeyonders.Info info = ColonistBeyonders.info(player.server, data.getUUID());
        if (info == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        IColony colony = data.getColony();
        long now = level.getDayTime(), today = now / 24000L;
        if (isGatheringNow(colony, now) && inHouse(colony, today, entity.blockPosition())) {
            boolean invited = false;
            for (ItemStack s : player.getInventory().items) if (GatheringInvitationItem.isFor(s, colony.getID(), today)) invited = true;
            if (!invited) {
                player.displayClientMessage(Component.translatable("lotmcraft.gathering.only_invited").withColor(0xB0A0C0), true);
                return;
            }
            List<BeyonderNPCEntity.TradeEntry> stock = STOCK.computeIfAbsent(data.getUUID(), k -> new ArrayList<>());
            open(player, entity, Component.translatable("lotmcraft.gathering.title", colony.getName()), stock, i -> stock.remove(i));
            return;
        }
        // fora da reunião: o Beyonder vende o convite para a próxima, em segredo
        long day = nextDay(colony, now);
        BlockPos at = place(colony, day);
        ItemStack invitation = GatheringInvitationItem.create(colony.getID(), colony.getName(), day, at.getX(), at.getY(), at.getZ());
        List<BeyonderNPCEntity.TradeEntry> one = new ArrayList<>(List.of(new BeyonderNPCEntity.TradeEntry(
                new ItemStack(ModItems.ONE_SOLI.get(), INVITATION_PRICE), ItemStack.EMPTY, invitation)));
        open(player, entity, Component.translatable("lotmcraft.gathering.invitation_offer"), one, i -> { });
    }

    private static void open(ServerPlayer player, EntityCitizen vendor, Component title, List<BeyonderNPCEntity.TradeEntry> trades,
                             java.util.function.IntConsumer sold) {
        List<BeyonderNPCEntity.TradeEntry> snapshot = new ArrayList<>(trades);
        player.openMenu(new SimpleMenuProvider((windowId, inv, p) -> new BeyonderTradeMenu(windowId, inv, vendor.getId(), snapshot)
                .onSold(i -> { if (i >= 0 && i < trades.size()) sold.accept(i); }), title), buf -> {
            buf.writeVarInt(vendor.getId());
            buf.writeBoolean(true);
            BeyonderTradeMenu.writeTrades(buf, snapshot);
        });
    }

    // ---------------------------------------------------------------- dados

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "lotmcraft_kingdom_gatherings");
    }

    private static final class Data extends SavedData {
        private final Set<Integer> seeded = new HashSet<>();
        /** colônia -> a casa de reunião (Door, Stalls) */
        private final Map<Integer, CompoundTag> houses = new HashMap<>();

        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            for (int id : tag.getIntArray("Seeded")) d.seeded.add(id);
            CompoundTag h = tag.getCompound("Houses");
            for (String k : h.getAllKeys()) d.houses.put(Integer.parseInt(k), h.getCompound(k));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.put("Seeded", new IntArrayTag(seeded.stream().mapToInt(Integer::intValue).toArray()));
            CompoundTag h = new CompoundTag();
            houses.forEach((k, v) -> h.put(String.valueOf(k), v));
            tag.put("Houses", h);
            return tag;
        }
    }
}
