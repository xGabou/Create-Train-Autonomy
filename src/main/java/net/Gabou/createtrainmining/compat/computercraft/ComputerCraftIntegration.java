package net.Gabou.createtrainmining.compat.computercraft;

import dan200.computercraft.api.peripheral.PeripheralCapability;

import net.Gabou.createtrainmining.block.ModBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** This class is only loaded inside the computercraft presence guard. */
public final class ComputerCraftIntegration {
    public static void register(IEventBus bus) {
        bus.addListener(
                (RegisterCapabilitiesEvent event) ->
                        event.registerBlockEntity(
                                PeripheralCapability.get(),
                                ModBlocks.CONTROLLER_ENTITY.get(),
                                (entity, side) -> new TrainControllerPeripheral(entity)));
    }
}
