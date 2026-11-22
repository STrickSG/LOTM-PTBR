package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.rituals.Believers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A conversão por vontade própria: uma vez por dia, cada colono de uma colônia com templo pode decidir entrar na Igreja
 * (ou Seita) desse templo. Ajudam a felicidade dele, quantos vizinhos já creem, o clero que mora ali e o nível do templo;
 * a pregação do Deus no altar triplica a chance de quem está perto. Quem foi ferido pelo Deus nunca entra, e quem já
 * segue alguém não troca. Numa Seita a chance é menor (ela é escondida); a Seita da Bruxa só aceita mulheres.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class ChurchConversion {
    private ChurchConversion() {}

    private static final Map<Integer, Long> LAST_DAY = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 200 != 0) return;
        long day = server.overworld().getDayTime() / 24000L;
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            Long last = LAST_DAY.put(colony.getID(), day);
            if (last == null || last == day) continue; // primeiro contato: só anota o dia
            dailyRoll(server, colony, 1.0, null, 0);
            SectRites.daily(server, colony);
        }
    }

    /** Os templos consagrados e já construídos (nível 1 ou mais) desta colônia */
    public static List<ChurchBuilding> temples(IColony colony) {
        List<ChurchBuilding> list = new ArrayList<>();
        for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
            if (building instanceof ChurchBuilding temple && temple.getGod() != null && temple.getBuildingLevel() >= 1) list.add(temple);
        }
        return list;
    }

    /**
     * Uma rodada de conversão. multiplier > 1 é a pregação; se near != null, só os colonos a até radius blocos dele.
     * Devolve quantos se converteram.
     */
    public static int dailyRoll(MinecraftServer server, IColony colony, double multiplier, Entity near, double radius) {
        List<ChurchBuilding> temples = temples(colony);
        if (temples.isEmpty()) return 0;
        RandomSource random = colony.getWorld().getRandom();
        Churches churches = Churches.get(server);
        List<ICitizenData> citizens = new ArrayList<>(colony.getCitizenManager().getCitizens());
        int converted = 0;
        for (ICitizenData citizen : citizens) {
            if (Believers.godOfCitizen(server, citizen.getUUID()) != null) continue;
            if (near != null) {
                Entity e = citizen.getEntity().orElse(null);
                if (e == null || e.distanceTo(near) > radius) continue;
            }
            java.util.Collections.shuffle(temples, new java.util.Random(random.nextLong()));
            for (ChurchBuilding temple : temples) {
                UUID god = temple.getGod();
                Churches.Info info = churches.info(god);
                if (info == null || Believers.isWronged(server, citizen.getUUID(), god)) continue;
                boolean sect = info.isSect();
                if (sect && "demoness".equals(info.pathway()) && !citizen.isFemale()) continue;
                // a Seita só aceita Beyonders do próprio caminho; a Ordem, também dos caminhos vizinhos
                if (sect) {
                    ColonistBeyonders.Info b = ColonistBeyonders.info(server, citizen.getUUID());
                    if (b != null && !info.accepts(b.pathway())) continue;
                }
                double chance = chance(server, colony, citizen, temple, god, sect) * multiplier;
                if (random.nextDouble() >= chance) continue;
                if (Believers.join(server, god, Churches.godName(server, god), citizen)) {
                    converted++;
                    citizen.getEntity().ifPresent(e -> {
                        if (e.level() instanceof ServerLevel level)
                            level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY(1.1), e.getZ(), 14, 0.3, 0.5, 0.3, 0.03);
                    });
                }
                break;
            }
        }
        return converted;
    }

    private static double chance(MinecraftServer server, IColony colony, ICitizenData citizen, ChurchBuilding temple, UUID god, boolean sect) {
        double happiness = citizen.getCitizenHappinessHandler().getHappiness(colony, citizen);
        int total = Math.max(1, colony.getCitizenManager().getCitizens().size());
        int members = 0, clergy = 0;
        for (ICitizenData other : colony.getCitizenManager().getCitizens()) {
            if (!god.equals(Believers.godOfCitizen(server, other.getUUID()))) continue;
            members++;
            if (ChurchRoles.isClergy(Believers.roleOf(server, other.getUUID()))) clergy++;
        }
        double h = Math.max(0, Math.min(10, happiness)) / 10.0;
        if (sect) {
            // a Seita não prega em público: atrai os infelizes e os desesperados, e os membros puxam os conhecidos
            return (0.02 + 0.12 * (1 - h) + 0.15 * members / total + 0.02 * Math.min(5, clergy) + 0.01 * temple.getBuildingLevel())
                    * SectRites.fervor(colony);
        }
        double chance = 0.04
                + 0.10 * h
                + 0.15 * members / total
                + 0.03 * Math.min(5, clergy)
                + 0.02 * temple.getBuildingLevel();
        return chance * ChurchPrayers.conversionMultiplier(colony);
    }
}
