package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IAssignsCitizen;
import com.minecolonies.core.colony.buildings.modules.BedHandlingModule;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;

/**
 * O templo como moradia: os fiéis da Igreja dele podem morar e dormir nas camas do prédio.
 * Cabem tantos quantas camas o prédio tiver; ninguém é colocado sozinho, quem escolhe é o Deus no menu do templo.
 */
public class ChurchLivingModule extends LivingBuildingModule {
    @Override
    public int getModuleMax() {
        BedHandlingModule beds = building.getFirstModuleOccurance(BedHandlingModule.class);
        return beds == null ? 0 : beds.getRegisteredBlocks().size();
    }

    /** Não pega os sem-teto da colônia: só mora quem o Deus escolher */
    @Override
    public void onColonyTick(IColony colony) {
    }

    /** Leva o colono para o templo, tirando-o da casa onde morava */
    public boolean moveIn(ICitizenData citizen) {
        if (hasAssignedCitizen(citizen)) return true;
        if (isFull()) return false;
        IBuilding old = citizen.getHomeBuilding();
        if (old != null) {
            for (IAssignsCitizen module : old.getModulesByType(IAssignsCitizen.class)) {
                if (module instanceof LivingBuildingModule && module.hasAssignedCitizen(citizen)) module.removeCitizen(citizen);
            }
        }
        return assignCitizen(citizen);
    }
}
