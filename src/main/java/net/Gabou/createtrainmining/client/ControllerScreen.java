package net.Gabou.createtrainmining.client;

import com.simibubi.create.content.trains.entity.TrainIconType;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Indicator;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;

import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.network.ControllerCommandPayload;
import net.Gabou.createtrainmining.network.ControllerMenu;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;

/**
 * Create widgets over an original railway instrument panel. Existing command semantics are
 * retained.
 */
public final class ControllerScreen extends AbstractSimiContainerScreen<ControllerMenu> {
    public static final int PANEL_WIDTH = 304, PANEL_HEIGHT = 226;
    private static final int ROWS = 6, VALUE_X = 226, VALUE_WIDTH = 66;
    private static final int INSET_TEXT = 0xdedbc9, MUTED_TEXT = 0xaab3a2;
    private static final ResourceLocation FRAME =
            ResourceLocation.fromNamespaceAndPath(
                    Createtrainmining.MODID, "textures/gui/controller.png");

    private enum Page {
        CONFIGURATION,
        MANUAL,
        ADVANCED
    }

    private record TextTip(Rect2i bounds, Component text) {}

    private record ValueControl(ScrollInput input, Consumer<String> synchronize) {}

    private CompoundTag status = new CompoundTag();
    private Page view = Page.CONFIGURATION;
    private int page, editDelay, manualSpeed = 20;
    private String manualDirection = "FORWARD", destination = "", error = "";
    private final Map<String, String> drafts = new LinkedHashMap<>(),
            pending = new LinkedHashMap<>();
    private final Map<String, ValueControl> values = new LinkedHashMap<>();
    private final Map<String, EditBox> textInputs = new LinkedHashMap<>();
    private final Set<String> customStationFields = new LinkedHashSet<>();
    private final List<TextTip> textTips = new ArrayList<>();
    private final List<GuiEventListener> configurationWidgets = new ArrayList<>();
    private IconButton start, stop, manual, advanced, previous, next;
    private Indicator indicator;
    private int pages = 1;

