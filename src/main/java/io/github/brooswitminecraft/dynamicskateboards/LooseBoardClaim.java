package io.github.brooswitminecraft.dynamicskateboards;

/**
 * Pure, Minecraft-free "first touch wins" latch: no entity/world types, so the race-safety rule
 * for two players reaching the same loose board in the same tick is unit-testable headlessly.
 *
 * <p>{@link LooseSkateboardEntity} owns exactly one of these per spawned loose board and calls
 * {@link #tryClaim()} once for every player found touching it on a given tick, in iteration
 * order. The server ticks entities single-threaded, so within one {@code tick()} call this is
 * already atomic; the latch exists so that guarantee is a property of the data (idempotent after
 * the first winner), not an assumption about call order the caller has to get right. Only the
 * tick that wins gives the item back and discards the entity &mdash; every other claimant that
 * tick (or any tick after) sees a closed latch and does nothing.
 */
public final class LooseBoardClaim {
    private boolean claimed;

    /** Returns {@code true} exactly once, for whichever caller claims first; {@code false} forever after. */
    public boolean tryClaim() {
        if (claimed) {
            return false;
        }
        claimed = true;
        return true;
    }

    public boolean isClaimed() {
        return claimed;
    }
}
