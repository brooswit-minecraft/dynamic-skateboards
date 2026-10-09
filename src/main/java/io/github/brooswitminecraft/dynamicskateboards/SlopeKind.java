package io.github.brooswitminecraft.dynamicskateboards;

/**
 * The 10 named pieces of the vertical transition family: 5 angles (shallow, raised shallow,
 * regular, steep, raised steep) each with a normal and an inverted (ceiling-hugging) variant. See
 * {@link SlopeShapes} for what {@code riseVoxels}/{@code verticalShift}/{@code inverted} mean
 * geometrically, and docs/grindable-edges.md for the authored angles and the raised/non-raised
 * pairing invariant this enum encodes:
 * {@code SHALLOW.exitHeight() == RAISED_SHALLOW.entryHeight()} and
 * {@code STEEP.exitHeight() == RAISED_STEEP.entryHeight()}.
 */
public enum SlopeKind {
    SHALLOW_SLOPE("shallow_slope", 4, 0, false),
    RAISED_SHALLOW_SLOPE("raised_shallow_slope", 4, 4, false),
    REGULAR_SLOPE("regular_slope", 6, 0, false),
    STEEP_SLOPE("steep_slope", 8, 0, false),
    RAISED_STEEP_SLOPE("raised_steep_slope", 8, 8, false),
    SHALLOW_SLOPE_INVERTED("shallow_slope_inverted", 4, 0, true),
    RAISED_SHALLOW_SLOPE_INVERTED("raised_shallow_slope_inverted", 4, 4, true),
    REGULAR_SLOPE_INVERTED("regular_slope_inverted", 6, 0, true),
    STEEP_SLOPE_INVERTED("steep_slope_inverted", 8, 0, true),
    RAISED_STEEP_SLOPE_INVERTED("raised_steep_slope_inverted", 8, 8, true);

    private final String blockName;
    private final int riseVoxels;
    private final int verticalShift;
    private final boolean inverted;

    SlopeKind(String blockName, int riseVoxels, int verticalShift, boolean inverted) {
        this.blockName = blockName;
        this.riseVoxels = riseVoxels;
        this.verticalShift = verticalShift;
        this.inverted = inverted;
    }

    public String blockName() {
        return blockName;
    }

    public int riseVoxels() {
        return riseVoxels;
    }

    public int verticalShift() {
        return verticalShift;
    }

    public boolean inverted() {
        return inverted;
    }

    /** The ramp's entry (low) height in the block's own 0..16 local frame, before inversion. */
    public int entryHeight() {
        return verticalShift;
    }

    /** The ramp's exit (high) height in the block's own 0..16 local frame, before inversion. */
    public int exitHeight() {
        return verticalShift + riseVoxels;
    }

    /** Angle of the ramp from horizontal, in degrees: atan(riseVoxels / 16). */
    public double angleDegrees() {
        return Math.toDegrees(Math.atan(riseVoxels / 16.0));
    }
}
