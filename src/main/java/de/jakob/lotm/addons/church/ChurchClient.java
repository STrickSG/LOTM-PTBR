package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Só no cliente: abre o menu do templo */
public final class ChurchClient {
    private ChurchClient() {}

    public static void open(ChurchBuilding.View view) {
        Minecraft.getInstance().setScreen(new ChurchScreen(view));
    }

    public static void openAt(BlockPos temple) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        IBuildingView view = IColonyManager.getInstance().getBuildingView(mc.level.dimension(), temple);
        if (view instanceof ChurchBuilding.View church) open(church);
    }
}
