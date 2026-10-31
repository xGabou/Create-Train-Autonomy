package net.Gabou.createtrainmining.mixin;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A level train's deployer should not infer a ramp from adjacent terrain or fluid-generated stone. */
@Mixin(TrackBlock.class)
public class TrackPlacementMixin {
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void createtrainmining$levelTracks(BlockPlaceContext ctx, CallbackInfoReturnable<BlockState> cir) {
        if (!net.Gabou.createtrainmining.core.TrackPlacementContext.LEVEL_TRAIN.get()) return;
        var state = cir.getReturnValue();
        if (state == null) return;
        TrackShape flat = switch (state.getValue(TrackBlock.SHAPE)) {
            case AE, AW -> TrackShape.XO;
            case AN, AS -> TrackShape.ZO;
            default -> null;
        };
        if (flat != null) cir.setReturnValue(state.setValue(TrackBlock.SHAPE, flat));
    }
}
