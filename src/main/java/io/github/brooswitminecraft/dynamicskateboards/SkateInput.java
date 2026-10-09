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
 */
public record SkateInput(
        boolean mainHandIsSkateboard,
        boolean jumpHeld,
        boolean onGround,
        double observedHorizontalSpeed,
        double facingHeadingDegrees) {
}
