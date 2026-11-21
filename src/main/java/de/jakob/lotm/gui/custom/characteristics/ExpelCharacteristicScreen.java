package de.jakob.lotm.gui.custom.characteristics;

import com.mojang.blaze3d.systems.RenderSystem;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItemHandler;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.error.ErrorActionPacket;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Expelir Característica (Autoridade Angelical): lista as características acumuladas no corpo,
 * com o ícone, o nome da Sequência, quantas cópias extras há e quanto cada uma pesa na corrupção.
 * Só as extras podem sair; a característica da Sequência atual fica.
 */
public class ExpelCharacteristicScreen extends Screen {
    private static final ResourceLocation DRAWER = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "introspect/drawer");
    private static final int TEXT_HEADING = 0xFFF4D58A, TEXT_BODY = 0xFFD8D0BC, TEXT_DIM = 0xFF8C8672;
    private static final int W = 230, ROW = 24;

    private record Row(int sequence, int extras) {}

    private final String pathway;
    private final List<Row> rows = new ArrayList<>();
    private int left, top, height;

    public ExpelCharacteristicScreen(CompoundTag data) {
        super(Component.translatable("gui.lotmcraft.expel_characteristic.title"));
        this.pathway = data.getString("pathway");
        ListTag list = data.getList("options", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.getBoolean("regress")) rows.add(new Row(tag.getInt("seq"), tag.getInt("stacks")));
        }
    }

    @Override
    protected void init() {
        height = 34 + Math.max(1, rows.size()) * ROW + 28;
        left = (this.width - W) / 2;
        top = (this.height - height) / 2;
        int y = top + 26;
        for (Row r : rows) {
            final int seq = r.sequence();
            addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.expel_characteristic.button"), b -> expel(seq))
                    .bounds(left + W - 62, y + 2, 54, 18).build());
            y += ROW;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.expel_characteristic.close"), b -> onClose())
                .bounds(left + W / 2 - 40, top + height - 24, 80, 18).build());
    }

    private void expel(int sequence) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("seq", sequence);
        PacketHandler.sendToServer(new ErrorActionPacket("self_char", tag));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        RenderSystem.enableBlend();
        g.blitSprite(DRAWER, left, top, W, height);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), left + 8, top + 8, TEXT_HEADING, true);
        g.fill(left + 4, top + 20, left + W - 4, top + 21, 0x66D9A94F);

        int y = top + 26;
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("gui.lotmcraft.expel_characteristic.none"), left + 10, y + 7, TEXT_DIM, true);
        }
        for (Row r : rows) {
            var item = BeyonderCharacteristicItemHandler.selectCharacteristicOfPathwayAndSequence(pathway, r.sequence());
            if (item != null) g.renderItem(new ItemStack(item), left + 8, y + 3);
            String name = BeyonderData.getSequenceName(pathway, r.sequence());
            Component line = Component.literal(r.sequence() + " · " + name);
            int maxW = W - 30 - 70;
            float sc = Math.min(1f, maxW / (float) Math.max(1, font.width(line)));
            g.pose().pushPose();
            g.pose().translate(left + 28, y + 3, 0);
            g.pose().scale(sc, sc, 1f);
            g.drawString(font, line, 0, 0, TEXT_BODY, true);
            g.pose().popPose();
            int pct = Math.round(r.extras() * (0.04f + 0.02f * (9 - r.sequence())) * 100);
            Component info = Component.translatable("gui.lotmcraft.expel_characteristic.info", r.extras(), pct);
            g.pose().pushPose();
            g.pose().translate(left + 28, y + 13, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, info, 0, 0, 0xFFD06A8A, true);
            g.pose().popPose();
            y += ROW;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
