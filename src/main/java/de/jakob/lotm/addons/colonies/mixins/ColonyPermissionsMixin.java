package de.jakob.lotm.addons.colonies.mixins;

import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.colony.permissions.Permissions;
import de.jakob.lotm.addons.colonies.Colonists;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** O Deus do Erro (Sequência 0) não segue as regras de nenhuma colônia: toda permissão é dele. E o estandarte da conquista entra. */
@Mixin(value = Permissions.class, remap = false)
public abstract class ColonyPermissionsMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private com.minecolonies.core.colony.Colony colony;

    @Inject(method = "hasPermission(Lnet/minecraft/world/entity/player/Player;Lcom/minecolonies/api/colony/permissions/Action;)Z",
            at = @At("HEAD"), cancellable = true)
    private void lotmcraft$errorGod(Player player, Action action, CallbackInfoReturnable<Boolean> cir) {
        if (Colonists.ignoresColonyRules(player)) cir.setReturnValue(true);
        // controlando o corpo de um colono daqui: você é ele, pode tudo
        else if (colony != null && de.jakob.lotm.addons.colonies.ControlledCitizens.controlsIn(player, colony.getID())) cir.setReturnValue(true);
        // guerra declarada: lá dentro, quem ataca pode tudo
        else if (colony != null && de.jakob.lotm.addons.colonies.ColonyConquest.atWar(colony.getID(), player.getUUID())) cir.setReturnValue(true);
        // o estandarte da conquista, no instante em que é fincado
        else if (action == Action.PLACE_BLOCKS && de.jakob.lotm.addons.colonies.ColonyConquest.placingConquestBanner()) cir.setReturnValue(true);
        // e o golpe nos defensores, em guerra declarada
        else if ((action == Action.ATTACK_ENTITY || action == Action.ATTACK_CITIZEN) && de.jakob.lotm.addons.colonies.ColonyConquest.attackingDefender()) cir.setReturnValue(true);
        // disfarçado de um colono daqui (Caminho do Erro): só pode abrir baús, nada de quebrar ou pôr blocos
        else if ((action == Action.OPEN_CONTAINER || action == Action.RIGHTCLICK_BLOCK || action == Action.RIGHTCLICK_ENTITY)
                && de.jakob.lotm.addons.rituals.ErrorDisguise.openingChest()) cir.setReturnValue(true);
    }

    /** Disfarçado de um colono daqui, os guardas veem o colono, não um inimigo */
    @Inject(method = "getRank(Lnet/minecraft/world/entity/player/Player;)Lcom/minecolonies/api/colony/permissions/Rank;",
            at = @At("RETURN"), cancellable = true)
    private void lotmcraft$disguisedRank(Player player, CallbackInfoReturnable<com.minecolonies.api.colony.permissions.Rank> cir) {
        if (colony == null || cir.getReturnValue() == null || !cir.getReturnValue().isHostile()) return;
        if (de.jakob.lotm.addons.colonies.ColonyConquest.atWar(colony.getID(), player.getUUID())) return;
        if (!player.level().isClientSide && (de.jakob.lotm.addons.rituals.ErrorDisguise.disguisedIn(player, colony.getID())
                || de.jakob.lotm.addons.colonies.ControlledCitizens.controlsIn(player, colony.getID()))) {
            cir.setReturnValue(((com.minecolonies.core.colony.permissions.Permissions) (Object) this).getRankNeutral());
        }
    }
}
