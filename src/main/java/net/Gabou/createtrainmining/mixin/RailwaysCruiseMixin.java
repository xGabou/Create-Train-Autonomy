package net.Gabou.createtrainmining.mixin;

import com.simibubi.create.content.trains.entity.Train;

import net.Gabou.createtrainmining.core.TrainAutomationManager;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The addon otherwise accelerates a second time outside Train.tick, even during schedule control.
 */
@Pseudo
@Mixin(
        targets = "com.vodmordia.railwaysuntold_additions.contraption.CruiseControlManager",
        remap = false)
public abstract class RailwaysCruiseMixin {
    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lcom/simibubi/create/content/trains/entity/Train;targetSpeed:D",
                            opcode = Opcodes.PUTFIELD),
            require = 1)
    private static void automation$targetSpeed(Train train, double speed) {
        if (!TrainAutomationManager.isManaged(train)) train.targetSpeed = speed;
    }

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lcom/simibubi/create/content/trains/entity/Train;manualTick:Z",
                            opcode = Opcodes.PUTFIELD),
            require = 1)
    private static void automation$manualTick(Train train, boolean manual) {
        if (!TrainAutomationManager.isManaged(train)) train.manualTick = manual;
    }

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/simibubi/create/content/trains/entity/Train;approachTargetSpeed(F)V"),
            require = 1)
    private static void automation$acceleration(Train train, float acceleration) {
        if (!TrainAutomationManager.isManaged(train)) train.approachTargetSpeed(acceleration);
    }
}
