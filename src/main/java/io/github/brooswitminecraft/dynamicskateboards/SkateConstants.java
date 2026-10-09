package io.github.brooswitminecraft.dynamicskateboards;

/**
 * Every tuning number the spec leaves open, in one place, so later stories' playtest passes can
 * turn knobs without hunting across the codebase.
 */
public final class SkateConstants {
    /** How far the player's render pose is lowered while SKATING, in blocks. Roughly sneak-like. */
    public static final float SKATE_STANCE_HEIGHT_OFFSET = 0.2f;

    private SkateConstants() {}
}
