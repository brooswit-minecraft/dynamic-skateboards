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

    private static SkateInput trick(
            boolean onGround, boolean shift, boolean attackJustPressed, boolean useHeld, TrickDirection direction) {
        return new SkateInput(true, false, onGround, 0.0, 0.0, shift, attackJustPressed, useHeld, direction, 0.0);
    }

    /** A touchdown ({@code onGround}) carrying {@code verticalVelocity} blocks/tick of incoming fall speed. */
    private static SkateInput landing(double verticalVelocity) {
        return new SkateInput(true, false, true, 0.0, 0.0, false, false, false, TrickDirection.NEUTRAL, verticalVelocity);
    }

    private static final class RecordingGrindSeam implements GrindSeam {
        private boolean called;

        @Override
        public boolean tryGrind(SkateInput input) {
            called = true;
            return false;
        }
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

    // --- Shift contextual dispatch: ride -> MANUAL, airborne/approaching -> grind seam ---

    @Test
    void shiftWhileRidingEntersManual() {
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        SkateState state = c.update(trick(true, true, false, false, TrickDirection.NEUTRAL));
        assertEquals(SkateState.MANUAL, state);
    }

    @Test
    void releasingShiftWhileManualReturnsToSkating() {
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        c.update(trick(true, true, false, false, TrickDirection.NEUTRAL)); // MANUAL
        SkateState state = c.update(trick(true, false, false, false, TrickDirection.NEUTRAL));
        assertEquals(SkateState.SKATING, state);
    }

    @Test
    void manualGoesAirborneIfGroundDisappears() {
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        c.update(trick(true, true, false, false, TrickDirection.NEUTRAL)); // MANUAL
        SkateState state = c.update(trick(false, true, false, false, TrickDirection.NEUTRAL));
        assertEquals(SkateState.AIRBORNE, state);
    }

    @Test
    void shiftWhileAirborneRoutesToGrindSeamNotManual() {
        RecordingGrindSeam seam = new RecordingGrindSeam();
        SkateController c = new SkateController(seam);
        c.update(held(true, false, true)); // SKATING
        c.update(held(true, false, false)); // AIRBORNE
        SkateState state = c.update(trick(false, true, false, false, TrickDirection.NEUTRAL));
        assertTrue(seam.called, "Shift while AIRBORNE must route to the grind seam");
        assertEquals(SkateState.AIRBORNE, state, "the no-op seam must not itself change state");
        assertFalse(state == SkateState.MANUAL, "airborne Shift must never become MANUAL");
    }

    @Test
    void shiftWhileAirborneNeverCallsSeamWhileRiding() {
        RecordingGrindSeam seam = new RecordingGrindSeam();
        SkateController c = new SkateController(seam);
        c.update(held(true, false, true)); // SKATING
        c.update(trick(true, true, false, false, TrickDirection.NEUTRAL)); // MANUAL via Shift
        assertFalse(seam.called, "Shift while riding must go to MANUAL, not the grind seam");
    }

    // --- FLIP family: left click + direction ---

    @Test
    void leftClickSelectsExpectedFlipPerDirection() {
        for (TrickDirection direction : TrickDirection.values()) {
            SkateController c = new SkateController();
            c.update(held(true, false, true)); // SKATING
            c.update(held(true, false, false)); // AIRBORNE
            c.update(trick(false, false, true, false, direction));
            assertEquals(TrickTable.flipFor(direction), c.activeFlip(),
                    "direction " + direction + " must select its table flip");
        }
    }

    @Test
    void flipLandsAfterLandingTolerance() {
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        c.update(held(true, false, false)); // AIRBORNE
        c.update(trick(false, false, true, false, TrickDirection.LEFT)); // selects HEELFLIP
        c.update(held(true, false, true)); // touches down -> LANDING
        FlipTrick landed = null;
        for (int i = 1; i <= SkateConstants.LANDING_TOLERANCE_TICKS; i++) {
            FlipTrick taken = c.update(held(true, false, true)) == SkateState.SKATING ? c.takeCompletedFlip() : null;
            if (taken != null) {
                landed = taken;
            }
        }
        assertEquals(FlipTrick.HEELFLIP, landed, "the selected flip must be reported landed once LANDING completes");
    }

    @Test
    void flipPressSlightlyBeforeTakeoffIsStillRecognized() {
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        c.update(trick(true, false, true, false, TrickDirection.FORWARD)); // press while still grounded
        c.update(held(true, false, true)); // one more grounded tick, well within the window
        c.update(held(true, false, false)); // leaves the ground -> AIRBORNE (dispatched from SKATING, no consume yet)
        c.update(held(true, false, false)); // first tick dispatched AS AIRBORNE: consumes the buffered press
        assertEquals(TrickTable.flipFor(TrickDirection.FORWARD), c.activeFlip(),
                "a press a couple of ticks before takeoff must still land the intended trick");
    }

    @Test
    void flipPressTooEarlyIsDroppedOutsideTheWindow() {
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        c.update(trick(true, false, true, false, TrickDirection.FORWARD)); // press while grounded
        for (int i = 0; i < SkateConstants.INPUT_BUFFER_WINDOW_TICKS + 2; i++) {
            c.update(held(true, false, true)); // stay grounded well past the window
        }
        c.update(held(true, false, false)); // leaves the ground -> AIRBORNE, long after the window elapsed
        c.update(held(true, false, false)); // first tick dispatched AS AIRBORNE: nothing left to consume
        assertNull(c.activeFlip(), "a press outside the buffer window must be dropped, not recognized late");
    }

    // --- GRAB family: right click (held) + direction ---

    @Test
    void rightClickSelectsExpectedGrabPerDirection() {
        for (TrickDirection direction : TrickDirection.values()) {
            SkateController c = new SkateController();
            c.update(held(true, false, true)); // SKATING
            c.update(held(true, false, false)); // AIRBORNE
            c.update(trick(false, false, false, true, direction));
            assertEquals(TrickTable.grabFor(direction), c.activeGrab(),
                    "direction " + direction + " must select its table grab");
        }
    }

    @Test
    void grabSustainsWhileHeldAndEndsOnRelease() {
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        c.update(held(true, false, false)); // AIRBORNE
        c.update(trick(false, false, false, true, TrickDirection.BACK));
        assertEquals(GrabTrick.TAIL, c.activeGrab());
        c.update(trick(false, false, false, true, TrickDirection.BACK)); // still held
        assertEquals(GrabTrick.TAIL, c.activeGrab(), "grab must sustain while held");
        c.update(trick(false, false, false, false, TrickDirection.BACK)); // released
        assertNull(c.activeGrab(), "grab must end on release");
    }

    @Test
    void grabEndsWhenLeavingAirborneEvenIfStillHeld() {
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        c.update(held(true, false, false)); // AIRBORNE
        c.update(trick(false, false, false, true, TrickDirection.RIGHT));
        assertEquals(GrabTrick.CRAIL, c.activeGrab());
        c.update(trick(true, false, false, true, TrickDirection.RIGHT)); // touches down, still "held"
        assertNull(c.activeGrab(), "a grab must not survive past AIRBORNE even if the button is still held");
    }

    // --- Vanilla click suppression (pure decision; wiring is Minecraft glue, untestable here) ---

    @Test
    void clicksAreSuppressedWhileSkatingAndRestoredWhenGrounded() {
        SkateController c = new SkateController();
        assertFalse(ClickSuppressionPolicy.suppressVanillaClicks(c.state()), "GROUNDED must not suppress");
        c.update(held(true, false, true)); // SKATING
        assertTrue(ClickSuppressionPolicy.suppressVanillaClicks(c.state()), "SKATING must suppress");
        c.update(held(false, false, true)); // board leaves hand -> GROUNDED
        assertFalse(ClickSuppressionPolicy.suppressVanillaClicks(c.state()), "suppression must be restored once skating ends");
    }

    // --- Bail (story f): a badly missed landing forces the loose-board exit, not LANDING ---

    @Test
    void normalLandingSpeedNeverBails() {
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        c.update(held(true, false, false)); // AIRBORNE
        SkateState state = c.update(landing(SkateConstants.BAIL_IMPACT_SPEED_THRESHOLD - 0.01));
        assertEquals(SkateState.LANDING, state, "a landing under the bail threshold must resolve normally");
        assertFalse(c.takeBailedThisTick());
    }

    @Test
    void landingAtOrAboveThresholdBails() {
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        c.update(held(true, false, false)); // AIRBORNE
        SkateState state = c.update(landing(SkateConstants.BAIL_IMPACT_SPEED_THRESHOLD));
        assertEquals(SkateState.GROUNDED, state, "a badly missed landing must exit skating, not enter LANDING");
        assertTrue(c.takeBailedThisTick(), "the bail flag must be set exactly for this tick");
        assertFalse(c.isSkating());
    }

    @Test
    void bailFlagIsConsumedOnce() {
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        c.update(held(true, false, false));
        c.update(landing(SkateConstants.BAIL_IMPACT_SPEED_THRESHOLD));
        assertTrue(c.takeBailedThisTick());
        assertFalse(c.takeBailedThisTick(), "the flag must not still read true after being taken");
    }

    @Test
    void bailDropsAnInProgressFlipInsteadOfCompletingIt() {
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        c.update(held(true, false, false)); // AIRBORNE
        c.update(trick(false, false, true, false, TrickDirection.LEFT)); // selects HEELFLIP
        c.update(landing(SkateConstants.BAIL_IMPACT_SPEED_THRESHOLD));
        assertNull(c.activeFlip(), "a bailed flip must not remain active");
        assertNull(c.takeCompletedFlip(), "a bailed flip must never report as completed");
    }

    @Test
    void bailFromHighSpeedGrindDismountStillTriggersOnTheFollowingTouchdown() {
        // Models WorldGrindSeam releasing a grind mid-air (ran off the end) and handing the
        // player back to normal falling physics: the controller stays AIRBORNE, and the very
        // next touchdown is where this story's bail check lives - no separate grind-specific
        // bail path is needed.
        SkateController c = new SkateController();
        c.update(held(true, false, true)); // SKATING
        c.update(held(true, false, false)); // AIRBORNE (grind acquired/ridden off-controller)
        c.update(held(true, false, false)); // still falling after release
        SkateState state = c.update(landing(SkateConstants.BAIL_IMPACT_SPEED_THRESHOLD + 1.0));
        assertEquals(SkateState.GROUNDED, state);
        assertTrue(c.takeBailedThisTick());
    }

    @Test
    void bailResetsSpeedAndChargeLikeAnyOtherExit() {
        SkateController c = new SkateController();
        c.update(held(true, false, true));
        c.update(held(true, true, true)); // CHARGING, so chargeTicks advances
        c.update(held(true, true, true));
        c.update(held(true, false, false)); // released into AIRBORNE (ollie)
        c.update(landing(SkateConstants.BAIL_IMPACT_SPEED_THRESHOLD));
        assertEquals(0, c.chargeTicks());
        assertEquals(0.0, c.speed(), 1e-9);
        assertNull(c.activeGrab());
    }
}
