package net.Gabou.createtrainmining.core;

/** Placement scope is thread-local and restored even if Create throws. */
public final class TrackPlacementContext {
    private TrackPlacementContext() {}
    public static final ThreadLocal<Boolean> LEVEL_TRAIN = ThreadLocal.withInitial(() -> false);
}
