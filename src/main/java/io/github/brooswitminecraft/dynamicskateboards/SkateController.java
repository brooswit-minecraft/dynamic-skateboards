package io.github.brooswitminecraft.dynamicskateboards;

/**
 * Pure arcade riding state machine: no Minecraft types, so it is unit-tested without a running
 * game. Driven once per tick by {@link SkateInput}; the caller (server-side only &mdash; the
 * client mirrors the result via {@code SkatingStatePayload} and never recomputes it) is
 * responsible for turning this controller's scalar outputs (state, speed, heading, ollie
 * impulse) into actual entity velocity.
 *
 * <p>Transitions:
 * <ul>
 *   <li>Off the board (main hand isn't the skateboard) &rarr; GROUNDED from any state.
 *   <li>GROUNDED &rarr; SKATING as soon as the board is the main-hand item.
 *   <li>SKATING &rarr; CHARGING when Jump is pressed while on the ground.
 *   <li>SKATING &rarr; AIRBORNE when the ground disappears (ramp/ledge) &mdash; no ollie impulse;
 *       existing horizontal speed simply carries into the air.
 *   <li>CHARGING &rarr; AIRBORNE on Jump release (the ollie pop, scaled by charge duration) or on
 *       leaving the ground while still holding Jump (charge is cancelled, no impulse).
 *   <li>AIRBORNE &rarr; LANDING on touching down; LANDING &rarr; SKATING after the landing
 *       tolerance, completing the stance's animation cycle, or back to AIRBORNE if the ground
 *       drops away again before that.
 *   <li>SKATING &rarr; MANUAL on Shift while on the ground; MANUAL &rarr; SKATING on Shift release,
 *       or &rarr; AIRBORNE if the ground disappears &mdash; the ride-side half of the contextual
 *       Shift dispatch (story (c)).
 *   <li>AIRBORNE + Shift routes to {@link GrindSeam#tryGrind} every tick instead &mdash; the
 *       airborne/approaching half of that same dispatch, never MANUAL.
 *   <li>AIRBORNE: left click selects a {@link FlipTrick} from {@link TrickTable} (buffered via
 *       {@code flipPressBuffer} so a press slightly before takeoff still counts), landing completes
 *       it ({@link #takeCompletedFlip()}); right click held selects/sustains a {@link GrabTrick},
 *       cleared on release or on leaving AIRBORNE.
 * </ul>
 */
public final class SkateController {
    private SkateState state = SkateState.GROUNDED;
    private int chargeTicks;
    private int landingTicks;
    private double speed;
    private double headingDegrees;
    private Double pendingOllieImpulse;
    private FlipTrick activeFlip;
    private FlipTrick completedFlip;
    private GrabTrick activeGrab;

    private final InputBuffer<Boolean> earlyJumpBuffer = new InputBuffer<>(SkateConstants.INPUT_BUFFER_WINDOW_TICKS);
    private final InputBuffer<TrickDirection> flipPressBuffer = new InputBuffer<>(SkateConstants.INPUT_BUFFER_WINDOW_TICKS);
    private final GrindSeam grindSeam;

    public SkateController() {
        this(GrindSeam.NONE);
    }

    /** @param grindSeam story (e)'s grind hook; see {@link GrindSeam} for the contract. */
    public SkateController(GrindSeam grindSeam) {
        this.grindSeam = grindSeam;
    }

