package de.jakob.lotm.addons.colonies.mixins;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.client.render.modeltype.registry.IModelTypeRegistry;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.client.render.RenderBipedCitizen;
import de.jakob.lotm.addons.colonies.MarionetteSkinPacket;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marionete-monstro que virou colono: corpo humano (modelo de jogador) com a skin de um Beyonder Errante */
@Mixin(value = RenderBipedCitizen.class, remap = false)
public abstract class RenderBipedCitizenMixin extends MobRenderer<AbstractEntityCitizen, CitizenModel<AbstractEntityCitizen>> {
    private RenderBipedCitizenMixin(EntityRendererProvider.Context context, CitizenModel<AbstractEntityCitizen> model, float shadow) {
        super(context, model, shadow);
    }

    @Inject(method = "setupMainModelFrom", at = @At("TAIL"))
    private void lotmcraft$marionetteModel(AbstractEntityCitizen citizen, CallbackInfo ci) {
        if (MarionetteSkinPacket.skinOf(citizen.getId()) == null) return;
        var type = IModelTypeRegistry.getInstance().getModelType(ModModelTypes.CUSTOM_ID);
        CitizenModel<AbstractEntityCitizen> human = type == null ? null : type.getMaleModel();
        if (human == null) return;
        human.young = citizen.isBaby();
        human.riding = citizen.getVehicle() != null;
        human.attackTime = citizen.attackAnim;
        this.model = human;
    }

    @Inject(method = "getTextureLocation(Lcom/minecolonies/api/entity/citizen/AbstractEntityCitizen;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true)
    private void lotmcraft$marionetteSkin(AbstractEntityCitizen citizen, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation skin = MarionetteSkinPacket.skinOf(citizen.getId());
        if (skin != null) cir.setReturnValue(skin);
    }
}
