package net.Gabou.createtrainmining.client;

import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.block.ModBlocks;
import net.Gabou.createtrainmining.client.ponder.ControllerPonderPlugin;
import net.Gabou.createtrainmining.network.ControllerStatusPayload;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Createtrainmining.MODID, value = Dist.CLIENT)
public final class ControllerClient {
    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModBlocks.CONTROLLER_MENU.get(), ControllerScreen::new);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new ControllerPonderPlugin());
    }

    public static void receive(ControllerStatusPayload packet) {
        if (Minecraft.getInstance().screen instanceof ControllerScreen screen
                && screen.getMenu().containerId == packet.menuId()
                && packet.status() != null) screen.receive(packet.status());
    }
}
