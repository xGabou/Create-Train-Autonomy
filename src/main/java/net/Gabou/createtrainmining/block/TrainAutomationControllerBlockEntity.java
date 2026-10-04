package net.Gabou.createtrainmining.block;

import net.Gabou.createtrainmining.core.TrainController;
import net.Gabou.createtrainmining.network.ControllerMenu;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TrainAutomationControllerBlockEntity extends BlockEntity
        implements MenuProvider {
    private CompoundTag saved = new CompoundTag();
    private TrainController controller;

    public TrainAutomationControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CONTROLLER_ENTITY.get(), pos, state);
    }

    public TrainController controller() {
        if (level == null || level.isClientSide || level.getServer() == null)
            throw new IllegalStateException("Controller is available on the server only");
        if (controller == null) {
            controller =
                    new TrainController(
                            level.getServer(),
                            this::setChanged,
                            () -> level.getBestNeighborSignal(worldPosition));
            controller.load(saved);
        }
        return controller;
    }

    public static void tick(
            Level level,
            BlockPos pos,
            BlockState state,
            TrainAutomationControllerBlockEntity entity) {
        entity.controller().tick();
        // The lamp observes existing state. Block updates occur only on visual changes.
        if (level.getGameTime() % 10 == 0) {
            var c = entity.controller();
            ControllerLamp lamp = ControllerLamp.INACTIVE;
            if (!c.getLastError().isEmpty()
                    || c.getControlMode() == net.Gabou.createtrainmining.api.ControlMode.ERROR)
                lamp = ControllerLamp.ERROR;
            else if (c.isEnabled()
                    || c.getControlMode()
                            == net.Gabou.createtrainmining.api.ControlMode.DIRECT_CONTROL
                    || c.getControlMode()
                            == net.Gabou.createtrainmining.api.ControlMode.SCHEDULE_CONTROL) {
                try {
                    lamp =
                            Math.abs(c.getCurrentSpeed()) > 0.0001
                                    ? ControllerLamp.RUNNING
                                    : ControllerLamp.WAITING;
                } catch (RuntimeException unavailable) {
                    lamp = ControllerLamp.ERROR;
                }
            }
            if (state.getValue(TrainAutomationControllerBlock.LAMP) != lamp)
                level.setBlock(pos, state.setValue(TrainAutomationControllerBlock.LAMP, lamp), 2);
        }
    }

    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Controller", controller == null ? saved : controller.save());
    }

    public void load(CompoundTag tag) {
        super.load(tag);
        saved = tag.getCompound("Controller").copy();
    }

    private void detach() {
        if (controller != null && level != null && !level.isClientSide) {
            controller.unload();
            saved = controller.save();
            controller = null;
        }
    }

    public void setRemoved() {
        detach();
        super.setRemoved();
    }

    public void onChunkUnloaded() {
        detach();
    }

    public Component getDisplayName() {
        return Component.translatable("block.createtrainmining.train_automation_controller");
    }

    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ControllerMenu(id, inv, worldPosition);
    }
}
