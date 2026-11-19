package de.jakob.lotm.entity.client;

import de.jakob.lotm.entity.custom.MirrorSelfEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** O reflexo tem a sua pele: o modelo de jogador com a pele do original */
public class MirrorSelfRenderer extends HumanoidMobRenderer<MirrorSelfEntity, PlayerModel<MirrorSelfEntity>> {
    public MirrorSelfRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull MirrorSelfEntity entity) {
        UUID owner = entity.getOwner();
        if (owner == null) return DefaultPlayerSkin.getDefaultTexture();
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.get(owner).texture();
    }
}
