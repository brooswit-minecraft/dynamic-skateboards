package io.github.brooswitminecraft.dynamicskateboards;

/**
 * One tick's worth of input to {@link SkateController}. Pure Java, no Minecraft types: the caller
 * (server-side, once per tick) is responsible for deriving every field from the authoritative
 * {@code ServerPlayer} state.
 *
 * @param mainHandIsSkateboard whether the skateboard is currently the main-hand item.
 * @param jumpHeld whether the player's Jump key is currently held, per the server's own
 *     authoritative mirror of it (see {@code SkateJumpInputPayload}) &mdash; never trust a
 *     client's claimed action, only its continuously-synced held-key state.
 * @param onGround whether the player is currently on the ground.
 * @param observedHorizontalSpeed the player's actual horizontal speed this tick, in blocks/tick,
 *     before this controller's own retention/acceleration model is applied &mdash; the signal used
 *     to tell whether the player is actively pushing for more speed.
 * @param facingHeadingDegrees the direction the player is currently facing/steering toward, in
 *     degrees (same convention as {@code Entity#getYRot()}).
 * @param shiftHeld whether the player's Shift key is currently held, per the server's own
 *     continuously-synced mirror of it (same pattern as {@code jumpHeld}) &mdash; never Minecraft's
 *     sneak state, which this mod never sets while skating.
 * @param attackJustPressed whether left click (attack) was newly pressed this tick &mdash; an edge,
 *     not a held level, since a flip is one discrete selection per press.
 * @param useHeld whether right click (use) is currently held &mdash; a grab is sustained while held
 *     and ends on release, so this is a level, not an edge.
 * @param direction the W/A/S/D direction this tick resolves to (see {@link TrickDirection}),
 *     selecting which flip/grab the table maps left/right click to.
 * @param verticalVelocity the player's own vertical speed, in blocks/tick, from the tick
 *     immediately BEFORE this one &mdash; i.e. the fall speed the moment before touchdown
 *     resolves it, not this tick's already-landed value (which Minecraft's own collision
 *     resolution has typically already zeroed out by the time the caller can read it). Used only
 *     to detect a badly missed landing (see {@link SkateConstants#BAIL_IMPACT_SPEED_THRESHOLD});
 *     the convenience constructor below defaults it to 0.0 for every caller that doesn't care.
 */
public record SkateInput(
        boolean mainHandIsSkateboard,
        boolean jumpHeld,
        boolean onGround,
        double observedHorizontalSpeed,
        double facingHeadingDegrees,
        boolean shiftHeld,
        boolean attackJustPressed,
        boolean useHeld,
        TrickDirection direction,
        double verticalVelocity) {

    /** Convenience for callers that only care about riding (no trick/manual input this tick). */
    public SkateInput(
            boolean mainHandIsSkateboard,
            boolean jumpHeld,
            boolean onGround,
            double observedHorizontalSpeed,
            double facingHeadingDegrees) {
        this(mainHandIsSkateboard, jumpHeld, onGround, observedHorizontalSpeed, facingHeadingDegrees,
                false, false, false, TrickDirection.NEUTRAL, 0.0);
    }
}
