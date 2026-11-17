package de.jakob.lotm.util.helper;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class MessageUtil {
    private MessageUtil() {}

    /**
     * Short status message shown above the hotbar (action bar) instead of in chat.
     * Works on both sides: a ServerPlayer sends it to its client, a client player shows it directly.
     * Entities that aren't players can't see messages, as with sendSystemMessage.
     */
    public static void actionBar(Entity entity, Component message) {
        if (entity instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }

    /** Same as {@link #actionBar(Entity, Component)}; the flag of sendSystemMessage(message, overlay) is ignored. */
    public static void actionBar(Entity entity, Component message, boolean ignoredOverlay) {
        actionBar(entity, message);
    }
}
