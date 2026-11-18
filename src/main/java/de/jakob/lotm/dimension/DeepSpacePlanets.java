package de.jakob.lotm.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Random;

/**
 * Planetas pesados do Espaço Profundo (ritual da Chave das Estrelas, Porta 1): esferas grandes e raras de pedra-negra,
 * com manchas de obsidiana chorona, rachaduras de magma que brilham, núcleo de magma e um anel de ametista em volta.
 * Cada região de REGION x REGION blocos tem no máximo um planeta, sempre no mesmo lugar.
 */
public final class DeepSpacePlanets {
    public static final int REGION = 1536;
    private static final int MARGIN = 160;

    private static final BlockState[] SURFACE = {
            Blocks.BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
            Blocks.BLACKSTONE.defaultBlockState(), Blocks.GILDED_BLACKSTONE.defaultBlockState()};
    private static final BlockState GLOW = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState CRACK = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState MANTLE = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState CORE = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState HEART = Blocks.SHROOMLIGHT.defaultBlockState();
    private static final BlockState RING = Blocks.AMETHYST_BLOCK.defaultBlockState();
    private static final BlockState RING_EDGE = Blocks.PURPUR_BLOCK.defaultBlockState();

    private DeepSpacePlanets() {}

    public record Planet(int x, int y, int z, int radius) {
        /** Está na superfície do planeta ou dentro dele (com uma folga para quem está em pé em cima) */
        public boolean contains(double px, double py, double pz, double extra) {
            double dx = px - x, dy = py - y, dz = pz - z;
            double r = radius + extra;
            return dx * dx + dy * dy + dz * dz <= r * r;
        }

        public int ringInner() {
            return (int) (radius * 1.45);
        }

        public int ringOuter() {
            return (int) (radius * 1.85);
        }

        public long key() {
            return BlockPos.asLong(x, y, z);
        }
    }

    /** O planeta da região (rx, rz), ou null se ela não tiver um */
    public static Planet inRegion(int rx, int rz) {
        Random rng = new Random(hash(rx, rz));
        if (rng.nextInt(100) >= 60) return null;
        int x = rx * REGION + MARGIN + rng.nextInt(REGION - 2 * MARGIN);
        int z = rz * REGION + MARGIN + rng.nextInt(REGION - 2 * MARGIN);
        int y = 100 + rng.nextInt(40);
        int radius = 34 + rng.nextInt(9);
        return new Planet(x, y, z, radius);
    }

    /** O planeta mais perto de (x, z), olhando a região do ponto e as vizinhas */
    public static Planet nearest(double x, double z) {
        int rx = Math.floorDiv((int) Math.floor(x), REGION), rz = Math.floorDiv((int) Math.floor(z), REGION);
        Planet best = null;
        double bestDist = Double.MAX_VALUE;
        for (int ix = rx - 1; ix <= rx + 1; ix++) {
            for (int iz = rz - 1; iz <= rz + 1; iz++) {
                Planet p = inRegion(ix, iz);
                if (p == null) continue;
                double d = (p.x - x) * (p.x - x) + (p.z - z) * (p.z - z);
                if (d < bestDist) { bestDist = d; best = p; }
            }
        }
        return best;
    }

    /** Espaço vazio em volta do planeta e do anel: ali não nascem asteroides nem ilhas */
    public static boolean inClearing(int x, int y, int z) {
        Planet p = nearest(x, z);
        if (p == null) return false;
        double dx = x - p.x, dz = z - p.z;
        double r = p.ringOuter() + 70;
        return dx * dx + dz * dz < r * r && Math.abs(y - p.y) < 100;
    }

