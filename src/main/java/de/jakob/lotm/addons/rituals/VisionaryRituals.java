package de.jakob.lotm.addons.rituals;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.addons.subconscious.SubconsciousSea;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.dimension.ModDimensions;
import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Locale;

/**
 * Rituais de avanço do Visionário. Cada um aceita vários jeitos, de qualquer Caminho:
 * <ul>
 *   <li>5, Caminhante dos Sonhos: um contrato com uma criatura do Mundo Espiritual e beber com a pena dela, em alegria ou raiva.</li>
 *   <li>4, Manipulador: numa multidão, entrar em ressonância com as emoções dela e beber.</li>
 *   <li>3, Tecelão de Sonhos: muita gente com o mesmo sonho seu numa só noite.</li>
 *   <li>2, Discernidor: deixar a sua marca no subconsciente de muita gente.</li>
 *   <li>1, Autor: um romance com pessoas reais, e o destino delas seguindo a história.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class VisionaryRituals {
    private VisionaryRituals() {}

    public static final int EMOTION_WINDOW = 20 * 30, CROWD = 30, CROWD_RADIUS = 32, RESONANCE_WINDOW = 20 * 60;
    public static final int SAME_DREAM = 20, DREAM_RADIUS = 32, MARKS_NEEDED = 50, CHAPTERS = 7;

    private static final String JOY = "LotmVisJoy", ANGER = "LotmVisAnger", RESONANCE = "LotmVisResonance",
            DREAMS = "LotmVisDreams", STORIES = "LotmVisStories", FEATHER = "LotmSpiritFeather";
    private static final int COLOR = 0xB8A8F0, BAD = 0xFF7A7A;

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Está na Sequência de onde se faz o ritual da Sequência {@code ritualSequence} do Visionário */
    private static boolean preparing(Player player, int ritualSequence) {
        return BeyonderData.isBeyonder(player) && "visionary".equals(BeyonderData.getPathway(player))
                && BeyonderData.getSequence(player) == ritualSequence + 1;
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    private static long night(ServerPlayer player) {
        return player.server.overworld().getDayTime() / 24000;
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    private static boolean isPerson(Entity entity) {
        return entity instanceof AbstractEntityCitizen || entity instanceof AbstractVillager || entity instanceof Player
                || entity instanceof BeyonderNPCEntity;
    }

    private static boolean asleep(LivingEntity entity) {
        if (entity instanceof com.minecolonies.core.entity.citizen.EntityCitizen citizen) return citizen.getCitizenSleepHandler().isAsleep() || citizen.isSleeping();
        return entity.isSleeping();
    }

    private static boolean emotionAbility(String id) {
        for (String word : new String[]{"emotion", "charm", "incite", "provoke", "hypno", "frenzy", "calm", "pacif", "placat", "rage", "wrath",
                "joy", "fear", "terror", "horror", "intimid", "inspir", "song", "poem", "speech", "eloquen", "seduc", "despair", "manipul"}) {
            if (id.contains(word)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 5, Caminhante dos Sonhos: o contrato e a pena

    private static boolean spiritCreature(Entity entity) {
        return entity instanceof LivingEntity && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().startsWith("spirit_");
    }

    /** Um livro assinado (o contrato) numa criatura do Mundo Espiritual: ela deixa uma pena */
    @SubscribeEvent
    public static void onContract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!player.level().dimension().equals(ModDimensions.SPIRIT_WORLD_DIMENSION_KEY) || !spiritCreature(event.getTarget())) return;
        ItemStack book = player.getMainHandItem();
        if (!book.is(Items.WRITTEN_BOOK)) return;
        event.setCanceled(true);
        Entity creature = event.getTarget();
        ItemStack feather = new ItemStack(Items.FEATHER);
        feather.set(DataComponents.CUSTOM_NAME, Component.translatable("lotmcraft.visionary.feather", creature.getName()).withStyle(s -> s.withItalic(false).withColor(COLOR)));
        CustomData.update(DataComponents.CUSTOM_DATA, feather, tag -> tag.putString(FEATHER, creature.getType().getDescriptionId()));
        book.shrink(1);
        if (!player.getInventory().add(feather)) player.drop(feather, false);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ENCHANT, creature.getX(), creature.getY() + 1, creature.getZ(), 40, .5, .8, .5, .3);
        level.playSound(null, creature.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, .6f);
        tell(player, Component.translatable("lotmcraft.visionary.contract", creature.getName()).withColor(COLOR));
    }

    private static boolean holdsFeather(ServerPlayer player) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
            if (stack.is(Items.FEATHER) && custom != null && custom.copyTag().contains(FEATHER)) return true;
        }
        return false;
    }

    /** Alegria ou raiva intensa agora: lutou, foi ferido, subiu de nível, comeu algo raro, ou um poder de emoção */
    private static boolean intenseEmotion(ServerPlayer player) {
        CompoundTag tag = data(player);
        long time = now(player);
        return (tag.contains(JOY) && time - tag.getLong(JOY) <= EMOTION_WINDOW) || (tag.contains(ANGER) && time - tag.getLong(ANGER) <= EMOTION_WINDOW)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE);
    }

    public static boolean featherAndEmotion(ServerPlayer player) {
        return holdsFeather(player) && intenseEmotion(player);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer hurt && preparing(hurt, 5)) data(hurt).putLong(ANGER, now(hurt));
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && preparing(attacker, 5)) data(attacker).putLong(ANGER, now(attacker));
    }

    @SubscribeEvent
    public static void onLevelUp(PlayerXpEvent.LevelChange event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevels() > 0 && preparing(player, 5)) data(player).putLong(JOY, now(player));
    }

    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !preparing(player, 5)) return;
        ItemStack food = event.getItem();
        if (food.is(Items.GOLDEN_APPLE) || food.is(Items.ENCHANTED_GOLDEN_APPLE) || food.is(Items.CAKE) || food.is(Items.PUMPKIN_PIE)
                || food.is(Items.HONEY_BOTTLE) || food.is(Items.COOKIE) || food.is(Items.GLOW_BERRIES)) data(player).putLong(JOY, now(player));
    }

    // ------------------------------------------------------------------ 4, Manipulador: a multidão e a ressonância

    public static int crowd(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CROWD_RADIUS),
                e -> e != player && e.isAlive() && isPerson(e)).size();
    }

    public static boolean resonated(ServerPlayer player) {
        CompoundTag tag = data(player);
        return crowd(player) >= CROWD && tag.contains(RESONANCE) && now(player) - tag.getLong(RESONANCE) <= RESONANCE_WINDOW;
    }

    /** Tocar um sino chama a multidão e faz o coração dela bater junto */
    @SubscribeEvent
    public static void onBell(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && preparing(player, 4) && event.getLevel().getBlockState(event.getPos()).is(Blocks.BELL)) {
            data(player).putLong(RESONANCE, now(player));
        }
    }

    // ------------------------------------------------------------------ 3, Tecelão de Sonhos: o mesmo sonho, numa noite

    /** Quem sonhou o seu sonho nesta noite por um poder de sonho (além das mentes do Mar) */
    public static int sameDream(ServerPlayer player) {
        CompoundTag dreams = data(player).getCompound(DREAMS);
        int byPower = dreams.getLong("night") == night(player) ? dreams.getCompound("who").size() : 0;
        return Math.max(byPower, 0) + SubconsciousSea.sameDreamTonight(player);
    }

    // ------------------------------------------------------------------ 1, Autor: o romance e os destinos

    private record Fate(String word, String key) {}

    private static final Fate[] FATES = {new Fate("morre", "dies"), new Fate("morrerá", "dies"), new Fate("morrera", "dies"),
            new Fate("sonha", "dreams"), new Fate("sonhará", "dreams"), new Fate("sonhara", "dreams"),
            new Fate("feliz", "happy"), new Fate("triste", "sad"), new Fate("aprende", "learns"), new Fate("aprenderá", "learns"),
            new Fate("aprendera", "learns"), new Fate("dies", "dies"), new Fate("dreams", "dreams"), new Fate("happy", "happy"),
            new Fate("sad", "sad"), new Fate("learns", "learns")};

    /** Uma pessoa real com este nome: um colono (de qualquer colônia) ou um jogador */
    private static ICitizenData citizenNamed(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        ICitizenData best = null;
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
                String name = citizen.getName().toLowerCase(Locale.ROOT);
                if (lower.contains(name) && (best == null || name.length() > best.getName().length())) best = citizen;
            }
        }
        return best;
    }

    private static int skillTotal(ICitizenData citizen) {
        int total = 0;
        for (Skill skill : Skill.values()) total += citizen.getCitizenSkillHandler().getLevel(skill);
        return total;
    }

    private static double happiness(ICitizenData citizen) {
        return citizen.getCitizenHappinessHandler().getHappiness(citizen.getColony(), citizen);
    }

    /** Lê o romance: um capítulo por página, com o nome de uma pessoa real e o destino dela */
    private static CompoundTag readStory(ServerPlayer player, ItemStack book) {
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content == null || !content.author().equals(player.getGameProfile().getName())) return null;
        CompoundTag story = new CompoundTag();
        CompoundTag chapters = new CompoundTag();
        for (var page : content.pages()) {
            String text = page.raw().getString();
            ICitizenData citizen = citizenNamed(text);
            if (citizen == null) continue;
            String lower = text.toLowerCase(Locale.ROOT);
            String fate = null;
            for (Fate f : FATES) if (lower.contains(f.word())) { fate = f.key(); break; }
            if (fate == null) continue;
            String key = citizen.getColony().getID() + ":" + citizen.getId();
            if (chapters.contains(key)) continue;
            CompoundTag chapter = new CompoundTag();
            chapter.putString("fate", fate);
            chapter.putString("name", citizen.getName());
            chapter.putUUID("uuid", citizen.getUUID());
            chapter.putInt("skills", skillTotal(citizen));
            chapter.putDouble("happy", happiness(citizen));
            chapters.put(key, chapter);
        }
        story.put("chapters", chapters);
        story.putString("title", content.title().raw());
        return story;
    }

    private static ICitizenData find(String key) {
        String[] parts = key.split(":");
        IColony colony = IColonyManager.getInstance().getAllColonies().stream().filter(c -> c.getID() == Integer.parseInt(parts[0])).findFirst().orElse(null);
        return colony == null ? null : colony.getCitizenManager().getCivilian(Integer.parseInt(parts[1]));
    }

    /** O destino de um capítulo se cumpriu? */
    private static boolean fulfilled(ServerPlayer author, String key, CompoundTag chapter) {
        ICitizenData citizen = find(key);
        return switch (chapter.getString("fate")) {
            case "dies" -> citizen == null;
            case "dreams" -> citizen != null && data(author).getCompound(SubconsciousSea.MARKS).contains(chapter.getUUID("uuid").toString());
            case "happy" -> citizen != null && happiness(citizen) >= 7 && chapter.getDouble("happy") < 7;
            case "sad" -> citizen != null && happiness(citizen) <= 3 && chapter.getDouble("happy") > 3;
            case "learns" -> citizen != null && skillTotal(citizen) > chapter.getInt("skills");
            default -> false;
        };
    }

    /** Quantos capítulos se cumpriram no romance mais adiantado */
    public static int[] storyProgress(ServerPlayer author) {
        CompoundTag stories = data(author).getCompound(STORIES);
        int[] best = {0, 0};
        for (String title : stories.getAllKeys()) {
            CompoundTag chapters = stories.getCompound(title).getCompound("chapters");
            int done = 0;
            for (String key : chapters.getAllKeys()) if (fulfilled(author, key, chapters.getCompound(key))) done++;
            if (done > best[0] || (done == best[0] && chapters.size() > best[1])) best = new int[]{done, chapters.size()};
        }
        return best;
    }

    public static boolean storyComplete(ServerPlayer author) {
        CompoundTag stories = data(author).getCompound(STORIES);
        for (String title : stories.getAllKeys()) {
            CompoundTag chapters = stories.getCompound(title).getCompound("chapters");
            if (chapters.size() < CHAPTERS) continue;
            boolean all = true;
            for (String key : chapters.getAllKeys()) if (!fulfilled(author, key, chapters.getCompound(key))) { all = false; break; }
            if (all) return true;
        }
        return false;
    }

    /** Um romance novo no inventário: a partir de agora, o destino das personagens segue a história */
    private static void tickStories(ServerPlayer player) {
        CompoundTag stories = data(player).getCompound(STORIES);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(Items.WRITTEN_BOOK)) continue;
            WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content == null || stories.contains(content.title().raw())) continue;
            CompoundTag story = readStory(player, stack);
            if (story == null) continue;
            int chapters = story.getCompound("chapters").size();
            if (chapters == 0) continue;
            stories.put(content.title().raw(), story);
            data(player).put(STORIES, stories);
            player.displayClientMessage(Component.translatable(chapters >= CHAPTERS ? "lotmcraft.visionary.story_begun" : "lotmcraft.visionary.story_short",
                    content.title().raw(), chapters, CHAPTERS).withColor(chapters >= CHAPTERS ? COLOR : BAD), false);
        }
    }

    // ------------------------------------------------------------------ poderes

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String id = event.getAbility().getId();
        if (preparing(player, 5) && emotionAbility(id)) data(player).putLong(JOY, now(player));
        if (preparing(player, 4) && emotionAbility(id)) data(player).putLong(RESONANCE, now(player));
        // 3: um poder de sonho perto de quem dorme: todos sonham o seu sonho
        if (preparing(player, 3) && (id.contains("dream") || id.contains("nightmare") || id.contains("sleep") || id.contains("midnight"))) {
            CompoundTag dreams = data(player).getCompound(DREAMS);
            if (dreams.getLong("night") != night(player)) {
                dreams = new CompoundTag();
                dreams.putLong("night", night(player));
            }
            CompoundTag who = dreams.getCompound("who");
            for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DREAM_RADIUS),
                    e -> e != player && isPerson(e) && asleep(e))) who.putBoolean(e.getUUID().toString(), true);
            dreams.put("who", who);
            data(player).put(DREAMS, dreams);
            tell(player, Component.translatable("lotmcraft.visionary.same_dream", sameDream(player), SAME_DREAM).withColor(COLOR));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 100 != 0) return;
        if (preparing(player, 1)) tickStories(player);
    }
}
