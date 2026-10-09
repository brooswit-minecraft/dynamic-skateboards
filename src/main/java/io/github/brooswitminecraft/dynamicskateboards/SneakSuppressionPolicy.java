package io.github.brooswitminecraft.dynamicskateboards;

/**
 * Pure decision of whether vanilla sneak must be suppressed this tick. Reading Shift's raw
 * keybinding state (see {@link SkateTrickInputPayload}) is safe on its own, but the SAME physical
 * key also drives vanilla's own sneak input path independently of anything this mod reads it for
 * &mdash; so avoiding Minecraft's sneak-state SETTER is not sufficient by itself: the vanilla INPUT
 * path still sneaks unless suppressed. Kept separate from the Minecraft glue that acts on it
 * ({@code DynamicSkateboardsMod}'s {@code MovementInputUpdateEvent} handler) so the decision itself
 * &mdash; suppressed while skating, restored the instant skating ends &mdash; is deterministically
 * unit-tested without a running game. Mirrors {@link ClickSuppressionPolicy}'s shape exactly; kept
 * as its own class because the two suppress different vanilla input paths (click vs. sneak).
 */
public final class SneakSuppressionPolicy {
    private SneakSuppressionPolicy() {}

    /** Vanilla sneak must not engage from the Shift key in any non-GROUNDED skate state. */
    public static boolean suppressVanillaSneak(SkateState state) {
        return state != SkateState.GROUNDED;
    }
}
