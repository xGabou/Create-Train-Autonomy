package net.Gabou.createtrainmining.mixin;

import com.simibubi.create.AllTags;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fix Create #8448: moving drills skip breakable blocks without collision. */
@Mixin(DrillMovementBehaviour.class)
public abstract class DrillMovementBehaviourMixin extends BlockBreakingMovementBehaviour {
    @Override
    protected float getBlockBreakingSpeed(com.simibubi.create.content.contraptions.behaviour.MovementContext context) {
        if (context.contraption instanceof com.simibubi.create.content.trains.entity.CarriageContraption
                && context.data.contains("BreakingPos")) {
            var pos = net.createmod.catnip.nbt.NBTHelper.readBlockPos(context.data, "BreakingPos");
            if (context.world.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.OBSIDIAN)
                    || context.world.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN))
                return Math.max(50, super.getBlockBreakingSpeed(context));
        }
        return super.getBlockBreakingSpeed(context);
    }

    @Inject(method = "canBreak", at = @At("RETURN"), cancellable = true, remap = false)
    private void createtrainmining$breakNonCollidingBlocks(
            Level world, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() || !state.getCollisionShape(world, pos).isEmpty())
            return;

        // Keep the normal breaker exclusions (air, fluids, unbreakable blocks and
        // create:non_breakable), as well as the moving drill's track protection.
        // Create still handles progress, drops, storage and water replacement.
        cir.setReturnValue(super.canBreak(world, pos, state) && !AllTags.AllBlockTags.TRACKS.matches(state));
    }
}
