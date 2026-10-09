package io.github.brooswitminecraft.dynamicskateboards;

/**
 * The directional component of a flip/grab input: W/A/S/D only, no new keybinds, collapsed to one
 * of five values. Pure Java so {@link TrickTable} lookups and {@link SkateController} dispatch are
 * unit-tested without a running game.
 */
public enum TrickDirection {
    NEUTRAL,
    FORWARD,
    BACK,
    LEFT,
    RIGHT;

    /**
     * Derives a single direction from the four WASD held-key booleans. Forward/back take priority
     * over left/right so a diagonal hold still resolves to one readable direction rather than
     * requiring an 8-way table; opposite keys held together (e.g. both forward and back) cancel to
     * {@link #NEUTRAL}, same as no keys held.
     */
    public static TrickDirection fromKeys(boolean forward, boolean back, boolean left, boolean right) {
        if (forward && !back) {
            return FORWARD;
        }
        if (back && !forward) {
            return BACK;
        }
        if (left && !right) {
            return LEFT;
        }
        if (right && !left) {
            return RIGHT;
        }
        return NEUTRAL;
    }
}
