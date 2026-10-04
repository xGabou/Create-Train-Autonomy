package net.Gabou.createtrainmining.api;

import net.neoforged.bus.api.Event;

import java.util.UUID;

/** Published on NeoForge.EVENT_BUS on the server thread. */
public final class AutomationEvent extends Event {
    public enum Type {
        TRAIN_SELECTED,
        DIRECT_DRIVE_STARTED,
        DIRECT_DRIVE_STOPPED,
        SCHEDULE_STARTED,
        TRAIN_ARRIVED,
        INVENTORY_CHANGED,
        AUTOMATION_STATE_CHANGED,
        AUTOMATION_ERROR
    }

    private final Type type;
    private final UUID controllerId, trainId;
    private final String detail;

    public AutomationEvent(Type type, UUID controllerId, UUID trainId, String detail) {
        this.type = type;
        this.controllerId = controllerId;
        this.trainId = trainId;
        this.detail = detail;
    }

    public Type type() {
        return type;
    }

    public UUID controllerId() {
        return controllerId;
    }

    public UUID trainId() {
        return trainId;
    }

    public String detail() {
        return detail;
    }
}
