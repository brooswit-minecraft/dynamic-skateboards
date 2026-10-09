package io.github.brooswitminecraft.dynamicskateboards;

/**
 * Pure, Minecraft-free holder for "is a grind currently active" plus the reacquire cooldown: no
 * Minecraft entity/world types, so the review-requested fix &mdash; a stale grind must not
 * silently resume once the skater isn't actually riding at all &mdash; is unit-testable headlessly,
 * same as {@link GrindFollower}. {@link WorldGrindSeam} is the only caller; it owns one of these.
 *
 * <p>Riding GROUNDED (board unequipped, or logically off the board for any other reason) is the
 * caller's own signal, not something this class infers &mdash; {@link #onRidingStateChanged}
 * must be told every tick, since a controller's own transitions (including GROUNDED) are the only
 * reliable source of "not riding at all" short of a fresh acquisition.
 */
public final class GrindSession {
    private GrindFollower follower;
    private int cooldownTicksRemaining;

    public boolean isActive() {
        return follower != null;
    }

    public GrindFollower current() {
        return follower;
    }

    public boolean canAcquire() {
        return follower == null && cooldownTicksRemaining <= 0;
    }

    public void start(GrindFollower newFollower) {
        follower = newFollower;
    }

    /** Ends the grind (any reason) and starts the reacquire cooldown, as a normal release does. */
    public void release() {
        follower = null;
        cooldownTicksRemaining = SkateConstants.GRIND_REACQUIRE_COOLDOWN_TICKS;
    }

    public void tickCooldown() {
        if (cooldownTicksRemaining > 0) {
            cooldownTicksRemaining--;
        }
    }

    /**
     * Must be called once per tick with whatever the caller's own state machine currently says
     * about riding. {@code false} drops any active grind immediately (and starts the cooldown,
     * exactly like a normal release) &mdash; the fix for the reviewed bug: going GROUNDED
     * mid-grind (board unequipped, or any other reason riding ends outright) must never leave a
     * stale follower to be silently resumed the next time Shift is pressed airborne.
     */
    public void onRidingStateChanged(boolean currentlyRiding) {
        if (!currentlyRiding && follower != null) {
            release();
        }
    }
}
