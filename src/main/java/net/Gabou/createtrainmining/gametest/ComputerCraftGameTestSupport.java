package net.Gabou.createtrainmining.gametest;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.PeripheralCapability;

import net.Gabou.createtrainmining.compat.computercraft.TrainControllerPeripheral;
import net.Gabou.createtrainmining.core.TrainController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/** Loaded only in the optional CC:Tweaked development test. */
final class ComputerCraftGameTestSupport {
    static void verify(
            GameTestHelper helper, ServerLevel level, BlockPos pos, TrainController controller) {
        var peripheral = level.getCapability(PeripheralCapability.get(), pos, Direction.UP);
        helper.assertTrue(
                peripheral instanceof TrainControllerPeripheral
                        && peripheral.getType().equals("train_automation_controller"),
                "CC:Tweaked must discover the generic controller capability");
        var remote = (TrainControllerPeripheral) peripheral;
        try {
            remote.startDrive("forward", .2);
            helper.assertTrue(
                    controller.isDirectDriving(),
                    "Peripheral must drive through the generic controller");
            remote.stopDrive();
        } catch (LuaException e) {
            throw new IllegalStateException(e);
        }
    }
}
