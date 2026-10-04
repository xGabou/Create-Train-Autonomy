package net.Gabou.createtrainmining.client.ponder;

import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.block.ModBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** Registers the controller's Ponder scene and lists it under Create's railway tag. */
public final class ControllerPonderPlugin implements PonderPlugin {
    private static final ResourceLocation TRAIN_RELATED =
            new ResourceLocation("create", "train_related");

    @Override
    public String getModId() {
        return Createtrainmining.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ModBlocks.CONTROLLER.getId())
                .addStoryBoard(
                        "train_automation_controller",
                        ControllerPonderScenes::automation,
                        TRAIN_RELATED);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.addToTag(TRAIN_RELATED).add(ModBlocks.CONTROLLER.getId());
    }
}
