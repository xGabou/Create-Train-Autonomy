package net.Gabou.createtrainmining.drive;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DriveSafetyTest {
    @Test
    void discreteBrakingNeverCrossesTheStopBuffer() {
        double acceleration = .02, margin = .5;
        for (double startDistance : new double[] {.5, .6, 1, 4.5, 20, 100}) {
            double remaining = startDistance;
            double speed = DriveSafety.safeSpeed(acceleration, remaining, margin);
            for (int tick = 0; tick < 1000 && speed > 1e-6; tick++) {
                double target = DriveSafety.safeSpeed(acceleration, remaining, margin);
                speed = Math.max(target, speed - acceleration);
                remaining -= speed;
                assertTrue(
                        remaining >= margin - 1e-8,
                        "crossed obstacle at distance " + startDistance);
            }
        }
    }

    @Test
    void noSpaceOrInvalidAccelerationMeansNoMovement() {
        assertEquals(0, DriveSafety.safeSpeed(.02, .2, .5));
        assertEquals(0, DriveSafety.safeSpeed(0, 10, .5));
        assertEquals(0, DriveSafety.safeSpeed(Double.NaN, 10, .5));
    }
}
