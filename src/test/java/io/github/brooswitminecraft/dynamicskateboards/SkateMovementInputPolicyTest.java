package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkateMovementInputPolicyTest {
    @Test
    void groundedLeavesVanillaInputUntouched() {
        var input = new SkateMovementInputPolicy.VanillaMovementInput(true, true, true, true, 0.0f, 0.0f);
        assertSame(input, SkateMovementInputPolicy.suppressControllerOwnedMovement(SkateState.GROUNDED, input));
    }

    @Test
    void skatingSuppressesVanillaBackAndStrafeButPreservesForwardIntent() {
        var sanitized = SkateMovementInputPolicy.suppressControllerOwnedMovement(SkateState.SKATING,
                new SkateMovementInputPolicy.VanillaMovementInput(true, true, true, true, 0.0f, 0.0f));

        assertTrue(sanitized.up());
        assertFalse(sanitized.down(), "S must not produce vanilla backward walking while braking");
        assertFalse(sanitized.left(), "A must not produce vanilla sideways walking while steering");
        assertFalse(sanitized.right(), "D must not produce vanilla sideways walking while steering");
        assertEquals(1.0f, sanitized.forwardImpulse());
        assertEquals(0.0f, sanitized.leftImpulse());
    }

    @Test
    void everyNonGroundedStateSuppressesControllerOwnedVanillaMovement() {
        for (SkateState state : SkateState.values()) {
            if (state == SkateState.GROUNDED) {
                continue;
            }
            var sanitized = SkateMovementInputPolicy.suppressControllerOwnedMovement(state,
                    new SkateMovementInputPolicy.VanillaMovementInput(false, true, true, true, -1.0f, 1.0f));
            assertFalse(sanitized.down(), state + " must suppress vanilla backward movement");
            assertFalse(sanitized.left(), state + " must suppress vanilla left strafe");
            assertFalse(sanitized.right(), state + " must suppress vanilla right strafe");
            assertEquals(0.0f, sanitized.forwardImpulse());
            assertEquals(0.0f, sanitized.leftImpulse());
        }
    }
}
