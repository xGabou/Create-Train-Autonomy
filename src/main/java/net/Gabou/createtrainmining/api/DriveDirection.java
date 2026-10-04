package net.Gabou.createtrainmining.api;

public enum DriveDirection {
    FORWARD(1),
    BACKWARD(-1);
    private final int sign;

    DriveDirection(int sign) {
        this.sign = sign;
    }

    public int sign() {
        return sign;
    }
}
