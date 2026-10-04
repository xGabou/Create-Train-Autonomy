package net.Gabou.createtrainmining.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConfigurationFieldTest {
    @Test
    void unsafeNumericInputsAreRejected() {
        var f = ConfigurationField.ratio("speed", "Speed", .2);
        for (String input : new String[] {"NaN", "Infinity", "-0.1", "1.1"})
            assertThrows(IllegalArgumentException.class, () -> f.parse(input));
        assertEquals(.85, f.parse("0.85"));
    }

    @Test
    void choicesAndBooleansAreStrict() {
        var f =
                ConfigurationField.choice(
                        "direction", "Direction", "FORWARD", "FORWARD", "BACKWARD");
        assertEquals("BACKWARD", f.parse("BACKWARD"));
        assertThrows(IllegalArgumentException.class, () -> f.parse("MINING"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigurationField.toggle("resume", "Resume", true).parse("yes"));
    }

    @Test
    void driveRequestRejectsNonFiniteOrOutOfRangeSpeeds() {
        for (double speed : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -.1, 1.1})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> DriveRequest.of(DriveDirection.FORWARD, speed));
    }
}
