package net.Gabou.createtrainmining.compat.computercraft;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.block.TrainAutomationControllerBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** This class is only loaded inside the computercraft presence guard. */
public final class ComputerCraftIntegration {
    public static final Capability<IPeripheral> PERIPHERAL =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static void register(IEventBus bus) {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, ComputerCraftIntegration::attach);
    }

    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof TrainAutomationControllerBlockEntity entity)) return;
        LazyOptional<IPeripheral> peripheral =
                LazyOptional.of(() -> new TrainControllerPeripheral(entity));
        event.addCapability(new ResourceLocation(Createtrainmining.MODID, "peripheral"),
                new ICapabilityProvider() {
                    @Override
                    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
                        return capability == PERIPHERAL ? peripheral.cast() : LazyOptional.empty();
                    }
                });
        event.addListener(peripheral::invalidate);
    }
}
