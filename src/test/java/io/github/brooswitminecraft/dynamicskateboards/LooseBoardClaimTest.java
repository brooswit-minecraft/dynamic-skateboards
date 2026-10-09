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
}
