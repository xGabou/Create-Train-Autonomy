package net.Gabou.createtrainmining.core;

import net.Gabou.createtrainmining.api.TrainDriveBackend;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class DriveBackendRegistry {
    private static final Map<String, Supplier<? extends TrainDriveBackend>> FACTORIES =
            new LinkedHashMap<>();

    private DriveBackendRegistry() {}

    public static synchronized void register(
            String id, Supplier<? extends TrainDriveBackend> factory) {
        if (!id.matches("[a-z0-9_.:-]+") || FACTORIES.putIfAbsent(id, factory) != null)
            throw new IllegalArgumentException("Invalid or duplicate backend: " + id);
    }

    public static synchronized TrainDriveBackend create(String id) {
        var factory = FACTORIES.get(id);
        if (factory == null) throw new IllegalArgumentException("Unknown drive backend: " + id);
        return factory.get();
    }

    public static synchronized List<String> ids() {
        return List.copyOf(FACTORIES.keySet());
    }
}
