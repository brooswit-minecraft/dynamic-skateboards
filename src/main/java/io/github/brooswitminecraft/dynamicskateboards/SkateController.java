package io.github.brooswitminecraft.dynamicskateboards;

/**
 * Pure hold-to-skate state machine: no Minecraft types, so it is unit-tested without a running
 * game. Driven ONLY by whether the skateboard is currently in the main hand; everything else
 * (charge/airborne/landing) is for story (b) to add transitions into/out of, not this one.
 */
public final class SkateController {
    private SkateState state = SkateState.GROUNDED;

    /** Recomputes the state from this tick's main-hand contents and returns the new state. */
    public SkateState update(boolean mainHandIsSkateboard) {
        if (!mainHandIsSkateboard) {
            state = SkateState.GROUNDED;
        } else if (state == SkateState.GROUNDED) {
            state = SkateState.SKATING;
        }
        return state;
    }

    public SkateState state() {
        return state;
    }

    public boolean isSkating() {
        return state != SkateState.GROUNDED;
    }
}
