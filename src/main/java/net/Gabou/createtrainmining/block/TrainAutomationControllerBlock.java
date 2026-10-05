package net.Gabou.createtrainmining.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TrainAutomationControllerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<ControllerLamp> LAMP =
            EnumProperty.create("lamp", ControllerLamp.class);
    private static final VoxelShape SHAPE =
            Shapes.or(
                    Block.box(0, 0, 0, 16, 2, 16),
                    Block.box(1, 2, 1, 15, 13, 15),
                    Block.box(0, 13, 0, 16, 15, 16));
    public static final MapCodec<TrainAutomationControllerBlock> CODEC =
            simpleCodec(TrainAutomationControllerBlock::new);

    public TrainAutomationControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(LAMP, ControllerLamp.INACTIVE));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LAMP);
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrainAutomationControllerBlockEntity(pos, state);
    }

    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        // Only real server worlds run the controller; Ponder scenes animate the lamp themselves.
        return !(level instanceof net.minecraft.server.level.ServerLevel)
                ? null
                : createTickerHelper(
                        type,
                        ModBlocks.CONTROLLER_ENTITY.get(),
                        TrainAutomationControllerBlockEntity::tick);
    }

    protected void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide
                && state.getBlock() != replacement.getBlock()
                && level.getBlockEntity(pos) instanceof TrainAutomationControllerBlockEntity entity)
            entity.controller().stop();
        super.onRemove(state, level, pos, replacement, moved);
    }

    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp
                && level.getBlockEntity(pos) instanceof TrainAutomationControllerBlockEntity entity)
            sp.openMenu(entity, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
