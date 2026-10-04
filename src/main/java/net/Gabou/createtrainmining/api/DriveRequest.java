package net.Gabou.createtrainmining.api;

import java.util.Objects;

/** Speed is a fraction of Create's configured manual maximum, not blocks/tick. */
public record DriveRequest(
        DriveDirection direction,
        double speed,
        SwitchStrategy switchStrategy,
        SignalBehavior signalBehavior,
        boolean stopAtTrackEnd,
        boolean allowChunkWaiting) {
    public enum SwitchStrategy {
        STRAIGHT,
        LEFT,
        RIGHT
    }

    public enum SignalBehavior {
        OBEY,
        IGNORE
    }

    public DriveRequest {
        Objects.requireNonNull(direction);
        Objects.requireNonNull(switchStrategy);
        Objects.requireNonNull(signalBehavior);
        if (!Double.isFinite(speed) || speed < 0 || speed > 1)
            throw new IllegalArgumentException("Speed must be between 0 and 1");
    }

    public static DriveRequest of(DriveDirection direction, double speed) {
        return new DriveRequest(
                direction, speed, SwitchStrategy.STRAIGHT, SignalBehavior.OBEY, true, true);
    }

    public DriveRequest withSpeed(double speed) {
        return new DriveRequest(
                direction,
                speed,
                switchStrategy,
                signalBehavior,
                stopAtTrackEnd,
                allowChunkWaiting);
    }

    public DriveRequest withDirection(DriveDirection direction) {
        return new DriveRequest(
                direction,
                speed,
                switchStrategy,
                signalBehavior,
                stopAtTrackEnd,
                allowChunkWaiting);
    }
}
