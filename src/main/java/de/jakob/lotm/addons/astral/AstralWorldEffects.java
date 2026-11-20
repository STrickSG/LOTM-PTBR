package de.jakob.lotm.addons.astral;

import com.mojang.blaze3d.platform.GlStateManager;
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

import java.util.Random;

/**
 * Mundo Astral: o céu é o Cosmos visto por trás da Barreira Astral. Milhares de estrelas, a faixa da galáxia,
 * nebulosas, os astros ocupados pelos Deuses Exteriores (o carmesim que pulsa, o que olha de volta), o sol em
 * pinceladas pálidas e, por cima de tudo, a película da Barreira, rachada, vazando luz.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public final class AstralWorldEffects {
    private AstralWorldEffects() {}

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/environment/astral/" + name + ".png");
    }

    /** Uma imagem no céu: textura, direção (graus), inclinação a partir do topo (graus), giro, tamanho, proporção, se soma luz, pulso, giro por tick */
    private record SkyImage(ResourceLocation texture, float yaw, float tilt, float roll, float size, float aspect, boolean additive, float pulse, float spin) {}

    /** O Cosmos atrás da Barreira: a galáxia, as nebulosas, astros comuns e, enormes e colados nela, os Deuses Exteriores */
    private static final SkyImage[] COSMOS = {
            new SkyImage(tex("galaxy"), 30f, 62f, 18f, 46f, 4f, true, 0f, 0f),
            new SkyImage(tex("galaxy"), 210f, 62f, -18f, 46f, 4f, true, 0f, 0f),
            new SkyImage(tex("nebula_violet"), 75f, 40f, 0f, 48f, 1f, true, 0f, 0f),
            new SkyImage(tex("nebula_teal"), 200f, 55f, 30f, 56f, 1f, true, 0f, 0f),
            new SkyImage(tex("nebula_crimson"), 310f, 30f, 70f, 40f, 1f, true, 0f, 0f),
            new SkyImage(tex("planet_gas"), 160f, 82f, 0f, 7f, 1f, false, 0f, 0f),
            new SkyImage(tex("planet_ring"), 285f, 84f, 10f, 6f, 1f, false, 0f, 0f),
            // os Deuses Exteriores
            new SkyImage(tex("outer_depravity"), 335f, 58f, 0f, 38f, 1f, false, 0.25f, 0f),
            new SkyImage(tex("outer_desire"), 265f, 70f, 0f, 34f, 1f, false, 0.15f, 0.002f),
            new SkyImage(tex("outer_chaos"), 20f, 24f, 0f, 26f, 1f, false, 0f, 0.004f),
            new SkyImage(tex("outer_hunger"), 110f, 64f, 15f, 40f, 1f, false, 0.1f, 0f),
            new SkyImage(tex("outer_inevitability"), 190f, 30f, 0f, 30f, 1f, true, 0f, 0.01f),
            new SkyImage(tex("outer_supernova"), 70f, 80f, 0f, 30f, 1f, true, 0.3f, 0f),
            new SkyImage(tex("outer_ravings"), 230f, 78f, 0f, 24f, 1f, true, 0.35f, 0.003f),
            new SkyImage(tex("outer_decay"), 150f, 46f, 0f, 30f, 1f, false, 0f, 0.001f),
            new SkyImage(tex("outer_overseer"), 0f, 2f, 0f, 22f, 1f, false, 0.08f, 0f),
            new SkyImage(tex("outer_fate"), 300f, 82f, 0f, 22f, 1f, false, 0f, 0.006f),
    };

    /** Do lado de dentro da Barreira: o Sol Ardente Eterno */
    private static final SkyImage[] INSIDE = {
            new SkyImage(tex("sun"), 140f, 35f, 0f, 20f, 1f, true, 0.04f, 0f),
    };

    private static final int STARS = 3500;
    private static final float[][] STAR = new float[STARS][];

    static {
        Random random = new Random(1368);
        for (int i = 0; i < STARS; i++) {
            float x = (float) random.nextGaussian(), y = (float) random.nextGaussian(), z = (float) random.nextGaussian();
            float len = Mth.sqrt(x * x + y * y + z * z);
            float size = 0.08f + random.nextFloat() * random.nextFloat() * 0.45f;
            int kind = random.nextInt(10);
            float r = kind < 2 ? 0.7f : kind < 4 ? 1f : 0.95f, g = kind < 2 ? 0.8f : kind < 4 ? 0.88f : 0.95f, b = kind < 2 ? 1f : kind < 4 ? 0.7f : 1f;
            STAR[i] = new float[]{x / len, y / len, z / len, size, r, g, b, random.nextFloat() * 100f};
        }
    }

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
            return new Vec3(0.02, 0.02, 0.05);
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
        public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, net.minecraft.client.renderer.LightTexture lightTexture,
                                         double camX, double camY, double camZ) {
            return true;
        }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                                 Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
            float time = ticks + partialTick;
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();

            // estrelas, piscando devagar
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder stars = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (float[] s : STAR) {
                float twinkle = 0.65f + 0.35f * Mth.sin(time * 0.05f + s[7]);
                star(stars, modelViewMatrix, s[0] * 100, s[1] * 100, s[2] * 100, s[3], s[4], s[5], s[6], twinkle);
            }
            BufferUploader.drawWithShader(stars.buildOrThrow());

            // o Cosmos e os Deuses Exteriores, atrás da Barreira
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            for (SkyImage image : COSMOS) drawImage(modelViewMatrix, image, time);

            // a Barreira Astral: uma película quase invisível em volta de tudo, rachada, vazando luz
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            float shimmer = 0.22f + 0.06f * Mth.sin(time * 0.02f);
            for (int face = 0; face < 6; face++) {
                Matrix4f m = new Matrix4f(modelViewMatrix);
                switch (face) {
                    case 1 -> m.rotateX(Mth.PI);
                    case 2 -> m.rotateX(Mth.HALF_PI);
                    case 3 -> m.rotateX(-Mth.HALF_PI);
                    case 4 -> m.rotateZ(Mth.HALF_PI);
                    case 5 -> m.rotateZ(-Mth.HALF_PI);
                    default -> { }
                }
                quad(m, tex("barrier"), 90f, 90f, 90f, 0f, 0f, 2f, 2f, shimmer, shimmer, shimmer, 1f);
            }

            // do lado de dentro: o sol
            for (SkyImage image : INSIDE) drawImage(modelViewMatrix, image, time);

            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            return true;
        }

        private static void drawImage(Matrix4f modelViewMatrix, SkyImage image, float time) {
            if (image.additive()) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            else RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            float glow = 1f - image.pulse() + image.pulse() * (0.5f + 0.5f * Mth.sin(time * 0.04f + image.yaw()));
            Matrix4f m = new Matrix4f(modelViewMatrix)
                    .rotateY((float) Math.toRadians(image.yaw()))
                    .rotateX((float) Math.toRadians(image.tilt()))
                    .rotateY((float) Math.toRadians(image.roll()) + time * image.spin());
            quad(m, image.texture(), image.size() * image.aspect(), image.size(), 100f, 0f, 0f, 1f, 1f, glow, glow, glow, 1f);
        }

        /** Um ponto de estrela virado para quem olha */
        private static void star(BufferBuilder b, Matrix4f m, float x, float y, float z, float size, float r, float g, float bl, float a) {
            float dx = x / 100f, dy = y / 100f, dz = z / 100f;
            float ax = Math.abs(dy) > 0.95f ? 1 : 0, ay = Math.abs(dy) > 0.95f ? 0 : 1;
            // u = d x up, v = d x u
            float ux = dy * 0 - dz * ay, uy = dz * ax - dx * 0, uz = dx * ay - dy * ax;
            float ul = Mth.sqrt(ux * ux + uy * uy + uz * uz);
            ux = ux / ul * size; uy = uy / ul * size; uz = uz / ul * size;
            float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
            b.addVertex(m, x - ux - vx, y - uy - vy, z - uz - vz).setColor(r, g, bl, a);
            b.addVertex(m, x + ux - vx, y + uy - vy, z + uz - vz).setColor(r, g, bl, a);
            b.addVertex(m, x + ux + vx, y + uy + vy, z + uz + vz).setColor(r, g, bl, a);
            b.addVertex(m, x - ux + vx, y - uy + vy, z - uz + vz).setColor(r, g, bl, a);
        }

        private static void quad(Matrix4f m, ResourceLocation texture, float w, float h, float d, float u0, float v0, float u1, float v1,
                                 float r, float g, float b, float a) {
            RenderSystem.setShaderTexture(0, texture);
            BufferBuilder q = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            q.addVertex(m, -w, d, -h).setUv(u0, v0).setColor(r, g, b, a);
            q.addVertex(m, w, d, -h).setUv(u1, v0).setColor(r, g, b, a);
            q.addVertex(m, w, d, h).setUv(u1, v1).setColor(r, g, b, a);
            q.addVertex(m, -w, d, h).setUv(u0, v1).setColor(r, g, b, a);
            BufferUploader.drawWithShader(q.buildOrThrow());
        }
    }
}
