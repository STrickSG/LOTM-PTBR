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

import com.minecolonies.core.entity.other.SittingEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A missa das 18h no comportamento dos colonos: a partir das 17h os fiéis largam o que estão fazendo e andam até o
 * templo do seu Deus na colônia, cada um senta no seu lugar, espalhados pelos bancos; ficam rezando até o fim da missa e então voltam ao normal, e como já é noite o
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
        ai.addTransition(new TickingTransition<IState>(MassState.MASS, () -> templeFor(citizen) == null, () -> {
            if (citizen.isPassenger()) citizen.stopRiding();
            return CitizenAIState.IDLE;
        }, 20));
    }

    /** O templo onde o fiel deve estar agora, ou null se não é hora (ou ele não é fiel, ou está em perigo) */
    public static ChurchBuilding templeFor(EntityCitizen entity) {
        if (!(entity.level() instanceof ServerLevel level)) return null;
        ICitizenData data = entity.getCitizenData();
        if (data == null || entity.getHealth() < entity.getMaxHealth() * 0.3f) return null;
        IColony colony = data.getColony();
        if (colony == null || colony.getRaiderManager().isRaided()) return null;
        // as reuniões secretas da Seita e da Ordem (os membros chamados e quem a Seita atraiu)
        ChurchBuilding secret = SectGatherings.templeFor(level, data);
        if (secret != null) return secret;
        if (!isMassTime(level.getDayTime()) || entity.isSleeping()) return null;
        UUID god = Believers.godOfCitizen(level.getServer(), data.getUUID());
        // a Seita e a Ordem não vão à missa pública
        if (god == null || Believers.isSecret(level.getServer(), god)) return null;
        ChurchBuilding best = null;
        for (ChurchBuilding temple : ChurchConversion.temples(colony)) {
            if (!god.equals(temple.getGod())) continue;
            if (best == null || temple.getBuildingLevel() > best.getBuildingLevel()) best = temple;
        }
        return best;
    }

    /** Anda até o seu lugar num banco do templo, senta e reza olhando para o altar */
    private static IState attend(EntityCitizen entity) {
        ChurchBuilding temple = templeFor(entity);
        if (temple == null) {
            if (entity.isPassenger()) entity.stopRiding();
            return CitizenAIState.IDLE;
        }
        if (entity.isSleeping()) entity.stopSleeping();   // a reunião secreta é à noite: levanta da cama
        BlockPos altar = temple.getPosition();
        ServerLevel level = (ServerLevel) entity.level();
        BlockPos seat = seatFor(entity, temple, level);
        if (entity.isPassenger()) {
            pray(entity, altar, level);
            return MassState.MASS;
        }
        if (seat == null) {
            if (EntityNavigationUtils.walkToPos(entity, altar, 6, true)) pray(entity, altar, level);
            return MassState.MASS;
        }
        if (EntityNavigationUtils.walkToPos(entity, seat, 1, true)) {
            long left = END - level.getDayTime() % 24000L;
            if (!SittingEntity.isSittingPosOccupied(seat, level)) SittingEntity.sitDown(seat, entity, (int) Math.max(20, left));
            pray(entity, altar, level);
        }
        return MassState.MASS;
    }

    private static void pray(EntityCitizen entity, BlockPos altar, ServerLevel level) {
        entity.lookAt(EntityAnchorArgument.Anchor.EYES, altar.getCenter());
        if (entity.getRandom().nextInt(3) == 0)
            level.sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY(1.2), entity.getZ(), 6, 0.3, 0.4, 0.3, 0.5);
    }

    // ---------------------------------------------------------------- os bancos

    private record Seats(long found, List<BlockPos> list) {}
    private static final Map<BlockPos, Seats> SEATS = new HashMap<>();

    /** O lugar deste fiel: os fiéis do templo se espalham pelos bancos, do mais perto do altar ao fundo */
    private static BlockPos seatFor(EntityCitizen entity, ChurchBuilding temple, ServerLevel level) {
        List<BlockPos> seats = seats(temple, level);
        if (seats.isEmpty()) return null;
        ICitizenData me = entity.getCitizenData();
        UUID god = temple.getGod();
        List<UUID> flock = new ArrayList<>();
        for (ICitizenData c : temple.getColony().getCitizenManager().getCitizens())
            if (god.equals(Believers.godOfCitizen(level.getServer(), c.getUUID()))) flock.add(c.getUUID());
        Collections.sort(flock);
        // espalhados por todos os bancos, da frente para o fundo
        int index = Math.max(0, flock.indexOf(me.getUUID())) * seats.size() / Math.max(1, flock.size());
        // se o lugar dele estiver ocupado por outro, tenta os seguintes
        for (int k = 0; k < seats.size(); k++) {
            BlockPos seat = seats.get((index + k) % seats.size());
            if (k == 0 || !SittingEntity.isSittingPosOccupied(seat, level)) return seat;
        }
        return seats.get(index % seats.size());
    }

    /** Os degraus que servem de banco: com espaço em cima, e quem senta fica de frente para o altar */
    private static List<BlockPos> seats(ChurchBuilding temple, ServerLevel level) {
        BlockPos altar = temple.getPosition();
        Seats cached = SEATS.get(altar);
        if (cached != null && level.getGameTime() - cached.found() < 6000) return cached.list();
        // a área do prédio, e no mínimo 40 blocos em volta do altar
        BlockPos min = altar.offset(-40, -4, -40), max = altar.offset(40, 3, 40);
        var corners = temple.getCorners();
        if (corners != null && corners.getA() != null && corners.getB() != null) {
            BlockPos a = corners.getA(), b = corners.getB();
            min = new BlockPos(Math.min(min.getX(), Math.min(a.getX(), b.getX())), min.getY(), Math.min(min.getZ(), Math.min(a.getZ(), b.getZ())));
            max = new BlockPos(Math.max(max.getX(), Math.max(a.getX(), b.getX())), max.getY(), Math.max(max.getZ(), Math.max(a.getZ(), b.getZ())));
        }
        List<BlockPos> list = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int y0 = Math.max(Math.min(min.getY(), max.getY()), altar.getY() - 4), y1 = Math.min(Math.max(min.getY(), max.getY()), altar.getY() + 2);
        for (int x = Math.min(min.getX(), max.getX()); x <= Math.max(min.getX(), max.getX()); x++)
            for (int z = Math.min(min.getZ(), max.getZ()); z <= Math.max(min.getZ(), max.getZ()); z++)
                for (int y = y0; y <= y1; y++) {
                    p.set(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("stairs")) continue;
                    if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) continue;
                    if (state.hasProperty(BlockStateProperties.HALF) && state.getValue(BlockStateProperties.HALF) != Half.BOTTOM) continue;
                    if (!level.getBlockState(p.above()).isAir() || !level.getBlockState(p.above(2)).isAir()) continue;
                    if (level.getBlockState(p.below()).isAir()) continue;
                    // quem senta olha para o lado oposto do encosto: tem de ser para o lado do altar
                    Direction looks = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
                    double dx = altar.getX() - x, dz = altar.getZ() - z;
                    if (looks.getStepX() * dx + looks.getStepZ() * dz <= 0) continue;
                    list.add(p.immutable());
                }
        list.sort(Comparator.comparingDouble(b -> b.distSqr(altar)));
        SEATS.put(altar, new Seats(level.getGameTime(), list));
        return list;
    }

    /** Está na missa (sentado nos bancos ou indo até eles), perto do altar, ou mora no templo: conta como presente */
    public static boolean isAttending(ICitizenData citizen, ChurchBuilding temple) {
        if (citizen.getHomeBuilding() == temple) return true;
        return citizen.getEntity().map(e -> e instanceof EntityCitizen ec && ec.getCitizenAI().getState() == MassState.MASS
                || e.blockPosition().closerThan(temple.getPosition(), 16)).orElse(false);
    }
}
