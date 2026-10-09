package io.github.brooswitminecraft.dynamicskateboards;

/**
 * Pure decision of whether vanilla left/right click behaviour (attack, use/place) should be
 * suppressed this tick. Kept separate from the Minecraft event handlers that act on it
 * ({@code DynamicSkateboardsMod}) so the decision itself &mdash; suppressed while skating, restored
 * the instant skating ends &mdash; is deterministically unit-tested without a running game.
 */
public final class ClickSuppressionPolicy {
    private ClickSuppressionPolicy() {}

    /** Vanilla left/right click must not fire in any non-GROUNDED skate state. */
    public static boolean suppressVanillaClicks(SkateState state) {
        return state != SkateState.GROUNDED;
    }
}
