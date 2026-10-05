package net.Gabou.createtrainmining.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;

import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.automation.mining.MiningConfiguration;
import net.Gabou.createtrainmining.block.ControllerLamp;
import net.Gabou.createtrainmining.block.ModBlocks;
import net.Gabou.createtrainmining.block.TrainAutomationControllerBlock;
import net.Gabou.createtrainmining.client.ControllerScreen;
import net.Gabou.createtrainmining.network.ControllerMenu;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Opt-in client visual fixtures. This package is excluded from the production jar. */
@EventBusSubscriber(modid = Createtrainmining.MODID, value = Dist.CLIENT)
public final class ControllerVisualPreview {
    private static boolean started;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!started
                && Boolean.getBoolean("createtrainmining.visualPreview")
                && mc.screen instanceof TitleScreen
                && mc.getOverlay() == null) {
            started = true;
            mc.setScreen(new Preview());
        }
    }

    private static final class Preview extends Screen {
        private ControllerScreen controller;
        private int stage, frames;
        private final int previousScale = Minecraft.getInstance().options.guiScale().get();
        private static final String[] NAMES = {
            "auto-stopped",
            "scale-2-running",
            "scale-3-waiting",
            "scale-4-error-tooltip",
            "scale-4-long-train-tooltip",
            "advanced",
            "manual",
            "overflow-page-2",
            "custom-station-edit",
            "empty",
            "cabinet-models",
            "choose-profile",
            "choose-train-and-profile",
            "profile-selection-confirmed"
        };
        private boolean initialized;

        Preview() {
            super(Component.literal("Controller presentation fixtures"));
        }

        @Override
        protected void init() {
            if (!initialized) {
                initialized = true;
                setup();
            }
        }

        private void setup() {
            frames = 0;
            int scale = stage < 4 ? new int[] {0, 2, 3, 4}[stage] : 4;
            minecraft.options.guiScale().set(scale);
            minecraft.resizeDisplay();
            var inventory = new Inventory(null);
            controller =
                    new ControllerScreen(
                            new ControllerMenu(0, inventory, BlockPos.ZERO),
                            inventory,
                            Component.translatable(
                                    "block.createtrainmining.train_automation_controller"));
            controller.init(minecraft, width, height);
            CompoundTag data = fixture();
            if (stage == 1) {
                data.putBoolean("Enabled", true);
                data.putString("State", "MINING");
                data.putString("Mode", "DIRECT_CONTROL");
            }
            if (stage == 2) {
                data.putBoolean("Enabled", true);
                data.putDouble("Speed", 0);
                data.putDouble("SpeedRatio", 0);
                data.putString("State", "UNLOADING");
                data.putString("Mode", "SCHEDULE_CONTROL");
                data.putString("Station", "MINING");
            }
            if (stage == 3) {
                data.putString("Mode", "ERROR");
                data.putString("State", "ERROR");
                data.putString(
                        "Error",
                        "Return station could not be reached: Northern Mountain Railway Storage and"
                            + " Maintenance Terminus");
            }
            if (stage == 4) {
                data.putString(
                        "Train",
                        "The Northern Mountain Railway Construction and Cargo Service Express");
                data.getList("Trains", Tag.TAG_COMPOUND)
                        .getCompound(0)
                        .putString("Name", data.getString("Train"));
                data.putString(
                        "Station", "Northern Mountain Railway Storage and Maintenance Terminus");
                data.putIntArray("CarriageLengths", new int[] {10, 8, 12, 4, 8, 10, 12, 5, 7, 9});
                data.putBoolean("DoubleEnded", true);
            }
            if (stage == 7) {
                data.putString("Profile", "preview:translated_profile");
                data.put("Profiles", strings("mining", "preview:translated_profile"));
                ListTag fields = new ListTag();
                for (int i = 0; i < 17; i++) {
                    CompoundTag field = new CompoundTag();
                    field.putString("Key", "field_" + i);
                    field.putString("Label", "Sehr lange \u00fcbersetzte Konfiguration " + (i + 1));
                    field.putString("Type", "NUMBER");
                    field.putString("Value", "0.25");
                    field.putDouble("Min", 0);
                    field.putDouble("Max", 1);
                    fields.add(field);
                }
                data.put("Fields", fields);
            }
            if (stage == 9) data = new CompoundTag();
            if (stage >= 11) {
                data.putString("Profile", "");
                data.put("Fields", new ListTag());
                data.putString("State", "IDLE");
                if (stage == 12) data.remove("TrainId");
            }
            controller.receive(data);
            if (stage >= 11) {
                ScrollInput profile = selector(198, 30);
                if (profile.getState() != 0)
                    throw new IllegalStateException("Missing profile did not show its placeholder");
                if (stage == 12 && selector(16, 45).getState() != 0)
                    throw new IllegalStateException("Missing train did not show its placeholder");
                if (stage == 13) {
                    profile.onClick(profile.getX() + 1, profile.getY() + 1);
                    if (profile.getState() != 1)
                        throw new IllegalStateException("Only available profile could not be clicked");
                    profile.setState(0);
                    profile.mouseScrolled(profile.getX() + 1, profile.getY() + 1, 0, -1);
                    if (profile.getState() != 1)
                        throw new IllegalStateException("Only available profile could not be scrolled");
                    controller.receive(fixture());
                    if (selector(198, 30).getState() != 0)
                        throw new IllegalStateException("Confirmed profile retained its placeholder");
                    // A configuration input must appear after the server confirms selection.
                    selector(226, 82);
                }
            }
            if (stage == 5) click(78, 186);
            if (stage == 6) click(56, 186);
            if (stage == 7) click(246, 186);
            if (stage == 8) {
                click(206, 61);
                var input =
                        controller.children().stream()
                                .filter(
                                        w ->
                                                w
                                                        instanceof
                                                        net.minecraft.client.gui.components.EditBox)
                                .map(w -> (net.minecraft.client.gui.components.EditBox) w)
                                .findFirst()
                                .orElseThrow();
                input.setValue("NORTHERN_MOUNTAIN_*");
                input.setFocused(true);
                controller.setFocused(input);
                data.putDouble("Inventory", .64);
                controller.receive(data);
                if (!input.getValue().equals("NORTHERN_MOUNTAIN_*"))
                    throw new IllegalStateException("Status update destroyed an edit");
            }
            for (var listener : controller.children()) {
                if (listener instanceof AbstractWidget widget
                        && widget.visible
                        && (widget.getX() < controller.getGuiLeft()
                                || widget.getY() < controller.getGuiTop()
                                || widget.getX() + widget.getWidth()
                                        > controller.getGuiLeft() + ControllerScreen.PANEL_WIDTH
                                || widget.getY() + widget.getHeight()
                                        > controller.getGuiTop() + ControllerScreen.PANEL_HEIGHT))
                    throw new IllegalStateException(
                            "Widget escapes controller frame: " + widget.getMessage());
            }
            if (stage == 0) {
                ScrollInput number =
                        controller.children().stream()
                                .filter(
                                        w ->
                                                w instanceof ScrollInput input
                                                        && input.getX()
                                                                == controller.getGuiLeft() + 226
                                                        && input.getY()
                                                                == controller.getGuiTop() + 82)
                                .map(w -> (ScrollInput) w)
                                .findFirst()
                                .orElseThrow();
                int prior = number.getState();
                number.mouseScrolled(number.getX() + 1, number.getY() + 1, 0, 1);
                controller.receive(data);
                if (number.getState() != prior + 1)
                    throw new IllegalStateException("Status update reset a scroll edit");
                // Restore the fixture for the screenshot without recreating widgets.
                number.setState(prior);
            }
        }

        private void click(int x, int y) {
            controller.children().stream()
                    .filter(
                            w ->
                                    w instanceof IconButton button
                                            && button.getX() == controller.getGuiLeft() + x
                                            && button.getY() == controller.getGuiTop() + y)
                    .map(w -> (IconButton) w)
                    .findFirst()
                    .orElseThrow()
                    .onClick(0, 0);
        }

        private ScrollInput selector(int x, int y) {
            return controller.children().stream()
                    .filter(w -> w instanceof ScrollInput input
                            && input.getX() == controller.getGuiLeft() + x
                            && input.getY() == controller.getGuiTop() + y)
                    .map(w -> (ScrollInput) w)
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public void tick() {
            if (frames > 25) {
                stage++;
                if (stage == NAMES.length) {
                    minecraft.options.guiScale().set(previousScale);
                    minecraft.resizeDisplay();
                    minecraft.stop();
                    return;
                }
                setup();
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mx, int my, float partial) {
            graphics.fill(0, 0, width, height, 0xff28302c);
            if (stage == 10) gallery(graphics);
            else {
                int x = stage == 4 ? controller.getGuiLeft() + 25 : controller.getGuiLeft() + 20;
                int y =
                        stage == 3
                                ? controller.getGuiTop() + 214
                                : stage == 4 ? controller.getGuiTop() + 50 : 0;
                controller.render(graphics, x, y, partial);
            }
            graphics.flush();
            frames++;
            if (frames == 20) {
                Path output =
                        minecraft
                                .gameDirectory
                                .toPath()
                                .resolve("../build/reference/presentation")
                                .normalize();
                try {
                    Files.createDirectories(output);
                    try (NativeImage image =
                            Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                        image.writeToFile(output.resolve(NAMES[stage] + ".png"));
                    }
                    String report =
                            NAMES[stage]
                                    + ": requested="
                                    + minecraft.options.guiScale().get()
                                    + ", effective="
                                    + minecraft.getWindow().getGuiScale()
                                    + ", viewport="
                                    + width
                                    + "x"
                                    + height
                                    + "\n";
                    Files.writeString(
                            output.resolve("verification.txt"),
                            report,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
                    System.out.print("PRESENTATION CHECK: " + report);
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }

        private void gallery(GuiGraphics graphics) {
            graphics.drawString(
                    font, "Cabinet: 4 lamp states x 4 horizontal facings", 12, 10, 0xdedbc9, false);
            var directions =
                    List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
            for (int lamp = 0; lamp < 4; lamp++) {
                for (int facing = 0; facing < 4; facing++) {
                    int x = 18 + facing * 77, y = 42 + lamp * 48;
                    var state =
                            ModBlocks.CONTROLLER
                                    .get()
                                    .defaultBlockState()
                                    .setValue(
                                            TrainAutomationControllerBlock.FACING,
                                            directions.get(facing))
                                    .setValue(
                                            TrainAutomationControllerBlock.LAMP,
                                            ControllerLamp.values()[lamp]);
                    GuiGameElement.of(state)
                            .scale(26)
                            .rotateBlock(20, 45, 0)
                            .at(x + 24, y + 12)
                            .render(graphics);
                    graphics.drawString(
                            font,
                            ControllerLamp.values()[lamp].getSerializedName()
                                    + " "
                                    + directions.get(facing).name().substring(0, 1),
                            x,
                            y + 23,
                            0xaab3a2,
                            false);
                    if (frames == 0) {
                        var sprites =
                                minecraft
                                        .getBlockRenderer()
                                        .getBlockModel(state)
                                        .getQuads(
                                                state,
                                                null,
                                                net.minecraft.util.RandomSource.create(0))
                                        .stream()
                                        .map(q -> q.getSprite().contents().name().toString())
                                        .distinct()
                                        .toList();
                        if (sprites.stream()
                                .noneMatch(
                                        sprite ->
                                                sprite.endsWith(
                                                        "controller_lamp_"
                                                                + state.getValue(
                                                                                TrainAutomationControllerBlock
                                                                                        .LAMP)
                                                                        .getSerializedName())))
                            throw new IllegalStateException(
                                    "Incorrect lamp model textures: " + state);
                    }
                }
            }
            graphics.renderItem(ModBlocks.CONTROLLER_ITEM.toStack(), width - 24, 8);
        }
    }

    private static CompoundTag fixture() {
        CompoundTag tag = new CompoundTag();
        tag.putString("TrainId", "1ad6b491-345e-45a2-bbc8-77c1e842e131");
        tag.putString("Train", "Miner");
        tag.putString("TrainIcon", "create:traditional");
        tag.putIntArray("CarriageLengths", new int[] {12, 8, 8});
        tag.putString("Profile", "mining");
        tag.putString("Backend", "native");
        tag.putString("Mode", "IDLE");
        tag.putString("State", "STOPPED");
        tag.putString("Station", "None");
        tag.putDouble("Inventory", .63);
        tag.putDouble("Speed", .1);
        tag.putDouble("SpeedRatio", .2);
        tag.put("Profiles", strings("mining"));
        tag.put("Backends", strings("native", "railways_additions"));
        tag.put(
                "Stations",
                strings(
                        "MINING",
                        "Main Yard",
                        "Northern Mountain Railway Storage and Maintenance Terminus"));
        ListTag trains = new ListTag();
        CompoundTag train = new CompoundTag();
        train.putString("Id", tag.getString("TrainId"));
        train.putString("Name", "Miner");
        trains.add(train);
        tag.put("Trains", trains);
        ListTag fields = new ListTag();
        for (var field : MiningConfiguration.schema()) {
            CompoundTag f = new CompoundTag();
            f.putString("Key", field.key());
            f.putString("Label", field.label());
            f.putString("Type", field.type().name());
            f.putString("Editor", field.editor().name());
            f.putBoolean("Advanced", field.advanced());
            f.putDouble("Min", field.min());
            f.putDouble("Max", field.max());
            f.putString(
                    "Value",
                    field.key().equals("return_station")
                            ? "MINING"
                            : field.defaultValue().toString());
            f.put("Options", strings(field.options().toArray(String[]::new)));
            fields.add(f);
        }
        tag.put("Fields", fields);
        return tag;
    }

    private static ListTag strings(String... values) {
        ListTag tag = new ListTag();
        for (String value : values) tag.add(StringTag.valueOf(value));
        return tag;
    }
}
