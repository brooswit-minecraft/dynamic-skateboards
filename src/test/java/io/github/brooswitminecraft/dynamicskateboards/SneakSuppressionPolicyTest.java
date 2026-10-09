package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SneakSuppressionPolicyTest {
    @Test
    void groundedIsNotSuppressed() {
        assertFalse(SneakSuppressionPolicy.suppressVanillaSneak(SkateState.GROUNDED));
    }

    @Test
    void everyNonGroundedSkateStateIsSuppressed() {
        for (SkateState state : SkateState.values()) {
            if (state == SkateState.GROUNDED) {
                continue;
            }
            assertTrue(SneakSuppressionPolicy.suppressVanillaSneak(state), state + " must suppress vanilla sneak");
        }
    }

    @Test
    void restoredAfterSkatingEnds() {
        SkateController c = new SkateController();
        c.update(new SkateInput(true, false, true, 0.0, 0.0)); // SKATING
        assertTrue(SneakSuppressionPolicy.suppressVanillaSneak(c.state()));
        c.update(new SkateInput(false, false, true, 0.0, 0.0)); // board leaves hand -> GROUNDED
        assertFalse(SneakSuppressionPolicy.suppressVanillaSneak(c.state()), "sneak suppression must be restored once skating ends");
    }
}
