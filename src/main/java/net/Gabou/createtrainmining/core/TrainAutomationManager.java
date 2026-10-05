package net.Gabou.createtrainmining.core;

import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** All mutable control state is scoped to the server and controller, never a singleton train. */
public final class TrainAutomationManager {
    private static final Map<MinecraftServer, TrainAutomationManager> SERVERS = new WeakHashMap<>();

    public static TrainAutomationManager get(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, TrainAutomationManager::new);
    }

    private final TrainControlManager ownership;
    private final java.util.Set<UUID> suspendedOrphans = new java.util.HashSet<>();

    private TrainAutomationManager(MinecraftServer server) {
        var data = TrainControlSavedData.get(server);
        ownership = new TrainControlManager(data.owners, data::setDirty);
    }

    private final Map<UUID, TrainController> controllers = new HashMap<>();

    public TrainControlManager ownership() {
        return ownership;
    }

    public void attach(TrainController controller) {
        var old = controllers.putIfAbsent(controller.getControllerId(), controller);
        if (old != null && old != controller)
            throw new IllegalStateException("Duplicate controller identity");
    }

    public void detach(TrainController controller) {
        controllers.remove(controller.getControllerId(), controller);
    }

    public TrainController owner(UUID trainId) {
        return controllers.get(ownership.owner(trainId));
    }

    public static boolean isManaged(Train train) {
        return SERVERS.values().stream().anyMatch(m -> m.ownership.owner(train.id) != null);
    }

    public static void beforeTrainTick(Train train, Level level) {
        if (level.isClientSide || level.getServer() == null) return;
        var manager = get(level.getServer());
        var controller = manager.owner(train.id);
        if (controller != null) {
            manager.suspendedOrphans.remove(train.id);
            controller.tickDrive();
        } else if (manager.ownership.owner(train.id) != null
                && manager.suspendedOrphans.add(train.id)) {
            TrainMovementIntegrations.suspend(train);
            train.runtime.paused = true;
            train.navigation.cancelNavigation();
            train.manualTick = false;
            train.targetSpeed = 0;
            train.speed = 0;
            train.speedBeforeStall = null;
            com.simibubi.create.Create.RAILWAYS.markTracksDirty();
        }
    }

    public static void playerTakeover(Train train, Level level) {
        if (level.getServer() == null) return;
        var controller = get(level.getServer()).owner(train.id);
        if (controller != null) controller.yieldToPlayer();
        else {
            var manager = get(level.getServer());
            var owner = manager.ownership.owner(train.id);
            if (owner != null) manager.ownership.release(train.id, owner);
            manager.suspendedOrphans.remove(train.id);
        }
    }

    public static void beforeActorTick(
            com.simibubi.create.content.trains.entity.CarriageContraptionEntity entity) {
        var level = entity.level();
        if (level.isClientSide || level.getServer() == null) return;
        var carriage = entity.getCarriage();
        if (carriage == null || carriage.train == null) return;
        var controller = get(level.getServer()).owner(carriage.train.id);
        if (controller != null) controller.tickActorControls(entity);
    }

    public static void shutdown(MinecraftServer server) {
        var manager = SERVERS.remove(server);
        if (manager != null) ListCopy.stop(manager);
    }

    private static final class ListCopy {
        static void stop(TrainAutomationManager manager) {
            java.util.List.copyOf(manager.controllers.values()).forEach(TrainController::unload);
        }
    }
}
