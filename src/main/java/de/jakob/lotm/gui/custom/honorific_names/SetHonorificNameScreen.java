package de.jakob.lotm.gui.custom.honorific_names;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toServer.SetHonorificNamePacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.playerMap.HonorificName;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedList;
import java.util.List;
import de.jakob.lotm.util.LotmText;

public class SetHonorificNameScreen extends Screen {

    private static final int COL_TITLE   = 0xFFDDDDDD;
    private static final int COL_LABEL   = 0xFFAAAAAA;
    private static final int COL_ERROR   = 0xFFFF6666;
    private static final int COL_HINT    = 0xFF888888;
    private static final int COL_DIVIDER = 0xFF555555;
    private static final int COL_PANEL   = 0xC0151515;

    private final String pathway;
    private final int sequence;
    private final int lineCount;

    private final List<EditBox> lineBoxes = new LinkedList<>();
    private String errorMessage = "";

    public SetHonorificNameScreen(String pathway, int sequence) {
        super(Component.translatableWithFallback("lotmcraft.text.set_honorific_name", "Set Honorific Name"));
        this.pathway = pathway;
        this.sequence = sequence;
        this.lineCount = requiredLineCount(sequence);
    }

    private static int requiredLineCount(int sequence) {
        if (sequence == 3) return 5;
        if (sequence == 2) return 4;
        return 3;
    }

    @Override
    protected void init() {
        super.init();
        lineBoxes.clear();

        int centerX = this.width / 2;
        int startY = this.height / 2 - (lineCount * 30) / 2 + 20;

        for (int i = 0; i < lineCount; i++) {
            EditBox box = new EditBox(this.font,
                    centerX - 130, startY + i * 30,
                    260, 20,
                    Component.translatableWithFallback("lotmcraft.text.line_2", "Line %s", String.valueOf((i + 1))));
            box.setMaxLength(HonorificName.MAX_LENGTH - 1);
            box.setHint(Component.translatableWithFallback("lotmcraft.text.line_2", "Line %s", String.valueOf((i + 1))));
            this.addRenderableWidget(box);
            lineBoxes.add(box);
        }

        int confirmY = startY + lineCount * 30 + 8;
        this.addRenderableWidget(
                Button.builder(Component.translatableWithFallback("lotmcraft.text.confirm", "Confirm"), btn -> onConfirm())
                        .bounds(centerX - 58, confirmY, 112, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.translatableWithFallback("lotmcraft.text.cancel", "Cancel"), btn -> onClose())
                        .bounds(centerX - 58, confirmY + 26, 112, 20)
                        .build()
        );
    }

    private void onConfirm() {
        LinkedList<String> lines = new LinkedList<>();
        for (EditBox box : lineBoxes) {
            String value = box.getValue().trim();
            if (value.isEmpty()) {
                errorMessage = LotmText.t("lotmcraft.text.all_lines_must_be_filled_in", "All lines must be filled in.");
                return;
            }
            lines.add(value);
        }

        if (sequence >= 4) {
            errorMessage = LotmText.t("lotmcraft.text.you_must_be_sequence_3_or", "You must be sequence 3 or higher to utilize honorific name.");
            return;
        }

        if (lines.stream().distinct().count() != lines.size()) {
            errorMessage = LotmText.t("lotmcraft.text.each_line_must_be_different", "Each line must be different.");
            return;
        }

        if (!HonorificName.validate(pathway, lines)) {
            errorMessage = LotmText.t("lotmcraft.text.each_line_must_contain_at_least", "Each line must contain at least one required word (see hints above).");
            return;
        }

        PacketHandler.sendToServer(new SetHonorificNamePacket(lines));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int startY  = this.height / 2 - (lineCount * 30) / 2 + 20;

        int panelTop    = startY - 66;
        int panelBottom = startY + lineCount * 30 + 60 + (errorMessage.isEmpty() ? 0 : 14);
        int panelLeft   = centerX - 148;
        int panelRight  = centerX + 148;

        gfx.fill(panelLeft, panelTop, panelRight, panelBottom, COL_PANEL);
        gfx.hLine(panelLeft,      panelRight - 1, panelTop,        COL_DIVIDER);
        gfx.hLine(panelLeft,      panelRight - 1, panelBottom - 1, COL_DIVIDER);
        gfx.vLine(panelLeft,      panelTop,        panelBottom,     COL_DIVIDER);
        gfx.vLine(panelRight - 1, panelTop,        panelBottom,     COL_DIVIDER);

        gfx.drawCenteredString(font, LotmText.t("lotmcraft.text.set_honorific_name", "Set Honorific Name"), centerX, panelTop + 8, COL_TITLE);

        int divY = panelTop + 20;
        gfx.hLine(centerX - 80, centerX + 80, divY, COL_DIVIDER);

        String pathInfo = BeyonderData.pathwayInfos.get(pathway).getSequenceName(sequence);
        gfx.drawCenteredString(font, pathInfo, centerX, divY + 5, COL_LABEL);

        List<String> requiredWords = HonorificName.getMustHaveWordsFor(pathway,
                net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected());
        if (!requiredWords.isEmpty()) {
            gfx.drawCenteredString(font, LotmText.t("lotmcraft.text.required_words_at_least_one_per", "Required words (at least one per line):"), centerX, divY + 17, COL_HINT);
            // a lista inteira, quebrada em linhas menores (antes era cortada com "...")
            String wordsStr = String.join(", ", requiredWords);
            float scale = font.width(wordsStr) > 280 ? 0.8f : 1f;
            gfx.pose().pushPose();
            gfx.pose().translate(centerX, divY + 27, 0);
            gfx.pose().scale(scale, scale, 1f);
            var lines = font.split(net.minecraft.network.chat.Component.literal(wordsStr), (int) (280 / scale));
            for (int i = 0; i < lines.size() && i < 2; i++) {
                gfx.drawCenteredString(font, lines.get(i), 0, i * 10, COL_HINT);
            }
            gfx.pose().popPose();
        }

        if (!errorMessage.isEmpty()) {
            gfx.drawCenteredString(font, errorMessage, centerX, startY + lineCount * 30 + 56, COL_ERROR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}