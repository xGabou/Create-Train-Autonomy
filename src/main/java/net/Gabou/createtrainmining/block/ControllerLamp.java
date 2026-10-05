package net.Gabou.createtrainmining.block;

import net.minecraft.util.StringRepresentable;

/** A visual state only; it never participates in train automation. */
public enum ControllerLamp implements StringRepresentable {
    INACTIVE,
    RUNNING,
    WAITING,
    ERROR;

    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
