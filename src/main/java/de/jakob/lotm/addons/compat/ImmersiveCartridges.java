package de.jakob.lotm.addons.compat;

import blusunrize.immersiveengineering.api.tool.BulletHandler;
import blusunrize.immersiveengineering.api.tool.BulletHandler.CodecsAndDefault;
import blusunrize.immersiveengineering.api.tool.BulletHandler.DamagingBullet;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.death.EyeOfDeathAbility;
import de.jakob.lotm.beyonders.rituals.RitualResultRegistry;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import malte0811.dualcodecs.DualCodecs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Cartuchos Beyonder para o revólver do Immersive Engineering, abençoados na mesa de rituais.
 * Só é carregada quando o Immersive Engineering está no modpack.
 * <p>
 * Os cartuchos guardam a força da bênção (a Sequência de quem rezou; quem não é Beyonder conta como 9).
 * O efeito segue a regra dos semideuses: não pega em quem for muito mais forte que a bênção.
 */
public final class ImmersiveCartridges {
    private ImmersiveCartridges() {}

    public static final String MOD_ID = "immersiveengineering";
    public static final ResourceLocation SLEEP = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "sleep");
    public static final ResourceLocation PURIFICATION = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "purification");
    /** Item do Immersive usado como base dos cartuchos abençoados */
    public static final ResourceLocation CASULL = ResourceLocation.fromNamespaceAndPath(MOD_ID, "bullet_casull");

    /** Força de quem não é Beyonder */
    private static final int COMMON_BLESSING = 9;
    private static final CodecsAndDefault<Integer> POTENCY = new CodecsAndDefault<>(DualCodecs.INT, COMMON_BLESSING);

    private static final int SLEEP_TICKS = 20 * 8;
    private static final int PURIFICATION_TICKS = 20 * 3;

    /** Tem que rodar antes do Immersive criar os itens dos cartuchos (no construtor do nosso mod) */
    public static void register() {
        BulletHandler.registerBullet(SLEEP, new BlessedBullet("sleep", 2f) {
            @Override
            protected void afterHit(LivingEntity target, @Nullable Entity shooter, int potency) {
                if (resists(target, potency)) return;
                target.addEffect(new MobEffectInstance(ModEffects.ASLEEP, SLEEP_TICKS, 0, false, true, true));
            }
        });
        BulletHandler.registerBullet(PURIFICATION, new BlessedBullet("purification", 6f) {
            @Override
            protected float getDamage(Entity hitEntity, boolean headshot) {
                float damage = super.getDamage(hitEntity, headshot);
                return hitEntity instanceof LivingEntity living && corrupted(living) ? damage * 1.5f : damage;
            }

            @Override
            protected void afterHit(LivingEntity target, @Nullable Entity shooter, int potency) {
                if (resists(target, potency)) return;
                // bênção de semideus em diante queima mais forte
                int amplifier = potency <= 4 ? 1 : 0;
                target.addEffect(new MobEffectInstance(ModEffects.PURIFICATION, PURIFICATION_TICKS, amplifier, false, true, true));
            }
        });
        RitualResultRegistry.register("bless_cartridges", new de.jakob.lotm.beyonders.rituals.RitualResultHandler() {
            @Override
            public void perform(java.util.Map<String, Object> params, ServerPlayer player, net.minecraft.core.BlockPos center) {}

            @Override
            public void perform(java.util.Map<String, Object> params, ServerPlayer player, net.minecraft.core.BlockPos center, List<ItemStack> sacrifices) {
                bless(params, player, center, sacrifices);
            }
        });
    }

    /** Mortos-vivos, espíritos e Caminhos malignos */
    private static boolean corrupted(LivingEntity entity) {
        return entity.getType().is(EntityTypeTags.UNDEAD) || EyeOfDeathAbility.isSpiritEntity(entity) || BeyonderData.isEvilPathway(entity);
    }

    private static boolean resists(LivingEntity target, int potency) {
        return AbilityUtil.isTargetSignificantlyStronger(potency, BeyonderData.getSequence(target));
    }

    /** Resultado do ritual: cada Cartucho Casull oferecido volta abençoado */
    private static void bless(java.util.Map<String, Object> params, ServerPlayer player, net.minecraft.core.BlockPos center, List<ItemStack> sacrifices) {
        Object kind = params.get("cartridge");
        ResourceLocation type = "purification".equals(kind) ? PURIFICATION : SLEEP;
        int count = 0;
        for (ItemStack stack : sacrifices) {
            if (CASULL.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) count += stack.getCount();
        }
        if (count <= 0) return;
        Item item = BulletHandler.getBulletItem(type);
        int potency = Math.min(COMMON_BLESSING, BeyonderData.getSequence(player));
        DataComponentType<Integer> component = potencyComponent(type);
        while (count > 0) {
            ItemStack blessed = new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize()));
            count -= blessed.getCount();
            if (component != null) blessed.set(component, potency);
            if (!player.getInventory().add(blessed)) player.drop(blessed, false);
        }
    }

    /** O Immersive registra o componente de cada cartucho como "lotmcraft_<nome>" */
    @SuppressWarnings("unchecked")
    @Nullable
    private static DataComponentType<Integer> potencyComponent(ResourceLocation type) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, type.getNamespace() + "_" + type.getPath());
        return (DataComponentType<Integer>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
    }

    private abstract static class BlessedBullet extends DamagingBullet<Integer> {
        private final String name;

        BlessedBullet(String name, float damage) {
            super(POTENCY,
                    (projectile, shooter, hit) -> projectile.damageSources().thrown(projectile, shooter),
                    damage,
                    () -> BulletHandler.emptyCasing.asItem().getDefaultInstance(),
                    ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "item/bullet_" + name));
            this.name = name;
        }

        protected abstract void afterHit(LivingEntity target, @Nullable Entity shooter, int potency);

        @Override
        public void onHitTarget(Level world, HitResult rtr, @Nullable UUID shooterUUID, Entity projectile, boolean headshot, Integer potency) {
            // o dano vem antes: o Sono acaba quando o alvo leva dano
            super.onHitTarget(world, rtr, shooterUUID, projectile, headshot, potency);
            if (world.isClientSide || !(rtr instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target) || !target.isAlive()) return;
            Entity shooter = shooterUUID != null && world instanceof ServerLevel server ? server.getEntity(shooterUUID) : null;
            if (shooter == null && projectile instanceof Projectile p) shooter = p.getOwner();
            afterHit(target, shooter, potency == null ? COMMON_BLESSING : potency);
        }

        @Override
        public void addTooltip(Integer potency, @Nullable Item.TooltipContext world, List<Component> list, TooltipFlag flag) {
            list.add(Component.translatable("lotmcraft.cartridge." + name + ".desc").withStyle(ChatFormatting.GRAY));
            int value = potency == null ? COMMON_BLESSING : potency;
            list.add(value >= COMMON_BLESSING
                    ? Component.translatable("lotmcraft.cartridge.common_blessing").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable("lotmcraft.cartridge.potency", value).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
