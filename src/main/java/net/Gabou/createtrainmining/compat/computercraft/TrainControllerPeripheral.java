package net.Gabou.createtrainmining.compat.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import net.Gabou.createtrainmining.api.DriveDirection;
import net.Gabou.createtrainmining.block.TrainAutomationControllerBlockEntity;
import net.Gabou.createtrainmining.core.*;

import java.util.*;
import java.util.function.Supplier;

public final class TrainControllerPeripheral implements IPeripheral {
    private final TrainAutomationControllerBlockEntity entity;

    public TrainControllerPeripheral(TrainAutomationControllerBlockEntity entity) {
        this.entity = entity;
    }

    public String getType() {
        return "train_automation_controller";
    }

    public boolean equals(IPeripheral other) {
        return other instanceof TrainControllerPeripheral p && p.entity == entity;
    }

    private TrainController controller() {
        if (entity.isRemoved()) throw new IllegalStateException("Controller has unloaded");
        return entity.controller();
    }

    private <T> T call(Supplier<T> function) throws LuaException {
        try {
            return function.get();
        } catch (RuntimeException e) {
            throw new LuaException(
                    e.getMessage() == null ? "Controller operation failed" : e.getMessage());
        }
    }

    private TrainController external() {
        var c = controller();
        c.requireExternalControl();
        return c;
    }

