package de.jakob.lotm.addons.colonies;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Ligação com o MineColonies: os rituais usam os colonos no lugar dos aldeões.
 * Cada colono é identificado pelo UUID fixo dos dados dele (não o da entidade, que muda quando ele renasce).
 */
public final class Colonists {
    private Colonists() {}

    /** Colono morador (não conta visitantes da taverna) */
    public static boolean isColonist(Entity entity) {
        return entity instanceof EntityCitizen citizen && !citizen.level().isClientSide && citizen.getCitizenData() != null;
    }

    /** Qualquer colono, morador ou visitante */
    public static boolean isAnyCitizen(Entity entity) {
        return entity instanceof AbstractEntityCitizen;
    }

    @Nullable
    public static ICitizenData data(Entity entity) {
        return entity instanceof EntityCitizen citizen ? citizen.getCitizenData() : null;
    }

    /** Identidade fixa do colono, ou null se não for colono */
    @Nullable
    public static UUID id(Entity entity) {
        ICitizenData data = data(entity);
        return data == null ? null : data.getUUID();
    }

    /** Todos os colonos que ainda existem, em todas as colônias */
    public static Set<UUID> allIds() {
        Set<UUID> ids = new HashSet<>();
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) ids.add(citizen.getUUID());
        }
        return ids;
    }

    /** Dados do colono com essa identidade, em qualquer colônia */
    @Nullable
    public static ICitizenData find(UUID id) {
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
                if (citizen.getUUID().equals(id)) return citizen;
            }
        }
        return null;
    }

    @Nullable
    public static IColony colonyAt(Level level, BlockPos pos) {
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        return colony != null && colony.isCoordInColony(level, pos) ? colony : null;
    }

    /** Colônia de que o jogador é dono, em qualquer dimensão */
    @Nullable
    public static IColony ownedBy(UUID player) {
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (player.equals(colony.getPermissions().getOwner())) return colony;
        }
        return null;
    }
}
