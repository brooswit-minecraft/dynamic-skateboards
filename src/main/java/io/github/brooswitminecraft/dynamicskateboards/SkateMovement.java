package io.github.brooswitminecraft.dynamicskateboards;

/**
 * Pure-math arcade movement model: no Minecraft types, so the speed-retention/kick/brake/
 * friction and steering curves are unit-testable without a running game. {@link SkateController}
 * calls these each tick; {@code DynamicSkateboardsMod} is the only place that turns the resulting
 * scalars into an actual {@code Vec3} velocity.
 */
public final class SkateMovement {
    private SkateMovement() {}

    /**
     * Next horizontal ground speed. A forward-foot kick adds a defined amount; holding the back
     * foot brakes to a stop and never reverses; with neither input the board coasts under its
     * tuned friction. Observed vanilla movement is deliberately not an acceleration source.
     */
    public static double nextGroundSpeed(double currentSpeed, boolean forwardKick, boolean braking) {
        double next;
        if (braking) {
            next = currentSpeed - SkateConstants.BRAKE_DECELERATION;
        } else if (forwardKick) {
            next = currentSpeed + SkateConstants.KICK_SPEED_GAIN;
        } else {
            next = currentSpeed * SkateConstants.FRICTION;
        }
        return clampSpeed(next);
    }

    /**
     * Next horizontal air speed. No passive friction while airborne (there is nothing to coast
     * against); air control instead moves speed toward {@code observedSpeed} at
     * {@link SkateConstants#AIR_CONTROL_ACCEL} per tick in either direction, generously biased by
     * being a useful rate rather than a token one.
     */
    public static double nextAirSpeed(double currentSpeed, double observedSpeed) {
        double target = Math.min(observedSpeed, SkateConstants.TOP_SPEED);
        double next = target > currentSpeed
                ? Math.min(target, currentSpeed + SkateConstants.AIR_CONTROL_ACCEL)
                : Math.max(target, currentSpeed - SkateConstants.AIR_CONTROL_ACCEL);
        return clampSpeed(next);
    }

    /** Turns a heading directly from raw left/right steering input; opposing inputs cancel. */
    public static double turnFromStrafe(double currentHeadingDegrees, boolean leftHeld, boolean rightHeld, double maxDeltaDegrees) {
        if (leftHeld == rightHeld) {
            return currentHeadingDegrees;
        }
        return currentHeadingDegrees + (rightHeld ? maxDeltaDegrees : -maxDeltaDegrees);
    }

    private static double clampSpeed(double speed) {
        return Math.min(SkateConstants.TOP_SPEED, Math.max(0.0, speed));
    }

    /**
     * Turns {@code currentHeadingDegrees} toward {@code targetHeadingDegrees} by at most
     * {@code maxDeltaDegrees}, taking the shorter way around the compass.
     */
    public static double turnTowardDegrees(double currentHeadingDegrees, double targetHeadingDegrees, double maxDeltaDegrees) {
        double delta = wrapDegrees(targetHeadingDegrees - currentHeadingDegrees);
        double clamped = Math.max(-maxDeltaDegrees, Math.min(maxDeltaDegrees, delta));
        return currentHeadingDegrees + clamped;
    }

    private static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    /**
     * Ollie vertical impulse, in blocks/tick, as a function of how many ticks Jump was held
     * before release. Monotonic non-decreasing in {@code chargeTicksAtRelease}, and never zero
     * (a zero-length tap still returns {@link SkateConstants#OLLIE_MIN_IMPULSE}) &mdash; a tap is
     * a defined, non-punishing small pop rather than nothing.
     */
    public static double ollieImpulse(int chargeTicksAtRelease) {
        int ticks = Math.max(0, Math.min(chargeTicksAtRelease, SkateConstants.OLLIE_MAX_CHARGE_TICKS));
        double t = ticks / (double) SkateConstants.OLLIE_MAX_CHARGE_TICKS;
        return SkateConstants.OLLIE_MIN_IMPULSE + t * (SkateConstants.OLLIE_MAX_IMPULSE - SkateConstants.OLLIE_MIN_IMPULSE);
    }
}
