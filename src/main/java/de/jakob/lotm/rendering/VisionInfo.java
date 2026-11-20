package de.jakob.lotm.rendering;

import de.jakob.lotm.network.packets.toClient.VisionTargetPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.ClientBeyonderCache;
import de.jakob.lotm.util.LotmText;
import de.jakob.lotm.util.data.PathwayInfos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * O painel das visões: uma caixa estreita na direita, no meio da tela. O nome fica numa caixinha no topo,
 * as linhas abreviadas no meio (PS, Arm, San, Esp...) e, na faixa escura de baixo, o caminho, a sequência
 * e o nome da sequência. Quanto mais alta a sequência de quem olha, mais aparece; se o alvo for mais forte, "???".
 */
public final class VisionInfo {
    private VisionInfo() {}

    private static final String UNKNOWN = "???";

    /** Uma linha "Rótulo: valor", com um ícone opcional no fim */
    public record Line(String label, String value, Icon icon) {}

    public enum Icon { NONE, HEART, SANITY, ARMOR }

    /** O conteúdo de um painel */
    public record Content(List<Line> lines, List<String> footer, int footerColor) {}

    public enum Kind { SPIRIT, SPECTATING, DEATH }

    public static Content build(Kind kind, LivingEntity target, float health, int viewerSeq) {
        List<Line> lines = new ArrayList<>();
        List<String> footer = new ArrayList<>();
        int footerColor = 0xFFE8E2F0;

        lines.add(new Line(t("hp", "HP"), num(health), Icon.HEART));
        lines.add(new Line(t("armor", "Arm"), String.valueOf(target.getArmorValue()), Icon.ARMOR));

        UUID id = target.getUUID();
        boolean beyonder = ClientBeyonderCache.isBeyonder(id);
        int targetSeq = ClientBeyonderCache.getSequence(id);
        boolean stronger = beyonder && targetSeq < viewerSeq;

        if (kind == Kind.DEATH) {
            if (viewerSeq <= 7) {
                float ratio = target.getHealth() / Math.max(1f, target.getMaxHealth());
                String d = ratio > 0.6f ? t("death.far", "Far") : ratio > 0.25f ? t("death.near", "Near") : t("death.imminent", "Imminent");
                lines.add(new Line(t("death", "Death"), d, Icon.NONE));
            }
            if (viewerSeq <= 5) {
                String type = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath();
                String nature = (type.contains("ghost") || type.contains("spirit") || type.contains("wraith") || type.contains("specter"))
                        ? t("nature.spirit", "Spirit")
                        : target.getType().is(EntityTypeTags.UNDEAD) ? t("nature.undead", "Undead") : t("nature.living", "Living");
                lines.add(new Line(t("nature", "Nat"), nature, Icon.NONE));
            }
            if (viewerSeq <= 3) footerColor = footer(footer, target, beyonder, stronger, targetSeq, true, true);
            return new Content(lines, footer, footerColor);
        }

        // Visão Espiritual e Observação: sanidade (Seq 7), espiritualidade e caminho (Seq 5), sequência (Seq 3)
        if (viewerSeq <= 7 && beyonder) {
            String san = stronger ? UNKNOWN : Math.round(sanityOf(target) * 100) + "%";
            lines.add(new Line(t("sanity", "San"), san, Icon.SANITY));
        }
        if (viewerSeq <= 5 && beyonder)
            lines.add(new Line(t("spirituality", "Spi"), stronger ? UNKNOWN : String.valueOf(Math.round(ClientBeyonderCache.getSpirituality(id))), Icon.NONE));
        if (viewerSeq <= 7) footerColor = footer(footer, target, beyonder, stronger, targetSeq, viewerSeq <= 5, viewerSeq <= 3);
        return new Content(lines, footer, footerColor);
    }

    /** A faixa de baixo: "Caminho do Sol" / "Sequência 7" / "Sumo Sacerdote Solar" */
    private static int footer(List<String> footer, LivingEntity target, boolean beyonder, boolean stronger, int targetSeq, boolean showPath, boolean showSeq) {
        if (!beyonder) {
            footer.add(t("not_beyonder", "Not a Beyonder"));
            return 0xFFA8A2B0;
        }
        if (stronger) {
            footer.add(UNKNOWN);
            return 0xFFE8E2F0;
        }
        String pathway = ClientBeyonderCache.getPathway(target.getUUID());
        PathwayInfos info = BeyonderData.pathwayInfos.get(pathway);
        if (!showPath || info == null) {
            footer.add(t("beyonder", "Beyonder"));
            return 0xFFE8E2F0;
        }
        footer.add(info.getName()); // só o nome do caminho, sem a palavra "Caminho"
        if (showSeq) {
            footer.add(LotmText.t("lotm.sequence", "Sequence") + " " + targetSeq);
            String seqName = info.getSequenceName(targetSeq);
            if (!seqName.isEmpty()) footer.add(seqName);
        }
        return 0xFF000000 | brighten(info.color());
    }

