package net.Gabou.createtrainmining.core;

import com.simibubi.create.content.trains.entity.Train;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Adapter hooks for suspending optional movement systems before a control handoff. */
public final class TrainMovementIntegrations {
    private static final List<Consumer<Train>> SUSPENDERS = new CopyOnWriteArrayList<>();

    private TrainMovementIntegrations() {}

    public static void registerSuspender(Consumer<Train> suspender) {
        SUSPENDERS.add(java.util.Objects.requireNonNull(suspender));
    }

    public static void suspend(Train train) {
        SUSPENDERS.forEach(s -> s.accept(train));
    }
}
