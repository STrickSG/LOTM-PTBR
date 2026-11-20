package de.jakob.lotm.addons.astral;

import de.jakob.lotm.LOTMCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * O Mundo Astral: um plano onde tudo é conceito. Parece o cosmos, mas a luz se junta em manchas de prisma, os astros
 * são desenhos rabiscados numa cortina preta, e flutuam "ideias" das coisas do mundo. (Por enquanto, só a dimensão.)
 */
public final class AstralWorld {
    private AstralWorld() {}

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "astral_world"));
}
