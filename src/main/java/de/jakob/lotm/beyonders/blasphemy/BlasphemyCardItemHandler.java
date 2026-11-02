package de.jakob.lotm.beyonders.blasphemy;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers one Card of Blasphemy for every pathway that has potion recipes and card art. */
public class BlasphemyCardItemHandler {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LOTMCraft.MOD_ID);

    // Pathways with card art, model and crafting recipe. Twilight Giant has recipes in 1.13.3 but no card art yet.
    private static final java.util.Set<String> WITH_ART = java.util.Set.of(
            "fool", "door", "error", "sun", "tyrant", "darkness", "death", "demoness", "red_priest",
            "mother", "abyss", "visionary", "wheel_of_fortune", "justiciar");

    public static void registerCards(IEventBus eventBus) {
        for (String pathway : BeyonderData.pathways) {
            if (!BeyonderData.implementedRecipes.containsKey(pathway) || !WITH_ART.contains(pathway)) continue;
            ITEMS.registerItem("blasphemy_card_" + pathway,
                    properties -> new BlasphemyCardItem(properties, pathway),
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
        }
        ITEMS.register(eventBus);
    }
}
