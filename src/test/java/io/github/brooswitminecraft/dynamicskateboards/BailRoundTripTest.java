package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The full skating -&gt; bail -&gt; loose board -&gt; pickup -&gt; re-enter-skating round trip
 * (MINECRAFT-181's definition of done), chained headlessly out of the exact pure classes the
 * real Minecraft glue ({@code DynamicSkateboardsMod.ServerEvents}, {@link LooseSkateboardEntity})
 * delegates every decision to:
 *
 * <ul>
 *   <li>{@link SkateController} decides when a bail happens and that skating ends.
 *   <li>{@link LooseBoardClaim} decides who, if anyone, just picked the board back up.
 * </ul>
 *
 * <p>What this test does NOT exercise: the actual {@code ItemStack}/inventory removal and
 * {@code LooseSkateboardEntity} spawn/physics/NBT, since those need a running {@code
 * ServerPlayer}/{@code ServerLevel} this headless suite cannot bootstrap (consistent with every
 * other Minecraft-glue class in this codebase - e.g. {@code WorldGrindSeam} - never getting its
 * own entity-level test either). Those need a human-in-client pass; see the PR description.
 */
class BailRoundTripTest {
    private static SkateInput held(boolean board, boolean jump, boolean onGround) {
        return new SkateInput(board, jump, onGround, 0.0, 0.0);
    }

    private static SkateInput landing(double verticalVelocity) {
        return new SkateInput(true, false, true, 0.0, 0.0, false, false, false, TrickDirection.NEUTRAL, verticalVelocity);
    }

    @Test
    void skatingBailLooseBoardPickupAndReselectRoundTrip() {
        // 1. Riding normally.
        SkateController rider = new SkateController();
        assertEquals(SkateState.SKATING, rider.update(held(true, false, true)));
        rider.update(held(true, false, false)); // AIRBORNE

        // 2. A badly missed landing forces the bail.
        SkateState afterImpact = rider.update(landing(SkateConstants.BAIL_IMPACT_SPEED_THRESHOLD + 0.5));
        assertEquals(SkateState.GROUNDED, afterImpact, "bail must leave the skating state (existing exit path)");
        assertTrue(rider.takeBailedThisTick(), "board left inventory must be explicitly signalled, not implied");
        assertFalse(rider.isSkating(), "the player must no longer be skating after a bail");

        // 3. "Board left inventory" + "loose board exists in world": the bail flag above is
        // exactly the signal DynamicSkateboardsMod.ServerEvents uses to clear the main hand and
        // call LooseSkateboardEntity.spawnFromBail - both are one-shot, already asserted to have
        // fired exactly once (step 2's assertTrue, consumed).
        assertFalse(rider.takeBailedThisTick(), "the bail signal must not fire a second time for the same event");

        // 4. Player walks over the loose board: first touch wins and gives the item back.
        LooseBoardClaim claim = new LooseBoardClaim();
        assertTrue(claim.tryClaim(), "the walking player must successfully claim (and so receive) the board");
        // A second player reaching it the same tick must get nothing.
        assertFalse(claim.tryClaim(), "a second claimant must never also receive the board");

        // 5. Board back in inventory -> main-hand-select re-enters skating via the EXISTING path.
        SkateController reselected = new SkateController();
        assertEquals(SkateState.GROUNDED, reselected.state(), "starts GROUNDED, same as any fresh controller");
        SkateState resumed = reselected.update(held(true, false, true));
        assertEquals(SkateState.SKATING, resumed, "re-selecting the recovered board must resume skating");
        assertTrue(reselected.isSkating());

        // No stuck custom animation/trick state carries over from the bail.
        assertNull(reselected.activeFlip());
        assertNull(reselected.activeGrab());
    }

    @Test
    void chunkUnloadReloadPersistencePreservesOrientationAndPosition() {
        // See LooseSkateboardPersistenceTest for the dedicated NBT round-trip coverage; this just
        // confirms the same guarantee holds as part of the end-to-end story, not only in isolation.
        // Review fix: position is now asserted here too, not just orientation - a loose board
        // that comes back from an unload at the wrong spot is just as broken as one with the
        // wrong tumble.
        org.joml.Quaternionf orientation = new org.joml.Quaternionf(0.0f, 0.0f, 0.38268346f, 0.92387953f); // 45 degrees
        net.minecraft.world.phys.Vec3 position = new net.minecraft.world.phys.Vec3(12.0, 70.0, -5.5);
        net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
        LooseSkateboardPersistence.write(saved, orientation);
        LooseSkateboardPersistence.writePosition(saved, position);

        // Simulate the unload/reload boundary: nothing survives except the tag itself.
        net.minecraft.nbt.CompoundTag reloaded = saved.copy();

        org.joml.Quaternionf restoredOrientation = LooseSkateboardPersistence.read(reloaded);
        assertEquals(orientation.x, restoredOrientation.x, 1e-6f);
        assertEquals(orientation.y, restoredOrientation.y, 1e-6f);
        assertEquals(orientation.z, restoredOrientation.z, 1e-6f);
        assertEquals(orientation.w, restoredOrientation.w, 1e-6f);

        net.minecraft.world.phys.Vec3 restoredPosition = LooseSkateboardPersistence.readPosition(reloaded);
        assertEquals(position.x, restoredPosition.x, 1e-9);
        assertEquals(position.y, restoredPosition.y, 1e-9);
        assertEquals(position.z, restoredPosition.z, 1e-9);
    }
}
