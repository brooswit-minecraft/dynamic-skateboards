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

    // --- Grinding (story e): detector, acquisition, following, release ---

    /**
     * Usable-length threshold for a candidate grind edge, in 1/16ths of a block. The spec's own
     * initial guideline ("approximately 4/16 of a block"); testing here found no reason to move
     * off it &mdash; see docs/grindable-edges.md, which every (d)-authored edge clears
     * comfortably at this value.
     */
    public static final double GRIND_MIN_EDGE_LENGTH_VOXELS = 4.0;

    /**
     * How far a single sample-to-sample height step (within one block's own box decomposition)
     * is still read as "the same smooth ramp," in 1/16ths of a block. Below this, consecutive
     * samples merge into one diagonal chord (a slope's stepped approximation); at or above it,
     * the profile splits into separate chords instead of faking a smooth line through a real
     * riser (a stair's jump from tread to tread is 8 voxels &mdash; nowhere close). Every
     * geometry this story ships (slopes: at most 1 voxel per column) stays well under this.
     */
    public static final double GRIND_PROFILE_MAX_SMOOTH_STEP_VOXELS = 2.0;

    /** How many blocks out from the player {@link GrindEdgeDetector} scans, each axis. Bounded and local by design: see that class's javadoc. */
    public static final int GRIND_SCAN_RADIUS_BLOCKS = 2;

    /**
     * Acquisition snap radius, in blocks: how far a candidate edge's nearest point may be from
     * the player for Shift to acquire it. Generous per the spec's "bias toward generous grind
     * snapping" &mdash; a player who aimed at a rail should get it.
     */
    public static final double GRIND_SNAP_RADIUS_BLOCKS = 2.5;

    /**
     * Approach tolerance, in degrees: how far the player's current horizontal heading may differ
     * from a candidate edge's own direction (or its reverse) for Shift to acquire it while
     * airborne/approaching. Generous, not strict alignment &mdash; the player is approaching, not
     * already riding the rail.
     */
    public static final double GRIND_APPROACH_MAX_ANGLE_DEGREES = 70.0;

    /** Ticks after release before the same edge can be re-acquired, avoiding an instant re-snap. */
    public static final int GRIND_REACQUIRE_COOLDOWN_TICKS = 10;

    // --- Bail (story f): rare, config-driven crash into a loose physics board ---

    /**
     * Impact speed threshold, in blocks/tick (the magnitude of the player's own downward vertical
     * speed the tick before touchdown resolves it), above which a landing counts as "badly
     * missed" and forces a bail instead of resuming SKATING. Comfortably above anything a normal
     * ollie/ramp landing produces &mdash; {@link #OLLIE_MAX_IMPULSE} is only 0.9 blocks/tick
     * upward, and a symmetric landing from max ollie height returns well under this value &mdash;
     * so this stays rare by construction: a real miss, a long fall, or a grind run off the end of
     * a rail with nothing underneath to land on.
     */
    public static final double BAIL_IMPACT_SPEED_THRESHOLD = 1.3;

    /** Mass of the loose physics board spawned on a bail, in kg, handed to Sable's rigid body. */
    public static final double BAIL_BOARD_MASS_KG = 2.5;

    /** Half-length of the loose board's physics box along the deck, in blocks. */
    public static final double BAIL_BOARD_HALF_LENGTH = 0.4;

    /** Half-width of the loose board's physics box across the deck, in blocks. */
    public static final double BAIL_BOARD_HALF_WIDTH = 0.1;

    /** Half-height of the loose board's physics box, in blocks. */
    public static final double BAIL_BOARD_HALF_HEIGHT = 0.05;

    /** Rest length of each of the loose board's four wheel-contact suspension rays, in blocks. */
    public static final double BAIL_WHEEL_REST_LENGTH = 0.12;

    /** Spring rate of each wheel-contact suspension ray, N/m. Passive only &mdash; no propulsion term exists. */
    public static final double BAIL_WHEEL_SPRING_RATE = 900.0;

    /** Damping rate of each wheel-contact suspension ray, N per (m/s). */
    public static final double BAIL_WHEEL_DAMPING_RATE = 60.0;

    /** Clamp on a single wheel-contact's per-tick suspension force, N, so a degenerate compression can't launch the board. */
    public static final double BAIL_WHEEL_MAX_SPRING_FORCE = 400.0;

    /**
     * Fraction of the crash's horizontal speed converted into tumbling angular velocity on bail,
     * in radians/second per block/tick of horizontal speed &mdash; "tumbles plausibly, not
     * static": a bail with real speed behind it must visibly tumble, not just slide as a flat box.
     */
    public static final double BAIL_TUMBLE_SPIN_FACTOR = 6.0;

    /** How far beyond the loose board's own box the walk-over pickup touch check reaches, in blocks. */
    public static final double BAIL_PICKUP_REACH_BLOCKS = 0.25;

    /**
     * Ticks after spawn a loose board refuses every pickup claim, win or lose &mdash; the bailing
     * player is standing on top of the spot it spawns at, so without this it would return to their
     * inventory within the same tick and they'd never see it come loose. 12 ticks = 0.6s: long
     * enough to read as "the board fell off and is lying there" even though the player hasn't
     * moved away yet, short enough that a player who immediately wants it back isn't kept waiting.
     */
    public static final int BAIL_PICKUP_GRACE_TICKS = 12;

    /** The loose board entity's registered bounding-box width (x/z), in blocks: generous enough to cover any tumble orientation. */
    public static final float BAIL_BOARD_ENTITY_WIDTH = 0.9f;

    /** The loose board entity's registered bounding-box height, in blocks. */
    public static final float BAIL_BOARD_ENTITY_HEIGHT = 0.3f;

    private SkateConstants() {}
}
