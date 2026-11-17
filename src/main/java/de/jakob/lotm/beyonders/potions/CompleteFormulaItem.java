package de.jakob.lotm.beyonders.potions;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toServer.OpenRecipeMenuPacket;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Fórmula Completa: a receita da poção e o pergaminho do ritual da mesma Sequência, juntados na bancada.
 * Mostra os dois lado a lado, vale como receita no Caldeirão de Preparo e como pergaminho na hora do ritual.
 */
public class CompleteFormulaItem extends Item {
    private final String pathway;
    private final int sequence;

    public CompleteFormulaItem(Properties properties, String pathway, int sequence) {
        super(properties);
        this.pathway = pathway;
        this.sequence = sequence;
    }

    public String getPathway() {
        return pathway;
    }

    public int getSequence() {
        return sequence;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
        if (level.isClientSide) {
            PacketHandler.sendToServer(new OpenRecipeMenuPacket(sequence, pathway, OpenRecipeMenuPacket.VIEW_CARD));
        }
        return InteractionResultHolder.success(player.getItemInHand(usedHand));
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        String sequenceName = BeyonderData.pathwayInfos.get(pathway).sequenceNames()[sequence];
        return Component.translatableWithFallback("lotm.complete_formula.named", "%s Complete Formula",
                Component.translatable("lotm.sequence." + sequenceName));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatableWithFallback("lotm.complete_formula.desc",
                "Potion recipe and advancement ritual of Sequence %s together.", String.valueOf(sequence)).withStyle(ChatFormatting.GRAY));
    }
}
