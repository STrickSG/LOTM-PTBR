package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.rituals.Believers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * As orações da Seita e da Ordem: não há missa todo dia. De vez em quando, à noite, alguns membros se reúnem em segredo
 * no esconderijo e fazem um pedido ao Deus (as Preces Pendentes, como os pedidos da Igreja).
 * <ul>
 *   <li>Seita: os pedidos comuns (cura, comida) vão com o sacrifício de um animal; os grandes (proteção, poder), com o de
 *   um colono que não é membro, atraído até o altar. O sacrifício paga o pedido (metade ou tudo).</li>
 *   <li>Ordem: sem sacrifício; a reunião adianta a digestão dos presentes e reforça as âncoras do Deus.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class SectGatherings {
    private SectGatherings() {}

    /** 21h: os escolhidos vão ao esconderijo; 22h30: o rito; 23h: voltam para casa */
    public static final long START = 15000, RITE = 16500, END = 17000;
    /** Chance de cada esconderijo ter reunião numa noite */
    private static final double CHANCE = 0.35;
    private static final Random RANDOM = new Random();

    private static final class Gathering {
        final UUID god;
        final int colony;
        final BlockPos temple;
        final List<UUID> members = new ArrayList<>();
        UUID victim;
        String petition;
        boolean done;

        Gathering(UUID god, int colony, BlockPos temple) {
            this.god = god;
            this.colony = colony;
            this.temple = temple;
        }
    }

    /** Colono (membro ou vítima) -> a reunião para a qual está indo esta noite */
    private static final Map<UUID, Gathering> CALLED = new HashMap<>();
    private static long lastDay = -1;

    public static boolean isGatheringTime(long dayTime) {
        long t = dayTime % 24000L;
        return t >= START && t < END;
    }

    /** O esconderijo para onde o colono deve ir agora (ou null) */
    public static ChurchBuilding templeFor(ServerLevel level, ICitizenData citizen) {
        if (!isGatheringTime(level.getDayTime())) return null;
        Gathering g = CALLED.get(citizen.getUUID());
        if (g == null || citizen.getColony() == null || citizen.getColony().getID() != g.colony) return null;
        return citizen.getColony().getServerBuildingManager().getBuilding(g.temple) instanceof ChurchBuilding temple ? temple : null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        long time = server.overworld().getDayTime(), day = time / 24000L, t = time % 24000L;
        if (t >= START && t < RITE && day != lastDay) {
            lastDay = day;
            CALLED.clear();
            for (IColony colony : IColonyManager.getInstance().getAllColonies()) call(server, colony);
        }
        if (t >= RITE && t < END) {
            for (Gathering g : new ArrayList<>(CALLED.values())) if (!g.done) rite(server, g);
        }
        if (t >= END || t < START) CALLED.clear();
    }

    /** Escolhe, em cada esconderijo que se reúne esta noite, alguns membros (e a vítima, se for preciso) */
    private static void call(MinecraftServer server, IColony colony) {
        Churches churches = Churches.get(server);
        for (ChurchBuilding temple : ChurchConversion.temples(colony)) {
            UUID god = temple.getGod();
            Churches.Info info = churches.info(god);
            if (info == null || !info.isSect() || RANDOM.nextDouble() >= CHANCE) continue;
            List<ICitizenData> members = new ArrayList<>(), others = new ArrayList<>();
            for (ICitizenData c : colony.getCitizenManager().getCitizens()) {
                UUID g = Believers.godOfCitizen(server, c.getUUID());
                if (god.equals(g)) members.add(c);
                else if (g == null || !Believers.isSecret(server, g)) others.add(c);
            }
            if (members.isEmpty()) continue;
            Collections.shuffle(members, RANDOM);
            Gathering g = new Gathering(god, colony.getID(), temple.getPosition());
            for (int i = 0; i < Math.min(members.size(), 2 + RANDOM.nextInt(3)); i++) {
                g.members.add(members.get(i).getUUID());
                CALLED.put(members.get(i).getUUID(), g);
            }
            g.petition = choosePetition(colony, members.subList(0, g.members.size()));
            // os pedidos grandes da Seita pedem uma pessoa: atraem um colono que não é membro
            boolean big = ChurchPrayers.PROTECT.equals(g.petition) || ChurchPrayers.POWER.equals(g.petition);
            if (info.isDark() && big && !others.isEmpty()) {
                ICitizenData victim = others.get(RANDOM.nextInt(others.size()));
                g.victim = victim.getUUID();
                CALLED.put(victim.getUUID(), g);
            }
        }
    }

    private static String choosePetition(IColony colony, List<ICitizenData> present) {
        if (colony.getRaiderManager().isRaided()) return ChurchPrayers.PROTECT;
        for (ICitizenData c : present) {
            LivingEntity e = c.getEntity().orElse(null);
            if (e != null && e.getHealth() < e.getMaxHealth() * 0.6f) return ChurchPrayers.HEAL;
        }
        for (ICitizenData c : present) if (c.getSaturation() < 4) return ChurchPrayers.FOOD;
        return ChurchPrayers.POWER;
    }

    /** O rito: quem chegou reza; a Seita sacrifica; o pedido sobe ao Deus */
    private static void rite(MinecraftServer server, Gathering g) {
        g.done = true;
        IColony colony = IColonyManager.getInstance().getColonyByWorld(g.colony, server.overworld());
        if (colony == null) colony = find(g.colony);
        if (colony == null || !(colony.getWorld() instanceof ServerLevel level)) return;
        Churches.Info info = Churches.get(server).info(g.god);
        if (info == null) return;
        List<ICitizenData> present = new ArrayList<>();
        for (ICitizenData c : colony.getCitizenManager().getCitizens()) {
            if (!g.members.contains(c.getUUID())) continue;
            LivingEntity e = c.getEntity().orElse(null);
            if (arrived(e, g.temple)) present.add(c);
        }
        if (present.isEmpty()) return;
        for (ICitizenData c : present) c.getEntity().ifPresent(e ->
                level.sendParticles(ParticleTypes.ENCHANT, e.getX(), e.getY(1.2), e.getZ(), 16, 0.3, 0.4, 0.3, 0.6));

        String petition = g.petition, offering = "";
        String offered = "";
        if (info.isDark()) {
            LivingEntity victim = null;
            if (g.victim != null) {
                for (ICitizenData c : colony.getCitizenManager().getCitizens())
                    if (c.getUUID().equals(g.victim)) {
                        LivingEntity e = c.getEntity().orElse(null);
                        if (arrived(e, g.temple)) {
                            victim = e;
                            offered = Believers.originalName(server, c);
                        }
                    }
            }
            if (victim != null) {
                // um Beyonder sacrificado vale mais: o pedido de poder pode virar uma poção
                ColonistBeyonders.Info b = ColonistBeyonders.info(server, g.victim);
                int seq = b != null ? b.sequence() : de.jakob.lotm.util.BeyonderData.isBeyonder(victim) ? de.jakob.lotm.util.BeyonderData.getSequence(victim) : 10;
                offering = seq <= 9 ? ChurchPrayers.BEYONDER + seq : ChurchPrayers.HUMAN;
                ServerPlayer god = server.getPlayerList().getPlayer(g.god);
                SectRites.humanSacrifice(server, g.god, god, colony, victim);
                vanish(colony, g.victim, victim, level);
            } else {
                // sem pessoa (ou pedido comum): um animal de perto, ou uma oferenda
                List<Animal> animals = level.getEntitiesOfClass(Animal.class, new AABB(g.temple).inflate(24));
                offering = ChurchPrayers.ANIMAL;
                if (!animals.isEmpty()) {
                    Animal a = animals.get(RANDOM.nextInt(animals.size()));
                    offered = a.getName().getString();
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, a.getX(), a.getY(0.5), a.getZ(), 20, 0.3, 0.4, 0.3, 0.03);
                    a.hurt(a.damageSources().magic(), Float.MAX_VALUE);
                } else {
                    offered = Component.translatable("lotmcraft.sect.offering.items").getString();
                }
            }
            level.playSound(null, g.temple, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.2f, 0.7f);
        } else {
            // a Ordem: sem sacrifício; os presentes adiantam a digestão e o Deus ganha um reforço nas âncoras
            for (ICitizenData c : present) ColonistBeyonders.hasten(server, c.getUUID(), 1);
            ChurchPrayers.addAnchorBonus(server, g.god, Math.min(0.05f, 0.01f * present.size()));
            level.playSound(null, g.temple, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 0.7f);
        }
        // de quebra, a reunião atrai os desesperados por perto
        ChurchConversion.dailyRoll(server, colony, 2.0, present.get(0).getEntity().orElse(null), 24);

        if (!offering.isEmpty()) petition = petition + ":" + offering;
        ICitizenData speaker = present.get(0);
        LivingEntity body = speaker.getEntity().orElse(null);
        if (body != null) ChurchPrayers.pray(server, g.god, speaker, body, petition);
        ServerPlayer god = server.getPlayerList().getPlayer(g.god);
        if (god != null) {
            String key = info.isDark() ? "lotmcraft.sect.gathering.dark" : "lotmcraft.sect.gathering.order";
            god.sendSystemMessage(Component.translatable(key, present.size(), info.name(), offered,
                    ChurchPrayers.describe(petition), ChurchPrayers.cost(petition)).withColor(info.isDark() ? 0xC04070 : 0xB9A0FF));
        }
    }

    /** Chegou à reunião: sentado nos bancos (ou a caminho deles) ou perto do altar */
    private static boolean arrived(LivingEntity e, BlockPos temple) {
        if (e == null) return false;
        if (e instanceof EntityCitizen c && c.getCitizenAI().getState() == ChurchMass.MassState.MASS) return true;
        return e.blockPosition().closerThan(temple, 24);
    }

    /**
     * O sacrificado some sem deixar rastro: sem aviso de morte, sem sepultura e sem luto (a colônia não pode sair atrás da
     * Seita). O MineColonies só fica sabendo que o colono não está mais lá.
     */
    private static void vanish(IColony colony, UUID id, LivingEntity victim, ServerLevel level) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, victim.getX(), victim.getY(0.5), victim.getZ(), 40, 0.4, 0.6, 0.4, 0.04);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, victim.getX(), victim.getY(0.5), victim.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
        ICitizenData data = null;
        for (ICitizenData c : colony.getCitizenManager().getCitizens()) if (c.getUUID().equals(id)) data = c;
        if (data != null) colony.getCitizenManager().removeCivilian(data);
        if (!victim.isRemoved()) victim.discard();
    }

    private static IColony find(int id) {
        for (IColony c : IColonyManager.getInstance().getAllColonies()) if (c.getID() == id) return c;
        return null;
    }
}