    /** Recomputes state, speed and heading from this tick's input and returns the new state. */
    public SkateState update(SkateInput input) {
        pendingOllieImpulse = null;
        completedFlip = null;
        earlyJumpBuffer.tick();
        flipPressBuffer.tick();

        if (!input.mainHandIsSkateboard()) {
            state = SkateState.GROUNDED;
            chargeTicks = 0;
            landingTicks = 0;
            speed = 0.0;
            headingDegrees = input.facingHeadingDegrees();
            activeFlip = null;
            activeGrab = null;
            return state;
        }

        // Buffered regardless of current state so a press slightly before takeoff (still SKATING)
        // is still recognized once AIRBORNE begins, within the window - the same generosity
        // earlyJumpBuffer gives the ollie.
        if (input.attackJustPressed()) {
            flipPressBuffer.buffer(input.direction());
        }

        switch (state) {
            case GROUNDED -> state = SkateState.SKATING;
            case SKATING -> {
                if (input.onGround() && input.shiftHeld()) {
                    state = SkateState.MANUAL;
                } else if (input.onGround() && input.jumpHeld()) {
                    state = SkateState.CHARGING;
                    chargeTicks = 0;
                } else if (!input.onGround()) {
                    state = SkateState.AIRBORNE;
                }
            }
            case MANUAL -> {
                if (!input.onGround()) {
                    state = SkateState.AIRBORNE;
                } else if (!input.shiftHeld()) {
                    state = SkateState.SKATING;
                }
                // else: stays MANUAL, generous/no balance-failure - keeps rolling like SKATING.
            }
            case CHARGING -> {
                if (!input.jumpHeld()) {
                    releaseOllie();
                } else if (!input.onGround()) {
                    // Rolled off the ground mid-charge: cancel the charge, no impulse.
                    state = SkateState.AIRBORNE;
                    chargeTicks = 0;
                } else {
                    chargeTicks = Math.min(chargeTicks + 1, SkateConstants.OLLIE_MAX_CHARGE_TICKS);
                }
            }
            case AIRBORNE -> {
                if (input.jumpHeld()) {
                    earlyJumpBuffer.buffer(Boolean.TRUE);
                }
                if (input.shiftHeld()) {
                    // The seam: airborne/approaching Shift routes to story (e)'s grind path, never
                    // to MANUAL. GrindSeam.NONE declines every time; the route itself is what this
                    // story ships and asserts.
                    grindSeam.tryGrind(input);
                }
                if (activeFlip == null) {
                    TrickDirection pendingFlip = flipPressBuffer.consume();
                    if (pendingFlip != null) {
                        activeFlip = TrickTable.flipFor(pendingFlip);
                    }
                }
                if (input.useHeld()) {
                    if (activeGrab == null) {
                        activeGrab = TrickTable.grabFor(input.direction());
                    }
                } else {
                    activeGrab = null;
                }
                if (input.onGround()) {
                    activeGrab = null;
                    state = SkateState.LANDING;
                    landingTicks = 0;
                }
            }
            case LANDING -> {
                if (input.jumpHeld()) {
                    earlyJumpBuffer.buffer(Boolean.TRUE);
                }
                if (!input.onGround()) {
                    state = SkateState.AIRBORNE;
                } else {
                    landingTicks++;
                    if (landingTicks >= SkateConstants.LANDING_TOLERANCE_TICKS) {
                        state = SkateState.SKATING;
                        if (activeFlip != null) {
                            completedFlip = activeFlip;
                            activeFlip = null;
                        }
                        if (earlyJumpBuffer.consume() != null) {
                            state = SkateState.CHARGING;
                            chargeTicks = 0;
                        }
                    }
                }
            }
        }

        updateMotion(input);
        return state;
    }

    private void releaseOllie() {
        state = SkateState.AIRBORNE;
        pendingOllieImpulse = SkateMovement.ollieImpulse(chargeTicks);
        chargeTicks = 0;
    }

    private void updateMotion(SkateInput input) {
        boolean grounded = input.onGround() && state != SkateState.AIRBORNE;
        double maxTurn = grounded ? SkateConstants.STEER_TURN_RATE_DEGREES : SkateConstants.AIR_STEER_TURN_RATE_DEGREES;
        headingDegrees = SkateMovement.turnTowardDegrees(headingDegrees, input.facingHeadingDegrees(), maxTurn);
        speed = grounded
                ? SkateMovement.nextGroundSpeed(speed, input.observedHorizontalSpeed())
                : SkateMovement.nextAirSpeed(speed, input.observedHorizontalSpeed());
    }

    public SkateState state() {
        return state;
    }

    public boolean isSkating() {
        return state != SkateState.GROUNDED;
    }

    public int chargeTicks() {
        return chargeTicks;
    }

    public double speed() {
        return speed;
    }

    public double headingDegrees() {
        return headingDegrees;
    }

    /** Returns and clears this tick's ollie impulse (blocks/tick) if one was just triggered. */
    public Double takePendingOllieImpulse() {
        Double value = pendingOllieImpulse;
        pendingOllieImpulse = null;
        return value;
    }

    /** The flip currently in progress (selected, not yet landed), or {@code null} if none. */
    public FlipTrick activeFlip() {
        return activeFlip;
    }

    /**
     * Returns and clears the flip that just landed this tick (completed its {@code LANDING}
     * tolerance), or {@code null} if none landed this tick. Minimal feedback only: there is no
     * "failed" case here, since this story implements no bail mechanic (story (f)'s concern) -
     * every flip that is still active when LANDING completes counts as landed.
     */
    public FlipTrick takeCompletedFlip() {
        FlipTrick value = completedFlip;
        completedFlip = null;
        return value;
    }

    /** The grab sustained while right click is held, or {@code null} if not currently held. */
    public GrabTrick activeGrab() {
        return activeGrab;
    }
}
