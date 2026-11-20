package de.jakob.lotm.addons.astral;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.jakob.lotm.LOTMCraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;

/**
 * Mundo Astral: um preto absoluto, sem névoa. No céu, desenhos de criança feitos a giz de cera: um sol de olhos
 * furados e sorriso comprido demais, uma figura alta e magra, crianças de mãos dadas com algo vermelho atrás.
 * Não são coisas de verdade, são o "conceito" delas, pintado no fundo do mundo.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public final class AstralWorldEffects {
    private AstralWorldEffects() {}

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/environment/astral/" + name + ".png");
    }

    /** Um desenho no céu: textura, direção (graus), inclinação a partir do topo (graus), tamanho e proporção */
    private record Drawing(ResourceLocation texture, float yaw, float tilt, float size, float aspect, float sway) {}

    private static final Drawing[] SKY = {
            new Drawing(tex("sun"), 0f, 38f, 34f, 1f, 0.6f),
            new Drawing(tex("figure_tall"), 120f, 72f, 26f, 0.5f, 1.5f),
            new Drawing(tex("children"), 230f, 76f, 30f, 2f, 0.8f),
    };

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "astral_world"), new Effects());
    }

    public static final class Effects extends DimensionSpecialEffects {
        public Effects() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
            return Vec3.ZERO;
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                    double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
            return true;
        }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                                 Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
            float time = ticks + partialTick;
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            for (Drawing drawing : SKY) {
                // os desenhos "respiram" de leve, como se algo do outro lado empurrasse o papel
                float sway = Mth.sin(time * 0.01f + drawing.yaw()) * drawing.sway();
                Matrix4f m = new Matrix4f(modelViewMatrix)
                        .rotateY((float) Math.toRadians(drawing.yaw() + sway))
                        .rotateX((float) Math.toRadians(drawing.tilt() + sway * 0.5f));
                float w = drawing.size() * drawing.aspect(), h = drawing.size(), d = 100f;
                RenderSystem.setShaderTexture(0, drawing.texture());
                BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                b.addVertex(m, -w, d, -h).setUv(0f, 0f).setColor(1f, 1f, 1f, 1f);
                b.addVertex(m, w, d, -h).setUv(1f, 0f).setColor(1f, 1f, 1f, 1f);
                b.addVertex(m, w, d, h).setUv(1f, 1f).setColor(1f, 1f, 1f, 1f);
                b.addVertex(m, -w, d, h).setUv(0f, 1f).setColor(1f, 1f, 1f, 1f);
                BufferUploader.drawWithShader(b.buildOrThrow());
            }
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            return true;
        }
    }
}
