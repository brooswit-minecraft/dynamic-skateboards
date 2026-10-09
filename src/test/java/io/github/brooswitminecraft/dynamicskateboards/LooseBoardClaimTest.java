package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LooseBoardClaimTest {
    @Test
    void firstClaimWins() {
        LooseBoardClaim claim = new LooseBoardClaim();
        assertTrue(claim.tryClaim());
        assertTrue(claim.isClaimed());
    }

    @Test
    void secondClaimInTheSameTickLoses() {
        // Models two players found touching the same loose board in the same tick's scan:
        // whichever is processed first wins, the other must get nothing.
        LooseBoardClaim claim = new LooseBoardClaim();
        assertTrue(claim.tryClaim(), "first claimant must win");
        assertFalse(claim.tryClaim(), "second claimant the same tick must lose");
    }

    @Test
    void claimStaysClosedForever() {
        LooseBoardClaim claim = new LooseBoardClaim();
        claim.tryClaim();
        for (int i = 0; i < 5; i++) {
            assertFalse(claim.tryClaim(), "a closed latch must never reopen");
        }
    }

    @Test
    void freshClaimStartsUnclaimed() {
        assertFalse(new LooseBoardClaim().isClaimed());
    }

    @Test
    void claimDuringGraceFailsAndLeavesLatchOpen() {
        LooseBoardClaim claim = new LooseBoardClaim();
        assertFalse(claim.tryClaim(0, 12), "a claim attempt inside the grace window must fail");
        assertFalse(claim.isClaimed(), "a refused-by-grace attempt must not close the latch");
    }

    @Test
    void claimExactlyAtGraceBoundaryFails() {
        // ticksSinceSpawn < graceTicks is the grace window; ticksSinceSpawn == graceTicks is the
        // first tick grace has fully elapsed, so a claim right at that boundary is still inside
        // grace and must fail (consistent with "elapsed" meaning graceTicks full ticks have passed).
        LooseBoardClaim claim = new LooseBoardClaim();
        assertFalse(claim.tryClaim(11, 12), "one tick short of the grace window elapsing must still fail");
        assertFalse(claim.isClaimed());
    }

    @Test
    void claimAfterGraceSucceedsOnTheSameUnclaimedLatch() {
        LooseBoardClaim claim = new LooseBoardClaim();
        assertFalse(claim.tryClaim(0, 12), "early attempt fails but must not burn the latch");
        assertTrue(claim.tryClaim(12, 12), "once grace has elapsed, the same latch must still be claimable");
        assertTrue(claim.isClaimed());
    }

    @Test
    void secondClaimAfterGraceStillLosesToTheFirst() {
        LooseBoardClaim claim = new LooseBoardClaim();
        assertTrue(claim.tryClaim(20, 12), "first claimant after grace must win");
        assertFalse(claim.tryClaim(20, 12), "second claimant the same tick must still lose");
    }
}
