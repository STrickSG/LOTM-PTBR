package de.jakob.lotm.rendering;

import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.ClientBeyonderCache;
import de.jakob.lotm.util.LotmText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * O que cada visão revela conforme a sequência de quem olha: quanto mais alta, mais informação.
 * Se o alvo for mais forte, o que ainda não dá para ver aparece como "???".
 */
public final class VisionInfo {
    private VisionInfo() {}

    private static final String UNKNOWN = "???";

    /** Visão Espiritual: estado do corpo e se é Beyonder (Seq 7), caminho (Seq 5), sequência e espiritualidade (Seq 3) */
    public static List<String> spirit(LivingEntity target, int viewerSeq) {
        List<String> lines = new ArrayList<>();
        if (viewerSeq > 7) return lines;
        beyonderLines(lines, target, viewerSeq, 5, 3, true);
        return lines;
    }

    /** Olho da Morte: quão perto da morte (Seq 7), a natureza (Seq 5), caminho e sequência (Seq 3) */
    public static List<String> death(LivingEntity target, int viewerSeq) {
        List<String> lines = new ArrayList<>();
        if (viewerSeq > 7) return lines;
        float ratio = target.getHealth() / Math.max(1f, target.getMaxHealth());
        String death = ratio > 0.6f ? LotmText.t("lotmcraft.vision.death.far", "Far")
                : ratio > 0.25f ? LotmText.t("lotmcraft.vision.death.near", "Near")
                : LotmText.t("lotmcraft.vision.death.imminent", "Imminent");
        lines.add(LotmText.t("lotmcraft.vision.death", "Death") + ": " + death);
        if (viewerSeq <= 5) {
            String nature;
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath();
            if (type.contains("ghost") || type.contains("spirit") || type.contains("wraith") || type.contains("specter") || type.contains("phantom") && !type.equals("phantom"))
                nature = LotmText.t("lotmcraft.vision.nature.spirit", "Spirit");
            else if (target.getType().is(EntityTypeTags.UNDEAD)) nature = LotmText.t("lotmcraft.vision.nature.undead", "Undead");
            else nature = LotmText.t("lotmcraft.vision.nature.living", "Living");
            lines.add(LotmText.t("lotmcraft.vision.nature", "Nature") + ": " + nature);
        }
        if (viewerSeq <= 3) beyonderLines(lines, target, viewerSeq, 3, 3, false);
        return lines;
    }

    /** Observação do Espectador: se é Beyonder (Seq 7), caminho (Seq 5), sequência e espiritualidade (Seq 3) */
    public static List<String> spectating(LivingEntity target, int viewerSeq) {
        List<String> lines = new ArrayList<>();
        if (viewerSeq > 7) return lines;
        beyonderLines(lines, target, viewerSeq, 5, 3, true);
        return lines;
    }

    private static void beyonderLines(List<String> lines, LivingEntity target, int viewerSeq, int pathwayAt, int sequenceAt, boolean sayIfBeyonder) {
        UUID id = target.getUUID();
        boolean beyonder = ClientBeyonderCache.isBeyonder(id);
        if (sayIfBeyonder)
            lines.add(LotmText.t("lotmcraft.vision.beyonder", "Beyonder") + ": " + (beyonder ? LotmText.t("lotmcraft.vision.yes", "Yes") : LotmText.t("lotmcraft.vision.no", "No")));
        if (!beyonder) return;
        int targetSeq = ClientBeyonderCache.getSequence(id);
        boolean stronger = targetSeq < viewerSeq;
        if (viewerSeq <= pathwayAt) {
            String pathway = ClientBeyonderCache.getPathway(id);
            String name = BeyonderData.pathwayInfos.containsKey(pathway) ? BeyonderData.pathwayInfos.get(pathway).getName() : pathway;
            lines.add(LotmText.t("lotm.pathway", "Pathway") + ": " + (stronger ? UNKNOWN : name));
        }
        if (viewerSeq <= sequenceAt) {
            lines.add(LotmText.t("lotm.sequence", "Sequence") + ": " + (stronger ? UNKNOWN : String.valueOf(targetSeq)));
            if (sayIfBeyonder)
                lines.add(LotmText.t("lotm.spirituality", "Spirituality") + ": " + (stronger ? UNKNOWN : String.valueOf(Math.round(ClientBeyonderCache.getSpirituality(id)))));
        }
    }

