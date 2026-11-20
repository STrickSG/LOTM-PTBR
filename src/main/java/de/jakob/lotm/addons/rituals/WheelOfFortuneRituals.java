package de.jakob.lotm.addons.rituals;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.subconscious.SubconsciousSea;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Locale;

/**
 * Rituais de avanço da Roda da Fortuna. Cada um aceita vários jeitos, de qualquer Caminho:
 * <ul>
 *   <li>5, Vencedor: 3 dias (o "mês") em azar, sem nenhuma sorte.</li>
 *   <li>4, Mago do Infortúnio: em azar, sem preparo, sobreviver a uma calamidade e beber logo depois.</li>
 *   <li>3, Caminhante do Caos: fixar um farol do destino (livro assinado numa magnetita ou sinalizador), ir longe e voltar a ele.</li>
 *   <li>2, Adivinho: uma revelação escrita sobre uma colônia, e ela acontecer como foi vista.</li>
 *   <li>1, Serpente de Mercúrio: tirar todas as memórias (o Livro das Memórias), beber e recebê-las de volta; ou regredir no
 *   Mar do Subconsciente com um Visionário por perto.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class WheelOfFortuneRituals {
    private WheelOfFortuneRituals() {}

    public static final int UNLUCKY_NEEDED = 24000 * 3, CALAMITY_SHARE_PERCENT = 50, CALAMITY_WINDOW = 20 * 60;
    public static final int NODE_DISTANCE = 500, NODE_WINDOW = 24000 * 10, NODE_REACH = 4;
    public static final int GROWTH = 5, VISIONARY_RADIUS = 16;

    private static final String UNLUCKY = "LotmWofUnlucky", SURVIVED = "LotmWofSurvived", NODE = "LotmWofNode", NODE_TIME = "LotmWofNodeTime",
            NODE_DIM = "LotmWofNodeDim", NODE_FAR = "LotmWofNodeFar", PROPHECIES = "LotmWofProphecies", MEMORIES = "LotmWofMemories",
            MEMORY_BOOK = "LotmMemoryBook", PENDING = "LotmWofMemoriesPending";
    private static final int COLOR = 0xBAD2F5, BAD = 0xFF7A7A;

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Está na Sequência de onde se faz o ritual da Sequência {@code ritualSequence} da Roda da Fortuna */
    private static boolean preparing(Player player, int ritualSequence) {
        return BeyonderData.isBeyonder(player) && "wheel_of_fortune".equals(BeyonderData.getPathway(player))
                && BeyonderData.getSequence(player) == ritualSequence + 1;
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    // ------------------------------------------------------------------ azar

    /** Em azar: o efeito Azar, ou um item amaldiçoado vestido */
    public static boolean unlucky(LivingEntity entity) {
        if (entity.hasEffect(MobEffects.UNLUCK)) return true;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack worn = entity.getItemBySlot(slot);
            if (!worn.isEmpty() && EnchantmentHelper.hasTag(worn, EnchantmentTags.CURSE)) return true;
        }
        return false;
    }

    /** Qualquer sorte agora: o efeito Sorte, Herói da Vila */
    private static boolean lucky(LivingEntity entity) {
        return entity.hasEffect(MobEffects.LUCK) || entity.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
    }

    // ------------------------------------------------------------------ 5, Vencedor: um mês em azar

    public static int unluckyTicks(ServerPlayer player) {
        return data(player).getInt(UNLUCKY);
    }

    public static boolean monthOfMisfortune(ServerPlayer player) {
        return unluckyTicks(player) >= UNLUCKY_NEEDED;
    }

    private static void loseStreak(ServerPlayer player, String why) {
        CompoundTag tag = data(player);
        int ticks = tag.getInt(UNLUCKY);
        if (ticks > 0 && ticks < UNLUCKY_NEEDED) {
            player.displayClientMessage(Component.translatable("lotmcraft.wof.luck_broke", Component.translatable("lotmcraft.wof.why_" + why)).withColor(BAD), false);
            tag.putInt(UNLUCKY, 0);
        }
    }

    private static void tickMisfortune(ServerPlayer player) {
        CompoundTag tag = data(player);
        int ticks = tag.getInt(UNLUCKY);
        if (ticks >= UNLUCKY_NEEDED) return;
        if (lucky(player)) {
            loseStreak(player, "luck");
            return;
        }
        if (!unlucky(player)) {
            loseStreak(player, "lost");
            return;
        }
        ticks += 20;
        tag.putInt(UNLUCKY, ticks);
        if (ticks >= UNLUCKY_NEEDED) tell(player, Component.translatable("lotmcraft.wof.month_done").withColor(COLOR));
        else if (ticks % 2400 == 0) tell(player, Component.translatable("lotmcraft.wof.month_progress", ticks * 100 / UNLUCKY_NEEDED).withColor(COLOR));
    }

    /** Um tesouro pescado é sorte */
    @SubscribeEvent
    public static void onFish(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !preparing(player, 5)) return;
        for (ItemStack stack : event.getDrops()) {
            if (stack.is(Items.NAME_TAG) || stack.is(Items.SADDLE) || stack.is(Items.NAUTILUS_SHELL) || stack.is(Items.ENCHANTED_BOOK)
                    || stack.is(Items.BOW) || stack.is(Items.FISHING_ROD) && stack.isEnchanted()) {
                loseStreak(player, "treasure");
                return;
            }
        }
    }

    // ------------------------------------------------------------------ 4, Mago do Infortúnio: a calamidade

    private static boolean calamity(DamageSource source) {
        return source.is(ModDamageTypes.LIGHTNING) || source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.DAMAGES_HELMET)
                || source.typeHolder().unwrapKey().map(k -> k.location().getNamespace().equals(LOTMCraft.MOD_ID)).orElse(false);
    }

    /** Sem preparo: sem armadura e sem nada que proteja ou cure */
    private static boolean unprepared(ServerPlayer player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack worn = player.getItemBySlot(slot);
            // um item amaldiçoado não protege: é parte do azar
            if (!worn.isEmpty() && !EnchantmentHelper.hasTag(worn, EnchantmentTags.CURSE)) return false;
        }
        for (var effect : new net.minecraft.core.Holder[]{MobEffects.DAMAGE_RESISTANCE, MobEffects.FIRE_RESISTANCE, MobEffects.REGENERATION,
                MobEffects.ABSORPTION, MobEffects.HEALTH_BOOST, MobEffects.SLOW_FALLING, MobEffects.WATER_BREATHING}) {
            @SuppressWarnings("unchecked") var holder = (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) effect;
            if (player.hasEffect(holder)) return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !preparing(player, 4)) return;
        if (!calamity(event.getSource()) || !unlucky(player) || !unprepared(player)) return;
        if (event.getNewDamage() >= player.getMaxHealth() * CALAMITY_SHARE_PERCENT / 100f && player.isAlive()) {
            data(player).putLong(SURVIVED, now(player));
            tell(player, Component.translatable("lotmcraft.wof.survived").withColor(COLOR));
        }
    }

    /** O Totem é sorte: estraga a sobrevivência */
    @SubscribeEvent
    public static void onTotem(LivingUseTotemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (preparing(player, 4)) data(player).remove(SURVIVED);
            if (preparing(player, 5)) loseStreak(player, "luck");
        }
    }

    public static boolean survivedCalamity(ServerPlayer player) {
        CompoundTag tag = data(player);
        return tag.contains(SURVIVED) && now(player) - tag.getLong(SURVIVED) <= CALAMITY_WINDOW;
    }

    // ------------------------------------------------------------------ 3, Caminhante do Caos: o farol do destino

    @SubscribeEvent
    public static void onFixNode(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND || !preparing(player, 3)) return;
        var state = event.getLevel().getBlockState(event.getPos());
        if (!(state.is(Blocks.LODESTONE) || state.is(Blocks.BEACON)) || !player.getMainHandItem().is(Items.WRITTEN_BOOK)) return;
        event.setCanceled(true);
        CompoundTag tag = data(player);
        tag.putLong(NODE, event.getPos().asLong());
        tag.putString(NODE_DIM, player.level().dimension().location().toString());
        tag.putLong(NODE_TIME, now(player));
        tag.remove(NODE_FAR);
        ServerLevel level = player.serverLevel();
        BlockPos p = event.getPos();
        level.sendParticles(ParticleTypes.END_ROD, p.getX() + .5, p.getY() + 1.2, p.getZ() + .5, 60, .3, 2, .3, .05);
        level.playSound(null, p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, .7f);
        player.displayClientMessage(Component.translatable("lotmcraft.wof.node_fixed").withColor(COLOR), false);
    }

    private static void tickNode(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (!tag.contains(NODE) || tag.getBoolean(NODE_FAR)) return;
        BlockPos node = BlockPos.of(tag.getLong(NODE));
        boolean otherWorld = !player.level().dimension().location().toString().equals(tag.getString(NODE_DIM));
        if (otherWorld || Math.hypot(player.getX() - node.getX(), player.getZ() - node.getZ()) >= NODE_DISTANCE) {
            tag.putBoolean(NODE_FAR, true);
            tell(player, Component.translatable("lotmcraft.wof.node_far").withColor(COLOR));
        }
    }

    public static boolean atNode(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (!tag.contains(NODE) || !tag.getBoolean(NODE_FAR) || now(player) - tag.getLong(NODE_TIME) > NODE_WINDOW) return false;
        if (!player.level().dimension().location().toString().equals(tag.getString(NODE_DIM))) return false;
        BlockPos node = BlockPos.of(tag.getLong(NODE));
        var state = player.level().getBlockState(node);
        return (state.is(Blocks.LODESTONE) || state.is(Blocks.BEACON)) && player.blockPosition().closerThan(node, NODE_REACH);
    }

    // ------------------------------------------------------------------ 2, Adivinho: a revelação sobre uma nação

    private static IColony colonyNamed(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        IColony best = null;
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            String name = colony.getName().toLowerCase(Locale.ROOT);
            if (!name.isBlank() && lower.contains(name) && (best == null || name.length() > best.getName().length())) best = colony;
        }
        return best;
    }

    private static String omen(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("cresce") || lower.contains("crescer") || lower.contains("grow")) return "grows";
        if (lower.contains("cai") || lower.contains("cair") || lower.contains("fall")) return "falls";
        if (lower.contains("prosper")) return "prospers";
        if (lower.contains("sofre") || lower.contains("suffer")) return "suffers";
        return null;
    }

    /** Lê as revelações nos livros que você escreveu e assinou */
    private static void tickProphecies(ServerPlayer player) {
        CompoundTag all = data(player).getCompound(PROPHECIES);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(Items.WRITTEN_BOOK)) continue;
            WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content == null || !content.author().equals(player.getGameProfile().getName()) || all.contains(content.title().raw())) continue;
            StringBuilder text = new StringBuilder(content.title().raw());
            for (var page : content.pages()) text.append(' ').append(page.raw().getString());
            IColony colony = colonyNamed(text.toString());
            String omen = omen(text.toString());
            if (colony == null || omen == null) continue;
            CompoundTag prophecy = new CompoundTag();
            prophecy.putInt("colony", colony.getID());
            prophecy.putString("dim", colony.getDimension().location().toString());
            prophecy.putString("omen", omen);
            prophecy.putInt("people", colony.getCitizenManager().getCurrentCitizenCount());
            prophecy.putDouble("happy", colony.getOverallHappiness());
            all.put(content.title().raw(), prophecy);
            data(player).put(PROPHECIES, all);
            player.displayClientMessage(Component.translatable("lotmcraft.wof.prophecy_seen", colony.getName(),
                    Component.translatable("lotmcraft.wof.omen_" + omen)).withColor(COLOR), false);
        }
    }

    private static boolean fulfilled(ServerPlayer player, CompoundTag prophecy) {
        IColony colony = null;
        for (IColony c : IColonyManager.getInstance().getAllColonies()) {
            if (c.getID() == prophecy.getInt("colony") && c.getDimension().location().toString().equals(prophecy.getString("dim"))) colony = c;
        }
        int was = prophecy.getInt("people");
        return switch (prophecy.getString("omen")) {
            case "grows" -> colony != null && colony.getCitizenManager().getCurrentCitizenCount() >= was + GROWTH;
            case "falls" -> colony == null || colony.getCitizenManager().getCurrentCitizenCount() * 2 <= was
                    || de.jakob.lotm.addons.colonies.ColonyConquest.conqueror(player.server, colony) != null;
            case "prospers" -> colony != null && colony.getOverallHappiness() >= 7 && prophecy.getDouble("happy") < 7;
            case "suffers" -> colony != null && colony.getOverallHappiness() <= 3 && prophecy.getDouble("happy") > 3;
            default -> false;
        };
    }

    public static boolean revelationCameTrue(ServerPlayer player) {
        CompoundTag all = data(player).getCompound(PROPHECIES);
        for (String title : all.getAllKeys()) if (fulfilled(player, all.getCompound(title))) return true;
        return false;
    }

    // ------------------------------------------------------------------ 1, Serpente de Mercúrio: as memórias

    /** Agachado, usar um livro comum com experiência: toda ela vai para o Livro das Memórias */
    @SubscribeEvent
    public static void onExtract(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown() || !event.getItemStack().is(Items.BOOK)) return;
        if (!preparing(player, 1) || player.totalExperience <= 0 && player.experienceLevel <= 0) return;
        event.setCanceled(true);
        int points = totalPoints(player);
        ItemStack book = new ItemStack(Items.BOOK);
        book.set(DataComponents.CUSTOM_NAME, Component.translatable("lotmcraft.wof.memory_book", player.getName())
                .withStyle(s -> s.withItalic(false).withColor(COLOR)));
        book.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> {
            tag.putInt(MEMORY_BOOK, points);
            tag.putUUID("LotmMemoryOwner", player.getUUID());
        });
        event.getItemStack().shrink(1);
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.totalExperience = 0;
        if (!player.getInventory().add(book)) player.drop(book, false);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 80, .4, .6, .4, 1.2);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, .5f);
        player.displayClientMessage(Component.translatable("lotmcraft.wof.memories_out").withColor(COLOR), false);
    }

    private static int totalPoints(ServerPlayer player) {
        int level = player.experienceLevel, points = 0;
        for (int l = 0; l < level; l++) points += l >= 30 ? 112 + (l - 30) * 9 : l >= 15 ? 37 + (l - 15) * 5 : 7 + l * 2;
        return points + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    private static ItemStack memoryBook(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
            if (stack.is(Items.BOOK) && custom != null && custom.copyTag().contains(MEMORY_BOOK)
                    && player.getUUID().equals(custom.copyTag().getUUID("LotmMemoryOwner"))) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Ao beber: sem nenhuma memória (experiência zero) e com o livro delas; ou no Mar do Subconsciente com um Visionário perto */
    public static boolean memoriesReady(ServerPlayer player) {
        ItemStack book = memoryBook(player);
        if (!book.isEmpty() && player.experienceLevel == 0 && player.experienceProgress <= 0.01f) {
            // as memórias voltam quando o avanço terminar
            data(player).putInt(PENDING, book.get(DataComponents.CUSTOM_DATA).copyTag().getInt(MEMORY_BOOK));
            book.shrink(1);
            return true;
        }
        if (SubconsciousSea.inSea(player)) {
            return !player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(VISIONARY_RADIUS),
                    e -> e != player && BeyonderData.isBeyonder(e) && "visionary".equals(BeyonderData.getPathway(e))).isEmpty();
        }
        return false;
    }

    /** Depois do avanço, as memórias voltam para você */
    private static void tickMemories(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (!tag.contains(PENDING) || !BeyonderData.isBeyonder(player) || BeyonderData.getSequence(player) > 1) return;
        int points = tag.getInt(PENDING);
        tag.remove(PENDING);
        player.giveExperiencePoints(points);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 120, 1.5, 1.5, 1.5, -1.2);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, .6f);
        player.displayClientMessage(Component.translatable("lotmcraft.wof.memories_back").withColor(COLOR), false);
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (preparing(player, 5)) tickMisfortune(player);
        if (preparing(player, 3)) tickNode(player);
        if (preparing(player, 2) && player.tickCount % 100 == 0) tickProphecies(player);
        tickMemories(player);
    }
}
