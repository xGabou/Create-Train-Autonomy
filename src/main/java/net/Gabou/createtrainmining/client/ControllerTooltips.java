package net.Gabou.createtrainmining.client;

import com.simibubi.create.foundation.item.ItemDescription;

import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.block.ModBlocks;
import net.createmod.catnip.lang.FontHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Reuse Create's localized Shift-description formatting without making Ponder scenes. */
@EventBusSubscriber(modid = Createtrainmining.MODID, value = Dist.CLIENT)
public final class ControllerTooltips {
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(ModBlocks.CONTROLLER_ITEM.get())) return;
        var description =
                ItemDescription.create(
                        ModBlocks.CONTROLLER_ITEM.get(), FontHelper.Palette.STANDARD_CREATE);
        if (description != null) event.getToolTip().addAll(1, description.getCurrentLines());
    }
}
