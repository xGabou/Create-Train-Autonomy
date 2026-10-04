package net.Gabou.createtrainmining.compat.railwaysadditions;

import com.simibubi.create.content.trains.entity.Train;

import net.Gabou.createtrainmining.core.DriveBackendRegistry;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/** Resolve optional classes only when the addon is installed. Target: supplied 1.0.1a artifact. */
public final class RailwaysAdditionsIntegration {
    private static Method engage, disengage, isEngaged;

    private RailwaysAdditionsIntegration() {}

    public static void register() {
        if (!ModList.get().isLoaded("railwaysuntold_additions")) return;
        try {
            var api =
                    Class.forName(
                            "com.vodmordia.railwaysuntold_additions.contraption.CruiseControlManager");
            engage = api.getMethod("engage", Train.class, double.class);
            disengage = api.getMethod("disengage", java.util.UUID.class);
            isEngaged = api.getMethod("isEngaged", java.util.UUID.class);
            net.Gabou.createtrainmining.core.TrainMovementIntegrations.registerSuspender(
                    RailwaysAdditionsIntegration::disengage);
            DriveBackendRegistry.register("railways_additions", RailwaysAdditionsDriveBackend::new);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Railways Additions Cruise Control API", e);
        }
    }

    static boolean isEngaged(Train train) {
        try {
            return isEngaged != null && (Boolean) isEngaged.invoke(null, train.id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Railways Additions Cruise Control failed", e);
        }
    }

    static void engage(Train train, double speed) {
        if (engage != null) invoke(engage, train, speed);
    }

    public static void disengage(Train train) {
        if (disengage != null) invoke(disengage, train.id);
    }

    private static void invoke(Method method, Object... args) {
        try {
            method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Railways Additions Cruise Control failed", e);
        }
    }
}
