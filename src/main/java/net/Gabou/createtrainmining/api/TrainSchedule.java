package net.Gabou.createtrainmining.api;

import java.util.List;

/** Station filters use Create's schedule matching rules. No Create NBT leaks to profiles. */
public record TrainSchedule(List<String> stations, boolean cyclic) {
    public TrainSchedule {
        stations = List.copyOf(stations);
        if (stations.isEmpty()
                || stations.size() > 128
                || stations.stream().anyMatch(s -> s.isBlank() || s.length() > 256))
            throw new IllegalArgumentException(
                    "A schedule needs 1 to 128 non-empty station filters");
    }

    public static TrainSchedule toStation(String name) {
        return new TrainSchedule(List.of(name), false);
    }
}
