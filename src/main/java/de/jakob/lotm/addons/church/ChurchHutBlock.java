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

    @Override
    public ResourceLocation getRegistryName() {
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, HUT_NAME);
    }
}
