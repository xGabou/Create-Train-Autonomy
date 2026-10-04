package net.Gabou.createtrainmining.drive;

/** Discrete-tick braking envelope includes this tick's travel and a buffer before the obstacle. */
public final class DriveSafety {
    private DriveSafety() {}

    public static double safeSpeed(double acceleration, double distance, double margin) {
        if (!(acceleration > 0) || !Double.isFinite(acceleration)) return 0;
        double available = Math.max(0, distance - margin);
        return Math.max(
                0,
                Math.sqrt(acceleration * acceleration + 2 * acceleration * available)
                        - acceleration);
    }
}
