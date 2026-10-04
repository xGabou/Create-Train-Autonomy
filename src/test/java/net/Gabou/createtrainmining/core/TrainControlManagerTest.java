package net.Gabou.createtrainmining.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class TrainControlManagerTest {
    @Test
    void leasesPreventConflictsAndCannotBeReleasedByAnotherController() {
        var leases = new TrainControlManager();
        var train = UUID.randomUUID();
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        leases.claim(train, a);
        leases.claim(train, a);
        assertThrows(IllegalStateException.class, () -> leases.claim(train, b));
        leases.release(train, b);
        assertTrue(leases.owns(train, a));
        leases.release(train, a);
        leases.claim(train, b);
        assertTrue(leases.owns(train, b));
    }

    @Test
    void independentTrainsAndServersHaveIndependentOwners() {
        var first = new TrainControlManager();
        var second = new TrainControlManager();
        var train = UUID.randomUUID();
        var other = UUID.randomUUID();
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        first.claim(train, a);
        first.claim(other, b);
        second.claim(train, b);
        assertTrue(first.owns(train, a));
        assertTrue(first.owns(other, b));
        assertTrue(second.owns(train, b));
    }
}
