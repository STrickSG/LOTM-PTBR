package de.jakob.lotm.beyonders.potions;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.rituals.AdvancementRituals;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registra um pergaminho de ritual para cada ritual de avanço ligado (ver AdvancementRituals). */
public class RitualRecipeItemHandler {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LOTMCraft.MOD_ID);

    /** Nome da Fórmula Completa de um ritual, ex.: priest_of_light_complete_formula */
    public static String formulaId(AdvancementRituals.Ritual ritual) {
        return de.jakob.lotm.util.BeyonderData.pathwayInfos.get(ritual.pathway()).sequenceNames()[ritual.sequence()] + "_complete_formula";
    }

    /** Pergaminho do ritual deste Caminho e Sequência, ou null se não houver ritual */
    public static Item ritualScroll(String pathway, int sequence) {
        AdvancementRituals.Ritual ritual = AdvancementRituals.get(pathway, sequence);
        if (ritual == null) return null;
        return ITEMS.getEntries().stream().map(h -> h.get())
                .filter(i -> i instanceof RitualRecipeItem r && r.getPathway().equals(pathway) && r.getSequence() == sequence)
                .findFirst().orElse(null);
    }

    public static void registerRituals(IEventBus eventBus) {
        for (AdvancementRituals.Ritual ritual : AdvancementRituals.all()) {
            ITEMS.registerItem(ritual.id(),
                    properties -> new RitualRecipeItem(properties, ritual.pathway(), ritual.sequence()),
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
            // Fórmula Completa: receita + pergaminho do ritual da mesma Sequência, feita na bancada
            ITEMS.registerItem(formulaId(ritual),
                    properties -> new CompleteFormulaItem(properties, ritual.pathway(), ritual.sequence()),
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
        }
        ITEMS.register(eventBus);
    }
}
