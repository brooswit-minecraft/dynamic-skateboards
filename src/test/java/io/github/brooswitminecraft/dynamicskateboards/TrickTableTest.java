package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class TrickTableTest {
    @Test
    void everyDirectionHasAFlip() {
        for (TrickDirection direction : TrickDirection.values()) {
            assertNotNull(TrickTable.flipFor(direction), direction + " must have a flip entry");
        }
    }

    @Test
    void everyDirectionHasAGrab() {
        for (TrickDirection direction : TrickDirection.values()) {
            assertNotNull(TrickTable.grabFor(direction), direction + " must have a grab entry");
        }
    }

    @Test
    void shippedTable() {
        assertEquals(FlipTrick.KICKFLIP, TrickTable.flipFor(TrickDirection.NEUTRAL));
        assertEquals(FlipTrick.POP_SHUVIT, TrickTable.flipFor(TrickDirection.FORWARD));
        assertEquals(FlipTrick.FAKIE_FLIP, TrickTable.flipFor(TrickDirection.BACK));
        assertEquals(FlipTrick.HEELFLIP, TrickTable.flipFor(TrickDirection.LEFT));
        assertEquals(FlipTrick.VARIAL_KICKFLIP, TrickTable.flipFor(TrickDirection.RIGHT));

        assertEquals(GrabTrick.INDY, TrickTable.grabFor(TrickDirection.NEUTRAL));
        assertEquals(GrabTrick.NOSE, TrickTable.grabFor(TrickDirection.FORWARD));
        assertEquals(GrabTrick.TAIL, TrickTable.grabFor(TrickDirection.BACK));
        assertEquals(GrabTrick.MUTE, TrickTable.grabFor(TrickDirection.LEFT));
        assertEquals(GrabTrick.CRAIL, TrickTable.grabFor(TrickDirection.RIGHT));
    }
}
