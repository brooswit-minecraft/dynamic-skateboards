package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkateMovementTest {
    @Test
    void groundSpeedRetentionNeverDecaysFasterThanTunedFriction() {
        double speed = 0.4;
        for (int tick = 0; tick < 10; tick++) {
            double expected = speed * SkateConstants.FRICTION;
            speed = SkateMovement.nextGroundSpeed(speed, 0.0); // nothing pushing it higher
            assertEquals(expected, speed, 1e-9, "decay must be governed exactly by FRICTION, tick " + tick);
        }
    }

    @Test
    void groundSpeedAcceleratesTowardObservedSpeedWhenPushedHigher() {
        double speed = 0.0;
        double next = SkateMovement.nextGroundSpeed(speed, SkateConstants.TOP_SPEED);
        assertEquals(SkateConstants.ACCELERATION, next, 1e-9);
    }

    @Test
    void groundSpeedNeverExceedsTopSpeed() {
        double speed = SkateMovement.nextGroundSpeed(SkateConstants.TOP_SPEED, 10.0);
        assertTrue(speed <= SkateConstants.TOP_SPEED);
    }

    @Test
    void turnTowardDegreesClampsToMaxDelta() {
        double result = SkateMovement.turnTowardDegrees(0.0, 90.0, 10.0);
        assertEquals(10.0, result, 1e-9);
    }

    @Test
    void turnTowardDegreesTakesShorterWayAroundCompass() {
        double result = SkateMovement.turnTowardDegrees(170.0, -170.0, 5.0);
        assertEquals(175.0, result, 1e-9, "170 -> -170 is a 20 degree gap the short way (through 180), not 340");
    }

    @Test
    void turnTowardDegreesSnapsWhenWithinMaxDelta() {
        double result = SkateMovement.turnTowardDegrees(0.0, 3.0, 10.0);
        assertEquals(3.0, result, 1e-9);
    }
}
