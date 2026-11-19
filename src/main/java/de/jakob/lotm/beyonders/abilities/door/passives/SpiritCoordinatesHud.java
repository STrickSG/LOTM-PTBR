package de.jakob.lotm.beyonders.abilities.door.passives;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.dimension.ModDimensions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Coordenadas no Mundo de Cima, no canto superior esquerdo (abaixo do painel do Beyonder), enquanto o servidor estiver mandando */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public class SpiritCoordinatesHud {
    private static final int COLOR = 0xDB11ED;
    /** Some se o servidor parar de mandar (saiu do Mundo Espiritual, perdeu a passiva) */
    private static final long TIMEOUT_MS = 2000;

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT,
                ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "spirit_world_coordinates"),
                SpiritCoordinatesHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        SpiritCoordinatesPacket coords = SpiritCoordinatesPacket.last;
        if (coords == null || mc.player == null || mc.level == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        if (System.currentTimeMillis() - SpiritCoordinatesPacket.receivedAt > TIMEOUT_MS) return;
        if (!mc.level.dimension().equals(ModDimensions.SPIRIT_WORLD_DIMENSION_KEY)) return;
        Component text = Component.translatable("ability.lotmcraft.spirit_world_awareness.overworld_coordinates")
                .append(": (" + coords.x() + ", " + coords.y() + ", " + coords.z() + ")");
        // logo abaixo do painel do Beyonder (medalhão, espiritualidade e a fileira de ícones embaixo dele)
        g.drawString(mc.font, text, 6, 66, COLOR, true);
    }
}
