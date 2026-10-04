package net.Gabou.createtrainmining.core;

import net.Gabou.createtrainmining.api.*;
import net.minecraft.resources.ResourceLocation;

public final class TrainActions {
    private TrainActions() {}

    public static TrainAction startDirectDrive(DriveRequest request) {
        return c -> c.controller().startDriving(request);
    }

    public static TrainAction stopDirectDrive() {
        return c -> c.controller().stopDriving();
    }

    public static TrainAction setSpeed(double speed) {
        return c -> c.controller().setSpeed(speed);
    }

    public static TrainAction applySchedule(TrainSchedule schedule) {
        return c -> c.controller().applySchedule(schedule);
    }

    public static TrainAction clearSchedule() {
        return c -> c.controller().clearSchedule();
    }

    public static TrainAction setActorState(ResourceLocation type, boolean enabled) {
        return c -> c.controller().setActorTypeEnabled(type, enabled);
    }
}
