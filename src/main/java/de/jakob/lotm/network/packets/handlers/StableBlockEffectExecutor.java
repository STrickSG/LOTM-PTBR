package de.jakob.lotm.network.packets.handlers;

import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Um efeito preso a um ponto do mundo que não some quando o bloco desse ponto muda. O do Photon apaga o efeito se o
 * bloco trocar (a grama quebrada pela própria habilidade, por exemplo), e aí o Estilhaçar o Espaço ficava sem os planetas.
 * Quando o pedaço do mundo descarrega, quem cuida é o {@link ClientHandler#tickBlockEffects()}.
 */
public class StableBlockEffectExecutor extends BlockEffectExecutor {
    public StableBlockEffectExecutor(FX fx, Level level, BlockPos pos) {
        super(fx, level, pos);
    }

    @Override
    public void updateFXObjectTick(IFXObject fxObject) {
        if (runtime != null && fxObject == runtime.getRoot() && runtimeEnded()) {
            retire(CACHE, pos);
        }
    }
}
