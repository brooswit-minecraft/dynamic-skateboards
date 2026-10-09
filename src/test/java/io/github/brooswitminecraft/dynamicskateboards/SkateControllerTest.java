package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkateControllerTest {
    private static SkateInput held(boolean board, boolean jump, boolean onGround) {
        return new SkateInput(board, jump, onGround, 0.0, 0.0);
    }

    @Test
    void mainHandEntersSkating() {
        SkateController c = new SkateController();
        assertEquals(SkateState.SKATING, c.update(held(true, false, true)));
        assertTrue(c.isSkating());
    }

    @Test
    void offHandIsNotSkating() {
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        // Moving the board to the off hand means it is no longer the main-hand item;
        // the caller always reports "is the skateboard the main-hand item", so this is false.
        SkateState state = c.update(held(false, false, true));
        assertEquals(SkateState.GROUNDED, state, "off hand must not be skating");
        assertFalse(c.isSkating(), "off hand must not be skating");
    }

    @Test
    void switchingAwayExitsSkating() {
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        c.update(held(false, false, true));
        assertEquals(SkateState.GROUNDED, c.state());
        assertFalse(c.isSkating());
    }

    @Test
    void reselectingResumesSkating() {
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        c.update(held(false, false, true));
        SkateState state = c.update(held(true, false, true));
        assertEquals(SkateState.SKATING, state);
        assertTrue(c.isSkating());
    }

    @Test
    void startsGrounded() {
        assertEquals(SkateState.GROUNDED, new SkateController().state());
    }

    @Test
    void holdingJumpOnGroundEntersCharging() {
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        assertEquals(SkateState.CHARGING, c.update(held(true, true, true)));
    }

    @Test
    void releasingJumpPopsIntoAirborneWithNoImpulseOnPlainRelease() {
        // A release while NOT charging (plain SKATING -> airborne via leaving the ground) must
        // never carry an ollie impulse: that's a ramp launch, not an ollie.
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        c.update(held(true, false, false)); // walked/launched off the ground, never charged
        assertEquals(SkateState.AIRBORNE, c.state());
        assertNull(c.takePendingOllieImpulse());
    }

    @Test
    void zeroLengthTapGivesDefinedNonPunishingPop() {
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        c.update(held(true, true, true)); // press -> CHARGING, chargeTicks = 0
        c.update(held(true, false, true)); // immediate release
        Double impulse = c.takePendingOllieImpulse();
        assertNotNull(impulse, "a tap must still produce a defined pop");
        assertEquals(SkateConstants.OLLIE_MIN_IMPULSE, impulse, 1e-9);
        assertTrue(impulse > 0.0, "a tap must not be punishing (zero impulse)");
    }

    @Test
    void chargeCurveIsMonotonicNonDecreasing() {
        double previous = -1.0;
        for (int ticks = 0; ticks <= SkateConstants.OLLIE_MAX_CHARGE_TICKS + 5; ticks++) {
            double impulse = SkateMovement.ollieImpulse(ticks);
            assertTrue(impulse >= previous, "impulse must not decrease as charge ticks increase");
            previous = impulse;
        }
        assertEquals(SkateConstants.OLLIE_MAX_IMPULSE, SkateMovement.ollieImpulse(SkateConstants.OLLIE_MAX_CHARGE_TICKS), 1e-9);
    }

    @Test
    void fullyChargedReleaseGivesMaxImpulse() {
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        c.update(held(true, true, true));
        for (int i = 0; i < SkateConstants.OLLIE_MAX_CHARGE_TICKS + 3; i++) {
            c.update(held(true, true, true));
        }
        c.update(held(true, false, true));
        assertEquals(SkateConstants.OLLIE_MAX_IMPULSE, c.takePendingOllieImpulse(), 1e-9);
    }

    @Test
    void leavingGroundMidChargeCancelsImpulse() {
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        c.update(held(true, true, true));
        c.update(held(true, true, true));
        SkateState state = c.update(held(true, true, false)); // still holding jump, ground vanishes
        assertEquals(SkateState.AIRBORNE, state);
        assertNull(c.takePendingOllieImpulse());
    }

    @Test
    void landingReturnsToSkatingAfterTolerance() {
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        c.update(held(true, false, false)); // airborne
        assertEquals(SkateState.AIRBORNE, c.state());
        c.update(held(true, false, true)); // touches down -> LANDING
        assertEquals(SkateState.LANDING, c.state());
        for (int i = 1; i < SkateConstants.LANDING_TOLERANCE_TICKS; i++) {
            assertEquals(SkateState.LANDING, c.update(held(true, false, true)));
        }
        assertEquals(SkateState.SKATING, c.update(held(true, false, true)));
    }

    @Test
    void jumpBufferedDuringLandingStartsChargeOnceSkating() {
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        c.update(held(true, false, false)); // airborne
        c.update(held(true, false, true)); // LANDING begins
        c.update(held(true, true, true));  // press Jump mid-landing (buffered, not yet charging)
        assertEquals(SkateState.LANDING, c.state());
        SkateState finalState = null;
        for (int i = 2; i <= SkateConstants.LANDING_TOLERANCE_TICKS; i++) {
            finalState = c.update(held(true, false, true));
        }
        assertEquals(SkateState.CHARGING, finalState, "buffered jump should start charging the moment landing completes");
    }
}
