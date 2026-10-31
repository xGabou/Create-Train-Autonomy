package net.Gabou.createtrainmining.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.drill.DrillBlock;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.Gabou.createtrainmining.Createtrainmining;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/** Development-only tests of the registered, mixin-patched Create drill behaviour. */
@GameTestHolder(Createtrainmining.MODID)
@PrefixGameTestTemplate(false)
public final class DrillGameTests {
    @GameTest(template = "framework_empty", timeoutTicks = 100)
    public static void movingDrillsBreakNonCollidingBlocks(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(pos.east(), Blocks.STONE.defaultBlockState());

        for (var state : List.of(
                Blocks.COBWEB.defaultBlockState(),
                Blocks.KELP.defaultBlockState(),
                Blocks.KELP_PLANT.defaultBlockState(),
                Blocks.SEAGRASS.defaultBlockState(),
                Blocks.TALL_SEAGRASS.defaultBlockState(),
                Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.EAST, true),
                Blocks.SCULK_VEIN.defaultBlockState().setValue(BlockStateProperties.EAST, true),
                Blocks.SHORT_GRASS.defaultBlockState())) {
            level.setBlockAndUpdate(pos, state);
            var placed = level.getBlockState(pos);
            helper.assertTrue(placed.is(state.getBlock()) && placed.getCollisionShape(level, pos).isEmpty(),
                    "Fixture must contain a non-colliding " + state);

            var contraption = contraption();
            var inventory = new ItemStackHandler(4);
            contraption.getStorage().initialize();
            contraption.getStorage().attachExternal(inventory);
            var entity = CarriageContraptionEntity.create(level, contraption);
            try {
                var context = drillContext(helper, contraption);
                context.position = Vec3.atCenterOf(pos);
                context.motion = context.relativeMotion = new Vec3(.25, 0, 0);
                var drill = (DrillMovementBehaviour) MovementBehaviour.REGISTRY.get(context.state);
                helper.assertTrue(drill.isActive(context), "Downward drills must work during sideways motion");
                drill.startMoving(context);
                drill.visitNewPosition(context, pos);
                helper.assertTrue(context.stall && context.data.contains("BreakingPos"),
                        "Moving drill must begin breaking " + state);
                for (int tick = 0; tick < 400 && level.getBlockState(pos).is(state.getBlock()); tick++)
                    drill.tick(context);
                helper.assertTrue(!level.getBlockState(pos).is(state.getBlock()) && !context.stall,
                        "Moving drill must finish breaking and release its stall for " + state);
                helper.assertTrue(level.getFluidState(pos).equals(placed.getFluidState()),
                        "Drilling must preserve the fluid in " + state);
                helper.assertTrue(level.getBlockState(pos.east()).is(Blocks.STONE),
                        "Drilling must leave adjacent support blocks alone");
                if (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)) {
                    helper.assertTrue(inventory.getStackInSlot(0).is(Items.KELP)
                                    && inventory.getStackInSlot(0).getCount() == 1,
                            "Create must collect the kelp drop into contraption storage exactly once");
                }
            } finally {
                entity.discard();
                level.removeBlock(pos, false);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 100)
    public static void movingDrillsKeepProtectedBlocksAndBreakStone(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var contraption = contraption();
        var entity = CarriageContraptionEntity.create(level, contraption);
        try {
            var context = drillContext(helper, contraption);
            var drill = (DrillMovementBehaviour) MovementBehaviour.REGISTRY.get(context.state);
            for (BlockState state : List.of(
                    Blocks.AIR.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                    Blocks.LAVA.defaultBlockState(), Blocks.BEDROCK.defaultBlockState(),
                    Blocks.BARRIER.defaultBlockState(), AllBlocks.TRACK.getDefaultState())) {
                level.setBlockAndUpdate(pos, state);
                var placed = level.getBlockState(pos);
                helper.assertTrue(placed.is(state.getBlock()), "Fixture must contain " + state);
                helper.assertTrue(!drill.canBreak(level, pos, placed), "Drill must still reject " + state);
                drill.visitNewPosition(context, pos);
                helper.assertTrue(!context.stall && !context.data.contains("BreakingPos")
                                && level.getBlockState(pos).equals(placed),
                        "Protected blocks must remain untouched without stalling: " + state);
            }
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            helper.assertTrue(drill.canBreak(level, pos, level.getBlockState(pos)),
                    "Ordinary solid blocks must remain drillable");
        } finally {
            entity.discard();
            level.removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 100)
    public static void trainDrillsBreakObsidianBeforeJamRecovery(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var contraption = contraption();
        var entity = CarriageContraptionEntity.create(level, contraption);
        try {
            for (var block : List.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN)) {
                level.setBlockAndUpdate(pos, block.defaultBlockState());
                var context = drillContext(helper, contraption);
                context.position = Vec3.atCenterOf(pos);
                context.motion = context.relativeMotion = new Vec3(.25, 0, 0);
                var drill = (DrillMovementBehaviour) MovementBehaviour.REGISTRY.get(context.state);
                drill.startMoving(context);
                drill.visitNewPosition(context, pos);
                for (int tick = 0; tick < 80 && level.getBlockState(pos).is(block); tick++) drill.tick(context);
                helper.assertTrue(!level.getBlockState(pos).is(block) && !context.stall,
                        "Train drills must finish obsidian before the 100-tick recovery threshold");
            }
        } finally {
            entity.discard();
            level.removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 100)
    public static void levelTrainTracksIgnoreFalseFluidSlopes(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(pos.east(), Blocks.COBBLESTONE.defaultBlockState());
        level.setBlockAndUpdate(pos.east().above(), Blocks.AIR.defaultBlockState());
        var player = new com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer(level, null);
        player.setYRot(-90);
        player.setXRot(90);
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var ctx = new net.minecraft.world.item.context.BlockPlaceContext(level, player,
                net.minecraft.world.InteractionHand.MAIN_HAND, AllBlocks.TRACK.asStack(), hit);
        var track = AllBlocks.TRACK.get();
        try {
            var original = track.getStateForPlacement(ctx);
            helper.assertTrue(original.getValue(com.simibubi.create.content.trains.track.TrackBlock.SHAPE)
                            == com.simibubi.create.content.trains.track.TrackShape.AE,
                    "Fixture must reproduce Create inferring a false ramp beside fluid-generated stone");
            net.Gabou.createtrainmining.core.TrackPlacementContext.LEVEL_TRAIN.set(true);
            var flat = track.getStateForPlacement(ctx);
            helper.assertTrue(flat.getValue(com.simibubi.create.content.trains.track.TrackBlock.SHAPE)
                            == com.simibubi.create.content.trains.track.TrackShape.XO,
                    "Level train deployers must place flat track");
            helper.assertTrue(flat.getValue(BlockStateProperties.WATERLOGGED),
                    "Flat track must preserve waterlogging");
        } finally {
            net.Gabou.createtrainmining.core.TrackPlacementContext.LEVEL_TRAIN.remove();
            player.discard();
        }
        helper.succeed();
    }

    private static MovementContext drillContext(GameTestHelper helper, CarriageContraption contraption) {
        var info = new StructureBlockInfo(BlockPos.ZERO,
                AllBlocks.MECHANICAL_DRILL.getDefaultState().setValue(DrillBlock.FACING, Direction.DOWN),
                new CompoundTag());
        return new MovementContext(helper.getLevel(), info, contraption);
    }

    private static CarriageContraption contraption() {
        // An unbound carriage uses Create's shared fallback storage. Give each
        // fixture its own cargo so item collection cannot leak between cases.
        var contraption = new CarriageContraption(Direction.EAST) {
            private final MountedStorageManager cargo = new MountedStorageManager();

            @Override
            public MountedStorageManager getStorage() {
                return cargo;
            }
        };
        contraption.bounds = new AABB(BlockPos.ZERO);
        contraption.getStorage().initialize();
        return contraption;
    }
}
