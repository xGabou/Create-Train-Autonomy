package net.Gabou.createtrainmining.core;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class TrainDiscovery {
    private TrainDiscovery() {}

    public static List<ManagedTrain> trains() {
        return Create.RAILWAYS.trains.values().stream()
                .filter(t -> !t.invalid)
                .map(t -> new ManagedTrain(t.id))
                .sorted(
                        Comparator.comparing(ManagedTrain::getName)
                                .thenComparing(ManagedTrain::getId))
                .toList();
    }

    public static UUID byName(String name) {
        var matches = trains().stream().filter(t -> t.getName().equals(name)).toList();
        if (matches.size() != 1)
            throw new IllegalArgumentException(
                    matches.isEmpty()
                            ? "No train named " + name
                            : "Train name is ambiguous; select its UUID");
        return matches.getFirst().getId();
    }

    public static List<StationView> stations() {
        return Create.RAILWAYS.trackNetworks.values().stream()
                .flatMap(g -> g.getPoints(EdgePointType.STATION).stream())
                .map(
                        s ->
                                new StationView(
                                        s.id,
                                        s.name,
                                        s.getPresentTrain() == null
                                                ? null
                                                : s.getPresentTrain().id))
                .sorted(Comparator.comparing(StationView::name).thenComparing(StationView::id))
                .toList();
    }
}
