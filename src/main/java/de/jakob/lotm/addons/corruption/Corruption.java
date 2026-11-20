package de.jakob.lotm.addons.corruption;

import de.jakob.lotm.util.BeyonderData;
import net.minecraft.world.entity.LivingEntity;

/**
 * A corrupção de um Beyonder (0 a 1). Por enquanto vem só das características acumuladas no corpo: cada cópia extra
 * de uma característica pesa mais quanto mais alta a Sequência dela (4% na Sequência 9, 20% na Sequência 1).
 * Funciona no servidor e no cliente (as cópias acumuladas já são sincronizadas).
 */
public final class Corruption {
    private Corruption() {}

    /** Quanto uma cópia extra de característica desta Sequência corrompe */
    public static float weight(int sequence) {
        return 0.04f + 0.02f * (9 - Math.max(0, Math.min(9, sequence)));
    }

    public static float fromStacks(int[] stacks) {
        if (stacks == null) return 0f;
        float total = 0f;
        for (int seq = 0; seq < stacks.length; seq++) {
            if (stacks[seq] > 0) total += stacks[seq] * weight(seq);
        }
        return Math.min(1f, total);
    }

    public static float of(LivingEntity entity) {
        if (!BeyonderData.isBeyonder(entity)) return 0f;
        return fromStacks(BeyonderData.getCharStacks(entity));
    }
}
