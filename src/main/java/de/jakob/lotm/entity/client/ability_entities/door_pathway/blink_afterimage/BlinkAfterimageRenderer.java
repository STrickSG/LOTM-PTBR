package de.jakob.lotm.entity.client.ability_entities.door_pathway.blink_afterimage;

import com.mojang.authlib.GameProfile;
import de.jakob.lotm.entity.custom.ability_entities.door_pathway.BlinkAfterimageEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlinkAfterimageRenderer extends MobRenderer<BlinkAfterimageEntity, PlayerModel<BlinkAfterimageEntity>> {
    private static final Map<UUID, ResourceLocation> SKIN_CACHE = new ConcurrentHashMap<>();

    public BlinkAfterimageRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(BlinkAfterimageEntity entity) {
        UUID ownerUUID = entity.getOwnerUUID();

        if (ownerUUID == null) {
            return entity.getDefaultSkinTexture();
        }

        // a skin de verdade chega depois (é baixada); antes disso vem a padrão, que não pode ficar guardada para sempre
        ResourceLocation fallback = DefaultPlayerSkin.get(ownerUUID).texture();
        ResourceLocation cached = SKIN_CACHE.get(ownerUUID);
        if (cached != null && !cached.equals(fallback)) {
            return cached;
        }

        ResourceLocation playerSkin = getPlayerSkin(ownerUUID);
        if (!playerSkin.equals(fallback)) SKIN_CACHE.put(ownerUUID, playerSkin);
        return playerSkin;
    }

    private ResourceLocation getPlayerSkin(UUID playerUUID) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null) {
            Player player = mc.level.getPlayerByUUID(playerUUID);
            if (player instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer) {
                return clientPlayer.getSkin().texture();
            }
        }

        if (mc.getConnection() != null) {
            PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(playerUUID);
            if (playerInfo != null) {
                return playerInfo.getSkin().texture();
            }
        }

        try {
            GameProfile profile = new GameProfile(playerUUID, null);
            PlayerSkin skin = mc.getSkinManager().getInsecureSkin(profile);
            return skin.texture();
        } catch (Exception e) {
            // If anything goes wrong, fall through to default
        }

        PlayerSkin defaultSkin = DefaultPlayerSkin.get(playerUUID);
        return defaultSkin.texture();
    }

    /**
     * Clears the skin cache. Can be called when needed (e.g., on resource reload)
     */
    public static void clearSkinCache() {
        SKIN_CACHE.clear();
    }
}