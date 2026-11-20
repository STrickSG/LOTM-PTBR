package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import de.jakob.lotm.LOTMCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** O que a Igreja registra: a cabana (prédio do MineColonies), o altar e o próprio tipo de prédio */
public final class ChurchRegistry {
    private ChurchRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LOTMCraft.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LOTMCraft.MOD_ID);
    public static final DeferredRegister<BuildingEntry> BUILDINGS = DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, LOTMCraft.MOD_ID);

    public static final DeferredHolder<Block, ChurchHutBlock> CHURCH_HUT = BLOCKS.register(ChurchHutBlock.HUT_NAME, ChurchHutBlock::new);
    public static final DeferredHolder<Item, ItemBlockHut> CHURCH_HUT_ITEM = ITEMS.register(ChurchHutBlock.HUT_NAME,
            () -> new ItemBlockHut(CHURCH_HUT.get(), new Item.Properties()));

    public static final DeferredHolder<Block, ChurchAltarBlock> ALTAR = BLOCKS.register("church_altar",
            () -> new ChurchAltarBlock(BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.STONE).noOcclusion().lightLevel(s -> 10)));
    public static final DeferredHolder<Item, BlockItem> ALTAR_ITEM = ITEMS.register("church_altar", () -> new BlockItem(ALTAR.get(), new Item.Properties()));

    public static final DeferredHolder<BuildingEntry, BuildingEntry> CHURCH = BUILDINGS.register(ChurchBuilding.SCHEMATIC, () -> new BuildingEntry.Builder()
            .setBuildingBlock(CHURCH_HUT.get())
            .setBuildingProducer(ChurchBuilding::new)
            .setBuildingViewProducer(() -> ChurchBuilding.View::new)
            .setRegistryName(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, ChurchBuilding.SCHEMATIC))
            .createBuildingEntry());

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BUILDINGS.register(modEventBus);
        modEventBus.addListener(ChurchRegistry::addHutToBlockEntity);
    }

    /** A cabana usa a entidade de bloco dos prédios do MineColonies, que só aceita os blocos que conhece */
    private static void addHutToBlockEntity(BlockEntityTypeAddBlocksEvent event) {
        event.modify(MinecoloniesTileEntities.BUILDING.get(), CHURCH_HUT.get());
    }
}
