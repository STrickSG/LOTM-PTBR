package de.jakob.lotm.addons.corruption;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.attachments.TransformationComponent;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Com 100% de corrupção a pessoa deixa de ser ela mesma: assume a forma de criatura mítica do Caminho e fica ali,
 * sem controle do corpo (não anda, não ataca, não usa habilidades nem itens), até alguém a matar. Nas Sequências baixas
 * a forma é só a aparência; de Semideus (Sequência 4) para cima ela também faz quem a vê perder o controle.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class LostToCorruption {
    private LostToCorruption() {}

    private static final String KEY = "LotmLostToCorruption";
    private static final ResourceLocation SCALE = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "lost_to_corruption");

    public static boolean isLost(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(KEY);
    }

    private static void setLost(Player player, boolean lost) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (lost) persisted.putBoolean(KEY, true);
        else persisted.remove(KEY);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** A corrupção chegou a 100% */
    public static void lose(ServerPlayer player) {
        if (isLost(player) || player.isCreative() || player.isSpectator()) return;
        setLost(player, true);
        transform(player);
        player.serverLevel().sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY(1.0), player.getZ(), 80, 0.6, 1.0, 0.6, 0.05);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.5f, 0.8f);
        player.sendSystemMessage(Component.translatable("lotmcraft.corruption.lost").withColor(0xB0306A));
    }

    private static void transform(ServerPlayer player) {
        int seq = BeyonderData.getSequence(player);
        if (seq <= 4) {
            // de Semideus para cima: a forma verdadeira, com tudo o que ela faz
            var ability = LOTMCraft.abilityHandler.getById("mythical_creature_form_ability");
            if (ability instanceof ToggleAbility toggle && !toggle.isActiveForEntity(player)) toggle.useAbility(player.serverLevel(), player);
            return;
        }
        // nas Sequências baixas: só a aparência, sem fazer ninguém perder o controle
        TransformationComponent t = player.getData(ModAttachments.TRANSFORMATION_COMPONENT);
        t.setTransformedAndSync(true, player);
        t.setTransformationIndexAndSync(TransformationComponent.TransformationType.MYTHICAL_CREATURE, player);
        t.setAdditionalDataAndSync(BeyonderData.getPathway(player), player);
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null && scale.getModifier(SCALE) == null)
            scale.addTransientModifier(new AttributeModifier(SCALE, 0.8, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void restore(ServerPlayer player) {
        setLost(player, false);
        var ability = LOTMCraft.abilityHandler.getById("mythical_creature_form_ability");
        if (ability instanceof ToggleAbility toggle && toggle.isActiveForEntity(player)) toggle.useAbility(player.serverLevel(), player);
        TransformationComponent t = player.getData(ModAttachments.TRANSFORMATION_COMPONENT);
        if (t.isTransformed() && t.getTransformationIndex() == TransformationComponent.TransformationType.MYTHICAL_CREATURE.getIndex())
            t.setTransformedAndSync(false, player);
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null) scale.removeModifier(SCALE);
    }

    /** Sem controle do corpo: parado, sem habilidades */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isLost(player)) return;
        player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
        player.hurtMarked = true;
        if (player.tickCount % 10 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 128, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 255, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));
            player.getData(ModAttachments.DISABLED_ABILITIES_COMPONENT).disableAbilityUsageForTime("lost_to_corruption", 40, player);
            TransformationComponent t = player.getData(ModAttachments.TRANSFORMATION_COMPONENT);
            if (!t.isTransformed()) transform(player);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isLost(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (isLost(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isLost(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreakBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isLost(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (isLost(event.getEntity())) event.setCanceled(true);
    }

    /** Morto, volta a ser quem era: a forma se desfaz e a corrupção cai para o piso (o peso das características sobressalentes) */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLost(player)) {
            restore(player);
            Corruption.set(player, 0f);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLost(player)) transform(player);
    }
}