    public ControllerScreen(ControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        setWindowSize(PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        setWindowSize(PANEL_WIDTH, PANEL_HEIGHT);
        super.init();
        buildWidgets();
        send("refresh", "", "");
    }

    private static MutableComponent tr(String key, Object... arguments) {
        return Component.translatable("gui.createtrainmining." + key, arguments);
    }

    private Component named(String kind, String id) {
        return Component.translatableWithFallback(
                "createtrainmining." + kind + "." + id.replace(':', '.'),
                humanize(id.substring(id.indexOf(':') + 1)));
    }

    private static String humanize(String value) {
        StringBuilder text = new StringBuilder();
        for (String word : value.toLowerCase(Locale.ROOT).split("[_ ]+")) {
            if (word.isEmpty()) continue;
            if (!text.isEmpty()) text.append(' ');
            text.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return text.toString();
    }

    private IconButton icon(int x, int y, ScreenElement glyph, String tooltip, Runnable callback) {
        IconButton button =
                new IconButton(leftPos + x, topPos + y, glyph) {
                    @Override
                    protected void renderTooltip(
                            GuiGraphics graphics, int mx, int my, float partial) {}
                };
        button.setToolTip(tr(tooltip));
        button.setMessage(tr(tooltip));
        button.withCallback(callback);
        return addRenderableWidget(button);
    }

    private static ScreenElement glyph(String name) {
        var texture =
                ResourceLocation.fromNamespaceAndPath(
                        Createtrainmining.MODID, "textures/gui/" + name + ".png");
        return (graphics, x, y) -> graphics.blit(texture, x, y, 0, 0, 16, 16, 16, 16);
    }

    private int rows() {
        return view == Page.ADVANCED ? 5 : ROWS;
    }

    private int rowY(int index) {
        int row = index % rows();
        return 62 + row * 20 + (view == Page.ADVANCED && page == 0 && row > 0 ? 14 : 0);
    }

    private void buildWidgets() {
        clearWidgets();
        values.clear();
        textInputs.clear();
        configurationWidgets.clear();
        var trains = status.getList("Trains", Tag.TAG_COMPOUND);
        List<String> trainIds = new ArrayList<>();
        List<Component> trainNames = new ArrayList<>();
        for (int i = 0; i < trains.size(); i++) {
            trainIds.add(trains.getCompound(i).getString("Id"));
            trainNames.add(Component.literal(trains.getCompound(i).getString("Name")));
        }
        selection(
                16,
                45,
                94,
                tr("train"),
                trainIds,
                trainNames,
                status.getString("TrainId"),
                id -> {
                    flushEdits();
                    send("select_train", "", id);
                },
                false,
                tr("select_train"));
        List<String> profiles = strings("Profiles");
        selection(
                198,
                30,
                94,
                tr("automation"),
                profiles,
                profiles.stream().map(id -> named("profile", id)).toList(),
                status.getString("Profile"),
                id -> {
                    flushEdits();
                    drafts.clear();
                    customStationFields.clear();
                    page = 0;
                    send("set_profile", "", id);
                },
                false,
                tr("select_profile"));
        start =
                icon(
                        12,
                        186,
                        AllIcons.I_PLAY,
                        "start",
                        () -> {
                            flushEdits();
                            send("start", "", "");
                        });
        start.green = true;
        stop = icon(34, 186, AllIcons.I_STOP, "stop", () -> send("stop", "", ""));
        manual = icon(56, 186, glyph("manual_controls"), "manual", () -> switchView(Page.MANUAL));
        advanced = icon(78, 186, glyph("gear"), "advanced", () -> switchView(Page.ADVANCED));
        icon(100, 186, AllIcons.I_REFRESH, "refresh", () -> send("refresh", "", ""));
        icon(274, 186, AllIcons.I_CONFIRM, "done", this::onClose);
        indicator = addRenderableWidget(new Indicator(leftPos + 16, topPos + 155, tr("state")));

        if (view == Page.MANUAL) {
            buildManual();
            pages = 1;
        } else {
            List<CompoundTag> fields = fields();
            pages = Math.max(1, (fields.size() + rows() - 1) / rows());
            page = Mth.clamp(page, 0, pages - 1);
            for (int i = page * rows(); i < Math.min(fields.size(), (page + 1) * rows()); i++)
                field(fields.get(i), rowY(i));
        }
        previous =
                icon(
                        198,
                        186,
                        AllIcons.I_CONFIG_PREV,
                        "previous",
                        () -> {
                            flushEdits();
                            page--;
                            buildWidgets();
                        });
        next =
                icon(
                        246,
                        186,
                        AllIcons.I_CONFIG_NEXT,
                        "next",
                        () -> {
                            flushEdits();
                            page++;
                            buildWidgets();
                        });
        updateAvailability();
    }

    private void switchView(Page target) {
        flushEdits();
        view = view == target ? Page.CONFIGURATION : target;
        page = 0;
        buildWidgets();
    }

    private List<CompoundTag> fields() {
        List<CompoundTag> result = new ArrayList<>();
        if (view == Page.ADVANCED) {
            CompoundTag backend = new CompoundTag();
            backend.putString("Key", "@backend");
            result.add(backend);
        }
        var schema = status.getList("Fields", Tag.TAG_COMPOUND);
        for (int i = 0; i < schema.size(); i++) {
            CompoundTag field = schema.getCompound(i);
            if (field.getBoolean("Advanced") == (view == Page.ADVANCED)) result.add(field);
        }
        return result;
    }

    private Component fieldLabel(CompoundTag field) {
        if (field.getString("Key").equals("@backend")) return tr("backend");
        return Component.translatableWithFallback(
                "createtrainmining.profile."
                        + status.getString("Profile").replace(':', '.')
                        + ".field."
                        + field.getString("Key"),
                field.getString("Label"));
    }

    private void field(CompoundTag field, int y) {
        String key = field.getString("Key");
        Component label = fieldLabel(field);
        if (key.equals("@backend")) {
            List<String> backends = strings("Backends");
            selection(
                    124,
                    y + 14,
                    168,
                    label,
                    backends,
                    backends.stream().map(id -> named("backend", id)).toList(),
                    status.getString("Backend"),
                    id -> send("set_backend", "", id),
                    true);
            return;
        }
        String value = drafts.getOrDefault(key, field.getString("Value"));
        String type = field.getString("Type");
        if (type.equals("BOOLEAN")) {
            ScrollInput input =
                    selection(
                            VALUE_X,
                            y,
                            VALUE_WIDTH,
                            label,
                            List.of("false", "true"),
                            List.of(tr("disabled"), tr("enabled")),
                            value,
                            selected -> queueEdit(key, selected),
                            true);
            values.put(
                    key,
                    new ValueControl(input, v -> input.setState(Boolean.parseBoolean(v) ? 1 : 0)));
        } else if (type.equals("CHOICE")) {
            List<String> options = tagStrings(field, "Options");
            ScrollInput input =
                    selection(
                            VALUE_X,
                            y,
                            VALUE_WIDTH,
                            label,
                            options,
                            options.stream().map(v -> named("choice", v)).toList(),
                            value,
                            selected -> queueEdit(key, selected),
                            true);
            values.put(
                    key,
                    new ValueControl(input, v -> input.setState(Math.max(0, options.indexOf(v)))));
        } else if (type.equals("NUMBER") && scrollable(field)) {
            boolean percent = field.getDouble("Min") == 0 && field.getDouble("Max") == 1;
            int multiplier =
                    percent
                                    || field.getDouble("Min") % 1 != 0
                                    || field.getDouble("Max") % 1 != 0
                                    || Double.parseDouble(value) % 1 != 0
                            ? 100
                            : 1;
            ScrollInput input =
                    number(
                            VALUE_X,
                            y,
                            VALUE_WIDTH,
                            label,
                            (int) Math.ceil(field.getDouble("Min") * multiplier),
                            (int) Math.floor(field.getDouble("Max") * multiplier) + 1,
                            (int) Math.round(Double.parseDouble(value) * multiplier),
                            n ->
                                    percent
                                            ? Component.literal(n + "%")
                                            : Component.literal(
                                                    multiplier == 1
                                                            ? Integer.toString(n)
                                                            : String.format(
                                                                    Locale.ROOT,
                                                                    "%.2f",
                                                                    n / 100.0)),
                            n -> queueEdit(key, Double.toString(n / (double) multiplier)),
                            true);
            values.put(
                    key,
                    new ValueControl(
                            input,
                            v ->
                                    input.setState(
                                            (int) Math.round(Double.parseDouble(v) * multiplier))));
        } else if (field.getString("Editor").equals("STATION")
                && !customStationFields.contains(key)) {
            List<String> options = stationOptions(value);
            ScrollInput input =
                    selection(
                            VALUE_X,
                            y,
                            VALUE_WIDTH,
                            label,
                            options,
                            options.stream()
                                    .map(
                                            v ->
                                                    v.isBlank()
                                                            ? tr("choose_station")
                                                            : Component.literal(v))
                                    .toList(),
                            value,
                            selected -> queueEdit(key, selected),
                            true);
            values.put(
                    key,
                    new ValueControl(input, v -> input.setState(Math.max(0, options.indexOf(v)))));
            IconButton custom =
                    icon(
                            206,
                            y - 1,
                            glyph("pencil"),
                            "custom_station",
                            () -> {
                                customStationFields.add(key);
                                buildWidgets();
                            });
            configurationWidgets.add(custom);
        } else {
            EditBox input =
                    addRenderableWidget(
                            new EditBox(
                                    font,
                                    leftPos + VALUE_X + 3,
                                    topPos + y + 4,
                                    VALUE_WIDTH - 26,
                                    10,
                                    label));
            input.setBordered(false);
            input.setTextColor(INSET_TEXT);
            input.setTextColorUneditable(MUTED_TEXT);
            input.setMaxLength(type.equals("STRING") ? (int) field.getDouble("Max") : 64);
            input.setValue(value);
            input.setResponder(v -> drafts.put(key, v));
            textInputs.put(key, input);
            configurationWidgets.add(input);
            IconButton apply =
                    icon(
                            274,
                            y - 1,
                            AllIcons.I_CONFIRM,
                            "apply",
                            () -> {
                                queueEdit(key, input.getValue());
                                flushEdits();
                                if (field.getString("Editor").equals("STATION")) {
                                    customStationFields.remove(key);
                                    buildWidgets();
                                }
                            });
            configurationWidgets.add(apply);
        }
    }

    private static boolean scrollable(CompoundTag field) {
        return Double.isFinite(field.getDouble("Min"))
                && Double.isFinite(field.getDouble("Max"))
                && Math.abs(field.getDouble("Min")) < 1_000_000
                && Math.abs(field.getDouble("Max")) < 1_000_000;
    }

    private void buildManual() {
        number(
                VALUE_X,
                62,
                VALUE_WIDTH,
                tr("drive_speed"),
                0,
                101,
                manualSpeed,
                n -> Component.literal(n + "%"),
                n -> manualSpeed = n,
                false);
        selection(
                VALUE_X,
                82,
                VALUE_WIDTH,
                tr("direction"),
                List.of("FORWARD", "BACKWARD"),
                List.of(named("choice", "FORWARD"), named("choice", "BACKWARD")),
                manualDirection,
                direction -> manualDirection = direction,
                false);
        List<String> stations = stationOptions(destination);
        if (destination.isBlank() && !strings("Stations").isEmpty())
            destination = strings("Stations").getFirst();
        selection(
                VALUE_X,
                102,
                VALUE_WIDTH,
                tr("destination"),
                stations,
                stations.stream()
                        .map(v -> v.isBlank() ? tr("choose_station") : Component.literal(v))
                        .toList(),
                destination,
                station -> destination = station,
                false);
        IconButton drive =
                icon(
                        124,
                        132,
                        AllIcons.I_PLAY,
                        "start_drive",
                        () -> send("drive", manualDirection, Double.toString(manualSpeed / 100.0)));
        drive.green = true;
        IconButton brake =
                icon(146, 132, AllIcons.I_STOP, "stop_drive", () -> send("stop_drive", "", ""));
        IconButton navigate =
                icon(
                        174,
                        132,
                        AllIcons.I_VIEW_SCHEDULE,
                        "go_to_station",
                        () -> send("go_to_station", "", destination));
        configurationWidgets.addAll(List.of(drive, brake, navigate));
    }

    private List<String> stationOptions(String current) {
        LinkedHashSet<String> stations = new LinkedHashSet<>();
        stations.add(current);
        stations.addAll(strings("Stations"));
        if (stations.size() > 1 && !current.isBlank()) stations.remove("");
        return new ArrayList<>(stations);
    }

    private ScrollInput selection(
            int x,
            int y,
            int width,
            Component label,
            List<String> ids,
            List<? extends Component> options,
            String selected,
            Consumer<String> callback,
            boolean configuration) {
        return selection(x, y, width, label, ids, options, selected, callback, configuration, null);
    }

    private ScrollInput selection(
            int x,
            int y,
            int width,
            Component label,
            List<String> ids,
            List<? extends Component> options,
            String selected,
            Consumer<String> callback,
            boolean configuration,
            Component placeholder) {
        boolean awaitingSelection = placeholder != null && !ids.isEmpty() && !ids.contains(selected);
        List<String> choices = new ArrayList<>(ids);
        List<Component> labels = new ArrayList<>(options);
        if (awaitingSelection) {
            choices.add(0, "");
            labels.add(0, placeholder);
        }
        SelectionScrollInput input =
                new SelectionScrollInput(leftPos + x, topPos + y, width, 16) {
                    @Override
                    protected void doRender(GuiGraphics graphics, int mx, int my, float partial) {
                        drawValue(graphics, this, formatter.apply(state), true);
                    }

                    @Override
                    protected void renderTooltip(
                            GuiGraphics graphics, int mx, int my, float partial) {}

                    @Override
                    public void onClick(double mx, double my) {
                        if (ids.isEmpty()) return;
                        setState((getState() + 1) % choices.size());
                        onChanged();
                    }

                    @Override
                    public boolean keyPressed(int key, int scan, int modifiers) {
                        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
                            setState(
                                    Math.floorMod(
                                            getState() + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1),
                                            Math.max(1, choices.size())));
                            onChanged();
                            return true;
                        }
                        return super.keyPressed(key, scan, modifiers);
                    }
                };
        input.forOptions(labels.isEmpty() ? List.of(tr("unavailable")) : labels)
                .titled(label.copy())
                .setState(Math.max(0, choices.indexOf(selected)))
                .calling(
                        i -> {
                            if (i >= (awaitingSelection ? 1 : 0) && i < choices.size())
                                callback.accept(choices.get(i));
                        });
        input.setMessage(label);
        input.active = !ids.isEmpty();
        addRenderableWidget(input);
        if (configuration) configurationWidgets.add(input);
        return input;
    }

