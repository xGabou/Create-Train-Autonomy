package net.Gabou.createtrainmining.core;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.EdgePointType;

import net.Gabou.createtrainmining.api.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;
import java.util.function.IntSupplier;

/** Server-thread facade shared by the block, profiles and optional external integrations. */
public final class TrainController {
    private final MinecraftServer server;
    private final Runnable dirty;
    private final IntSupplier redstone;
    private final TrainAutomationManager manager;
    private final TrainScheduleController schedules = new TrainScheduleController();
    private final TrainActorController actors = new TrainActorController();
    private UUID controllerId = UUID.randomUUID(), selectedTrain;
    private String profileId = "", backendId = "native", lastError = "";
    private TrainDriveBackend backend = DriveBackendRegistry.create("native");
    private AutomationProfile profile;
    private final Map<String, Object> config = new LinkedHashMap<>();
    private CompoundTag persistentData = new CompoundTag();
    private ControlMode mode = ControlMode.IDLE;
    private boolean enabled, restorePending, attached;
    private UUID lastStation;
    private List<Integer> inventoryVersions = List.of();
    private int inventoryFingerprint;
    private String lastProfileState = "";
    private final AutomationContext context =
            new AutomationContext() {
                public ManagedTrain train() {
                    return getSelectedTrain();
                }

                public TrainController controller() {
                    return TrainController.this;
                }

                public List<String> stations() {
                    return getStations();
                }

                public long currentTime() {
                    return server.overworld().getGameTime();
                }

                public Map<String, Object> configuration() {
                    return getConfig();
                }

                public CompoundTag persistentData() {
                    return persistentData;
                }

                public int redstonePower() {
                    return redstone.getAsInt();
                }

                public Optional<Object> integration(String id) {
                    return IntegrationRegistry.find(id);
                }

                public void markDirty() {
                    dirty.run();
                }
            };

    public TrainController(MinecraftServer server, Runnable dirty, IntSupplier redstone) {
        this.server = server;
        this.dirty = dirty;
        this.redstone = redstone;
        manager = TrainAutomationManager.get(server);
    }

    private void checkThread() {
        if (!server.isSameThread())
            throw new IllegalStateException("Train controls must run on the server thread");
    }

    public UUID getControllerId() {
        return controllerId;
    }

    public UUID getSelectedTrainId() {
        return selectedTrain;
    }

    public ManagedTrain getSelectedTrain() {
        if (selectedTrain == null) throw new IllegalStateException("Select a train first");
        var train = new ManagedTrain(selectedTrain);
        train.unwrap();
        return train;
    }

    public List<ManagedTrain> discoverTrains() {
        checkThread();
        return Create.RAILWAYS.trains.values().stream()
                .filter(t -> !t.invalid)
                .map(t -> new ManagedTrain(t.id))
                .sorted(
                        Comparator.comparing(ManagedTrain::getName)
                                .thenComparing(t -> t.getId().toString()))
                .toList();
    }

    public void selectTrain(UUID id) {
        checkThread();
        if (!Create.RAILWAYS.trains.containsKey(id))
            throw new IllegalArgumentException("Unknown train UUID: " + id);
        stop();
        selectedTrain = id;
        mode = ControlMode.IDLE;
        lastStation = null;
        inventoryVersions = List.of();
        lastError = "";
        emit(AutomationEvent.Type.TRAIN_SELECTED, id.toString());
        dirty.run();
    }

    public void selectTrainByName(String name) {
        var matches = discoverTrains().stream().filter(t -> t.getName().equals(name)).toList();
        if (matches.size() != 1)
            throw new IllegalArgumentException(
                    matches.isEmpty()
                            ? "No train named " + name
                            : "Train name is ambiguous; use its UUID");
        selectTrain(matches.get(0).getId());
    }

    private Train claim() {
        checkThread();
        attach();
        var managed = getSelectedTrain();
        if (managed.hasPlayerControl())
            throw new IllegalStateException("A player is controlling this train");
        manager.ownership().claim(selectedTrain, controllerId);
        return managed.unwrap();
    }

