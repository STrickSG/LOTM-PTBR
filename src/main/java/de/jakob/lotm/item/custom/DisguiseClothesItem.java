package de.jakob.lotm.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Roupas de alguém cuja vida foi tomada (Erro, Cavalo de Troia do Destino). Usadas uma vez, você as veste e
 * fica com a aparência dessa pessoa; usadas de novo, você as tira. Nenhum poder Beyonder, só um disfarce.
 * Guardam o nome, de quem é o disfarce e como desenhar a pessoa (tipo de criatura e a aparência dela).
 */
public class DisguiseClothesItem extends Item {
    public static final String NAME = "DisguiseName", OWNER = "DisguiseOwner", LOOK = "DisguiseLook";

    public DisguiseClothesItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) de.jakob.lotm.addons.rituals.ErrorDisguise.toggle(serverPlayer, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static String disguiseName(ItemStack stack) {
        return data(stack).getString(NAME);
    }

    @Nullable
    public static UUID owner(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
    }

    /** Aparência: "type" (id da criatura) e o que ela precisa para ser desenhada igual */
    public static CompoundTag look(ItemStack stack) {
        return data(stack).getCompound(LOOK);
    }

    public static ItemStack create(Item item, String name, UUID owner, CompoundTag look) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString(NAME, name);
        tag.putUUID(OWNER, owner);
        tag.put(LOOK, look);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        String name = disguiseName(stack);
        return name.isEmpty() ? super.getName(stack) : Component.translatable("item.lotmcraft.disguise_clothes.of", name);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lotmcraft.disguise_clothes.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
