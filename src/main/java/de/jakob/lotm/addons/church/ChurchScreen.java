package de.jakob.lotm.addons.church;

import com.mojang.blaze3d.systems.RenderSystem;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * O menu do templo, no estilo dos menus do mod: a moldura da Igreja do Caminho, abas "Igreja" e "Âncoras",
 * consagrar/fundar (com o nome escolhido pelo jogador) e o botão para a janela de construção do MineColonies.
 */
public class ChurchScreen extends Screen {
    private static final int W = 220, H = 236;
    private static final int TITLE = 0x4A2E05, TEXT = 0x3B2A12, DIM = 0x7A6440, GOLD = 0xA87A12, BAD = 0x9A2A1A;

    private final ChurchBuilding.View view;
    private int left, top, tab;
    private EditBox nameBox;
    private boolean sect;

    public ChurchScreen(ChurchBuilding.View view) {
        super(Component.translatable("lotmcraft.church.title"));
        this.view = view;
    }

    private static ResourceLocation panel(String pathway) {
        // por enquanto só a moldura do Sol; as outras Igrejas ganham a sua conforme forem feitas
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/church/church_sun.png");
    }

    /** O que o menu mostrava da última vez: se os dados do templo chegarem depois de abrir, os botões se refazem */
    private String shown = "";

    private String state() {
        return view.god + "|" + view.churchName + "|" + view.getBuildingLevel();
    }

    @Override
    public void tick() {
        super.tick();
        if (!state().equals(shown)) init();
    }

    private boolean consecrated() {
        return view.god != null;
    }

    private boolean isGod() {
        return consecrated() && Minecraft.getInstance().player != null && view.god.equals(Minecraft.getInstance().player.getUUID());
    }

    @Override
    protected void init() {
        shown = state();
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        addRenderableWidget(tabButton(0, "lotmcraft.church.tab.church", left + 22));
        addRenderableWidget(tabButton(1, "lotmcraft.church.tab.anchors", left + W / 2 + 2));

        if (tab == 0) {
            if (!consecrated() || isGod()) {
                nameBox = new EditBox(font, left + 30, top + 150, W - 60, 16, Component.translatable("lotmcraft.church.name"));
                nameBox.setMaxLength(Churches.MAX_NAME);
                nameBox.setHint(Component.translatable("lotmcraft.church.name_hint").withStyle(ChatFormatting.GRAY));
                if (isGod()) nameBox.setValue(view.churchName);
                addRenderableWidget(nameBox);
            }
            if (!consecrated()) {
                addRenderableWidget(Button.builder(kindLabel(), b -> { sect = !sect; b.setMessage(kindLabel()); })
                        .bounds(left + 30, top + 170, W - 60, 16).build());
                addRenderableWidget(Button.builder(Component.translatable("lotmcraft.church.consecrate"), b -> send("consecrate"))
                        .bounds(left + 30, top + 189, W - 60, 18).build());
            } else if (isGod()) {
                addRenderableWidget(Button.builder(Component.translatable("lotmcraft.church.rename"), b -> send("rename"))
                        .bounds(left + 30, top + 170, W - 60, 18).build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("lotmcraft.church.building"), b -> view.openColonyWindow())
                .bounds(left + 30, top + H - 26, W - 60, 16).build());
    }

    private Component kindLabel() {
        return Component.translatable(sect ? "lotmcraft.church.kind.sect" : "lotmcraft.church.kind.church");
    }

    private Button tabButton(int index, String key, int x) {
        Button button = Button.builder(Component.translatable(key), b -> { tab = index; init(); }).bounds(x, top + 40, W / 2 - 24, 16).build();
        button.active = tab != index;
        return button;
    }

    private void send(String action) {
        String name = nameBox == null ? "" : nameBox.getValue();
        PacketDistributor.sendToServer(new ChurchActionPacket(view.getPosition(), action, name, sect ? Churches.SECT : Churches.CHURCH));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x80100A04, 0xA0201406);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend();
        g.blit(panel(view.pathway), left, top, 0, 0, W, H, W, H);

        // o nome da Igreja (ou o aviso de templo sem Deus)
        Component title = consecrated() && !view.churchName.isEmpty()
                ? Component.literal(view.churchName).withStyle(ChatFormatting.BOLD)
                : Component.translatable("lotmcraft.church.unconsecrated").withStyle(ChatFormatting.BOLD);
        float s = Math.min(1f, (W - 50f) / font.width(title));
        g.pose().pushPose();
        g.pose().translate(left + W / 2f, top + 24, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, TITLE, false);
        g.pose().popPose();

        int y = top + 64;
        if (tab == 0) {
            if (consecrated()) {
                String kindKey = Churches.SECT.equals(view.kind) ? "lotmcraft.church.kind.sect" : "lotmcraft.church.kind.church";
                line(g, Component.translatable("lotmcraft.church.info.god", view.godName), y);
                line(g, Component.translatable("lotmcraft.church.info.kind", Component.translatable(kindKey)), y + 12);
                line(g, Component.translatable("lotmcraft.church.info.pathway", pathwayName(view.pathway)), y + 24);
                line(g, Component.translatable("lotmcraft.church.info.temples", view.temples), y + 36);
                line(g, Component.translatable("lotmcraft.church.info.level", view.getBuildingLevel()), y + 48);
                if (isGod()) g.drawCenteredString(font, Component.translatable("lotmcraft.church.rename_hint"), left + W / 2, top + 138, DIM);
            } else {
                for (var l : font.split(Component.translatable("lotmcraft.church.consecrate_hint", Churches.MAX_SEQUENCE), W - 50)) {
                    g.drawString(font, l, left + 25, y, TEXT, false);
                    y += 11;
                }
            }
        } else {
            for (var l : font.split(Component.translatable("lotmcraft.church.anchors_soon"), W - 50)) {
                g.drawString(font, l, left + 25, y, TEXT, false);
                y += 11;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void line(GuiGraphics g, Component text, int y) {
        g.drawString(font, text, left + 25, y, TEXT, false);
    }

    private static Component pathwayName(String pathway) {
        if (pathway == null || pathway.isEmpty()) return Component.literal("-");
        var info = BeyonderData.pathwayInfos.get(pathway);
        return info == null ? Component.literal(pathway) : Component.translatable("lotm.pathway." + pathway).withColor(0x8A5A00);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
