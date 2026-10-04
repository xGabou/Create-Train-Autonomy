package net.Gabou.createtrainmining.api;

import com.simibubi.create.content.trains.entity.Train;

/**
 * Adapter SPI. Only backends/adapters should access Create Train fields. One instance per
 * controller.
 */
public interface TrainDriveBackend {
    boolean isAvailable(Train train);

    void start(Train train, DriveRequest request);

    void stop(Train train);

    void tick(Train train);

    void setSpeed(Train train, double speed);

    void setDirection(Train train, DriveDirection direction);

    double getSpeed(Train train);

    DriveDirection getDirection(Train train);

    boolean isDriving(Train train);

    String getId();

    String getDisplayName();
}
