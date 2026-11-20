package de.jakob.lotm.network.packets.handlers;

import de.jakob.lotm.LOTMCraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Mantém os efeitos duradouros (como a Jaula Espacial) certos quando você se afasta e volta */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public final class BlockEffectTicker {
    private BlockEffectTicker() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientHandler.tickBlockEffects();
    }
}