    /** "Caminho do Sol", "Caminho da Porta" */
    private static final Set<String> FEMININE = Set.of("door", "demoness", "darkness", "death", "mother", "moon", "white_tower", "wheel_of_fortune");

    private static String pathwayTitle(String pathway, String name) {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        if (lang != null && lang.startsWith("pt")) return "Caminho " + (FEMININE.contains(pathway) ? "da " : "do ") + name;
        return LotmText.f("lotmcraft.vision.pathway_title", "%s Pathway", name);
    }

    private static float sanityOf(LivingEntity target) {
        if (VisionTargetPacket.clientEntity == target.getId()) return VisionTargetPacket.clientSanity;
        if (target == Minecraft.getInstance().player) return 1f;
        return 1f;
    }

    private static String t(String key, String fallback) {
        return LotmText.t("lotmcraft.vision.short." + key, fallback);
    }

    private static String num(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static int brighten(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = r + (255 - r) / 2; g = g + (255 - g) / 2; b = b + (255 - b) / 2;
        return (r << 16) | (g << 8) | b;
    }

    /** A cor do nome segue a aura: branco pacífico, vermelho agressivo, amarelo neutro */
    public static int titleColor(LivingEntity e) {
        if (e instanceof Enemy || (e instanceof Mob m && m.isAggressive())) return 0xFFFF6A5E;
        if (e instanceof NeutralMob || e instanceof Player) return 0xFFFFD75E;
        return 0xFFF4F4F4;
    }

    public static final int LINE_HEIGHT = 11;
    private static final float SCALE = 0.55f;
    private static final ResourceLocation ARMOR_SPRITE = ResourceLocation.withDefaultNamespace("hud/armor_full");
    private static final ResourceLocation HEART_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full");

    /** Desenha o painel no meio da direita, nas cores da visão (accent) */
    public static void card(GuiGraphics g, Font font, String title, int titleColor, int accent, Content c) {
        g.pose().pushPose();
        g.pose().scale(SCALE, SCALE, 1);
        int sw = (int) (g.guiWidth() / SCALE), sh = (int) (g.guiHeight() / SCALE);

        int w = Math.max(84, font.width(title) + 18);
        for (Line l : c.lines()) w = Math.max(w, font.width(l.label() + ": " + l.value()) + 12 + (l.icon() == Icon.NONE ? 0 : 11));
        for (String f : c.footer()) w = Math.max(w, font.width(f) + 16);
        int headerH = 16;
        int footerH = Math.max(14, c.footer().size() * LINE_HEIGHT + 6);
        int h = 3 + headerH + 4 + c.lines().size() * LINE_HEIGHT + 3 + footerH + 3;
        int x = sw - w - 4, y = sh / 2 - h / 2;
        int rgb = accent & 0xFFFFFF;

        // caixa
        g.fill(x, y, x + w, y + h, 0xC80D0A12);
        outline(g, x, y, w, h, 0xE0000000 | rgb);
        // caixinha do nome
        int hx = x + 3, hy = y + 3, hw = w - 6;
        g.fill(hx, hy, hx + hw, hy + headerH, 0x70000000 | rgb);
        outline(g, hx, hy, hw, headerH, 0x90000000 | rgb);
        g.drawCenteredString(font, title, x + w / 2, hy + 4, titleColor);

        // linhas
        int ly = hy + headerH + 4;
        for (Line l : c.lines()) {
            String text = l.label() + ": " + l.value();
            g.drawString(font, text, x + 6, ly, 0xFFF2EEF7, true);
            int ix = x + 6 + font.width(text) + 2;
            switch (l.icon()) {
                case HEART -> g.blitSprite(HEART_SPRITE, ix, ly - 1, 9, 9);
                case ARMOR -> g.blitSprite(ARMOR_SPRITE, ix, ly - 1, 9, 9);
                case SANITY -> g.drawString(font, "\u2764", ix + 1, ly, 0xFFB57BEE, true); // coração roxo
                default -> {}
            }
            ly += LINE_HEIGHT;
        }

        // faixa escura de baixo: caminho, sequência e nome da sequência
        int fy = y + h - 3 - footerH, fx = x + 3, fw = w - 6;
        g.fill(fx, fy, fx + fw, fy + footerH, 0xD0050408);
        outline(g, fx, fy, fw, footerH, 0x60000000 | rgb);
        int ty = fy + 4;
        for (String f : c.footer()) {
            g.drawCenteredString(font, f, x + w / 2, ty, c.footerColor());
            ty += LINE_HEIGHT;
        }
        g.pose().popPose();
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
