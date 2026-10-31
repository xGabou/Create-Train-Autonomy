package net.Gabou.createtrainmining.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import org.spongepowered.asm.mixin.injection.Coerce;
import net.Gabou.createtrainmining.core.TrackPlacementContext;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DeployerMovementBehaviour.class)
public class DeployerTrackPlacementMixin {


    @WrapMethod(method = "activate", remap = false)
    private void createtrainmining$placementContext(MovementContext context, BlockPos pos,
            DeployerFakePlayer player, @Coerce Object mode, Operation<Void> original) {
        boolean previous = TrackPlacementContext.LEVEL_TRAIN.get();
        TrackPlacementContext.LEVEL_TRAIN.set(context.contraption instanceof CarriageContraption
                && Math.abs(context.rotation.apply(new Vec3(0, 1, 0)).y - 1) < 1e-4);
        try {
            original.call(context, pos, player, mode);
        } finally {
            TrackPlacementContext.LEVEL_TRAIN.set(previous);
        }
    }
}