    private ScrollInput number(
            int x,
            int y,
            int width,
            Component label,
            int min,
            int max,
            int value,
            java.util.function.Function<Integer, Component> format,
            Consumer<Integer> callback,
            boolean configuration) {
        ScrollInput input =
                new ScrollInput(leftPos + x, topPos + y, width, 16) {
                    @Override
                    protected void doRender(GuiGraphics graphics, int mx, int my, float partial) {
                        drawValue(graphics, this, formatter.apply(state), false);
                    }

                    @Override
                    protected void renderTooltip(
                            GuiGraphics graphics, int mx, int my, float partial) {}

                    @Override
                    public void onClick(double mx, double my) {
                        setState(getState() + 1);
                        onChanged();
                    }
                };
        input.withRange(min, max)
                .format(format)
                .withShiftStep(5)
                .titled(label.copy())
                .setState(value)
                .calling(callback);
        input.setMessage(label);
        addRenderableWidget(input);
        if (configuration) configurationWidgets.add(input);
        return input;
    }

    private void drawValue(
            GuiGraphics graphics, ScrollInput input, Component value, boolean choice) {
        int x = input.getX(), y = input.getY(), width = input.getWidth();
        recess(graphics, x, y, width, 16, input.isHoveredOrFocused() && input.active);
        graphics.drawString(
                font,
                ellipsize(value, width - 13),
                x + 4,
                y + 4,
                input.active ? INSET_TEXT : MUTED_TEXT,
                false);
        graphics.fill(
                x + width - 6, y + 4, x + width - 3, y + 5, input.active ? 0xffc5ac70 : 0xff808878);
        graphics.fill(
                x + width - 5, y + 3, x + width - 4, y + 6, input.active ? 0xffc5ac70 : 0xff808878);
        graphics.fill(
                x + width - 6,
                y + 11,
                x + width - 3,
                y + 12,
                input.active ? 0xffc5ac70 : 0xff808878);
        if (choice) graphics.fill(x + width - 5, y + 10, x + width - 4, y + 13, 0xff9a855b);
    }

