package de.jakob.lotm.gui.custom.characteristics;

import com.mojang.blaze3d.systems.RenderSystem;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItemHandler;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.error.ErrorActionPacket;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Expelir Característica (Autoridade Angelical), no visual de latão da Introspecção:
 * moldura, emblema do caminho, placa com o título, cada característica num encaixe com o tubo
 * da corrupção que ela pesa e botões de latão. Só as extras podem sair.
 */
public class ExpelCharacteristicScreen extends Screen {
    private static ResourceLocation spr(String n) { return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "introspect/" + n); }
    private static final ResourceLocation FRAME = spr("frame"), INSET = spr("panel_inset"), NAMEPLATE = spr("nameplate"),
            DIVIDER = spr("divider"), SLOT = spr("slot_idle"), COUNTER = spr("counter_plate"),
            BTN_IDLE = spr("mini_btn_idle"), BTN_HOVER = spr("mini_btn_hover"), BTN_PRESSED = spr("mini_btn_pressed"), COG = spr("cog_small");

    private static final int TEXT_HEADING = 0xFFF4D58A, TEXT_NORMAL = 0xFFE8CF94, TEXT_BODY = 0xFFD8D0BC, TEXT_DIM = 0xFF8C8672;
    private static final int W = 250, ROW = 30, HEADER = 50;

    /** path vazio = do próprio caminho */
    private record Row(int sequence, int extras, String path, String shownPath) {}

    private final String pathway;
    private final List<Row> rows = new ArrayList<>();
    private int left, top, h;
    private float ticks;

    public ExpelCharacteristicScreen(CompoundTag data) {
        super(Component.translatable("gui.lotmcraft.expel_characteristic.title"));
        this.pathway = data.getString("pathway");
        ListTag list = data.getList("options", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.getBoolean("regress")) rows.add(new Row(tag.getInt("seq"), tag.getInt("stacks"), "", tag.getString("path")));
        }
        // as de Sequência 1 dos caminhos vizinhos
        ListTag neighbors = data.getList("neighbors", Tag.TAG_COMPOUND);
        for (int i = 0; i < neighbors.size(); i++) {
            CompoundTag tag = neighbors.getCompound(i);
            rows.add(new Row(1, tag.getInt("stacks"), tag.getString("path"), tag.getString("path")));
        }
    }

    @Override
    protected void init() {
        h = HEADER + Math.max(1, rows.size()) * ROW + 40;
        left = (this.width - W) / 2;
        top = (this.height - h) / 2;
        int y = top + HEADER;
        for (Row r : rows) {
            final Row row = r;
            addRenderableWidget(new BrassButton(left + W - 80, y + 5, 66, 20,
                    Component.translatable("gui.lotmcraft.expel_characteristic.button"), () -> expel(row)));
            y += ROW;
        }
        addRenderableWidget(new BrassButton(left + W / 2 - 38, top + h - 30, 76, 20,
                Component.translatable("gui.lotmcraft.expel_characteristic.close"), this::onClose));
    }

    private void expel(Row row) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("seq", row.sequence());
        if (!row.path().isEmpty()) tag.putString("path", row.path());
        PacketHandler.sendToServer(new ErrorActionPacket("self_char", tag));
        onClose();
    }

    @Override
    public void tick() { ticks++; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.blitSprite(FRAME, left, top, W, h);

        // engrenagens girando nos cantos de cima, como na Introspecção
        float t = ticks + partial;
        for (int[] c : new int[][]{{left - 6, top - 6, 1}, {left + W - 18, top - 6, -1}}) {
            g.pose().pushPose();
            g.pose().translate(c[0] + 12, c[1] + 12, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(t * 1.5f * c[2]));
            g.blitSprite(COG, -12, -12, 24, 24);
            g.pose().popPose();
        }
        renderContent(g, partial);
    }

    /** O conteúdo fica embaixo dos botões: é desenhado junto com o fundo */
    private void renderContent(GuiGraphics g, float partial) {
        
        // a placa com o título
        int px = left + 14, pw = W - 28;
        g.blitSprite(NAMEPLATE, px, top + 15, pw, 20);
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, px + (pw - font.width(title)) / 2, top + 21, TEXT_HEADING, false);
        g.blitSprite(DIVIDER, left + 12, top + HEADER - 8, W - 24, 5);

        int y = top + HEADER;
        if (rows.isEmpty()) {
            g.blitSprite(INSET, left + 12, y, W - 24, ROW - 4);
            Component none = Component.translatable("gui.lotmcraft.expel_characteristic.none");
            g.drawString(font, none, left + (W - font.width(none)) / 2, y + 9, TEXT_DIM, false);
        }
        for (Row r : rows) {
            g.blitSprite(INSET, left + 12, y, W - 24, ROW - 2);
            // a característica num encaixe
            g.blitSprite(SLOT, left + 17, y + 5, 18, 18);
            var item = BeyonderCharacteristicItemHandler.selectCharacteristicOfPathwayAndSequence(r.shownPath().isEmpty() ? pathway : r.shownPath(), r.sequence());
            if (item != null) g.renderItem(new ItemStack(item), left + 18, y + 6);

            // nome da Sequência e quantas características extras
            String name = BeyonderData.getSequenceName(r.shownPath().isEmpty() ? pathway : r.shownPath(), r.sequence());
            Component line = Component.literal(r.sequence() + " · " + name);
            int nameX = left + 40, maxW = W - 40 - 118;
            float sc = Math.min(1f, maxW / (float) Math.max(1, font.width(line)));
            g.pose().pushPose();
            g.pose().translate(nameX, y + 10, 0);
            g.pose().scale(sc, sc, 1f);
            g.drawString(font, line, 0, 0, TEXT_BODY, true);
            g.pose().popPose();
            int cx = left + W - 112;
            g.blitSprite(COUNTER, cx, y + 7, 28, 14);
            String count = "×" + r.extras();
            g.drawString(font, count, cx + 14 - font.width(count) / 2, y + 10, TEXT_NORMAL, false);
            y += ROW;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Botão de latão (as abas da Introspecção), no lugar do botão cinza do Minecraft */
    private class BrassButton extends AbstractButton {
        private final Runnable action;

        BrassButton(int x, int y, int w, int h, Component text, Runnable action) {
            super(x, y, w, h, text);
            this.action = action;
        }

        @Override
        public void onPress() { action.run(); }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            // a placa de latão com borda (a mesma do título); clareia quando o mouse passa
            boolean hover = isHoveredOrFocused();
            if (hover) RenderSystem.setShaderColor(1.25f, 1.15f, 0.9f, 1f);
            g.blitSprite(NAMEPLATE, getX(), getY(), width, height);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            Component m = getMessage().copy().withStyle(ChatFormatting.BOLD);
            int color = isHoveredOrFocused() ? 0xFFFFF2C8 : TEXT_HEADING;
            g.drawString(font, m, getX() + (width - font.width(m)) / 2, getY() + (height - 8) / 2, color, true);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