    private static String bodyState(LivingEntity target) {
        float ratio = target.getHealth() / Math.max(1f, target.getMaxHealth());
        String s = ratio > 0.85f ? LotmText.t("lotmcraft.vision.state.healthy", "Healthy")
                : ratio > 0.5f ? LotmText.t("lotmcraft.vision.state.hurt", "Hurt")
                : ratio > 0.2f ? LotmText.t("lotmcraft.vision.state.badly_hurt", "Badly hurt")
                : LotmText.t("lotmcraft.vision.state.dying", "Dying");
        if (target.isOnFire()) s += ", " + LotmText.t("lotmcraft.vision.state.burning", "burning");
        if (target.isFullyFrozen()) s += ", " + LotmText.t("lotmcraft.vision.state.frozen", "frozen");
        return s;
    }

    public static final int LINE_HEIGHT = 11;

    /** A linha da vida, com o coração em vez de barra */
    public static String healthLine(float health) {
        String v = health == Math.floor(health) ? String.valueOf((int) health) : String.format(java.util.Locale.ROOT, "%.1f", health);
        return LotmText.t("lotmcraft.vision.health", "Health") + ": " + v + " \u00a7c\u2764";
    }

    /**
     * O painel das visões: uma caixa estreita encostada na direita, na altura do meio da tela,
     * com o nome no cabeçalho e as linhas "Rótulo: valor" embaixo, nas cores de cada visão.
     */
    public static void card(GuiGraphics g, Font font, String title, int accent, int titleColor, List<String> lines) {
        // menor, como o do Tensura: tudo desenhado em 70% do tamanho
        final float scale = 0.7f;
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        int sw = (int) (g.guiWidth() / scale), sh = (int) (g.guiHeight() / scale);
        int w = Math.max(90, font.width(title) + 16);
        for (String l : lines) w = Math.max(w, font.width(l) + 12);
        int h = 19 + lines.size() * LINE_HEIGHT + 4;
        int x = sw - w - 4;
        int y = sh / 2 - h / 2;
        int border = 0xFF000000 | (accent & 0xFFFFFF);

        g.fill(x, y, x + w, y + h, 0xC40E0B14);                                   // fundo
        g.fill(x + 1, y + 1, x + w - 1, y + 15, 0x50000000 | (accent & 0xFFFFFF)); // cabeçalho
        g.fill(x, y, x + w, y + 1, border);                                        // borda fina
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        g.fill(x + 1, y + 15, x + w - 1, y + 16, 0x90000000 | (accent & 0xFFFFFF)); // linha embaixo do nome

        g.drawCenteredString(font, title, x + w / 2, y + 4, titleColor);
        int ly = y + 20;
        for (String l : lines) {
            int cut = l.indexOf(": ");
            if (cut > 0) {
                String label = l.substring(0, cut + 2);
                g.drawString(font, label, x + 6, ly, 0xFFB9B2C4, true);
                g.drawString(font, l.substring(cut + 2), x + 6 + font.width(label), ly, 0xFFF2EEF7, true);
            } else g.drawString(font, l, x + 6, ly, 0xFFF2EEF7, true);
            ly += LINE_HEIGHT;
        }
        g.pose().popPose();
    }

    /** Onde os painéis das visões ficam: à esquerda, embaixo da barra de espiritualidade e das coordenadas da Porta (y 66) */
    public static final int PANEL_X = 6;
    public static final int PANEL_Y = 80;
    public static final float PANEL_SCALE = 0.8f;

    /** Largura do painel: a de sempre, ou mais se alguma linha não couber */
    public static int width(Font font, List<String> lines, int base) {
        int w = base;
        for (String l : lines) w = Math.max(w, font.width(l) + 24);
        return w;
    }

    /** Desenha as linhas centralizadas, a partir de y */
    public static void draw(GuiGraphics g, Font font, List<String> lines, int centerX, int y, int color) {
        for (String line : lines) {
            g.drawString(font, line, centerX - font.width(line) / 2 + 1, y + 1, 0x55000000);
            g.drawCenteredString(font, line, centerX, y, color);
            y += LINE_HEIGHT;
        }
    }
}
