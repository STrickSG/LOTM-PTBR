package de.jakob.lotm.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Random;

/**
 * Planetas pesados do Espaço Profundo (ritual da Chave das Estrelas, Porta 1): esferas grandes e raras,
 * com núcleo de magma e faixas de obsidiana chorona que parecem girar. Cada região de REGION x REGION
 * blocos tem no máximo um planeta, sempre no mesmo lugar (a posição vem só das coordenadas da região).
 */
public final class DeepSpacePlanets {
    public static final int REGION = 1536;
    private static final int MARGIN = 120;

    private static final BlockState CORE = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState BAND_DARK = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState BAND_GLOW = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState CRUST = Blocks.BLACKSTONE.defaultBlockState();

    private DeepSpacePlanets() {}

    public record Planet(int x, int y, int z, int radius) {
        /** Está na superfície do planeta ou dentro dele (com uma folga para quem está em pé em cima) */
        public boolean contains(double px, double py, double pz, double extra) {
            double dx = px - x, dy = py - y, dz = pz - z;
            double r = radius + extra;
            return dx * dx + dy * dy + dz * dz <= r * r;
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
        int y = 90 + rng.nextInt(40);
        int radius = 26 + rng.nextInt(7);
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

    /** Coloca no chunk a parte dos planetas que cai nele */
    public static void placeInChunk(ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        int rx0 = Math.floorDiv(minX - 40, REGION), rx1 = Math.floorDiv(minX + 15 + 40, REGION);
        int rz0 = Math.floorDiv(minZ - 40, REGION), rz1 = Math.floorDiv(minZ + 15 + 40, REGION);
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                Planet p = inRegion(rx, rz);
                if (p == null) continue;
                if (p.x + p.radius < minX || p.x - p.radius > minX + 15 || p.z + p.radius < minZ || p.z - p.radius > minZ + 15) continue;
                place(chunk, p);
            }
        }
    }

    private static void place(ChunkAccess chunk, Planet p) {
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int dx = minX + lx - p.x, dz = minZ + lz - p.z;
                if (dx * dx + dz * dz > p.radius * p.radius) continue;
                for (int dy = -p.radius; dy <= p.radius; dy++) {
                    int wy = p.y + dy;
                    if (wy < chunk.getMinBuildHeight() || wy >= chunk.getMaxBuildHeight()) continue;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > p.radius) continue;
                    BlockState state;
                    if (dist < p.radius * 0.45) state = CORE;
                    else if (dist > p.radius - 1.2) state = ((dy + p.radius) / 3) % 2 == 0 ? BAND_GLOW : CRUST; // faixas da superfície
                    else state = ((dy + p.radius) / 3) % 2 == 0 ? BAND_GLOW : BAND_DARK;
                    chunk.setBlockState(pos.set(lx, wy, lz), state, false);
                }
            }
        }
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
