package de.jakob.lotm.gui.custom.recipe;

import de.jakob.lotm.util.BeyonderData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RecipeMenuProvider implements MenuProvider {
    private final List<ItemStack> ingredients;
    private final List<ItemStack> materials;
    private final RecipeMenu.Layout layout;

    private final String pathway;
    private final int sequence;



    public RecipeMenuProvider(List<ItemStack> ingredients, List<ItemStack> materials, RecipeMenu.Layout layout) {
        this.ingredients = ingredients;
        this.materials = materials;
        this.layout = layout;
        this.pathway = layout.pathway();
        this.sequence = layout.sequence();
    }

    @Override
    public @NotNull Component getDisplayName() {
        // só o pergaminho de ritual abre sem a receita: aí o título é o do ritual
        Component name = Component.translatable("lotm.sequence." + BeyonderData.pathwayInfos.get(pathway).getRawSequenceName(sequence));
        Component title = layout.recipe() ? Component.translatable("lotm.potion_recipe.named", name)
                : Component.translatableWithFallback("lotm.advancement_ritual.named", "%s Advancement Ritual", name);
        return title.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RecipeMenu(ingredients, materials, containerId, playerInventory, layout);
    }
}
