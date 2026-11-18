package de.jakob.lotm.addons.colonies.mixins;

import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.colony.permissions.PermissionsView;
import de.jakob.lotm.addons.colonies.Colonists;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No cliente: as telas da colônia também deixam o Deus do Erro fazer tudo */
@Mixin(value = PermissionsView.class, remap = false)
public abstract class ColonyPermissionsViewMixin {
    @Inject(method = "hasPermission(Lnet/minecraft/world/entity/player/Player;Lcom/minecolonies/api/colony/permissions/Action;)Z",
            at = @At("HEAD"), cancellable = true)
    private void lotmcraft$errorGod(Player player, Action action, CallbackInfoReturnable<Boolean> cir) {
        if (Colonists.ignoresColonyRules(player)) cir.setReturnValue(true);
    }
}
