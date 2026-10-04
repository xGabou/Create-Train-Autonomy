package net.Gabou.createtrainmining.client;

import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.block.ModBlocks;
import net.Gabou.createtrainmining.client.ponder.ControllerPonderPlugin;
import net.Gabou.createtrainmining.network.ControllerStatusPayload;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Createtrainmining.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ControllerClient {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(
                    ModBlocks.CONTROLLER_MENU.get(), ControllerScreen::new);
            PonderIndex.addPlugin(new ControllerPonderPlugin());
        });
    }

    public static void receivePause(net.Gabou.createtrainmining.network.ActorPausePayload packet) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var entity = level.getEntity(packet.entityId());
        if (entity instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity c
                && c.getContraption() != null)
            net.Gabou.createtrainmining.core.TrainActorController.applyPause(
                    c.getContraption(), packet.filter(), packet.paused());
    }

    public static void receive(ControllerStatusPayload packet) {
        if (Minecraft.getInstance().screen instanceof ControllerScreen screen
                && screen.getMenu().containerId == packet.menuId()
                && packet.status() != null) screen.receive(packet.status());
    }
}
