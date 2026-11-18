package de.jakob.lotm.beyonders.rituals;

import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItem;
import de.jakob.lotm.beyonders.potions.PotionRecipeItemHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record  RitualRecipe(
        String id,
        String discoveredFrom,
        String candle,
        List<Sacrifice> sacrifices,
        Honorific honorific,
        Conditions conditions,
        ParticleColor particleColor,
        String bookColor,
        int durationTicks,
        Result result
) {

    public record Sacrifice(String item, int count) {}

    /** {@code translated}: as mesmas linhas em português (a mesa aceita as duas) */
    public record Honorific(List<String> lines, List<String> translated) {}

    /** Compara sem maiúsculas, acentos, vírgulas e pontos */
    private static List<String> normalized(List<String> lines) {
        return lines.stream().map(s -> java.text.Normalizer.normalize(s.toLowerCase().strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace(",", "").replace(".", "").replaceAll("\\s+", " ")).toList();
    }

    public record Conditions(
            int minSequence
    ) {}

    public record ParticleColor(
            float r,
            float g,
            float b
    ) {}

    public record Result(
            String type,
            Map<String, Object> params
    ) {}

    // getPath() returns only the name (e.g.) cobblestone, toString() returns the full identifier (e.g.) minecraft:cobblestone :)
    public boolean matches(
            ItemStack candle,
            List<ItemStack> sacrifices,
            int sequence,
            List<String> honorificLines
    ) {
        if (!BuiltInRegistries.ITEM.getKey(candle.getItem()).toString().equals(this.candle)) {
            return false;
        }

        if (sacrifices.size() != this.sacrifices.size()) {
            return false;
        }

        if (this.sacrifices.size() == 1 && "lotmcraft:beyonder_characteristic".equals(this.sacrifices.getFirst().item())) {
            ItemStack offered = sacrifices.getFirst();
            if (!(offered.getItem() instanceof BeyonderCharacteristicItem characteristic) || offered.getCount() != this.sacrifices.getFirst().count()) {
                return false;
            }
            if (PotionRecipeItemHandler.selectRecipeOfPathwayAndSequence(characteristic.getPathway(), characteristic.getSequence() + 1) == null) {
                return false;
            }
        } else {
            Map<String, Integer> providedSacrifices = new HashMap<>();
            for (ItemStack sacrifice : sacrifices) {
                String key = BuiltInRegistries.ITEM.getKey(sacrifice.getItem()) + "|" + sacrifice.getCount();
                providedSacrifices.merge(key, 1, Integer::sum);
            }

            Map<String, Integer> requiredSacrifices = new HashMap<>();
            for (Sacrifice requiredSacrifice : this.sacrifices) {
                String key = requiredSacrifice.item() + "|" + requiredSacrifice.count();
                requiredSacrifices.merge(key, 1, Integer::sum);
            }

            if (!providedSacrifices.equals(requiredSacrifices)) {
                return false;
            }
        }

        if (sequence > this.conditions.minSequence()) {
            return false;
        }

        // o nome honorífico vale em inglês ou em português
        List<String> typed = normalized(honorificLines);
        boolean english = typed.equals(normalized(this.honorific.lines()));
        boolean portuguese = this.honorific.translated() != null && typed.equals(normalized(this.honorific.translated()));
        if (!english && !portuguese) {
            return false;
        }
        return true;
    }
}