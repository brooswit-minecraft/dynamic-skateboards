package io.github.brooswitminecraft.dynamicskateboards;

/**
 * Pure client-input sanitization for directions the server-authoritative skateboard controller
 * owns. While skating, A/D must not create vanilla sideways walking and S must not create vanilla
 * backward walking: those keys are already sent as raw intent for board steering and braking.
 * W remains available to vanilla's normal input pipeline; the controller's server velocity is the
 * movement authority for skating.
 */
public final class SkateMovementInputPolicy {
    private SkateMovementInputPolicy() {}

    /** Snapshot of the mutable vanilla movement fields the client hook reads and writes. */
    public record VanillaMovementInput(
            boolean up, boolean down, boolean left, boolean right, float forwardImpulse, float leftImpulse) {}

    /**
     * Removes controller-owned backward/lateral movement for every skating state and leaves
     * GROUNDED input untouched. Forward impulse is recalculated after clearing S so W+S cannot
     * cancel a forward input which remains physically held.
     */
    public static VanillaMovementInput suppressControllerOwnedMovement(SkateState state, VanillaMovementInput input) {
        if (state == SkateState.GROUNDED) {
            return input;
        }
        return new VanillaMovementInput(input.up(), false, false, false, input.up() ? 1.0f : 0.0f, 0.0f);
    }
}
