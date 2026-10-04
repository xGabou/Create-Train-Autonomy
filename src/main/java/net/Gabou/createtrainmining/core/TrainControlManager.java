package net.Gabou.createtrainmining.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-scoped leases; main-thread only. Release requires the owner's identity. */
public final class TrainControlManager {
    private final Map<UUID, UUID> owners;
    private final Runnable dirty;

    public TrainControlManager() {
        this(new HashMap<>(), () -> {});
    }

    TrainControlManager(Map<UUID, UUID> owners, Runnable dirty) {
        this.owners = owners;
        this.dirty = dirty;
    }

    public void claim(UUID trainId, UUID controllerId) {
        UUID owner = owners.putIfAbsent(trainId, controllerId);
        if (owner != null && !owner.equals(controllerId))
            throw new IllegalStateException("Train is already controlled by " + owner);
        if (owner == null) dirty.run();
    }

    public boolean owns(UUID trainId, UUID controllerId) {
        return controllerId.equals(owners.get(trainId));
    }

    public void release(UUID trainId, UUID controllerId) {
        if (owners.remove(trainId, controllerId)) dirty.run();
    }

    public UUID owner(UUID trainId) {
        return owners.get(trainId);
    }
}
