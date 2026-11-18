package de.jakob.lotm.addons.rituals.mixins;

import de.jakob.lotm.addons.rituals.RedPriestRituals;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Toda mudança de clima forçada (poderes, artefatos, profecias, comandos) passa por aqui; a troca natural não */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatherMixin {
    @Inject(method = "setWeatherParameters", at = @At("HEAD"))
    private void lotmcraft$weatherChanged(int clearTime, int weatherTime, boolean raining, boolean thundering, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.isRaining() != raining || level.isThundering() != thundering) RedPriestRituals.weatherChanged(level);
    }
}
