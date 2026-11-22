package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.ai.statemachine.AIEventTarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIBlockingEventType;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.rituals.Believers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A missa das 18h no comportamento dos colonos: a partir das 17h os fiéis largam o que estão fazendo e andam até o
 * templo do seu Deus na colônia; ficam rezando até o fim da missa e então voltam ao normal, e como já é noite o
 * MineColonies os manda para casa dormir. Quem mora no templo dorme lá mesmo.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class ChurchMass {
    private ChurchMass() {}

    /** O estado do colono enquanto vai à missa e reza */
    public enum MassState implements IState { MASS }

    /** 17h: os fiéis começam a ir; 18h: a missa; 18h45: acabou */
    public static final long GATHER = 11000, MASS = 12000, END = 12750;

    private static final Map<EntityCitizen, Boolean> HOOKED = Collections.synchronizedMap(new WeakHashMap<>());

    public static boolean isMassTime(long dayTime) {
        long t = dayTime % 24000L;
        return t >= GATHER && t < END;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntityCitizen citizen)) return;
        if (HOOKED.put(citizen, Boolean.TRUE) != null) return;
        var ai = citizen.getCitizenAI();
        // antes de qualquer outra coisa (trabalho, comer, vagar): na hora da missa, o fiel vai ao templo
        ai.addTransition(new AIEventTarget<IState>(AIBlockingEventType.AI_BLOCKING, () -> templeFor(citizen) != null, () -> attend(citizen), 20));
        // acabou a missa: volta ao normal (e, sendo noite, vai dormir)
        ai.addTransition(new TickingTransition<IState>(MassState.MASS, () -> true, () -> CitizenAIState.IDLE, 20));
    }

    /** O templo onde o fiel deve estar agora, ou null se não é hora (ou ele não é fiel, ou está em perigo) */
    public static ChurchBuilding templeFor(EntityCitizen entity) {
        if (!(entity.level() instanceof ServerLevel level) || !isMassTime(level.getDayTime())) return null;
        ICitizenData data = entity.getCitizenData();
        if (data == null || entity.isSleeping() || entity.getHealth() < entity.getMaxHealth() * 0.3f) return null;
        IColony colony = data.getColony();
        if (colony == null || colony.getRaiderManager().isRaided()) return null;
        UUID god = Believers.godOfCitizen(level.getServer(), data.getUUID());
        if (god == null) return null;
        ChurchBuilding best = null;
        for (ChurchBuilding temple : ChurchConversion.temples(colony)) {
            if (!god.equals(temple.getGod())) continue;
            if (best == null || temple.getBuildingLevel() > best.getBuildingLevel()) best = temple;
        }
        return best;
    }

    /** Anda até o templo; chegando, reza olhando para o altar */
    private static IState attend(EntityCitizen entity) {
        ChurchBuilding temple = templeFor(entity);
        if (temple == null) return CitizenAIState.IDLE;
        BlockPos at = temple.getPosition();
        if (EntityNavigationUtils.walkToPos(entity, at, 6, true)) {
            entity.lookAt(EntityAnchorArgument.Anchor.EYES, at.getCenter());
            if (entity.level() instanceof ServerLevel level && entity.getRandom().nextInt(3) == 0)
                level.sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY(1.2), entity.getZ(), 6, 0.3, 0.4, 0.3, 0.5);
        }
        return MassState.MASS;
    }

    /** Já está no templo (ou mora nele): conta como presente na missa */
    public static boolean isAttending(ICitizenData citizen, ChurchBuilding temple) {
        if (citizen.getHomeBuilding() == temple) return true;
        return citizen.getEntity().map(e -> e.blockPosition().closerThan(temple.getPosition(), 16)).orElse(false);
    }
}
