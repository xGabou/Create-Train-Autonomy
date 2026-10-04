package net.Gabou.createtrainmining.compat.railwaysadditions;

import com.simibubi.create.content.trains.entity.Train;

import net.Gabou.createtrainmining.api.*;
import net.Gabou.createtrainmining.drive.NativeCreateDriveBackend;

/** Addon engagement/remote visibility with the same native steering/safety envelope. */
public final class RailwaysAdditionsDriveBackend extends NativeCreateDriveBackend {
    public void start(Train train, DriveRequest request) {
        super.start(train, request);
        updateCruise(train);
    }

    public void setSpeed(Train train, double speed) {
        super.setSpeed(train, speed);
        updateCruise(train);
    }

    public void setDirection(Train train, DriveDirection direction) {
        super.setDirection(train, direction);
        updateCruise(train);
    }

    private void updateCruise(Train train) {
        RailwaysAdditionsIntegration.engage(
                train, request.direction().sign() * request.speed() * train.maxSpeed());
    }

    public void tick(Train train) {
        if (isDriving(train) && !RailwaysAdditionsIntegration.isEngaged(train)) {
            stop(train);
            return;
        }
        super.tick(train);
    }

    public void stop(Train train) {
        RailwaysAdditionsIntegration.disengage(train);
        super.stop(train);
    }

    public String getId() {
        return "railways_additions";
    }

    public String getDisplayName() {
        return "Railways Additions Cruise Control";
    }
}
