package net.Gabou.createtrainmining.mixin;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;

import net.Gabou.createtrainmining.core.TrainAutomationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CarriageContraptionEntity.class)
public abstract class PlayerControlsMixin {
    @Inject(method = "startControlling", at = @At("RETURN"), remap = false)
    private void automation$playerTakeover(
            BlockPos pos, Player player, CallbackInfoReturnable<Boolean> cir) {
        var entity = (CarriageContraptionEntity) (Object) this;
        if (cir.getReturnValue() && !entity.level().isClientSide && entity.getCarriage() != null)
            TrainAutomationManager.playerTakeover(entity.getCarriage().train, entity.level());
    }
}
