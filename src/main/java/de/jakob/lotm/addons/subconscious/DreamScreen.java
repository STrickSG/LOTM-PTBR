package de.jakob.lotm.addons.subconscious;

import com.mojang.blaze3d.systems.RenderSystem;
import de.jakob.lotm.LOTMCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * O menu de sonhos: o painel estrelado do mod (o mesmo do Ritual de Avanço), com quem dorme, o ofício, a felicidade,
 * as habilidades e os sonhos que dá para deixar nessa mente.
 */
public class DreamScreen extends Screen {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/recipes/ritual.png");
    private static final int W = 192, H = 208;
    private static final int TITLE = 0xE6D6FF, CAPTION = 0xB8A6E0, TEXT = 0xDDD3F0, DIM = 0x8C7FB0, BAD = 0xE07A7A;
    private static final String[] SKILLS = {"Athletics", "Dexterity", "Strength", "Agility", "Stamina", "Mana", "Adaptability", "Focus",
            "Creativity", "Knowledge", "Intelligence"};

    private final CompoundTag mind;
    private final UUID sleeper;
    private final List<String> actions = new ArrayList<>();
    private int left, top, hovered = -1;

    private DreamScreen(CompoundTag mind) {
        super(Component.literal(mind.getString("name")));
        this.mind = mind;
        this.sleeper = mind.getUUID("uuid");
        for (String a : mind.getString("actions").split(",")) if (!a.isBlank()) actions.add(a);
    }

    public static void open(CompoundTag mind) {
        Minecraft.getInstance().setScreen(new DreamScreen(mind));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean used() {
        return mind.getBoolean("tonight");
    }

    /** Onde fica o botão de cada sonho: duas colunas */
    private int[] button(int i) {
        int rows = (actions.size() + 1) / 2;
        int bw = 80, bh = 22, gap = 4;
        int startY = top + H - 12 - rows * (bh + gap) + gap;
        int col = i % 2, row = i / 2;
        int x = left + 14 + col * (bw + 4);
        if (actions.size() % 2 == 1 && i == actions.size() - 1) x = left + (W - bw) / 2;
        return new int[]{x, startY + row * (bh + gap), bw, bh};
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend();
        g.blit(PANEL, left, top, 0, 0, W, H, W, H);

        // quem dorme, e o ofício
        Component name = Component.literal(mind.getString("name")).withStyle(ChatFormatting.BOLD);
        float scale = Math.min(1f, 104f / font.width(name));
        g.pose().pushPose();
        g.pose().translate(left + W / 2f, top + 9 + (1 - scale) * 4, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, name, -font.width(name) / 2, 0, TITLE, true);
        g.pose().popPose();
        Component job = Component.translatable(mind.getString("job")).withStyle(ChatFormatting.ITALIC);
        g.drawString(font, job, left + (W - font.width(job)) / 2, top + 28, CAPTION, false);

        int y = top + 42;
        // felicidade (colono), com uma barrinha
        if (mind.contains("happiness")) {
            double h = mind.getDouble("happiness");
            Component line = Component.translatable("lotmcraft.sea.screen.happiness", String.format("%.1f", h));
            g.drawString(font, line, left + 16, y, TEXT, false);
            int bx = left + 16 + font.width(line) + 4, bw = W - 32 - font.width(line) - 4;
            g.fill(bx, y + 2, bx + bw, y + 6, 0x60201040);
            int fill = (int) (bw * Math.max(0, Math.min(10, h)) / 10);
            g.fill(bx, y + 2, bx + fill, y + 6, h >= 7 ? 0xFF8FD18A : h <= 3 ? 0xFFD17A7A : 0xFFC9B8F0);
            y += 12;
        }
        if (mind.contains("infoKey")) {
            g.drawString(font, Component.translatable(mind.getString("infoKey"), mind.getString("infoArg")), left + 16, y, TEXT, false);
            y += 12;
        }
        // habilidades do colono, em duas colunas
        if (mind.contains("skills")) {
            CompoundTag skills = mind.getCompound("skills");
            g.pose().pushPose();
            g.pose().translate(left + 16, y, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            int i = 0;
            for (String skill : SKILLS) {
                if (!skills.contains(skill)) continue;
                int col = i % 2, row = i / 2;
                Component c = Component.translatable("lotmcraft.sea.skill." + skill.toLowerCase()).append(": ")
                        .append(Component.literal(String.valueOf(skills.getInt(skill))).withColor(TITLE));
                g.drawString(font, c, col * 106, row * 10, DIM, false);
                i++;
            }
            g.pose().popPose();
        }

        // os sonhos
        hovered = -1;
        for (int i = 0; i < actions.size(); i++) {
            int[] b = button(i);
            boolean over = !used() && mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
            if (over) hovered = i;
            boolean nightmare = actions.get(i).equals("nightmare");
            int border = used() ? 0x60605070 : over ? (nightmare ? 0xFFE07A7A : 0xFFE6D6FF) : (nightmare ? 0xA0A04A4A : 0xA08C7FB0);
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], used() ? 0x60100A20 : over ? 0xA0302058 : 0x80180F30);
            g.renderOutline(b[0], b[1], b[2], b[3], border);
            Component label = Component.translatable("lotmcraft.sea.action." + actions.get(i));
            float ls = Math.min(1f, (b[2] - 6f) / font.width(label));
            g.pose().pushPose();
            g.pose().translate(b[0] + b[2] / 2f, b[1] + (b[3] - 8 * ls) / 2f, 0);
            g.pose().scale(ls, ls, 1f);
            g.drawString(font, label, -font.width(label) / 2, 0, used() ? DIM : nightmare ? BAD : TEXT, false);
            g.pose().popPose();
        }
        if (used()) {
            Component done = Component.translatable("lotmcraft.sea.already");
            int[] b = button(0);
            float ds = Math.min(1f, (W - 24f) / font.width(done));
            g.pose().pushPose();
            g.pose().translate(left + W / 2f, b[1] - 11, 0);
            g.pose().scale(ds, ds, 1f);
            g.drawString(font, done, -font.width(done) / 2, 0, BAD, false);
            g.pose().popPose();
        }
        // o que o sonho faz, ao passar o mouse
        if (hovered >= 0) {
            List<FormattedCharSequence> hint = font.split(Component.translatable("lotmcraft.sea.action." + actions.get(hovered) + ".hint"), 180);
            g.renderTooltip(font, hint, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x90080414, 0xB0100828);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered >= 0 && !used()) {
            PacketDistributor.sendToServer(new DreamActionPacket(sleeper, actions.get(hovered)));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f));
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
