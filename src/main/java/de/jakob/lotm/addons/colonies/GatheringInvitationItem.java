package de.jakob.lotm.addons.colonies;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * O convite para a reunião de Beyonders de um reino de NPC: comprado de um Beyonder do reino, diz o dia, a hora e o
 * lugar da próxima reunião. Na reunião, os Beyonders só vendem a quem traz o convite.
 */
public class GatheringInvitationItem extends Item {
    public GatheringInvitationItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(int colony, String kingdom, long day, int x, int y, int z) {
        ItemStack stack = new ItemStack(de.jakob.lotm.item.ModItems.GATHERING_INVITATION.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt("Colony", colony);
        tag.putString("Kingdom", kingdom);
        tag.putLong("Day", day);
        tag.putInt("X", x);
        tag.putInt("Y", y);
        tag.putInt("Z", z);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static CompoundTag data(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    /** O convite vale para a reunião deste reino neste dia */
    public static boolean isFor(ItemStack stack, int colony, long day) {
        if (!(stack.getItem() instanceof GatheringInvitationItem)) return false;
        CompoundTag tag = data(stack);
        return tag.getInt("Colony") == colony && tag.getLong("Day") == day;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = data(stack);
        if (!tag.contains("Day")) return;
        tooltip.add(Component.translatable("lotmcraft.gathering.invitation.kingdom", tag.getString("Kingdom")).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("lotmcraft.gathering.invitation.when", tag.getLong("Day") + 1).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("lotmcraft.gathering.invitation.where", tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("lotmcraft.gathering.invitation.secret").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
