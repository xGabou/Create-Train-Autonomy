package net.Gabou.createtrainmining.mixin;

import com.simibubi.create.content.trains.entity.Train;

import net.Gabou.createtrainmining.core.TrainAutomationManager;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Train.class)
public abstract class TrainTickMixin {
    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/simibubi/create/content/trains/schedule/ScheduleRuntime;tick(Lnet/minecraft/world/level/Level;)V",
                            ordinal = 0),
            remap = false)
    private void automation$beforeNavigation(Level level, CallbackInfo ci) {
        TrainAutomationManager.beforeTrainTick((Train) (Object) this, level);
    }
}
