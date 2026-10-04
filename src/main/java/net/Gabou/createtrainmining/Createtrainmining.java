package net.Gabou.createtrainmining;

import net.Gabou.createtrainmining.automation.mining.MiningAutomationProfile;
import net.Gabou.createtrainmining.block.ModBlocks;
import net.Gabou.createtrainmining.compat.computercraft.ComputerCraftIntegration;
import net.Gabou.createtrainmining.compat.railwaysadditions.RailwaysAdditionsIntegration;
import net.Gabou.createtrainmining.core.*;
import net.Gabou.createtrainmining.drive.NativeCreateDriveBackend;
import net.Gabou.createtrainmining.network.ControllerNetworking;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(Createtrainmining.MODID)
public final class Createtrainmining {
    /** Keep the published namespace stable; the display name and all features are generic. */
    public static final String MODID = "createtrainmining";

    public Createtrainmining(IEventBus bus) {
        DriveBackendRegistry.register("native", NativeCreateDriveBackend::new);
        AutomationProfileRegistry.register("mining", MiningAutomationProfile::new);
        RailwaysAdditionsIntegration.register();
        ModBlocks.register(bus);
        bus.addListener(ControllerNetworking::register);
        bus.addListener(this::creativeTab);
        if (ModList.get().isLoaded("computercraft")) ComputerCraftIntegration.register(bus);
        NeoForge.EVENT_BUS.addListener(
                (ServerStoppingEvent event) -> TrainAutomationManager.shutdown(event.getServer()));
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS)
            event.accept(ModBlocks.CONTROLLER_ITEM.get());
    }
}
