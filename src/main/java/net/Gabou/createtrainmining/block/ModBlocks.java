package net.Gabou.createtrainmining.block;

import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.network.ControllerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, Createtrainmining.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Createtrainmining.MODID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Createtrainmining.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Createtrainmining.MODID);
    public static final RegistryObject<TrainAutomationControllerBlock> CONTROLLER =
            BLOCKS.register(
                    "train_automation_controller",
                    () ->
                            new TrainAutomationControllerBlock(
                                    BlockBehaviour.Properties.of()
                                            .mapColor(MapColor.METAL)
                                            .noOcclusion()
                                            .strength(3.5f)
                                            .requiresCorrectToolForDrops()));
    public static final RegistryObject<BlockItem> CONTROLLER_ITEM =
            ITEMS.register("train_automation_controller",
                    () -> new BlockItem(CONTROLLER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<TrainAutomationControllerBlockEntity>>
            CONTROLLER_ENTITY =
                    ENTITIES.register(
                            "train_automation_controller",
                            () ->
                                    BlockEntityType.Builder.of(
                                                    TrainAutomationControllerBlockEntity::new,
                                                    CONTROLLER.get())
                                            .build(null));
    public static final RegistryObject<MenuType<ControllerMenu>> CONTROLLER_MENU =
            MENUS.register(
                    "train_automation_controller",
                    () ->
                            IForgeMenuType.create(
                                    (id, inv, data) ->
                                            new ControllerMenu(id, inv, data.readBlockPos())));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
