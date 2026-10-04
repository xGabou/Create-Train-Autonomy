package net.Gabou.createtrainmining.block;

import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.network.ControllerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Createtrainmining.MODID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Createtrainmining.MODID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Createtrainmining.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Createtrainmining.MODID);
    public static final DeferredBlock<TrainAutomationControllerBlock> CONTROLLER =
            BLOCKS.register(
                    "train_automation_controller",
                    () ->
                            new TrainAutomationControllerBlock(
                                    BlockBehaviour.Properties.of()
                                            .mapColor(MapColor.METAL)
                                            .noOcclusion()
                                            .strength(3.5f)
                                            .requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> CONTROLLER_ITEM =
            ITEMS.registerSimpleBlockItem("train_automation_controller", CONTROLLER);
    public static final DeferredHolder<
                    BlockEntityType<?>, BlockEntityType<TrainAutomationControllerBlockEntity>>
            CONTROLLER_ENTITY =
                    ENTITIES.register(
                            "train_automation_controller",
                            () ->
                                    BlockEntityType.Builder.of(
                                                    TrainAutomationControllerBlockEntity::new,
                                                    CONTROLLER.get())
                                            .build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> CONTROLLER_MENU =
            MENUS.register(
                    "train_automation_controller",
                    () ->
                            IMenuTypeExtension.create(
                                    (id, inv, data) ->
                                            new ControllerMenu(id, inv, data.readBlockPos())));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
