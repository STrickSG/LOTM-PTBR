package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.colonies.Colonists;
import de.jakob.lotm.addons.rituals.Believers;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * O que é próprio das organizações escondidas:
 * <ul>
 *   <li>Sacrifício de uma pessoa (nas reuniões da Seita, {@link SectGatherings}): o Deus ganha digestão e espiritualidade,
 *   os membros Beyonders adiantam a digestão e a Seita fica em fervor por 3 dias (recruta mais, mas chama atenção).</li>
 *   <li>Conflito com a Igreja: na mesma colônia, o clero da Igreja descobre membros escondidos (que abandonam a
 *   organização), e os membros escondidos corrompem fiéis infelizes da Igreja.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class SectRites {
    private SectRites() {}

    private static final Random RANDOM = new Random();
    /** colônia -> até que tick dura o fervor depois de um sacrifício */
    private static final Map<Integer, Long> FERVOR = new HashMap<>();
    private static final double ALTAR_RADIUS = 10;

    /** O fervor de um sacrifício recente deixa a Seita recrutar mais */
    public static double fervor(IColony colony) {
        return inFervor(colony) ? 1.5 : 1.0;
    }

    private static boolean inFervor(IColony colony) {
        Long until = FERVOR.get(colony.getID());
        return until != null && colony.getWorld().getGameTime() < until;
    }

    // ---------------------------------------------------------------- sacrifício

    /** Um colono sacrificado no altar numa reunião da Seita: o Deus avança a digestão, os membros também, e vem o fervor */
    public static void humanSacrifice(MinecraftServer server, UUID godId, ServerPlayer god, IColony colony, LivingEntity victim) {
        if (god != null && BeyonderData.isBeyonder(god)) {
            BeyonderData.setDigestionProgress(god, Math.min(1f, BeyonderData.getDigestionProgress(god) + 0.10f));
            BeyonderData.setSpirituality(god, BeyonderData.getMaxSpirituality(god));
        }
        for (UUID member : Believers.members(server, godId)) ColonistBeyonders.hasten(server, member, 2);
        FERVOR.put(colony.getID(), colony.getWorld().getGameTime() + 24000L * 3);
    }

    // ---------------------------------------------------------------- conflito com a Igreja

    /** Uma vez por dia em cada colônia onde há templos de uma Igreja e de uma organização escondida */
    public static void daily(MinecraftServer server, IColony colony) {
        List<ChurchBuilding> temples = ChurchConversion.temples(colony);
        Churches churches = Churches.get(server);
        List<UUID> open = new ArrayList<>(), hidden = new ArrayList<>();
        for (ChurchBuilding t : temples) {
            Churches.Info info = churches.info(t.getGod());
            if (info == null) continue;
            List<UUID> list = info.isSect() ? hidden : open;
            if (!list.contains(t.getGod())) list.add(t.getGod());
        }
        if (open.isEmpty() || hidden.isEmpty()) return;
        List<ICitizenData> citizens = colony.getCitizenManager().getCitizens();
        boolean noisy = inFervor(colony);
        for (UUID church : open) {
            // o clero da Igreja descobre membros escondidos
            for (ICitizenData priest : citizens) {
                if (!church.equals(Believers.godOfCitizen(server, priest.getUUID()))) continue;
                if (!ChurchRoles.isClergy(Believers.roleOf(server, priest.getUUID()))) continue;
                if (RANDOM.nextDouble() >= (noisy ? 0.12 : 0.04)) continue;
                List<ICitizenData> suspects = new ArrayList<>();
                for (ICitizenData c : citizens) {
                    UUID g = Believers.godOfCitizen(server, c.getUUID());
                    if (g != null && hidden.contains(g)) suspects.add(c);
                }
                if (suspects.isEmpty()) break;
                ICitizenData found = suspects.get(RANDOM.nextInt(suspects.size()));
                UUID sectGod = Believers.godOfCitizen(server, found.getUUID());
                String name = Believers.originalName(server, found);
                String sectName = churches.info(sectGod).name();
                Believers.leave(server, found);
                tell(server, church, "lotmcraft.sect.exposed.church", 0xFFE29A, Believers.originalName(server, priest), name, sectName);
                tell(server, sectGod, "lotmcraft.sect.exposed.sect", 0xFF7A7A, name);
            }
        }
        for (UUID sectGod : hidden) {
            Churches.Info info = churches.info(sectGod);
            // os membros escondidos corrompem os fiéis infelizes da Igreja
            for (ICitizenData member : citizens) {
                if (!sectGod.equals(Believers.godOfCitizen(server, member.getUUID()))) continue;
                if (RANDOM.nextDouble() >= 0.03) continue;
                List<ICitizenData> targets = new ArrayList<>();
                for (ICitizenData c : citizens) {
                    UUID g = Believers.godOfCitizen(server, c.getUUID());
                    if (g == null || !open.contains(g) || ChurchRoles.isClergy(Believers.roleOf(server, c.getUUID()))) continue;
                    if (c.getCitizenHappinessHandler().getHappiness(colony, c) >= 7) continue;
                    ColonistBeyonders.Info b = ColonistBeyonders.info(server, c.getUUID());
                    if (b != null && !info.accepts(b.pathway())) continue;
                    targets.add(c);
                }
                if (targets.isEmpty()) break;
                ICitizenData turned = targets.get(RANDOM.nextInt(targets.size()));
                UUID oldGod = Believers.godOfCitizen(server, turned.getUUID());
                Believers.leave(server, turned);
                if (Believers.join(server, sectGod, Churches.godName(server, sectGod), turned)) {
                    tell(server, sectGod, "lotmcraft.sect.corrupted", 0xD8B4FF, Believers.originalName(server, turned));
                    tell(server, oldGod, "lotmcraft.sect.lost_to_sect", 0xFF7A7A, Believers.originalName(server, turned));
                }
            }
        }
    }

    private static void tell(MinecraftServer server, UUID who, String key, int color, Object... args) {
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p != null) p.sendSystemMessage(Component.translatable(key, args).withColor(color));
    }
}
