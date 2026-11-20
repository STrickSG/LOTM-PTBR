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
 * Mundo Astral: o céu é o Cosmos visto por trás da Barreira Astral, mas aqui tudo é conceito, e o conceito chega
 * filtrado, sem a corrupção do Cosmos: como desenhos de criança a giz de cera, sem rostos. Estrelas de giz, a faixa
 * da galáxia e nebulosas em borrões pastel, os Deuses Exteriores rabiscados, enormes, colados na Barreira, o Sol
 * Ardente Eterno do lado de dentro e, por cima de tudo, as rachaduras da Barreira riscadas a giz.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public final class AstralWorldEffects {
    private AstralWorldEffects() {}

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/environment/astral/" + name + ".png");
    }

    /** Uma imagem no céu, parada: textura, direção (graus), inclinação a partir do topo (graus), giro, tamanho, proporção, se soma luz */
    private record SkyImage(ResourceLocation texture, float yaw, float tilt, float roll, float size, float aspect, boolean additive) {}

    /**
     * O Cosmos atrás da Barreira. O céu não se mexe: cada coisa tem o seu lugar fixo.
     * Os 10 Deuses Exteriores ficam bem separados: um no topo, quatro num anel alto e cinco num anel logo abaixo do horizonte,
     * intercalados, e o lugar que sobra no anel alto é do sol. Galáxias, nebulosas e planetas preenchem os vãos.
     */
    private static final SkyImage[] COSMOS = {
            // nebulosas, grandes e fracas, no fundo
            new SkyImage(tex("nebula_violet"), 180f, 25f, 0f, 30f, 1f, true),
            new SkyImage(tex("nebula_teal"), 36f, 140f, 30f, 30f, 1f, true),
            new SkyImage(tex("nebula_crimson"), 252f, 140f, 70f, 28f, 1f, true),
            // galáxias nos vãos entre os anéis (podem repetir)
            new SkyImage(tex("galaxy"), 18f, 75f, 20f, 10f, 1f, true),
            new SkyImage(tex("galaxy_pink"), 90f, 75f, -40f, 9f, 1f, true),
            new SkyImage(tex("galaxy_teal"), 162f, 75f, 10f, 10f, 1f, true),
            new SkyImage(tex("galaxy"), 234f, 75f, 60f, 9f, 1f, true),
            new SkyImage(tex("galaxy_pink"), 0f, 25f, -20f, 8f, 1f, true),
            new SkyImage(tex("galaxy_teal"), 72f, 25f, 30f, 8f, 1f, true),
            new SkyImage(tex("galaxy"), 288f, 25f, 45f, 8f, 1f, true),
            new SkyImage(tex("galaxy_teal"), 108f, 135f, 15f, 9f, 1f, true),
            new SkyImage(tex("galaxy_pink"), 324f, 135f, -30f, 9f, 1f, true),
            // planetas comuns, pequenos
            new SkyImage(tex("planet_gas"), 126f, 78f, 0f, 5f, 1f, false),
            new SkyImage(tex("planet_ring"), 270f, 80f, 10f, 4.5f, 1f, false),
            // os Deuses Exteriores: no topo, o Supervisor olhando de cima
            new SkyImage(tex("outer_overseer"), 0f, 0f, 0f, 13f, 1f, false),
            // anel alto (o quinto lugar é o do sol, em 324)
            new SkyImage(tex("outer_depravity"), 36f, 50f, 0f, 16f, 1f, false),
            new SkyImage(tex("outer_supernova"), 108f, 50f, 0f, 13f, 1f, true),
            new SkyImage(tex("outer_chaos"), 180f, 50f, 0f, 13f, 1f, false),
            new SkyImage(tex("outer_desire"), 252f, 50f, 0f, 14f, 1f, false),
            // anel logo abaixo do horizonte, intercalado com o de cima
            new SkyImage(tex("outer_hunger"), 0f, 100f, 15f, 15f, 1f, false),
            new SkyImage(tex("outer_inevitability"), 72f, 100f, 0f, 14f, 1f, true),
            new SkyImage(tex("outer_ravings"), 144f, 100f, 0f, 12f, 1f, true),
            new SkyImage(tex("outer_decay"), 216f, 100f, 0f, 13f, 1f, false),
            new SkyImage(tex("outer_fate"), 288f, 100f, 0f, 12f, 1f, false),
    };

    /** Do lado de dentro da Barreira: o Sol Ardente Eterno, o mais perto de todos e por isso o maior */
    private static final SkyImage[] INSIDE = {
            new SkyImage(tex("sun"), 324f, 50f, 0f, 48f, 1f, true),
    };

    private static final int STARS = 700;
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
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();

            // o fundo: a folha inteira pintada de escuro a giz de cera
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
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
                quad(m, tex("sky"), 120f, 120f, 120f, 0f, 0f, 3f, 3f, 1f, 1f, 1f, 1f);
            }

            // estrelas: estrelinhas de giz, cada uma com o seu brilho
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, tex("star"));
            BufferBuilder stars = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (float[] s : STAR) {
                float bright = 0.55f + 0.45f * Mth.sin(s[7]);
                star(stars, modelViewMatrix, s[0] * 100, s[1] * 100, s[2] * 100, 0.6f + s[3] * 3f, 1f, 1f, 1f, bright);
            }
            BufferUploader.drawWithShader(stars.buildOrThrow());

            // o Cosmos e os Deuses Exteriores, atrás da Barreira
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            for (SkyImage image : COSMOS) drawImage(modelViewMatrix, image);

            // a Barreira Astral: uma película quase invisível em volta de tudo, rachada, vazando luz
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
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
                quad(m, tex("barrier"), 90f, 90f, 90f, 0f, 0f, 2f, 2f, 0.5f, 0.5f, 0.5f, 1f);
            }

            // do lado de dentro: o sol
            for (SkyImage image : INSIDE) drawImage(modelViewMatrix, image);

            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            return true;
        }

        private static void drawImage(Matrix4f modelViewMatrix, SkyImage image) {
            if (image.additive()) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            else RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            Matrix4f m = new Matrix4f(modelViewMatrix)
                    .rotateY((float) Math.toRadians(image.yaw()))
                    .rotateX((float) Math.toRadians(image.tilt()))
                    .rotateY((float) Math.toRadians(image.roll()));
            quad(m, image.texture(), image.size() * image.aspect(), image.size(), 100f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
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
            b.addVertex(m, x - ux - vx, y - uy - vy, z - uz - vz).setUv(0, 0).setColor(r, g, bl, a);
            b.addVertex(m, x + ux - vx, y + uy - vy, z + uz - vz).setUv(1, 0).setColor(r, g, bl, a);
            b.addVertex(m, x + ux + vx, y + uy + vy, z + uz + vz).setUv(1, 1).setColor(r, g, bl, a);
            b.addVertex(m, x - ux + vx, y - uy + vy, z - uz + vz).setUv(0, 1).setColor(r, g, bl, a);
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
