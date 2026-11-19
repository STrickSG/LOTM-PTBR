package de.jakob.lotm.addons.colonies;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Controlar o corpo de um colono (Parasitismo no modo Controlar, ou qualquer controle de corpo):
 * <ul>
 *   <li>enquanto o corpo está com você, o colono fica "viajando" (o MineColonies não cria outro igual);</li>
 *   <li>na colônia dele você é ele: todas as permissões e os guardas não te atacam.</li>
 * </ul>
 * Guardado no jogador, para não deixar o colono preso em viagem se o servidor fechar no meio.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class ControlledCitizens {
    private ControlledCitizens() {}

    private static final String TAG = "LotmControlledCitizen";

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Chamado logo antes de o corpo do colono sair do mundo para ser controlado */
    public static void onStart(ServerPlayer player, LivingEntity target) {
        if (!(target instanceof EntityCitizen citizen) || citizen.getCitizenData() == null) return;
        ICitizenData data = citizen.getCitizenData();
        data.getColony().getTravellingManager().startTravellingTo(data.getId(), citizen.blockPosition(), Integer.MAX_VALUE / 2);
        CompoundTag tag = new CompoundTag();
        tag.putInt("colony", data.getColony().getID());
        tag.putInt("citizen", data.getId());
        persisted(player).put(TAG, tag);
    }

    /** Chamado quando o controle acaba (o corpo volta ao mundo, ou não) */
    public static void onStop(ServerPlayer player) {
        CompoundTag tag = persisted(player).getCompound(TAG);
        if (tag.isEmpty()) return;
        persisted(player).remove(TAG);
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (colony.getID() == tag.getInt("colony")) colony.getTravellingManager().finishTravellingFor(tag.getInt("citizen"));
        }
    }

    /** Está controlando agora um colono desta colônia */
    public static boolean controlsIn(Player player, int colonyId) {
        if (player.level().isClientSide) return false;
        CompoundTag tag = persisted(player).getCompound(TAG);
        return !tag.isEmpty() && tag.getInt("colony") == colonyId
                && player.getData(ModAttachments.ENTITY_CONTROLLING_COMPONENT).isControlling();
    }

    /** Voltou ao jogo sem estar controlando (servidor fechou no meio): o colono volta da viagem */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !player.getData(ModAttachments.ENTITY_CONTROLLING_COMPONENT).isControlling()) {
            onStop(player);
        }
    }
}