    private void attach() {
        if (!attached) {
            manager.attach(this);
            attached = true;
        }
    }

    private boolean owns() {
        return selectedTrain != null
                && manager.owner(selectedTrain) == this
                && manager.ownership().owns(selectedTrain, controllerId);
    }

    public void startDriving(DriveDirection direction, double speed) {
        startDriving(DriveRequest.of(direction, speed));
    }

    public void startDriving(DriveRequest request) {
        var train = getSelectedTrain().unwrap();
        if (!backend.isAvailable(train))
            throw new IllegalStateException("Drive backend unavailable for this train");
        train = claim();
        TrainMovementIntegrations.suspend(train);
        // Match Create's startControlling order: suspend schedule, cancel navigation and release
        // reservations.
        train.runtime.paused = true;
        train.navigation.cancelNavigation();
        train.navigation.waitingForSignal = null;
        train.reservedSignalBlocks.clear();
        backend.start(train, request);
        mode = ControlMode.DIRECT_CONTROL;
        lastError = "";
        emit(AutomationEvent.Type.DIRECT_DRIVE_STARTED, request.direction().name());
        dirty.run();
    }

    public void stopDriving() {
        checkThread();
        if (!owns()) return;
        var train = getSelectedTrain().unwrap();
        if (backend.isDriving(train) || mode == ControlMode.DIRECT_CONTROL) {
            backend.stop(train);
            mode = ControlMode.IDLE;
            emit(AutomationEvent.Type.DIRECT_DRIVE_STOPPED, "");
            dirty.run();
        }
        releaseIfIdle();
    }

    public void setSpeed(double speed) {
        checkThread();
        if (!owns()) throw new IllegalStateException("Controller does not own this train");
        backend.setSpeed(getSelectedTrain().unwrap(), speed);
        dirty.run();
    }

    public void setDirection(DriveDirection direction) {
        checkThread();
        if (!owns()) throw new IllegalStateException("Controller does not own this train");
        backend.setDirection(getSelectedTrain().unwrap(), direction);
        dirty.run();
    }

    public boolean isDirectDriving() {
        return mode == ControlMode.DIRECT_CONTROL;
    }

    private void resetManual(Train train) {
        TrainMovementIntegrations.suspend(train);
        backend.stop(train);
        train.manualTick = false;
        train.manualSteer = SteerDirection.NONE;
        train.backwardsDriver = null;
    }

    public void applySchedule(TrainSchedule schedule) {
        checkThread();
        var train = getSelectedTrain().unwrap();
        schedules.validate(train, schedule);
        train = claim();
        boolean wasDirect = isDirectDriving();
        resetManual(train);
        train.runtime.paused = true;
        train.navigation.cancelNavigation();
        if (wasDirect) emit(AutomationEvent.Type.DIRECT_DRIVE_STOPPED, "");
        schedules.applySchedule(train, schedule);
        mode = ControlMode.SCHEDULE_CONTROL;
        lastError = "";
        emit(AutomationEvent.Type.SCHEDULE_STARTED, String.join(", ", schedule.stations()));
        dirty.run();
    }

    public void goToStation(String name) {
        applySchedule(TrainSchedule.toStation(name));
    }

    public void clearSchedule() {
        var train = claim();
        boolean wasDirect = isDirectDriving();
        resetManual(train);
        schedules.clearSchedule(train);
        mode = ControlMode.IDLE;
        if (wasDirect) emit(AutomationEvent.Type.DIRECT_DRIVE_STOPPED, "");
        dirty.run();
        releaseIfIdle();
    }

    public void pauseSchedule() {
        var train = claim();
        resetManual(train);
        schedules.pauseSchedule(train);
        mode = ControlMode.IDLE;
        dirty.run();
        releaseIfIdle();
    }

