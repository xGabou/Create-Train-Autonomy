package net.Gabou.createtrainmining.core;

import net.Gabou.createtrainmining.api.AutomationProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class AutomationProfileRegistry {
    private static final Map<String, Supplier<? extends AutomationProfile>> FACTORIES =
            new LinkedHashMap<>();

    private AutomationProfileRegistry() {}

    public static synchronized void register(
            String id, Supplier<? extends AutomationProfile> factory) {
        if (!id.matches("[a-z0-9_.:-]+") || FACTORIES.putIfAbsent(id, factory) != null)
            throw new IllegalArgumentException("Invalid or duplicate profile: " + id);
    }

    public static synchronized AutomationProfile create(String id) {
        var factory = FACTORIES.get(id);
        if (factory == null)
            throw new IllegalArgumentException("Unknown automation profile: " + id);
        return factory.get();
    }

    public static synchronized List<String> ids() {
        return List.copyOf(FACTORIES.keySet());
    }
}
