package net.Gabou.createtrainmining.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.Gabou.createtrainmining.core.TrainAutomationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContraptionEntity.class)
public abstract class CarriageActorTickMixin {
    @Inject(method = "tickActors", at = @At("HEAD"), remap = false)
    private void automation$beforeActors(CallbackInfo ci) {
        if ((Object) this instanceof CarriageContraptionEntity carriage)
            TrainAutomationManager.beforeActorTick(carriage);
    }
}