    public void resumeSchedule() {
        var train = getSelectedTrain().unwrap();
        if (!schedules.hasSchedule(train)) throw new IllegalStateException("No schedule to resume");
        train = claim();
        resetManual(train);
        schedules.resumeSchedule(train);
        mode = ControlMode.SCHEDULE_CONTROL;
        emit(AutomationEvent.Type.SCHEDULE_STARTED, "resumed");
        dirty.run();
    }

    public String getCurrentStation() {
        return getSelectedTrain().getCurrentStation();
    }

    public double getCurrentSpeed() {
        return getSelectedTrain().getSpeed();
    }

    public DriveDirection getDirection() {
        return mode == ControlMode.DIRECT_CONTROL
                ? backend.getDirection(getSelectedTrain().unwrap())
                : getSelectedTrain().getDirection();
    }

    public TrainInventoryView getInventory() {
        return getSelectedTrain().getInventory();
    }

    public double getInventoryUsage() {
        return getInventory().getUsageRatio();
    }

    public List<String> getStations() {
        var train = getSelectedTrain().unwrap();
        if (train.graph == null) return List.of();
        return train.graph.getPoints(EdgePointType.STATION).stream()
                .map(s -> s.name)
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> listActorTypes() {
        return actors.listActorTypes(getSelectedTrain());
    }

    public boolean setActorTypeEnabled(ResourceLocation type, boolean enabled) {
        claim();
        boolean changed = actors.setActorTypeEnabled(getSelectedTrain(), type, enabled);
        actors.tickPauses(getSelectedTrain());
        return changed;
    }

    public boolean isActorTypeEnabled(ResourceLocation type) {
        return actors.isActorTypeEnabled(getSelectedTrain(), type);
    }

    public void setPausedActorTypes(Set<ResourceLocation> types) {
        claim();
        actors.setPausedTypes(getSelectedTrain(), types);
    }

    public boolean hasSlopeNearTrain(DriveDirection direction, double lookAhead) {
        checkThread();
        return TrainTrackProbe.hasSlopeNearTrain(getSelectedTrain().unwrap(), direction, lookAhead);
    }

    public ControlMode getControlMode() {
        return mode;
    }

    public String getStatus() {
        return mode == ControlMode.ERROR
                        || mode == ControlMode.MANUAL_PLAYER_CONTROL
                        || profile == null
                ? mode.name()
                : profile.getStatus();
    }

    public String getLastError() {
        return lastError;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getBackendId() {
        return backendId;
    }

    public boolean isEnabled() {
        return enabled || restorePending;
    }

    public Map<String, Object> getConfig() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(config));
    }

    public List<ConfigurationField> getConfigurationSchema() {
        return profile == null ? List.of() : profile.getConfigurationSchema();
    }

    public void setProfile(String id) {
        checkThread();
        var next = AutomationProfileRegistry.create(id);
        stop();
        profileId = id;
        profile = next;
        config.clear();
        profile.getConfigurationSchema().forEach(f -> config.put(f.key(), f.defaultValue()));
        persistentData = new CompoundTag();
        lastError = "";
        mode = ControlMode.IDLE;
        dirty.run();
    }

    public void setConfig(String key, String value) {
        checkThread();
        if (isEnabled())
            throw new IllegalStateException("Stop automation before editing its configuration");
        var field =
                getConfigurationSchema().stream()
                        .filter(f -> f.key().equals(key))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Unknown configuration key: " + key));
        var candidate = new LinkedHashMap<>(config);
        candidate.put(key, field.parse(value));
        profile.validateConfiguration(candidate);
        config.clear();
        config.putAll(candidate);
        dirty.run();
    }

    public void setBackend(String id) {
        checkThread();
        var next = DriveBackendRegistry.create(id);
        stop();
        backend = next;
        backendId = id;
        lastError = "";
        mode = ControlMode.IDLE;
        dirty.run();
    }

    public void start() {
        checkThread();
        if (enabled) return;
        if (profile == null) throw new IllegalStateException("Select an automation profile first");
        profile.validateConfiguration(config);
        claim();
        enabled = true;
        restorePending = false;
        lastError = "";
        try {
            profile.onStart(context);
            emit(AutomationEvent.Type.AUTOMATION_STATE_CHANGED, profile.getStatus());
            dirty.run();
        } catch (RuntimeException e) {
            fail(e);
            throw e;
        }
    }

