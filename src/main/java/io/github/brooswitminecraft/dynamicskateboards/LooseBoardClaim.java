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
 *
 * <p>Review fix (pickup grace): {@link #tryClaim(int, int)} layers a ticks-since-spawn gate on
 * top of the same primitive &mdash; a claim attempt still inside the grace window is refused
 * WITHOUT closing the latch, so it is not "the first claimant", just an early one; the same latch
 * can still be claimed for real once the grace window has elapsed. This stays pure (no
 * entity/world types) so the gate is unit-testable exactly like the rest of this class.
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

    /**
     * Same contract as {@link #tryClaim()}, except every attempt while {@code ticksSinceSpawn <
     * graceTicks} is refused and leaves the latch untouched (not claimed, so a later attempt &mdash;
     * grace expired or not &mdash; can still win it).
     */
    public boolean tryClaim(int ticksSinceSpawn, int graceTicks) {
        if (ticksSinceSpawn < graceTicks) {
            return false;
        }
        return tryClaim();
    }

    public boolean isClaimed() {
        return claimed;
    }
}
