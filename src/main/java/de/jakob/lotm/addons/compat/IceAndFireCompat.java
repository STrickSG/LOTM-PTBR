package de.jakob.lotm.addons.compat;

import de.jakob.lotm.LOTMCraft;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * Ice and Fire (sem depender dele): dragões de estágio 4 e 5 são semideuses.
 * Estágio 4 conta como Sequência 4 e estágio 5 como Sequência 3, para os rituais e para a regra do mod
 * (poderes de Beyonders de Sequência 5 ou mais fraca não afetam semideuses). Dano de outras fontes funciona normalmente.
 */
public final class IceAndFireCompat {
    private IceAndFireCompat() {}

    private static final String DRAGON_CLASS = "com.iafenvoy.iceandfire.entity.DragonBaseEntity";

    /** Método getDragonStage do dragão, ou null se a classe não for um dragão do Ice and Fire */
    private static final ClassValue<Method> STAGE = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                if (!c.getName().equals(DRAGON_CLASS)) continue;
                try {
                    return c.getMethod("getDragonStage");
                } catch (NoSuchMethodException e) {
                    return null;
                }
            }
            return null;
        }
    };

    /** Sequência do dragão como semideus (4 ou 3), ou a de não-Beyonder */
    public static int demigodSequence(LivingEntity entity) {
        Method stage = STAGE.get(entity.getClass());
        if (stage == null) return LOTMCraft.NON_BEYONDER_SEQ;
        try {
            int value = (int) stage.invoke(entity);
            if (value >= 5) return 3;
            if (value == 4) return 4;
        } catch (ReflectiveOperationException | ClassCastException ignored) {
        }
        return LOTMCraft.NON_BEYONDER_SEQ;
    }

    public static boolean isDemigodDragon(LivingEntity entity) {
        return demigodSequence(entity) != LOTMCraft.NON_BEYONDER_SEQ;
    }
}
