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
 * </ul>
 */
public final class SkateController {
    private SkateState state = SkateState.GROUNDED;
    private int chargeTicks;
    private int landingTicks;
    private double speed;
    private double headingDegrees;
    private Double pendingOllieImpulse;

    private final InputBuffer<Boolean> earlyJumpBuffer = new InputBuffer<>(SkateConstants.INPUT_BUFFER_WINDOW_TICKS);

    /** Recomputes state, speed and heading from this tick's input and returns the new state. */
    public SkateState update(SkateInput input) {
        pendingOllieImpulse = null;
        earlyJumpBuffer.tick();

        if (!input.mainHandIsSkateboard()) {
            state = SkateState.GROUNDED;
            chargeTicks = 0;
            landingTicks = 0;
            speed = 0.0;
            headingDegrees = input.facingHeadingDegrees();
            return state;
        }

        switch (state) {
            case GROUNDED -> state = SkateState.SKATING;
            case SKATING -> {
                if (input.onGround() && input.jumpHeld()) {
                    state = SkateState.CHARGING;
                    chargeTicks = 0;
                } else if (!input.onGround()) {
                    state = SkateState.AIRBORNE;
                }
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
                if (input.onGround()) {
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
}
