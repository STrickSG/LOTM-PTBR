package de.jakob.lotm.addons.church;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import de.jakob.lotm.LOTMCraft;
import net.minecraft.resources.ResourceLocation;

/** A cabana da Igreja: o bloco que vira o prédio no MineColonies (como a cabana de qualquer outro trabalhador) */
public class ChurchHutBlock extends AbstractBlockHut<ChurchHutBlock> {
    public static final String HUT_NAME = "blockhutchurch";

    @Override
    public String getHutName() {
        return HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return ChurchRegistry.CHURCH.get();
    }

    /**
     * A igreja só existe no pacote de estilos LOTM. Se a cabana for colocada sem a ferramenta de construção
     * (ou com o estilo da colônia selecionado), aponta para o pacote LOTM e a planta da Igreja do Sol,
     * senão a janela de construir procura a igreja no estilo da colônia e não acha nada.
     */
    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding te) {
            var pack = te.getStructurePack();
            if (pack == null || !ChurchModels.PACK.equals(pack.getName())) {
                var lotm = com.ldtteam.structurize.storage.StructurePacks.getStructurePack(ChurchModels.PACK);
                if (lotm != null) {
                    te.setStructurePack(lotm);
                    te.setBlueprintPath(ChurchModels.DEFAULT_PATH);
                }
            }
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, HUT_NAME);
    }
}