    /** Coloca no chunk a parte dos planetas (e dos anéis) que cai nele */
    public static void placeInChunk(ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        int reach = 90;
        int rx0 = Math.floorDiv(minX - reach, REGION), rx1 = Math.floorDiv(minX + 15 + reach, REGION);
        int rz0 = Math.floorDiv(minZ - reach, REGION), rz1 = Math.floorDiv(minZ + 15 + reach, REGION);
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                Planet p = inRegion(rx, rz);
                if (p == null) continue;
                int outer = p.ringOuter();
                if (p.x + outer < minX || p.x - outer > minX + 15 || p.z + outer < minZ || p.z - outer > minZ + 15) continue;
                place(chunk, p);
            }
        }
    }

    private static void place(ChunkAccess chunk, Planet p) {
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long seed = p.key();
        int r = p.radius;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = minX + lx, wz = minZ + lz;
                int dx = wx - p.x, dz = wz - p.z;
                int flat = dx * dx + dz * dz;

                // anel achatado em volta do equador, com falhas (pedaços soltos)
                double ringDist = Math.sqrt(flat);
                if (ringDist >= p.ringInner() && ringDist <= p.ringOuter() && p.y >= chunk.getMinBuildHeight() && p.y < chunk.getMaxBuildHeight()) {
                    double gap = noise(seed + 7, wx * .09, 0, wz * .09);
                    if (gap > .32) {
                        boolean edge = ringDist < p.ringInner() + 1.2 || ringDist > p.ringOuter() - 1.2;
                        chunk.setBlockState(pos.set(lx, p.y, lz), edge ? RING_EDGE : RING, false);
                    }
                }

                if (flat > r * r) continue;
                for (int dy = -r; dy <= r; dy++) {
                    int wy = p.y + dy;
                    if (wy < chunk.getMinBuildHeight() || wy >= chunk.getMaxBuildHeight()) continue;
                    double dist = Math.sqrt(flat + dy * dy);
                    if (dist > r) continue;
                    BlockState state;
                    if (dist < 3) state = HEART;                                   // coração que brilha
                    else if (dist < r * .45) state = CORE;                          // núcleo de magma
                    else if (dist < r - 2.2) state = MANTLE;                       // manto de obsidiana
                    else {
                        // superfície: manchas, obsidiana chorona e rachaduras de magma
                        double n = noise(seed, wx * .07, wy * .07, wz * .07);
                        double crack = Math.abs(noise(seed + 3, wx * .11, wy * .11, wz * .11) - .5);
                        if (crack < .025) state = CRACK;
                        else if (n > .74) state = GLOW;
                        else state = SURFACE[Math.min(SURFACE.length - 1, (int) (n * 1.3 * SURFACE.length) % SURFACE.length)];
                    }
                    chunk.setBlockState(pos.set(lx, wy, lz), state, false);
                }
            }
        }
    }

    // ruído de valor 3D simples (determinístico, sem depender da semente do mundo)
    private static double noise(long seed, double x, double y, double z) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double c000 = rand(seed, x0, y0, z0), c100 = rand(seed, x0 + 1, y0, z0);
        double c010 = rand(seed, x0, y0 + 1, z0), c110 = rand(seed, x0 + 1, y0 + 1, z0);
        double c001 = rand(seed, x0, y0, z0 + 1), c101 = rand(seed, x0 + 1, y0, z0 + 1);
        double c011 = rand(seed, x0, y0 + 1, z0 + 1), c111 = rand(seed, x0 + 1, y0 + 1, z0 + 1);
        double x00 = lerp(fx, c000, c100), x10 = lerp(fx, c010, c110), x01 = lerp(fx, c001, c101), x11 = lerp(fx, c011, c111);
        return lerp(fz, lerp(fy, x00, x10), lerp(fy, x01, x11));
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double rand(long seed, int x, int y, int z) {
        long v = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        v ^= v >>> 33;
        v *= 0xff51afd7ed558ccdL;
        v ^= v >>> 33;
        return (v >>> 11) * 0x1.0p-53;
    }

    private static long hash(int x, int z) {
        long v = (long) x * 734287L + (long) z * 912931L + 0x50_4C_41_4E_45_54L;
        v ^= v >>> 33;
        v *= 0xff51afd7ed558ccdL;
        v ^= v >>> 33;
        v *= 0xc4ceb9fe1a85ec53L;
        v ^= v >>> 33;
        return v;
    }
}
