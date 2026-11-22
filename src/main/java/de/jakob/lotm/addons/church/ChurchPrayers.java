package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.datafixers.util.Pair;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.rituals.Believers;
import de.jakob.lotm.events.HonorificNamesEventHandler;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.playerMap.PendingPrayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * As orações da Igreja:
 * <ul>
 *   <li>Pedidos: um fiel ferido, com fome ou com a colônia atacada reza pedindo ajuda ao seu Deus. O pedido chega às
 *   Preces Pendentes (menu de Nomes Honoríficos). Atender custa espiritualidade e faz a fé crescer na colônia;
 *   ignorar três vezes seguidas faz o fiel perder a fé.</li>
 *   <li>Missa: às 18h, os fiéis que foram ao templo (ou moram nele) rezam juntos; reforça as âncoras do Deus até
 *   a missa do dia seguinte. A ida e a volta dos colonos ficam em {@link ChurchMass}.</li>
 *   <li>Prece no altar: um jogador agachado clica no altar com a mão vazia e reza ao Deus do templo.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class ChurchPrayers {
    private ChurchPrayers() {}

    public static final String HEAL = "heal", FOOD = "food", PROTECT = "protect", POWER = "power";
    /** O sacrifício que acompanha o pedido de uma Seita: "pedido:animal", "pedido:human" ou "pedido:beyonderN" (N = Sequência) */
    public static final String ANIMAL = "animal", HUMAN = "human", BEYONDER = "beyonder";

    /** "animal", "human" ou "beyonder" (sem a Sequência) */
    public static String offeringType(String petition) {
        String o = offeringOf(petition);
        return o.startsWith(BEYONDER) ? BEYONDER : o;
    }

    /** A Sequência do Beyonder sacrificado (10 se não foi um Beyonder) */
    public static int sacrificedSequence(String petition) {
        String o = offeringOf(petition);
        if (!o.startsWith(BEYONDER)) return 10;
        try { return Integer.parseInt(o.substring(BEYONDER.length())); } catch (NumberFormatException e) { return 10; }
    }

    public static String kindOf(String petition) {
        int i = petition.indexOf(':');
        return i < 0 ? petition : petition.substring(0, i);
    }

    public static String offeringOf(String petition) {
        int i = petition.indexOf(':');
        return i < 0 ? "" : petition.substring(i + 1);
    }

    /** Quanto de espiritualidade custa atender cada pedido; o sacrifício paga parte (animal) ou tudo (pessoa) */
    public static int cost(String petition) {
        int base = switch (kindOf(petition)) {
            case HEAL -> 120;
            case FOOD -> 80;
            case PROTECT -> 250;
            case POWER -> 300;
            default -> 0;
        };
        return switch (offeringType(petition)) {
            case HUMAN, BEYONDER -> 0;
            case ANIMAL -> base / 2;
            default -> base;
        };
    }

    /** "cura", ou "cura (sacrificaram um animal)" */
    public static Component describe(String petition) {
        Component what = Component.translatable("lotmcraft.church.petition." + kindOf(petition));
        String offering = offeringType(petition);
        return offering.isEmpty() ? what : Component.translatable("lotmcraft.sect.offering." + offering, what);
    }

    /** A hora da missa: 18h (o dia começa às 6); os fiéis vão ao templo desde as 17h (ChurchMass) */
    public static final long MASS_TIME = ChurchMass.MASS;
    private static final int CHECK_EVERY = 1200, PER_GOD = 8, IGNORES_TO_LEAVE = 3;
    private static final long PETITION_COOLDOWN = 24000, PETITION_EXPIRES = 24000;

    private static final Random RANDOM = new Random();
    /** citizen -> o tick do último pedido */
    private static final Map<UUID, Long> lastPetition = new HashMap<>();
    /** citizen -> (deus, tick do pedido) dos pedidos ainda sem resposta */
    private static final Map<UUID, Pair<UUID, Long>> open = new HashMap<>();
    /** citizen -> pedidos ignorados em seguida */
    private static final Map<UUID, Integer> ignored = new HashMap<>();
    /** colônia -> até que tick a fé está mais forte por um pedido atendido */
    private static final Map<Integer, Long> favor = new HashMap<>();
    /** deus -> (reforço das âncoras, até que dia vale) */
    private static final Map<UUID, Pair<Float, Long>> mass = new HashMap<>();
    /** jogador -> o tick da última prece no altar */
    private static final Map<UUID, Long> altarCooldown = new HashMap<>();
    private static long lastMassDay = -1;

    // ---------------------------------------------------------------- consultas para outras partes

    /** Soma um reforço às âncoras do Deus até o fim do dia seguinte (a reunião secreta da Seita) */
    public static void addAnchorBonus(MinecraftServer server, UUID god, float bonus) {
        long day = server.overworld().getDayTime() / 24000L;
        Pair<Float, Long> m = mass.get(god);
        float now = m != null && day <= m.getSecond() ? m.getFirst() : 0f;
        mass.put(god, new Pair<>(Math.min(0.10f, now + bonus), day + 1));
    }

    /** O reforço das âncoras deixado pela última missa (0 se já passou) */
    public static float massBonus(ServerPlayer god) {
        Pair<Float, Long> m = mass.get(god.getUUID());
        if (m == null) return 0f;
        long day = god.server.overworld().getDayTime() / 24000L;
        return day <= m.getSecond() ? m.getFirst() : 0f;
    }

    /** Pedidos atendidos fazem os colonos crerem com mais facilidade nos dias seguintes */
    public static double conversionMultiplier(IColony colony) {
        Long until = favor.get(colony.getID());
        return until != null && colony.getWorld().getGameTime() < until ? 1.5 : 1.0;
    }

    // ---------------------------------------------------------------- o tique do servidor

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long time = server.overworld().getDayTime();
        long day = time / 24000L;
        if (time % 24000L >= MASS_TIME && time % 24000L < ChurchMass.END && day != lastMassDay) {
            lastMassDay = day;
            holdMasses(server, day);
        }
        if (server.getTickCount() % CHECK_EVERY != 0) return;
        expire(server);
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) petitions(server, colony);
    }

    // ---------------------------------------------------------------- pedidos

    private static void petitions(MinecraftServer server, IColony colony) {
        boolean raided = colony.getRaiderManager().isRaided();
        long now = server.overworld().getGameTime();
        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            UUID id = citizen.getUUID();
            UUID god = Believers.godOfCitizen(server, id);
            if (god == null || open.containsKey(id)) continue;
            if (Believers.isSecret(server, god)) continue;   // a Seita e a Ordem pedem nas reuniões (SectGatherings)
            Long last = lastPetition.get(id);
            if (last != null && now - last < PETITION_COOLDOWN) continue;
            LivingEntity entity = citizen.getEntity().orElse(null);
            if (entity == null || !entity.isAlive()) continue;
            String petition = null;
            if (raided) petition = PROTECT;
            else if (entity.getHealth() < entity.getMaxHealth() * 0.6f) petition = HEAL;
            else if (citizen.getSaturation() < 4) petition = FOOD;
            if (petition == null || RANDOM.nextDouble() > 0.35) continue;
            if (HonorificNamesEventHandler.getPendingPrayers(god).size() >= PER_GOD) continue;
            pray(server, god, citizen, entity, petition);
            lastPetition.put(id, now);
            if (PROTECT.equals(petition)) break;   // um pedido de proteção por colônia
        }
    }

    static void pray(MinecraftServer server, UUID god, ICitizenData citizen, LivingEntity entity, String petition) {
        UUID id = citizen.getUUID();
        String name = Believers.originalName(server, citizen);
        HonorificNamesEventHandler.answerState.add(new Pair<>(god, id));
        HonorificNamesEventHandler.addPendingPrayer(god, new PendingPrayer(id, name, BeyonderData.getPathway(entity), BeyonderData.getSequence(entity),
                entity.getX(), entity.getY(), entity.getZ(), petition));
        open.put(id, new Pair<>(god, server.overworld().getGameTime()));
        if (entity.level() instanceof ServerLevel level)
            level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY(1.1), entity.getZ(), 8, 0.2, 0.3, 0.2, 0.02);
        ServerPlayer player = server.getPlayerList().getPlayer(god);
        if (player != null && !Believers.isSecret(server, god)) player.sendSystemMessage(Component.translatable("lotmcraft.church.petition.heard", name,
                describe(petition), cost(petition)).withColor(0xFFE29A));
    }

    /** Pedidos sem resposta por um dia contam como ignorados */
    private static void expire(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (var it = open.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now - e.getValue().getSecond() < PETITION_EXPIRES) continue;
            it.remove();
            UUID god = e.getValue().getFirst();
            HonorificNamesEventHandler.answerState.remove(new Pair<>(god, e.getKey()));
            HonorificNamesEventHandler.removePendingPrayer(god, e.getKey());
            ignore(server, god, e.getKey());
        }
    }

    /** A resposta do Deus a um pedido (pelo menu de Nomes Honoríficos) */
    public static void answer(ServerPlayer god, PendingPrayer prayer, boolean grant) {
        MinecraftServer server = god.server;
        UUID id = prayer.senderUUID();
        ICitizenData citizen = find(id);
        if (grant) {
            int cost = cost(prayer.petition());
            if (cost > 0 && BeyonderData.getSpirituality(god) < cost) {
                god.sendSystemMessage(Component.translatable("lotmcraft.church.petition.no_spirituality", cost).withColor(0xFF7A7A));
                return;   // o pedido continua pendente
            }
            if (cost > 0) BeyonderData.reduceSpirituality(god, cost);
        }
        open.remove(id);
        HonorificNamesEventHandler.answerState.remove(new Pair<>(god.getUUID(), id));
        HonorificNamesEventHandler.removePendingPrayer(god.getUUID(), id);
        if (citizen == null) return;
        String name = Believers.originalName(server, citizen);
        if (!grant) {
            ignore(server, god.getUUID(), id);
            return;
        }
        ignored.remove(id);
        IColony colony = citizen.getColony();
        LivingEntity entity = citizen.getEntity().orElse(null);
        switch (kindOf(prayer.petition())) {
            case HEAL -> {
                if (entity != null) {
                    entity.setHealth(entity.getMaxHealth());
                    entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 1));
                }
            }
            case FOOD -> citizen.increaseSaturation(20);
            case POWER -> {
                // o poder pedido: os membros adiantam a digestão
                for (UUID member : Believers.members(server, god.getUUID())) ColonistBeyonders.hasten(server, member, 2);
                // com um Beyonder sacrificado, a bênção vem como poção no altar: o Deus não aparece, e o membro a bebe
                if (BEYONDER.equals(offeringType(prayer.petition()))) {
                    Churches.Info info = Churches.get(server).info(god.getUUID());
                    int seq = ColonistBeyonders.bless(server, citizen, info == null ? BeyonderData.getPathway(god) : info.pathway(),
                            sacrificedSequence(prayer.petition()), god.getUUID());
                    if (seq > 0) {
                        if (entity != null && entity.level() instanceof ServerLevel level) {
                            level.sendParticles(ParticleTypes.WITCH, entity.getX(), entity.getY(1.0), entity.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
                            level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 1f, 0.8f);
                        }
                        god.sendSystemMessage(Component.translatable("lotmcraft.sect.blessing.potion", name, seq).withColor(0xD8B4FF));
                    } else {
                        god.sendSystemMessage(Component.translatable("lotmcraft.sect.blessing.too_weak", name).withColor(0xB0A0C0));
                    }
                }
            }
            case PROTECT -> {
                for (ICitizenData other : colony.getCitizenManager().getCitizens()) other.getEntity().ifPresent(e -> {
                    e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 180, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 180, 0));
                });
            }
            default -> { }
        }
        if (entity != null && entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY(1.0), entity.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
            level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1f, 1.4f);
        }
        favor.put(colony.getID(), colony.getWorld().getGameTime() + 24000L * 3);
        god.sendSystemMessage(Component.translatable("lotmcraft.church.petition.granted", name,
                Component.translatable("lotmcraft.church.petition." + kindOf(prayer.petition()))).withColor(0xFFE29A));
    }

    private static void ignore(MinecraftServer server, UUID god, UUID citizenId) {
        int n = ignored.merge(citizenId, 1, Integer::sum);
        if (n < IGNORES_TO_LEAVE) return;
        ignored.remove(citizenId);
        ICitizenData citizen = find(citizenId);
        if (citizen == null || !god.equals(Believers.godOfCitizen(server, citizenId))) return;
        String name = Believers.originalName(server, citizen);
        Believers.leave(server, citizen);
        ServerPlayer player = server.getPlayerList().getPlayer(god);
        if (player != null) player.sendSystemMessage(Component.translatable("lotmcraft.church.petition.lost_faith", name).withColor(0xFF7A7A));
    }

    private static ICitizenData find(UUID id) {
        for (IColony colony : IColonyManager.getInstance().getAllColonies())
            for (ICitizenData c : colony.getCitizenManager().getCitizens()) if (c.getUUID().equals(id)) return c;
        return null;
    }

    // ---------------------------------------------------------------- missa

    private static void holdMasses(MinecraftServer server, long day) {
        Map<UUID, Float> bonus = new HashMap<>();
        Map<UUID, int[]> report = new HashMap<>();
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            for (ChurchBuilding temple : ChurchConversion.temples(colony)) {
                UUID god = temple.getGod();
                if (Believers.isSecret(server, god)) continue;
                BlockPos at = temple.getPosition();
                int attendees = 0;
                for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
                    if (!god.equals(Believers.godOfCitizen(server, citizen.getUUID()))) continue;
                    LivingEntity entity = citizen.getEntity().orElse(null);
                    if (!ChurchMass.isAttending(citizen, temple)) continue;
                    attendees++;
                    if (entity != null && entity.level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY(1.2), entity.getZ(), 20, 0.3, 0.4, 0.3, 0.6);
                        entity.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, at.getCenter());
                    }
                }
                if (attendees == 0) continue;
                if (colony.getWorld() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5, 60, 1.5, 2, 1.5, 0.03);
                    level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 0.8f);
                }
                float b = Math.min(0.10f, 0.002f * attendees * temple.getBuildingLevel());
                bonus.merge(god, b, Float::sum);
                int[] r = report.computeIfAbsent(god, k -> new int[2]);
                r[0]++;
                r[1] += attendees;
            }
        }
        mass.entrySet().removeIf(e -> e.getValue().getSecond() < day);
        for (var e : bonus.entrySet()) {
            float b = Math.min(0.10f, e.getValue());
            mass.put(e.getKey(), new Pair<>(b, day));
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            int[] r = report.get(e.getKey());
            if (player != null) player.sendSystemMessage(Component.translatable("lotmcraft.church.mass.held", r[1], r[0],
                    String.format("%.1f", b * 100)).withColor(0xFFE29A));
        }
    }

    // ---------------------------------------------------------------- prece no altar

    /** Um jogador reza ao Deus do templo pelo altar; devolve false se não pôde rezar agora */
    public static boolean prayAtAltar(ServerPlayer player, ChurchBuilding temple) {
        UUID god = temple.getGod();
        if (god == null) {
            player.displayClientMessage(Component.translatable("lotmcraft.church.altar.no_god").withColor(0xFFB070), true);
            return false;
        }
        if (god.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("lotmcraft.own_praying").withColor(0xFFB070), true);
            return false;
        }
        long now = player.server.overworld().getGameTime();
        Long last = altarCooldown.get(player.getUUID());
        if (last != null && now - last < 20 * 60) {
            player.displayClientMessage(Component.translatable("lotmcraft.church.altar.prayed_recently").withColor(0xFFB070), true);
            return false;
        }
        altarCooldown.put(player.getUUID(), now);
        var key = new Pair<>(god, player.getUUID());
        if (!HonorificNamesEventHandler.answerState.contains(key)) {
            HonorificNamesEventHandler.answerState.add(key);
            HonorificNamesEventHandler.addPendingPrayer(god, new PendingPrayer(player.getUUID(), player.getName().getString(),
                    BeyonderData.getPathway(player), BeyonderData.getSequence(player), player.getX(), player.getY(), player.getZ()));
        }
        Player target = player.server.getPlayerList().getPlayer(god);
        if (target != null) target.sendSystemMessage(HonorificNamesEventHandler.formNotification(player));
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(1.0), player.getZ(), 15, 0.3, 0.5, 0.3, 0.03);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("lotmcraft.church.altar.prayed", Churches.godName(player.server, god)).withColor(0xFFE29A), true);
        return true;
    }
}
