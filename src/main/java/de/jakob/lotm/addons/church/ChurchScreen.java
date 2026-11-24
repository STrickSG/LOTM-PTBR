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
    private int left, top, tab, scroll;
    private ChurchMembersPacket members;
    private static final int ROWS = 8;
    private EditBox nameBox;
    /** 0 = Igreja, 1 = Seita Sombria, 2 = Seita Tranquila */
    private int kindChoice;

    public ChurchScreen(ChurchBuilding.View view) {
        super(Component.translatable("lotmcraft.church.title"));
        this.view = view;
        // o esconderijo só serve à Seita e à Ordem; a igreja, só à Igreja
        if (ChurchModels.isHideout(view.getStructurePath())) kindChoice = 1;
    }

    private static ResourceLocation panel(String pathway) {
        // por enquanto só a moldura do Sol; as outras Igrejas ganham a sua conforme forem feitas
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/church/church_sun.png");
    }

    /** O que o menu mostrava da última vez: se os dados do templo chegarem depois de abrir, os botões se refazem */
    private String shown = "";

    private String state() {
        return view.god + "|" + view.churchName + "|" + view.getBuildingLevel() + "|" + (members == null ? "" : members.members().size() + ":" + members.flags() + ":" + members.members().hashCode());
    }

    public void setMembers(ChurchMembersPacket packet) {
        if (packet.temple().equals(view.getPosition())) members = packet;
    }

    private void requestMembers() {
        PacketDistributor.sendToServer(new ChurchActionPacket(view.getPosition(), "members", "", ""));
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
        int tw = (W - 44) / 3;
        addRenderableWidget(tabButton(0, "lotmcraft.church.tab.church", left + 22, tw));
        addRenderableWidget(tabButton(1, "lotmcraft.church.tab.members", left + 22 + tw, tw));
        addRenderableWidget(tabButton(2, "lotmcraft.church.tab.anchors", left + 22 + tw * 2, tw));
        if (tab != 0 && members == null && consecrated()) requestMembers();
        if (tab == 1 && members != null) {
            if (members.canEdit() && !Churches.SECT.equals(view.kind)) {   // a Seita e a Ordem não pregam
                Button preach = Button.builder(Component.translatable(members.preachedToday() ? "lotmcraft.church.preach.done_today" : "lotmcraft.church.preach"),
                        b -> PacketDistributor.sendToServer(new ChurchActionPacket(view.getPosition(), "preach", "", ""))).bounds(left + 30, top + 64, W - 60, 16).build();
                preach.active = !members.preachedToday();
                addRenderableWidget(preach);
            }
            var list = members.members();
            scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - ROWS)));
            for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
                var m = list.get(scroll + i);
                int y = top + 86 + i * 14;
                if (members.canEdit()) {
                    Button home = Button.builder(Component.literal(m.home() ? "⌂" : "+"), b -> PacketDistributor.sendToServer(new ChurchActionPacket(view.getPosition(), "home", m.id().toString(), "")))
                            .bounds(left + W - 63, y - 2, 11, 12)
                            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(m.home() ? "lotmcraft.church.home.tooltip_leave" : "lotmcraft.church.home.tooltip_move")))
                            .build();
                    addRenderableWidget(home);
                    addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(m, -1)).bounds(left + W - 50, y - 2, 11, 12).build());
                    addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(m, 1)).bounds(left + W - 37, y - 2, 11, 12).build());
                }
            }
        }

        if (tab == 0) {
            // na Seita e na Ordem o Deus vê o aviso de como elas se reúnem: o nome e o botão descem para dar lugar
            int shift = sectHint() ? 18 : 0;
            if (!consecrated() || isGod()) {
                nameBox = new EditBox(font, left + 30, top + 150 + shift, W - 60, 16, Component.translatable("lotmcraft.church.name"));
                nameBox.setMaxLength(Churches.MAX_NAME);
                nameBox.setHint(Component.translatable("lotmcraft.church.name_hint").withStyle(ChatFormatting.GRAY));
                if (isGod()) nameBox.setValue(view.churchName);
                addRenderableWidget(nameBox);
            }
            if (!consecrated()) {
                addRenderableWidget(Button.builder(kindLabel(), b -> {
                            kindChoice = ChurchModels.isHideout(view.getStructurePath()) ? (kindChoice == 1 ? 2 : 1) : 0;
                            b.setMessage(kindLabel());
                        })
                        .bounds(left + 30, top + 170, W - 60, 16).build());
                addRenderableWidget(Button.builder(Component.translatable("lotmcraft.church.consecrate"), b -> send("consecrate"))
                        .bounds(left + 30, top + 189, W - 60, 18).build());
            } else if (isGod()) {
                addRenderableWidget(Button.builder(Component.translatable("lotmcraft.church.rename"), b -> send("rename"))
                        .bounds(left + 30, top + 170 + shift, W - 60, 18).build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("lotmcraft.church.building"), b -> view.openColonyWindow())
                .bounds(left + 30, top + H - 26, W - 60, 16).build());
    }

    private boolean sectHint() {
        return isGod() && Churches.SECT.equals(view.kind);
    }

    private Component kindLabel() {
        return Component.translatable(switch (kindChoice) {
            case 1 -> "lotmcraft.church.kind.sect_dark";
            case 2 -> "lotmcraft.church.kind.sect_calm";
            default -> "lotmcraft.church.kind.church";
        });
    }

    private void cycle(ChurchMembersPacket.Member member, int dir) {
        var roles = ChurchRoles.of(view.kind);
        int i = Math.max(0, roles.indexOf(member.role()));
        String next = roles.get(Math.floorMod(i + dir, roles.size()));
        PacketDistributor.sendToServer(new ChurchActionPacket(view.getPosition(), "role", member.id().toString(), next));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == 1 && members != null) {
            scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private Button tabButton(int index, String key, int x, int w) {
        Button button = Button.builder(Component.translatable(key), b -> { tab = index; scroll = 0; init(); }).bounds(x, top + 40, w, 16).build();
        button.active = tab != index;
        return button;
    }

    private void send(String action) {
        String name = nameBox == null ? "" : nameBox.getValue();
        String kind = switch (kindChoice) {
            case 1 -> Churches.SECT + ":" + Churches.DARK;
            case 2 -> Churches.SECT + ":" + Churches.CALM;
            default -> Churches.CHURCH;
        };
        PacketDistributor.sendToServer(new ChurchActionPacket(view.getPosition(), action, name, kind));
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
                String kindKey = !Churches.SECT.equals(view.kind) ? "lotmcraft.church.kind.church"
                        : Churches.DARK.equals(view.nature) ? "lotmcraft.church.kind.sect_dark" : "lotmcraft.church.kind.sect_calm";
                line(g, Component.translatable("lotmcraft.church.info.god", view.godName), y);
                line(g, Component.translatable("lotmcraft.church.info.kind", Component.translatable(kindKey)), y + 12);
                line(g, Component.translatable("lotmcraft.church.info.pathway", pathwayName(view.pathway)), y + 24);
                line(g, Component.translatable("lotmcraft.church.info.temples", view.temples), y + 36);
                line(g, Component.translatable("lotmcraft.church.info.level", view.getBuildingLevel()), y + 48);
                if (sectHint()) {
                    g.drawCenteredString(font, Component.translatable("lotmcraft.church.rename_hint"), left + W / 2, top + 124, DIM);
                    // o aviso em letra menor, entre as informações e o nome
                    float hs = 0.8f;
                    int hy = top + 136;
                    for (var l : font.split(Component.translatable(Churches.DARK.equals(view.nature) ? "lotmcraft.sect.hint.dark" : "lotmcraft.sect.hint.order"), (int) ((W - 50) / hs))) {
                        g.pose().pushPose();
                        g.pose().translate(left + 25, hy, 0);
                        g.pose().scale(hs, hs, 1f);
                        g.drawString(font, l, 0, 0, 0x7A1A3A, false);
                        g.pose().popPose();
                        hy += 9;
                    }
                } else if (isGod()) g.drawCenteredString(font, Component.translatable("lotmcraft.church.rename_hint"), left + W / 2, top + 138, DIM);
            } else {
                for (var l : font.split(Component.translatable("lotmcraft.church.consecrate_hint", Churches.MAX_SEQUENCE), W - 50)) {
                    g.drawString(font, l, left + 25, y, TEXT, false);
                    y += 11;
                }
            }
        } else if (!consecrated()) {
            for (var l : font.split(Component.translatable("lotmcraft.church.no_god_yet"), W - 50)) {
                g.drawString(font, l, left + 25, y, TEXT, false);
                y += 11;
            }
        } else if (members == null) {
            g.drawCenteredString(font, Component.translatable("lotmcraft.church.loading"), left + W / 2, y, DIM);
        } else if (tab == 1) {
            var list = members.members();
            if (list.isEmpty()) {
                int ny = top + 90;
                for (var l : font.split(Component.translatable("lotmcraft.church.members.none"), W - 50)) {
                    g.drawString(font, l, left + 25, ny, TEXT, false);
                    ny += 11;
                }
            }
            for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
                var m = list.get(scroll + i);
                int ry = top + 86 + i * 14;
                Component role = Component.translatable("lotmcraft.church.role." + (Churches.SECT.equals(view.kind) && "believer".equals(m.role()) ? "member" : m.role()));
                int roleW = (int) (font.width(role) * 0.8f);
                Component name = Component.literal(m.name());
                if (!m.beyonder().isEmpty()) {
                    String[] b = m.beyonder().split(":");
                    name = name.copy().append(Component.literal(" · ").withColor(DIM)).append(Component.translatable("lotmcraft.church.member_seq", b[1]).withColor(0x8A5A00));
                }
                int edit = members.canEdit() ? 13 : 0;
                if (m.home()) name = Component.literal("⌂ ").withColor(GOLD).append(name);
                small(g, name, left + 24, ry, 0.85f, TEXT, W - 82 - roleW - edit);
                small(g, role, left + W - 54 - roleW - edit, ry + 1, 0.8f, "believer".equals(m.role()) ? DIM : GOLD, roleW + 2);
            }
            if (members.beds() > 0) {
                // as camas à esquerda; a página (quando há mais fiéis que linhas) vai para a direita
                long living = list.stream().filter(ChurchMembersPacket.Member::home).count();
                small(g, Component.translatable("lotmcraft.church.home.beds", living, members.beds()), left + 24, top + 86 + ROWS * 14 + 1, 0.8f, DIM, W - 100);
                if (list.size() > ROWS) {
                    String page = (scroll + 1) + "-" + Math.min(list.size(), scroll + ROWS) + " / " + list.size();
                    g.drawString(font, page, left + W - 24 - font.width(page), top + 86 + ROWS * 14, DIM, false);
                }
            } else if (list.size() > ROWS) g.drawCenteredString(font, Component.literal((scroll + 1) + "-" + Math.min(list.size(), scroll + ROWS) + " / " + list.size()), left + W / 2, top + 86 + ROWS * 14, DIM);
        } else {
            line(g, Component.translatable("lotmcraft.church.anchors.total", members.total()), y);
            line(g, Component.translatable("lotmcraft.church.anchors.floor", String.format("%.1f", members.floorPercent() / 10f)), y + 12);
            int yy = y + 30;
            g.drawString(font, Component.translatable("lotmcraft.church.anchors.by_colony"), left + 25, yy, GOLD, false);
            yy += 12;
            for (String c : members.colonies()) {
                if (yy > top + H - 44) break;
                small(g, Component.literal("• " + c), left + 30, yy, 0.9f, TEXT, W - 60);
                yy += 11;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Texto pequeno, encolhido para caber na largura */
    private void small(GuiGraphics g, Component text, int x, int y, float scale, int color, int maxW) {
        float sc = Math.min(scale, maxW / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
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
