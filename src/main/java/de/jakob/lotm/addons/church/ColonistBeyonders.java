package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.colonies.Colonists;
import de.jakob.lotm.addons.rituals.Believers;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.potions.BeyonderPotion;
import de.jakob.lotm.entity.ModEntities;
import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Colonos Beyonders. O Deus dá uma poção a um fiel (clique direito no colono com ela): Igreja aceita qualquer Caminho,
 * Seita só o Caminho do Deus. O Caminho e a Sequência ficam guardados aqui, pela identidade do colono, porque o
 * MineColonies recria a entidade. Eles só lutam contra monstros, nunca ferem pessoas nem quebram blocos, e não usam as
 * habilidades de calamidade. Beber antes de digerir (ou subir alto demais) pode fazê-los perder o controle: o Deus recebe
 * um pedido de socorro e tem 60 segundos para salvar o fiel, gastando espiritualidade; se não salvar, ele vira um monstro.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class ColonistBeyonders extends SavedData {
    /** A Sequência mais alta que um colono pode alcançar (Anjo); a 0 é só para jogadores */
    public static final int HIGHEST = 1;
    private static final int RESCUE_TICKS = 20 * 60;

    public record Info(String pathway, int sequence, long potionDay) {}

    private final Map<UUID, Info> colonists = new HashMap<>();

    /** Pedidos de socorro em aberto: colono -> (Deus, quando acaba, a poção que ele bebeu) */
    private record Rescue(UUID god, long deadline, String pathway, int sequence, String name) {}
    private static final Map<UUID, Rescue> RESCUES = new HashMap<>();

    public static ColonistBeyonders get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ColonistBeyonders::new, ColonistBeyonders::load, null), "lotmcraft_colonist_beyonders");
    }

    public static Info info(MinecraftServer server, UUID citizen) {
        return get(server).colonists.get(citizen);
    }

    /** Dias de digestão da poção atual antes de a próxima ser segura */
    private static int digestDays(int sequence) {
        return switch (sequence) {
            case 9, 8 -> 1;
            case 7, 6 -> 2;
            case 5 -> 3;
            case 4 -> 4;
            case 3 -> 5;
            default -> 7;
        };
    }

    /** A Sequência que um cargo pede (9 = qualquer Beyonder; 10 = não precisa ser Beyonder) */
    public static int requiredSequence(String role) {
        return switch (role) {
            case "priest", "acolyte" -> 9;
            case "sect_priest" -> 7;
            case "deacon", "bishop" -> 5;
            case "archbishop", "high_priest" -> 4;
            case "pope", "leader" -> 2;
            default -> 10;
        };
    }

    // ---------------------------------------------------------------- aplicar ao colono

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractEntityCitizen citizen)) return;
        ICitizenData data = Colonists.data(citizen);
        if (data == null || event.getLevel().getServer() == null) return;
        Info info = info(event.getLevel().getServer(), data.getUUID());
        if (info != null) apply(citizen, info);
    }

    private static void apply(LivingEntity entity, Info info) {
        BeyonderData.setBeyonder(entity, info.pathway(), info.sequence(), true, false, true, false, true, false);
    }

    /** A característica de um colono Beyonder foi arrancada: ele cai uma Sequência (na 9, deixa de ser Beyonder) */
    public static void regress(MinecraftServer server, LivingEntity entity) {
        if (!(entity instanceof AbstractEntityCitizen) || server == null) return;
        ICitizenData citizen = Colonists.data(entity);
        if (citizen == null) return;
        ColonistBeyonders data = get(server);
        Info info = data.colonists.get(citizen.getUUID());
        if (info == null) return;
        if (info.sequence() >= 9) data.colonists.remove(citizen.getUUID());
        else data.colonists.put(citizen.getUUID(), new Info(info.pathway(), info.sequence() + 1, info.potionDay()));
        data.setDirty();
    }

    // ---------------------------------------------------------------- a poção

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BeyonderPotion potion) || !Colonists.isAnyCitizen(event.getTarget())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof AbstractEntityCitizen entity)) return;
        ICitizenData citizen = Colonists.data(entity);
        if (citizen == null) return;
        MinecraftServer server = player.server;
        UUID id = citizen.getUUID();
        if (!player.getUUID().equals(Believers.godOfCitizen(server, id))) {
            say(player, "lotmcraft.colonist_beyonder.not_your_believer", 0xFF7A7A);
            return;
        }
        if (RESCUES.containsKey(id)) return;
        Churches.Info church = Churches.get(server).info(player.getUUID());
        String pathway = potion.getPathway();
        int sequence = potion.getSequence();
        if (church != null && Churches.SECT.equals(church.kind()) && !pathway.equals(church.pathway())) {
            say(player, "lotmcraft.colonist_beyonder.sect_pathway", 0xFF7A7A, Component.translatable("lotm.pathway." + church.pathway()));
            return;
        }
        if (sequence < HIGHEST) {
            say(player, "lotmcraft.colonist_beyonder.too_high", 0xFF7A7A);
            return;
        }
        // Santos (4 e 3) e Anjos (2 e 1) só onde o Deus tem a Catedral Sagrada
        if (sequence <= 4 && !ChurchBuilding.hasSacredCathedral(citizen.getColony(), player.getUUID())) {
            say(player, "lotmcraft.colonist_beyonder.needs_sacred", 0xFF7A7A);
            return;
        }
        ColonistBeyonders data = get(server);
        Info current = data.colonists.get(id);
        long day = server.overworld().getDayTime() / 24000L;
        boolean risky;
        if (current == null) {
            if (sequence != 9) { say(player, "lotmcraft.colonist_beyonder.start_at_9", 0xFF7A7A); return; }
            risky = false;
        } else {
            if (!pathway.equals(current.pathway()) || sequence != current.sequence() - 1) {
                say(player, "lotmcraft.colonist_beyonder.next_only", 0xFF7A7A, current.sequence() - 1, Component.translatable("lotm.pathway." + current.pathway()));
                return;
            }
            risky = day - current.potionDay() < digestDays(current.sequence());
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        entity.playSound(SoundEvents.GENERIC_DRINK, 1f, 1f);

        // risco: beber antes de digerir, ou subir para semideus/anjo
        Random random = new Random();
        double risk = risky ? 0.5 : sequence <= 4 ? 0.15 : 0.0;
        if (random.nextDouble() < risk) {
            startRescue(server, player.getUUID(), citizen, entity, pathway, sequence);
            return;
        }
        advance(server, citizen, entity, pathway, sequence);
        say(player, "lotmcraft.colonist_beyonder.drank", 0xFFE29A, Believers.originalName(server, citizen), sequence, Component.translatable("lotm.pathway." + pathway));
    }

    private static void advance(MinecraftServer server, ICitizenData citizen, LivingEntity entity, String pathway, int sequence) {
        ColonistBeyonders data = get(server);
        Info info = new Info(pathway, sequence, server.overworld().getDayTime() / 24000L);
        data.colonists.put(citizen.getUUID(), info);
        data.setDirty();
        if (entity != null) {
            apply(entity, info);
            if (entity.level() instanceof ServerLevel level)
                level.sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY(1.0), entity.getZ(), 40, 0.4, 0.6, 0.4, 0.5);
        }
    }

    // ---------------------------------------------------------------- perder o controle e o socorro do Deus

    private static void startRescue(MinecraftServer server, UUID god, ICitizenData citizen, LivingEntity entity, String pathway, int sequence) {
        String name = Believers.originalName(server, citizen);
        RESCUES.put(citizen.getUUID(), new Rescue(god, server.getTickCount() + RESCUE_TICKS, pathway, sequence, name));
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RESCUE_TICKS, 3));
        entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, RESCUE_TICKS, 0));
        ServerPlayer godPlayer = server.getPlayerList().getPlayer(god);
        if (godPlayer != null && canHear(godPlayer, entity)) {
            Component button = Component.translatable("lotmcraft.colonist_beyonder.save_button").withStyle(style -> style
                    .withColor(ChatFormatting.GOLD).withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lotmigreja salvar " + citizen.getUUID()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("lotmcraft.colonist_beyonder.save_hover", rescueCost(sequence)))));
            godPlayer.sendSystemMessage(Component.translatable("lotmcraft.colonist_beyonder.losing_control", name).withColor(0xFF9A6A).append(" ").append(button));
            godPlayer.playNotifySound(SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
        }
    }

    /** Sem o Reino Divino, o Deus só ouve o socorro na mesma dimensão (o Reino amplia isso nas orações) */
    private static boolean canHear(ServerPlayer god, Entity colonist) {
        return god.level() == colonist.level();
    }

    private static int rescueCost(int sequence) {
        return 150 * (10 - sequence);
    }

    private static void save(ServerPlayer god, UUID citizenId) {
        Rescue rescue = RESCUES.get(citizenId);
        if (rescue == null || !rescue.god().equals(god.getUUID())) {
            god.displayClientMessage(Component.translatable("lotmcraft.colonist_beyonder.no_rescue").withColor(0xFF7A7A), true);
            return;
        }
        ICitizenData citizen = Colonists.find(citizenId) instanceof ICitizenData c ? c : null;
        LivingEntity entity = citizen == null ? null : citizen.getEntity().orElse(null);
        if (citizen == null || entity == null || !canHear(god, entity)) {
            god.displayClientMessage(Component.translatable("lotmcraft.colonist_beyonder.cannot_reach").withColor(0xFF7A7A), true);
            return;
        }
        int cost = rescueCost(rescue.sequence());
        if (BeyonderData.getSpirituality(god) < cost) {
            god.displayClientMessage(Component.translatable("lotmcraft.colonist_beyonder.no_spirituality", cost).withColor(0xFF7A7A), true);
            return;
        }
        BeyonderData.reduceSpirituality(god, cost);
        RESCUES.remove(citizenId);
        entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        entity.removeEffect(MobEffects.CONFUSION);
        advance(god.server, citizen, entity, rescue.pathway(), rescue.sequence());
        if (entity.level() instanceof ServerLevel level)
            level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY(1.0), entity.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
        god.sendSystemMessage(Component.translatable("lotmcraft.colonist_beyonder.saved", rescue.name()).withColor(0xFFE29A));
    }

    private static void loseControl(MinecraftServer server, UUID citizenId, Rescue rescue) {
        ICitizenData citizen = Colonists.find(citizenId) instanceof ICitizenData c ? c : null;
        ServerPlayer god = server.getPlayerList().getPlayer(rescue.god());
        if (citizen == null) return;
        LivingEntity entity = citizen.getEntity().orElse(null);
        if (entity != null && entity.level() instanceof ServerLevel level) {
            BeyonderNPCEntity monster = new BeyonderNPCEntity(ModEntities.BEYONDER_NPC.get(), level, true, rescue.pathway(), rescue.sequence());
            monster.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), 0);
            monster.setCustomName(Component.translatable("lotmcraft.colonist_beyonder.lost_name", rescue.name()));
            level.addFreshEntity(monster);
            level.sendParticles(ParticleTypes.SQUID_INK, entity.getX(), entity.getY(1.0), entity.getZ(), 60, 0.5, 0.8, 0.5, 0.05);
            level.playSound(null, entity.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1f, 1.2f);
        }
        ColonistBeyonders data = get(server);
        data.colonists.remove(citizenId);
        data.setDirty();
        citizen.getColony().getCitizenManager().removeCivilian(citizen);
        if (entity != null) entity.discard();
        if (god != null) god.sendSystemMessage(Component.translatable("lotmcraft.colonist_beyonder.lost", rescue.name()).withColor(0xFF6A6A));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lotmigreja")
                .then(Commands.literal("salvar").then(Commands.argument("colono", StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    try { save(player, UUID.fromString(StringArgumentType.getString(ctx, "colono"))); } catch (IllegalArgumentException ignored) { }
                    return 1;
                }))));
    }

    // ---------------------------------------------------------------- combate seguro

    /** Habilidades grandes demais para usar dentro de uma colônia */
    private static final String[] CALAMITIES = {"tsunami", "earthquake", "hurricane", "storm", "wrath", "calamity", "tornado", "thunder_god", "divine_kingdom",
            "domain", "plague", "disease", "disaster", "collapse", "apocalypse", "return_to_earth", "world", "black_hole", "fragmentation", "psyche",
            "underworld", "flaring_sun", "tide", "authority", "starfall", "meteor", "inferno", "ice_age", "vortex", "seal"};

    private static boolean safe(Ability ability) {
        if (!ability.canBeUsedByNPC || ability.lowestSequenceUsable() <= 3) return false;
        String id = ability.getId();
        for (String bad : CALAMITIES) if (id.contains(bad)) return false;
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        // socorros que venceram
        if (!RESCUES.isEmpty()) {
            Iterator<Map.Entry<UUID, Rescue>> it = RESCUES.entrySet().iterator();
            List<Map.Entry<UUID, Rescue>> lost = new ArrayList<>();
            while (it.hasNext()) {
                var entry = it.next();
                if (server.getTickCount() >= entry.getValue().deadline()) { lost.add(entry); it.remove(); }
            }
            lost.forEach(e -> loseControl(server, e.getKey(), e.getValue()));
        }
        if (server.getTickCount() % 15 != 0) return;
        ColonistBeyonders data = get(server);
        if (data.colonists.isEmpty()) return;
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
                Info info = data.colonists.get(citizen.getUUID());
                if (info == null || RESCUES.containsKey(citizen.getUUID())) continue;
                AbstractEntityCitizen entity = citizen.getEntity().orElse(null);
                if (entity == null || !(entity.level() instanceof ServerLevel level)) continue;
                if (!BeyonderData.isBeyonder(entity)) apply(entity, info);
                fight(level, entity, info);
            }
        }
    }

    /** Ataca o monstro mais perto (até 16 blocos) com uma habilidade segura */
    private static void fight(ServerLevel level, AbstractEntityCitizen entity, Info info) {
        LivingEntity target = null;
        double best = 16 * 16;
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(16), e -> e instanceof Enemy && e.isAlive())) {
            double d = mob.distanceToSqr(entity);
            if (d < best) { best = d; target = mob; }
        }
        if (target == null) return;
        LivingEntity previous = entity.getTarget();
        entity.setTarget(target);
        entity.getLookControl().setLookAt(target);
        entity.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        List<Ability> abilities = new ArrayList<>();
        for (Ability a : LOTMCraft.abilityHandler.getByPathwayAndSequence(info.pathway(), info.sequence())) {
            if (safe(a) && a.canUse(entity)) abilities.add(a);
        }
        if (!abilities.isEmpty() && level.random.nextInt(3) == 0) {
            abilities.get(level.random.nextInt(abilities.size())).useAbility(level, entity);
        }
        entity.setTarget(previous);
    }

    /** Colono Beyonder morto solta a característica da Sequência dele, como qualquer Beyonder */
    @SubscribeEvent
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AbstractEntityCitizen entity) || !(entity.level() instanceof ServerLevel level)) return;
        ICitizenData citizen = Colonists.data(entity);
        if (citizen == null) return;
        ColonistBeyonders data = get(level.getServer());
        Info info = data.colonists.remove(citizen.getUUID());
        if (info == null) return;
        data.setDirty();
        RESCUES.remove(citizen.getUUID());
        var characteristic = de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItemHandler.selectCharacteristicOfPathwayAndSequence(info.pathway(), info.sequence());
        if (characteristic == null) return;
        ItemStack stack = new ItemStack(characteristic);
        // os outros colonos recolhem tudo o que cai no chão: quem matou (se foi um jogador) já leva a característica
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player killer) {
            if (!killer.getInventory().add(stack)) killer.drop(stack, false);
        } else {
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack));
        }
    }

    /** Colono Beyonder só fere monstros: nunca pessoas, colonos, animais ou aldeões */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof AbstractEntityCitizen attacker) || event.getEntity().level().isClientSide()) return;
        if (!BeyonderData.isBeyonder(attacker)) return;
        if (!(event.getEntity() instanceof Enemy)) event.setCanceled(true);
    }

    // ---------------------------------------------------------------- mensagens e dados

    private static void say(ServerPlayer player, String key, int color, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withColor(color), true);
    }

    static ColonistBeyonders load(CompoundTag tag, HolderLookup.Provider provider) {
        ColonistBeyonders data = new ColonistBeyonders();
        CompoundTag list = tag.getCompound("Colonists");
        for (String key : list.getAllKeys()) {
            CompoundTag c = list.getCompound(key);
            data.colonists.put(UUID.fromString(key), new Info(c.getString("Pathway"), c.getInt("Sequence"), c.getLong("Day")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag list = new CompoundTag();
        colonists.forEach((id, info) -> {
            CompoundTag c = new CompoundTag();
            c.putString("Pathway", info.pathway());
            c.putInt("Sequence", info.sequence());
            c.putLong("Day", info.potionDay());
            list.put(id.toString(), c);
        });
        tag.put("Colonists", list);
        return tag;
    }
}