    public void stop() {
        checkThread();
        attach();
        restorePending = false;
        boolean wasEnabled = enabled;
        enabled = false;
        try {
            if (wasEnabled && profile != null && owns()) profile.onStop(context);
        } finally {
            haltOwned();
            if (mode != ControlMode.MANUAL_PLAYER_CONTROL) mode = ControlMode.IDLE;
            dirty.run();
        }
    }

    private void haltOwned() {
        if (!owns()) return;
        var train = Create.RAILWAYS.trains.get(selectedTrain);
        if (train != null) {
            actors.setPausedTypes(getSelectedTrain(), Set.of());
            boolean wasDirect = backend.isDriving(train);
            resetManual(train);
            train.runtime.paused = true;
            train.navigation.cancelNavigation();
            if (wasDirect) emit(AutomationEvent.Type.DIRECT_DRIVE_STOPPED, "");
        }
        manager.ownership().release(selectedTrain, controllerId);
        Create.RAILWAYS.markTracksDirty();
    }

    private void releaseIfIdle() {
        if (!enabled && mode == ControlMode.IDLE && owns()) {
            actors.setPausedTypes(getSelectedTrain(), Set.of());
            manager.ownership().release(selectedTrain, controllerId);
        }
    }

    public void requireExternalControl() {
        checkThread();
        if (enabled || restorePending)
            throw new IllegalStateException(
                    "Stop the automation profile before using direct external controls");
    }

    public void unload() {
        checkThread();
        restorePending |= enabled;
        enabled = false;
        haltOwned();
        if (attached) manager.detach(this);
        attached = false;
        mode = ControlMode.IDLE;
        dirty.run();
    }

    public void yieldToPlayer() {
        checkThread();
        cleanupProfile();
        enabled = false;
        restorePending = false;
        haltOwned();
        mode = ControlMode.MANUAL_PLAYER_CONTROL;
        lastError = "Automation stopped because a player took control";
        emit(AutomationEvent.Type.AUTOMATION_STATE_CHANGED, mode.name());
        dirty.run();
    }

