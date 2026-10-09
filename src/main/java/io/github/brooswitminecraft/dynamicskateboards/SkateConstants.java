package io.github.brooswitminecraft.dynamicskateboards;

/**
 * Every tuning number the spec leaves open, in one place, so later stories' playtest passes can
 * turn knobs without hunting across the codebase. No inline magic numbers belong in
 * {@link SkateController} or {@link SkateMovement} &mdash; add a constant here instead.
 */
public final class SkateConstants {
    /** How far the player's render pose is lowered while SKATING, in blocks. Roughly sneak-like. */
    public static final float SKATE_STANCE_HEIGHT_OFFSET = 0.2f;

    /** Extra lowering (on top of {@link #SKATE_STANCE_HEIGHT_OFFSET}) while CHARGING an ollie. */
    public static final float CHARGE_STANCE_EXTRA_HEIGHT_OFFSET = 0.12f;

    /** Pose extension (raised back toward standing) while AIRBORNE, in blocks. */
    public static final float AIRBORNE_HEIGHT_OFFSET = 0.0f;

    /** Pose offset while LANDING, blended back toward {@link #SKATE_STANCE_HEIGHT_OFFSET}. */
    public static final float LANDING_HEIGHT_OFFSET = 0.1f;

    // --- Ground movement: speed retention / acceleration / friction ---

    /** Maximum horizontal ground speed, in blocks/tick. */
    public static final double TOP_SPEED = 0.5;

    /** How much horizontal speed can increase per tick while actively pushed toward a higher speed. */
    public static final double ACCELERATION = 0.035;

    /**
     * Fraction of horizontal ground speed retained per tick once nothing is pushing it higher
     * (deliberately gentler than vanilla's own ground friction so the skateboard "coasts").
     */
    public static final double FRICTION = 0.985;

    /** Maximum heading change toward the player's facing direction per tick while grounded, in degrees. */
    public static final double STEER_TURN_RATE_DEGREES = 10.0;

    // --- Air control ---

    /** How much horizontal air speed can change per tick toward the steered direction. */
    public static final double AIR_CONTROL_ACCEL = 0.03;

    /**
     * Maximum heading change toward the player's facing direction per tick while airborne, in
     * degrees. Generously biased above {@link #STEER_TURN_RATE_DEGREES} per the "useful,
     * generously biased" air control requirement.
     */
    public static final double AIR_STEER_TURN_RATE_DEGREES = 16.0;

    // --- Ollie charge / release ---

    /** Vertical pop applied on a zero-length tap release, in blocks/tick. Never punishing. */
    public static final double OLLIE_MIN_IMPULSE = 0.42;

    /** Vertical pop applied on a fully-charged release, in blocks/tick. */
    public static final double OLLIE_MAX_IMPULSE = 0.9;

    /** Ticks of holding Jump to reach full ollie charge. */
    public static final int OLLIE_MAX_CHARGE_TICKS = 20;

    // --- Landing ---

    /**
     * Ticks spent in LANDING before returning to SKATING &mdash; the landing tolerance: forgiving
     * on purpose, so an imperfect landing never interrupts the ride.
     */
    public static final int LANDING_TOLERANCE_TICKS = 6;

    // --- Input buffer ---

    /**
     * Default recognition window, in ticks, for buffered inputs (e.g. an ollie charge started by
     * pressing Jump slightly before touching down). Shared by any {@link InputBuffer} consumer.
     */
    public static final int INPUT_BUFFER_WINDOW_TICKS = 6;

    private SkateConstants() {}
}
