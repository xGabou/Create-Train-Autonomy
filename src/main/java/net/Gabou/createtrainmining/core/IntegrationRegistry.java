package net.Gabou.createtrainmining.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Addons can provide immutable/server-safe services without adding core dependencies. */
public final class IntegrationRegistry {
    private static final Map<String, Object> SERVICES = new ConcurrentHashMap<>();

    private IntegrationRegistry() {}

    public static void register(String id, Object service) {
        if (SERVICES.putIfAbsent(id, java.util.Objects.requireNonNull(service)) != null)
            throw new IllegalArgumentException("Integration already registered: " + id);
    }

    public static Optional<Object> find(String id) {
        return Optional.ofNullable(SERVICES.get(id));
    }
}
