package de.jakob.lotm.addons.rituals.mixins;

import de.jakob.lotm.addons.rituals.ErrorDisguise;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * Cavalo de Troia do Destino: mensagens do servidor que citariam alguém disfarçado (progresso, morte, entrada e saída)
 * não são enviadas. Quem vive a vida de outro não pode ser exposto.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListDisguiseMixin {

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
    private void lotmcraft$hideDisguised(Component message, boolean bypassHiddenChat, CallbackInfo ci) {
        if (ErrorDisguise.exposes(message)) ci.cancel();
    }

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V", at = @At("HEAD"), cancellable = true)
    private void lotmcraft$hideDisguisedPerPlayer(Component message, Function<ServerPlayer, Component> perPlayer, boolean bypassHiddenChat, CallbackInfo ci) {
        if (ErrorDisguise.exposes(message)) ci.cancel();
    }
}