    private void run(Runnable function) throws LuaException {
        call(
                () -> {
                    function.run();
                    return null;
                });
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getTrain() throws LuaException {
        return call(
                () -> {
                    var t = controller().getSelectedTrain();
                    var out = new LinkedHashMap<String, Object>();
                    out.put("uuid", t.getId().toString());
                    out.put("name", t.getName());
                    out.put("speed", t.getSpeed());
                    out.put(
                            "direction",
                            controller().getDirection().name().toLowerCase(Locale.ROOT));
                    out.put("derailed", t.isDerailed());
                    out.put("inventory_usage", t.getInventory().getUsageRatio());
                    out.put("carriages", t.getCarriageCount());
                    out.put("has_schedule", t.hasSchedule());
                    out.put("schedule_paused", t.isSchedulePaused());
                    out.put("schedule_finished", t.isScheduleFinished());
                    out.put("navigating", t.isNavigating());
                    out.put("station", t.getCurrentStation() == null ? "" : t.getCurrentStation());
                    out.put("waiting_for_chunks", t.isWaitingForChunks());
                    out.put("forward_conductor", t.hasForwardConductor());
                    out.put("backward_conductor", t.hasBackwardConductor());
                    return out;
                });
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getTrains() throws LuaException {
        return call(
                () ->
                        controller().discoverTrains().stream()
                                .map(
                                        t ->
                                                Map.<String, Object>of(
                                                        "uuid",
                                                        t.getId().toString(),
                                                        "name",
                                                        t.getName()))
                                .toList());
    }

    @LuaFunction(mainThread = true)
    public final void selectTrain(String uuid) throws LuaException {
        run(() -> controller().selectTrain(UUID.fromString(uuid)));
    }

    @LuaFunction(mainThread = true)
    public final void selectTrainByName(String name) throws LuaException {
        run(() -> controller().selectTrainByName(name));
    }

    @LuaFunction(mainThread = true)
    public final String getProfile() throws LuaException {
        return call(() -> controller().getProfileId());
    }

    @LuaFunction(mainThread = true)
    public final List<String> getProfiles() {
        return AutomationProfileRegistry.ids();
    }

    @LuaFunction(mainThread = true)
    public final void setProfile(String profile) throws LuaException {
        run(() -> controller().setProfile(profile));
    }

    @LuaFunction(mainThread = true)
    public final String getState() throws LuaException {
        return call(() -> controller().getStatus());
    }

    @LuaFunction(mainThread = true)
    public final String getControlMode() throws LuaException {
        return call(() -> controller().getControlMode().name());
    }

    @LuaFunction(mainThread = true)
    public final String getLastError() throws LuaException {
        return call(() -> controller().getLastError());
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getConfig() throws LuaException {
        return call(() -> controller().getConfig());
    }

    @LuaFunction(mainThread = true)
    public final void setConfig(IArguments arguments) throws LuaException {
        String key = arguments.getString(0);
        Object value = arguments.get(1);
        run(() -> controller().setConfig(key, String.valueOf(value)));
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getInventory() throws LuaException {
        return call(
                () -> {
                    var view = controller().getInventory();
                    var items = new ArrayList<Map<String, Object>>();
                    for (int slot = 0; slot < view.getSlots(); slot++) {
                        var stack = view.getStack(slot);
                        if (!stack.isEmpty())
                            items.add(
                                    Map.of(
                                            "slot",
                                            slot + 1,
                                            "name",
                                            net.minecraft.core.registries.BuiltInRegistries.ITEM
                                                    .getKey(stack.getItem())
                                                    .toString(),
                                            "count",
                                            stack.getCount(),
                                            "max_count",
                                            stack.getMaxStackSize()));
                    }
                    return items;
                });
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getInventoryStats() throws LuaException {
        return call(
                () -> {
                    var view = controller().getInventory();
                    return Map.of(
                            "slots",
                            view.getSlots(),
                            "used_items",
                            view.getUsedItemCount(),
                            "estimated_capacity",
                            view.getEstimatedCapacity(),
                            "usage_ratio",
                            view.getUsageRatio());
                });
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getConfigurationSchema() throws LuaException {
        return call(
                () ->
                        controller().getConfigurationSchema().stream()
                                .map(
                                        f ->
                                                Map.<String, Object>of(
                                                        "key",
                                                        f.key(),
                                                        "label",
                                                        f.label(),
                                                        "type",
                                                        f.type().name().toLowerCase(Locale.ROOT),
                                                        "default",
                                                        f.defaultValue(),
                                                        "min",
                                                        f.min(),
                                                        "max",
                                                        f.max(),
                                                        "options",
                                                        f.options()))
                                .toList());
    }

    @LuaFunction(mainThread = true)
    public final void start() throws LuaException {
        run(() -> controller().start());
    }

    @LuaFunction(mainThread = true)
    public final void stop() throws LuaException {
        run(() -> controller().stop());
    }

    @LuaFunction(mainThread = true)
    public final void startDrive(String direction, double speed) throws LuaException {
        run(
                () ->
                        external()
                                .startDriving(
                                        DriveDirection.valueOf(direction.toUpperCase(Locale.ROOT)),
                                        speed));
    }

    @LuaFunction(mainThread = true)
    public final void stopDrive() throws LuaException {
        run(() -> external().stopDriving());
    }

    @LuaFunction(mainThread = true)
    public final void setSpeed(double speed) throws LuaException {
        run(() -> external().setSpeed(speed));
    }

    @LuaFunction(mainThread = true)
    public final void setDirection(String direction) throws LuaException {
        run(
                () ->
                        external()
                                .setDirection(
                                        DriveDirection.valueOf(
                                                direction.toUpperCase(Locale.ROOT))));
    }

    @LuaFunction(mainThread = true)
    public final void goToStation(String station) throws LuaException {
        run(() -> external().goToStation(station));
    }

    @LuaFunction(mainThread = true)
    public final List<String> getStations() throws LuaException {
        return call(() -> controller().getStations());
    }

    @LuaFunction(mainThread = true)
    public final void clearSchedule() throws LuaException {
        run(() -> external().clearSchedule());
    }

    @LuaFunction(mainThread = true)
    public final void pauseSchedule() throws LuaException {
        run(() -> external().pauseSchedule());
    }

    @LuaFunction(mainThread = true)
    public final void resumeSchedule() throws LuaException {
        run(() -> external().resumeSchedule());
    }

    @LuaFunction(mainThread = true)
    public final List<String> getDriveBackends() {
        return DriveBackendRegistry.ids();
    }

    @LuaFunction(mainThread = true)
    public final String getDriveBackend() throws LuaException {
        return call(() -> controller().getBackendId());
    }

    @LuaFunction(mainThread = true)
    public final void setDriveBackend(String id) throws LuaException {
        run(() -> controller().setBackend(id));
    }

    @LuaFunction(mainThread = true)
    public final List<String> getActorTypes() throws LuaException {
        return call(() -> controller().listActorTypes());
    }

    @LuaFunction(mainThread = true)
    public final boolean setActorEnabled(String id, boolean enabled) throws LuaException {
        return call(
                () ->
                        external()
                                .setActorTypeEnabled(
                                        net.minecraft.resources.ResourceLocation.parse(id),
                                        enabled));
    }

    @LuaFunction(mainThread = true)
    public final boolean isActorEnabled(String id) throws LuaException {
        return call(
                () ->
                        controller()
                                .isActorTypeEnabled(
                                        net.minecraft.resources.ResourceLocation.parse(id)));
    }
}
