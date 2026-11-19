package de.jakob.lotm.addons.rituals;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.util.shapeShifting.mixin.LimbAnimatorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente: quem veste as roupas de outra pessoa aparece como ela (o colono, o aldeão, o Beyonder Errante),
 * para todo mundo. É só desenho, a partir das roupas que o servidor diz que estão vestidas: nenhum poder.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public final class ErrorDisguiseRender {
    private ErrorDisguiseRender() {}

    private record Cached(String key, LivingEntity body, String name) {}

    private static final Map<UUID, Cached> BODIES = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        DisguiseSyncPacket.Look worn = DisguiseSyncPacket.DISGUISED.get(player.getUUID());
        if (worn == null) {
            BODIES.remove(player.getUUID());
            return;
        }
        CompoundTag look = worn.look();
        String name = worn.name();
        if (!look.contains("type")) return;
        LivingEntity body = bodyFor(player, look, name);
        if (body == null) return;
        event.setCanceled(true);
        copyState(player, body);
        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body);
        renderer.render(body, player.getYRot(), event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    /** O corpo desenhado mostra o nome da pessoa (o colono sem dados da colônia mostraria o nome do tipo) */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        for (Cached cached : BODIES.values()) {
            if (cached.body() == event.getEntity()) {
                event.setContent(Component.literal(cached.name()));
                return;
            }
        }
    }

    private static LivingEntity bodyFor(AbstractClientPlayer player, CompoundTag look, String name) {
        String key = look + "|" + name;
        Cached cached = BODIES.get(player.getUUID());
        if (cached != null && cached.key().equals(key)) return cached.body();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(look.getString("type")));
        Entity created = type.create(player.level());
        if (!(created instanceof LivingEntity body)) return null;
        if (body instanceof AbstractEntityCitizen citizen) {
            var data = citizen.getEntityData();
            data.set(AbstractEntityCitizen.DATA_MODEL, look.getString("model"));
            data.set(AbstractEntityCitizen.DATA_TEXTURE, look.getInt("texture"));
            data.set(AbstractEntityCitizen.DATA_IS_FEMALE, look.getInt("female"));
            data.set(AbstractEntityCitizen.DATA_STYLE, look.getString("style"));
            data.set(AbstractEntityCitizen.DATA_TEXTURE_SUFFIX, look.getString("suffix"));
            data.set(AbstractEntityCitizen.DATA_RENDER_METADATA, look.getString("meta"));
            data.set(AbstractEntityCitizen.DATA_IS_CHILD, look.getBoolean("child"));
            citizen.setTextureDirty();
        } else if (look.contains("nbt")) {
            try {
                body.load(look.getCompound("nbt"));
            } catch (RuntimeException ignored) {
                // sem a aparência exata, fica a aparência padrão dessa criatura
            }
        }
        // o colono sem dados da colônia ignora nome próprio; o nome vem pelo evento do nome em cima da cabeça
        body.setCustomName(Component.literal(name));
        BODIES.put(player.getUUID(), new Cached(key, body, name));
        return body;
    }

    /** O corpo desenhado segue o jogador: posição, olhar, pose, passos, braço e o que está nas mãos */
    private static void copyState(AbstractClientPlayer player, LivingEntity body) {
        body.setPos(player.getX(), player.getY(), player.getZ());
        body.xo = player.xo;
        body.yo = player.yo;
        body.zo = player.zo;
        body.setYRot(player.getYRot());
        body.setXRot(player.getXRot());
        body.yRotO = player.yRotO;
        body.xRotO = player.xRotO;
        body.yBodyRot = player.yBodyRot;
        body.yBodyRotO = player.yBodyRotO;
        body.yHeadRot = player.yHeadRot;
        body.yHeadRotO = player.yHeadRotO;
        body.setPose(player.getPose());
        body.setShiftKeyDown(player.isShiftKeyDown());
        body.setInvisible(player.isInvisible());
        LimbAnimatorAccessor from = (LimbAnimatorAccessor) player.walkAnimation;
        LimbAnimatorAccessor to = (LimbAnimatorAccessor) body.walkAnimation;
        to.setPos(player.walkAnimation.position());
        to.setSpeed(from.getSpeed());
        to.setPrevSpeed(from.getPrevSpeed());
        body.swingTime = player.swingTime;
        body.swinging = player.swinging;
        body.attackAnim = player.attackAnim;
        body.oAttackAnim = player.oAttackAnim;
        body.swingingArm = player.swinging ? player.swingingArm : null;
        body.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem());
        body.setItemSlot(EquipmentSlot.OFFHAND, player.getOffhandItem());
        body.tickCount = player.tickCount;
    }

    /** A mesma janela que abre ao clicar no colono de verdade */
    static void openCitizenWindow(DisguiseCitizenWindowPacket packet) {
        var dimension = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(packet.dimension()));
        var colony = com.minecolonies.api.colony.IColonyManager.getInstance().getColonyView(packet.colonyId(), dimension);
        if (colony == null) return;
        var citizen = colony.getCitizen(packet.citizenId());
        if (citizen != null) new com.minecolonies.core.client.gui.WindowInteraction(citizen).open();
    }
}