    public void tickDrive() {
        if (!owns()) return;
        try {
            if (getSelectedTrain().hasPlayerControl()) {
                yieldToPlayer();
                return;
            }
            if (mode == ControlMode.DIRECT_CONTROL) {
                backend.tick(getSelectedTrain().unwrap());
                if (!backend.isDriving(getSelectedTrain().unwrap())) {
                    mode = ControlMode.IDLE;
                    emit(AutomationEvent.Type.DIRECT_DRIVE_STOPPED, "Track end reached");
                    dirty.run();
                    releaseIfIdle();
                }
            }
            if (enabled) profile.beforeTrainTick(context);
            actors.tickPauses(getSelectedTrain());
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    /** Also cover contraptions created by chunk loading after Train.tick's movement hook. */
    public void tickActorControls(com.simibubi.create.content.trains.entity.CarriageContraptionEntity entity) {
        if (!owns()) return;
        try {
            if (enabled) profile.beforeTrainTick(context);
            actors.tickPauses(entity);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    public void tick() {
        checkThread();
        try {
            attach();
            if (mode == ControlMode.MANUAL_PLAYER_CONTROL
                    && selectedTrain != null
                    && !getSelectedTrain().hasPlayerControl()) {
                mode = ControlMode.IDLE;
                dirty.run();
            }
            if (restorePending) {
                start();
            }
            if (selectedTrain == null || (!enabled && !owns())) return;
            var train = getSelectedTrain();
            if (train.hasPlayerControl()) {
                yieldToPlayer();
                return;
            }
            if (train.isDerailed()) throw new IllegalStateException("Selected train is derailed");
            var station = train.unwrap().currentStation;
            if (station != null && !station.equals(lastStation)) {
                emit(AutomationEvent.Type.TRAIN_ARRIVED, train.getCurrentStation());
                if (enabled) profile.onTrainArrived(context, train.getCurrentStation());
            }
            lastStation = station;
            var versions = train.getInventory().versions();
            if (!versions.equals(inventoryVersions)
                    || (context.currentTime() % 100 == 0
                            && train.getInventory().fingerprint() != inventoryFingerprint)) {
                inventoryFingerprint = train.getInventory().fingerprint();
                inventoryVersions = versions;
                emit(AutomationEvent.Type.INVENTORY_CHANGED, "");
                if (enabled) profile.onInventoryChanged(context);
            }
            if (enabled) {
                profile.tick(context);
                String state = profile.getStatus();
                if (!state.equals(lastProfileState)) {
                    lastProfileState = state;
                    emit(AutomationEvent.Type.AUTOMATION_STATE_CHANGED, state);
                    dirty.run();
                }
            }
            if (mode == ControlMode.SCHEDULE_CONTROL && train.isScheduleFinished()) {
                mode = ControlMode.IDLE;
                dirty.run();
                releaseIfIdle();
            }
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void cleanupProfile() {
        if (enabled && profile != null && owns()) {
            try {
                profile.onStop(context);
            } catch (RuntimeException ignored) {
                /* Preserve the original movement error and always release control. */
            }
        }
    }

    public void fail(RuntimeException error) {
        cleanupProfile();
        enabled = false;
        restorePending = false;
        haltOwned();
        mode = ControlMode.ERROR;
        lastError =
                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        emit(AutomationEvent.Type.AUTOMATION_ERROR, lastError);
        dirty.run();
    }

    private void emit(AutomationEvent.Type type, String detail) {
        MinecraftForge.EVENT_BUS.post(
                new AutomationEvent(
                        type, controllerId, selectedTrain, detail == null ? "" : detail));
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("ControllerId", controllerId);
        if (selectedTrain != null) tag.putUUID("TrainId", selectedTrain);
        tag.putString("Profile", profileId);
        tag.putString("DriveBackend", backendId);
        tag.putBoolean("Enabled", isEnabled());
        tag.putString("LastError", lastError);
        var values = new CompoundTag();
        config.forEach(
                (k, v) -> {
                    if (v instanceof Boolean b) values.putBoolean(k, b);
                    else if (v instanceof Number n) values.putDouble(k, n.doubleValue());
                    else values.putString(k, v.toString());
                });
        tag.put("Configuration", values);
        tag.put("ProfileData", persistentData.copy());
        if (profile != null) tag.put("ProfileState", profile.serializeState());
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.hasUUID("ControllerId")) controllerId = tag.getUUID("ControllerId");
        selectedTrain = tag.hasUUID("TrainId") ? tag.getUUID("TrainId") : null;
        profileId = tag.getString("Profile");
        backendId = tag.contains("DriveBackend") ? tag.getString("DriveBackend") : "native";
        lastError = tag.getString("LastError");
        persistentData = tag.getCompound("ProfileData").copy();
        try {
            backend = DriveBackendRegistry.create(backendId);
            if (!profileId.isEmpty()) {
                profile = AutomationProfileRegistry.create(profileId);
                var values = tag.getCompound("Configuration");
                for (var f : profile.getConfigurationSchema()) {
                    Object value = f.defaultValue();
                    if (values.contains(f.key()))
                        value =
                                f.parse(
                                        switch (f.type()) {
                                            case NUMBER ->
                                                    Double.toString(values.getDouble(f.key()));
                                            case BOOLEAN ->
                                                    Boolean.toString(values.getBoolean(f.key()));
                                            default -> values.getString(f.key());
                                        });
                    config.put(f.key(), value);
                }
                profile.validateConfiguration(config);
                profile.deserializeState(tag.getCompound("ProfileState"));
            }
            restorePending = tag.getBoolean("Enabled");
        } catch (RuntimeException e) {
            restorePending = false;
            mode = ControlMode.ERROR;
            lastError = e.getMessage();
        }
    }
}
