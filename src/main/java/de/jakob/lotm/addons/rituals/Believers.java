package de.jakob.lotm.addons.rituals;

import com.minecolonies.api.colony.ICitizenData;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.colonies.Colonists;
import de.jakob.lotm.item.ModItems;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fiéis: colonos do MineColonies que adoram um jogador (de qualquer Caminho), com o título "Fiel de [jogador]" no nome.
 * Cada colono só pode adorar uma pessoa, e quem já foi ferido por você nunca vira seu fiel.
 * Só Beyonders de Sequência 3 ou maior (3, 2, 1, 0) podem reunir fiéis.
 * O ritual do Anjo Branco (Sol, Sequência 1) pede 100 fiéis.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class Believers {
    public static final int NEEDED = 100;
    /** Sequência mínima para reunir fiéis: 3 ou maior (número menor) */
    public static final int MAX_SEQUENCE = 3;
    private static final String BELIEVER_OF = "LotmBelieverOf", OLD_NAME = "LotmNameBeforeFaith", WRONGED_BY = "LotmWrongedBy", SELF = "LotmSelfBeliever";
    private static final int COLOR = 0xFFE29A;

    private static int lastPrune = Integer.MIN_VALUE / 2;

    private Believers() {}

    /** Quantos fiéis o jogador tem, contando ele mesmo se já se incluiu */
    public static int count(ServerPlayer player) {
        Data data = data(player.server);
        // conferir quais colonos ainda existem custa uma volta por todas as colônias: no máximo a cada 10 segundos
        int tick = player.server.getTickCount();
        if (tick - lastPrune > 200 || tick < lastPrune) {
            lastPrune = tick;
            data.prune(Colonists.allIds());
        }
        return data.of(player.getUUID()).size() + (worshipsSelf(player) ? 1 : 0);
    }

    /** Sanidade mínima com 100 fiéis ou mais: os fiéis são âncoras e seguram o portador antes de perder o controle */
    public static final float ANCHOR_FLOOR = 0.20f;

    /**
     * Âncoras: Beyonders de Sequência 3 ou maior com fiéis não deixam a sanidade cair abaixo deste valor.
     * Cresce com os fiéis, até ANCHOR_FLOOR com 100 ou mais. 0 para quem não tem fiéis ou não pode tê-los.
     */
    public static float anchorFloor(net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !BeyonderData.isBeyonder(player) || BeyonderData.getSequence(player) > MAX_SEQUENCE) return 0f;
        return ANCHOR_FLOOR * Math.min(count(player), NEEDED) / NEEDED;
    }

    public static boolean worshipsSelf(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(SELF);
    }

    /**
     * Contagem de fiéis com o total pedido pelo ritual do Anjo Branco (Sol 1): só aparece para quem está
     * no Sol, Sequência 2, prestes a fazer esse ritual. Para os outros não há mensagem de contagem.
     */
    private static void countMessage(ServerPlayer player, String key, int color) {
        boolean beforeWhiteAngel = BeyonderData.isBeyonder(player) && "sun".equals(BeyonderData.getPathway(player)) && BeyonderData.getSequence(player) == 2;
        if (beforeWhiteAngel) player.displayClientMessage(Component.translatable(key, count(player), NEEDED).withColor(color), true);
    }

    /** Só Beyonders de Sequência 3 ou maior podem reunir fiéis; os outros recebem um aviso. */
    private static boolean canGather(ServerPlayer player) {
        if (BeyonderData.isBeyonder(player) && BeyonderData.getSequence(player) <= MAX_SEQUENCE) return true;
        player.displayClientMessage(Component.translatable("lotmcraft.believers.not_worthy", MAX_SEQUENCE).withColor(0xFF7A7A), true);
        return false;
    }

    /** Clique direito no ar com o Registro Sagrado: o jogador se registra entre os próprios fiéis */
    public static void worshipSelf(ServerPlayer player) {
        if (!canGather(player)) return;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.getBoolean(SELF)) {
            persisted.putBoolean(SELF, true);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(1.1), player.getZ(), 25, 0.4, 0.6, 0.4, 0.05);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
            countMessage(player, "lotmcraft.believers.self", COLOR);
        } else {
            countMessage(player, "lotmcraft.believers.count", COLOR);
        }
    }

    // Clique direito num colono com o Registro Sagrado: ele vira seu fiel (antes que a tela do colono abra)
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().is(ModItems.SACRED_REGISTER.get()) || !Colonists.isAnyCitizen(event.getTarget())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getEntity() instanceof ServerPlayer player) || !canGather(player)) return;
        Entity colonist = event.getTarget();
        ICitizenData citizen = Colonists.data(colonist);
        if (citizen == null) return; // visitante da taverna: ainda não mora na colônia

        Data data = data(player.server);
        UUID id = citizen.getUUID();
        UUID god = data.godOf(id);
        if (god != null) {
            player.displayClientMessage(Component.translatable(god.equals(player.getUUID()) ? "lotmcraft.believers.already_yours" : "lotmcraft.believers.other_god").withColor(COLOR), true);
            return;
        }
        if (data.wronged(id).contains(player.getUUID())) {
            colonist.playSound(SoundEvents.VILLAGER_NO, 1f, 1f);
            player.displayClientMessage(Component.translatable("lotmcraft.believers.wronged").withColor(0xFF7A7A), true);
            return;
        }

        data.add(player.getUUID(), id);
        giveTitle(data, citizen, colonist, player);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD, colonist.getX(), colonist.getY(1.1), colonist.getZ(), 20, 0.3, 0.5, 0.3, 0.04);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, colonist.getX(), colonist.getY(1.0), colonist.getZ(), 8, 0.4, 0.4, 0.4, 0);
        colonist.playSound(SoundEvents.VILLAGER_CELEBRATE, 1f, 1f);
        countMessage(player, "lotmcraft.believers.converted", COLOR);
    }

    // Ferir um colono: ele perde a fé em você (se era seu fiel) e nunca mais aceita você
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        ICitizenData citizen = Colonists.data(event.getEntity());
        if (citizen == null) return;
        Data data = data(player.server);
        UUID id = citizen.getUUID();
        data.wrong(id, player.getUUID());
        if (player.getUUID().equals(data.godOf(id))) {
            forget(data, citizen, event.getEntity());
            countMessage(player, "lotmcraft.believers.lost", 0xFF7A7A);
        }
    }

    // Fiel morto deixa de contar
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        ICitizenData citizen = Colonists.data(event.getEntity());
        if (citizen == null || event.getEntity().getServer() == null) return;
        Data data = data(event.getEntity().getServer());
        data.removeEverywhere(citizen.getUUID());
        data.names.remove(citizen.getUUID());
        data.setDirty();
    }

    private static void forget(Data data, ICitizenData citizen, Entity colonist) {
        UUID id = citizen.getUUID();
        UUID god = data.godOf(id);
        if (god == null) return;
        data.remove(god, id);
        String old = data.names.remove(id);
        if (old != null) {
            citizen.setName(old);
            if (colonist != null) colonist.setCustomName(Component.literal(old));
        }
        data.setDirty();
    }

    // título "Fiel de [jogador]" depois do nome do colono; o nome original volta se a fé acabar
    private static void giveTitle(Data data, ICitizenData citizen, Entity colonist, ServerPlayer player) {
        String old = citizen.getName();
        data.names.put(citizen.getUUID(), old);
        data.setDirty();
        String title = Component.translatable("lotmcraft.believers.title", player.getName().getString()).getString();
        colonist.setCustomName(Component.literal(old + " (" + title + ")"));
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "lotmcraft_believers_colonists");
    }

    /** Fiéis de cada jogador (colonos, pela identidade fixa), salvos no mundo; o colono pode estar num chunk descarregado */
    private static final class Data extends SavedData {
        private final Map<UUID, Set<UUID>> believers = new HashMap<>();
        private final Map<UUID, Set<UUID>> wronged = new HashMap<>();
        private final Map<UUID, String> names = new HashMap<>();

        Set<UUID> of(UUID god) {
            return believers.getOrDefault(god, Set.of());
        }

        UUID godOf(UUID citizen) {
            for (Map.Entry<UUID, Set<UUID>> entry : believers.entrySet()) {
                if (entry.getValue().contains(citizen)) return entry.getKey();
            }
            return null;
        }

        Set<UUID> wronged(UUID citizen) {
            return wronged.getOrDefault(citizen, Set.of());
        }

        void wrong(UUID citizen, UUID player) {
            if (wronged.computeIfAbsent(citizen, k -> new HashSet<>()).add(player)) setDirty();
        }

        void add(UUID god, UUID citizen) {
            believers.computeIfAbsent(god, k -> new HashSet<>()).add(citizen);
            setDirty();
        }

        void remove(UUID god, UUID citizen) {
            Set<UUID> set = believers.get(god);
            if (set != null && set.remove(citizen)) setDirty();
        }

        void removeEverywhere(UUID citizen) {
            believers.values().forEach(set -> set.remove(citizen));
            wronged.remove(citizen);
        }

        /** Tira os fiéis que não existem mais (colônia apagada, colono que foi embora) */
        void prune(Set<UUID> existing) {
            boolean changed = false;
            for (Set<UUID> set : believers.values()) changed |= set.retainAll(existing);
            changed |= names.keySet().retainAll(existing);
            changed |= wronged.keySet().retainAll(existing);
            if (changed) setDirty();
        }

        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data data = new Data();
            CompoundTag gods = tag.getCompound("Believers");
            for (String god : gods.getAllKeys()) data.believers.put(UUID.fromString(god), uuids(gods.getList(god, Tag.TAG_INT_ARRAY)));
            CompoundTag wronged = tag.getCompound("Wronged");
            for (String citizen : wronged.getAllKeys()) data.wronged.put(UUID.fromString(citizen), uuids(wronged.getList(citizen, Tag.TAG_INT_ARRAY)));
            CompoundTag names = tag.getCompound("Names");
            for (String citizen : names.getAllKeys()) data.names.put(UUID.fromString(citizen), names.getString(citizen));
            return data;
        }

        private static Set<UUID> uuids(ListTag list) {
            Set<UUID> set = new HashSet<>();
            for (Tag uuid : list) set.add(NbtUtils.loadUUID(uuid));
            return set;
        }

        private static ListTag list(Set<UUID> set) {
            ListTag list = new ListTag();
            set.forEach(uuid -> list.add(NbtUtils.createUUID(uuid)));
            return list;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            CompoundTag gods = new CompoundTag();
            believers.forEach((god, set) -> gods.put(god.toString(), list(set)));
            tag.put("Believers", gods);
            CompoundTag wrongedTag = new CompoundTag();
            wronged.forEach((citizen, set) -> wrongedTag.put(citizen.toString(), list(set)));
            tag.put("Wronged", wrongedTag);
            CompoundTag namesTag = new CompoundTag();
            names.forEach((citizen, name) -> namesTag.putString(citizen.toString(), name));
            tag.put("Names", namesTag);
            return tag;
        }
    }
}
