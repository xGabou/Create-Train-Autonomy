package net.Gabou.createtrainmining.core;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;

import net.Gabou.createtrainmining.api.DriveDirection;

import java.util.UUID;

/** Resolves the live Create object each access. No copied motion/navigation state. */
public final class ManagedTrain {
    private final UUID id;

    public ManagedTrain(UUID id) {
        this.id = id;
    }

    Train unwrap() {
        Train train = Create.RAILWAYS.trains.get(id);
        if (train == null || train.invalid)
            throw new IllegalStateException("Selected train is unavailable: " + id);
        return train;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return unwrap().name.getString();
    }

    public UUID getCurrentGraph() {
        var graph = unwrap().graph;
        return graph == null ? null : graph.id;
    }

    public String getCurrentStation() {
        var station = unwrap().getCurrentStation();
        return station == null ? null : station.name;
    }

    public double getSpeed() {
        return unwrap().speed;
    }

    public double getTargetSpeed() {
        return unwrap().targetSpeed;
    }

    public DriveDirection getDirection() {
        return (unwrap().speed == 0 ? unwrap().currentlyBackwards : unwrap().speed < 0)
                ? DriveDirection.BACKWARD
                : DriveDirection.FORWARD;
    }

    public boolean isDerailed() {
        return unwrap().derailed;
    }

    public boolean isWaitingForChunks() {
        return unwrap().carriageWaitingForChunks != -1;
    }

    public boolean isNavigating() {
        return unwrap().navigation.destination != null;
    }

    public boolean hasSchedule() {
        return unwrap().runtime.getSchedule() != null;
    }

    public boolean isSchedulePaused() {
        return unwrap().runtime.paused;
    }

    public boolean isScheduleFinished() {
        return unwrap().runtime.completed;
    }

    public int getCarriageCount() {
        return unwrap().carriages.size();
    }

    public boolean hasForwardConductor() {
        return unwrap().hasForwardConductor();
    }

    public boolean hasBackwardConductor() {
        return unwrap().hasBackwardConductor();
    }

    public boolean hasPlayerControl() {
        boolean[] controlled = {false};
        unwrap().carriages
                .forEach(
                        c ->
                                c.forEachPresentEntity(
                                        e ->
                                                controlled[0] |=
                                                        e.getControllingPlayer().isPresent()));
        return controlled[0];
    }

    public TrainInventoryView getInventory() {
        return new TrainInventoryView(this);
    }
}