    private void recess(GuiGraphics graphics, int x, int y, int width, int height, boolean hover) {
        graphics.fill(x, y, x + width, y + height, 0xff222c29);
        graphics.fill(x + 1, y + 1, x + width, y + height, hover ? 0xff627064 : 0xff515b50);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, 0xffedebde);
    }

    private void queueEdit(String key, String value) {
        drafts.put(key, value);
        pending.put(key, value);
        editDelay = 6;
    }

    private void flushEdits() {
        Map<String, String> edits = new LinkedHashMap<>(pending);
        pending.clear();
        edits.forEach((key, value) -> send("set_config", key, value));
    }

    private void send(String action, String key, String value) {
        error = "";
        if (minecraft.getConnection() == null) return;
        PacketDistributor.sendToServer(
                new ControllerCommandPayload(
                        menu.containerId, menu.position(), action, key, value));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!pending.isEmpty() && --editDelay <= 0) flushEdits();
    }

    @Override
    public void onClose() {
        flushEdits();
        super.onClose();
    }

    public void receive(CompoundTag next) {
        boolean changed = !structure(status).equals(structure(next));
        status = next;
        String reportedError = next.getString("Error");
        if (!reportedError.isEmpty()) {
            error = reportedError;
            drafts.keySet().removeIf(key -> !pending.containsKey(key));
        }
        var fields = status.getList("Fields", Tag.TAG_COMPOUND);
        for (int i = 0; i < fields.size(); i++) {
            CompoundTag field = fields.getCompound(i);
            String key = field.getString("Key"), value = field.getString("Value");
            if (value.equals(drafts.get(key))) drafts.remove(key);
            ValueControl control = values.get(key);
            if (control != null && !drafts.containsKey(key)) control.synchronize.accept(value);
            EditBox text = textInputs.get(key);
            if (text != null && !drafts.containsKey(key) && !text.isFocused()) {
                text.setResponder(null);
                text.setValue(value);
                text.setResponder(v -> drafts.put(key, v));
            }
        }
        if (changed && minecraft != null) buildWidgets();
        else updateAvailability();
    }

    private static CompoundTag structure(CompoundTag data) {
        CompoundTag structure = data.copy();
        for (String key :
                List.of(
                        "State",
                        "Mode",
                        "Enabled",
                        "Error",
                        "Speed",
                        "SpeedRatio",
                        "Inventory",
                        "Station",
                        "Direction",
                        "TrainIcon",
                        "CarriageLengths",
                        "DoubleEnded")) structure.remove(key);
        var fields = structure.getList("Fields", Tag.TAG_COMPOUND);
        for (int i = 0; i < fields.size(); i++) fields.getCompound(i).remove("Value");
        return structure;
    }

    private void updateAvailability() {
        if (start == null) return;
        boolean selected = !status.getString("TrainId").isEmpty();
        boolean enabled = status.getBoolean("Enabled");
        start.active = selected && !enabled && !status.getString("Profile").isEmpty();
        stop.active = selected;
        manual.green = view == Page.MANUAL;
        advanced.green = view == Page.ADVANCED;
        previous.visible = next.visible = pages > 1;
        previous.active = page > 0;
        next.active = page < pages - 1;
        for (GuiEventListener widget : configurationWidgets) {
            if (widget instanceof net.minecraft.client.gui.components.AbstractWidget control)
                control.active = !enabled && selected;
        }
        indicator.state =
                !error.isEmpty() || status.getString("Mode").equals("ERROR")
                        ? Indicator.State.RED
                        : enabled
                                        || List.of("DIRECT_CONTROL", "SCHEDULE_CONTROL")
                                                .contains(status.getString("Mode"))
                                ? Math.abs(status.getDouble("Speed")) > .0001
                                        ? Indicator.State.GREEN
                                        : Indicator.State.YELLOW
                                : Indicator.State.OFF;
    }

    private List<String> strings(String key) {
        return tagStrings(status, key);
    }

    private static List<String> tagStrings(CompoundTag tag, String key) {
        var list = tag.getList(key, Tag.TAG_STRING);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) result.add(list.getString(i));
        return result;
    }

    private Component ellipsize(Component text, int width) {
        if (font.width(text) <= width) return text;
        return Component.literal(
                        font.plainSubstrByWidth(
                                        text.getString(), Math.max(0, width - font.width("...")))
                                + "...")
                .setStyle(text.getStyle());
    }

    private void text(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        graphics.drawString(font, ellipsize(text, width), leftPos + x, topPos + y, color, false);
        textTips.add(new TextTip(new Rect2i(leftPos + x, topPos + y - 1, width, 11), text));
    }

    private String speed() {
        return String.format(Locale.ROOT, "%.0f%%", status.getDouble("SpeedRatio") * 100);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        textTips.clear();
        graphics.blit(
                FRAME, leftPos, topPos, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
        AllIcons.I_VIEW_SCHEDULE.render(graphics, leftPos + 10, topPos + 5);
        text(graphics, title, 32, 10, 260, 0x54452e);
        text(graphics, tr("train"), 16, 32, 92, MUTED_TEXT);
        text(graphics, tr("automation"), 124, 34, 70, AllGuiTextures.FONT_COLOR);
        text(
                graphics,
                tr(
                        view == Page.ADVANCED
                                ? "advanced_title"
                                : view == Page.MANUAL ? "manual_title" : "configuration"),
                124,
                49,
                166,
                AllGuiTextures.FONT_COLOR);
        trainIcon(graphics);
        text(graphics, tr("speed"), 16, 86, 46, MUTED_TEXT);
        text(graphics, Component.literal(speed()), 68, 86, 40, INSET_TEXT);
        textTips.add(
                new TextTip(
                        new Rect2i(leftPos + 16, topPos + 85, 94, 12),
                        tr(
                                "speed_details",
                                String.format(Locale.ROOT, "%.3f", status.getDouble("Speed")))));
        text(graphics, tr("station"), 16, 103, 92, MUTED_TEXT);
        String station = status.getString("Station");
        text(
                graphics,
                station.isEmpty() || station.equals("None")
                        ? tr("no_station")
                        : Component.literal(station),
                16,
                116,
                92,
                INSET_TEXT);
        text(graphics, tr("control_mode"), 16, 133, 92, MUTED_TEXT);
        text(graphics, named("mode", status.getString("Mode")), 16, 145, 92, INSET_TEXT);
        text(graphics, named("state", status.getString("State")), 16, 163, 92, INSET_TEXT);
        if (view == Page.MANUAL) {
            text(graphics, tr("drive_speed"), 124, 66, 98, AllGuiTextures.FONT_COLOR);
            text(graphics, tr("direction"), 124, 86, 98, AllGuiTextures.FONT_COLOR);
            text(graphics, tr("destination"), 124, 106, 98, AllGuiTextures.FONT_COLOR);
            text(graphics, tr("manual_hint"), 124, 161, 168, AllGuiTextures.FONT_COLOR);
        } else {
            List<CompoundTag> fields = fields();
            if (view == Page.CONFIGURATION && status.getString("Profile").isEmpty()) {
                text(graphics, tr("profile_selection_prompt"), 124, 87, 168,
                        AllGuiTextures.FONT_COLOR);
                text(graphics, tr("profile_selection_hint"), 124, 102, 168,
                        AllGuiTextures.FONT_COLOR);
            }
            for (int i = page * rows(); i < Math.min(fields.size(), (page + 1) * rows()); i++) {
                int y = rowY(i);
                int labelWidth =
                        fields.get(i).getString("Editor").equals("STATION")
                                        && !customStationFields.contains(
                                                fields.get(i).getString("Key"))
                                ? 78
                                : 98;
                text(
                        graphics,
                        fieldLabel(fields.get(i)),
                        124,
                        y + 4,
                        labelWidth,
                        AllGuiTextures.FONT_COLOR);
                if (!fields.get(i).getString("Key").equals("@backend"))
                    recess(graphics, leftPos + VALUE_X, topPos + y, VALUE_WIDTH, 16, false);
            }
        }
        if (pages > 1)
            text(
                    graphics,
                    Component.literal((page + 1) + "/" + pages),
                    220,
                    191,
                    24,
                    AllGuiTextures.FONT_COLOR);
        Component bottom =
                error.isEmpty()
                        ? tr(
                                "status_bar",
                                String.format(
                                        Locale.ROOT, "%.0f%%", status.getDouble("Inventory") * 100),
                                speed(),
                                named("state", status.getString("State")))
                        : tr("error", error);
        text(graphics, bottom, 14, 210, 276, error.isEmpty() ? INSET_TEXT : 0xffb29a);
    }

    private void trainIcon(GuiGraphics graphics) {
        ResourceLocation id = ResourceLocation.tryParse(status.getString("TrainIcon"));
        int[] carriages = status.getIntArray("CarriageLengths");
        if (id == null || carriages.length == 0) {
            text(graphics, tr("no_train"), 16, 67, 94, MUTED_TEXT);
            return;
        }
        TrainIconType icon = TrainIconType.byId(id);
        int width = icon.getIconWidth(TrainIconType.ENGINE);
        for (int i = 1; i < carriages.length; i++)
            width +=
                    1
                            + icon.getIconWidth(
                                    i == carriages.length - 1 && status.getBoolean("DoubleEnded")
                                            ? TrainIconType.FLIPPED_ENGINE
                                            : carriages[i]);
        graphics.enableScissor(leftPos + 16, topPos + 64, leftPos + 110, topPos + 79);
        int x = leftPos + 16 + (width <= 94 ? (94 - width) / 2 : 94 - width);
        for (int i = carriages.length - 1; i > 0; i--)
            x +=
                    icon.render(
                                    i == carriages.length - 1 && status.getBoolean("DoubleEnded")
                                            ? TrainIconType.FLIPPED_ENGINE
                                            : carriages[i],
                                    graphics,
                                    x,
                                    topPos + 67)
                            + 1;
        icon.render(TrainIconType.ENGINE, graphics, x, topPos + 67);
        graphics.disableScissor();
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderTooltip(graphics, mouseX, mouseY);
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractSimiWidget widget
                    && widget.visible
                    && widget.isMouseOver(mouseX, mouseY)) {
                List<Component> tooltip = new ArrayList<>(widget.getToolTip());
                if (!widget.active
                        && status.getBoolean("Enabled")
                        && (configurationWidgets.contains(widget) || widget == start))
                    tooltip.add(tr("stop_before_editing").withStyle(ChatFormatting.GRAY));
                if (widget.getY() == topPos + 45 && !status.getString("TrainId").isEmpty())
                    tooltip.add(
                            Component.literal(status.getString("TrainId"))
                                    .withStyle(ChatFormatting.DARK_GRAY));
                showTooltip(graphics, tooltip, mouseX, mouseY);
                return;
            }
        }
        for (int i = textTips.size() - 1; i >= 0; i--) {
            TextTip tip = textTips.get(i);
            if (tip.bounds.contains(mouseX, mouseY)) {
                showTooltip(graphics, List.of(tip.text), mouseX, mouseY);
                return;
            }
        }
    }

    private void showTooltip(GuiGraphics graphics, List<Component> lines, int x, int y) {
        List<Component> wrapped = new ArrayList<>();
        int maxWidth = Math.min(220, width - 24);
        for (Component line : lines) {
            if (line.getString().startsWith("> ")) wrapped.add(ellipsize(line, maxWidth));
            else
                font.getSplitter()
                        .splitLines(line, maxWidth, Style.EMPTY)
                        .forEach(
                                part ->
                                        wrapped.add(
                                                Component.literal(part.getString())
                                                        .setStyle(line.getStyle())));
        }
        int maxLines = Math.max(4, (height - 24) / 10);
        if (wrapped.size() > maxLines) {
            wrapped.subList(maxLines - 1, wrapped.size()).clear();
            wrapped.add(Component.literal("...").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!wrapped.isEmpty()) graphics.renderComponentTooltip(font, wrapped, x, y);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
                && getFocused() instanceof EditBox) {
            drafts.forEach(pending::put);
            flushEdits();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
