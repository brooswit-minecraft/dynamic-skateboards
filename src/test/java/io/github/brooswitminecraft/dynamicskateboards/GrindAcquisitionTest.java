package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

/**
 * Deterministic, headless tests over {@link GrindAcquisition}: generous snapping, approach
 * tolerance, and the deterministic tie-break MINECRAFT-98 requires ("the same input picks the
 * same edge every time").
 */
class GrindAcquisitionTest {
    private static GrindPath edgeAt(double x0, double z0, double x1, double z1) {
        return new GrindPath(List.of(new GrindEdge(new Vec3(x0, 0, z0), new Vec3(x1, 0, z1))));
    }

    @Test
    void candidateBeyondSnapRadiusIsNotSelected() {
        GrindPath farAway = edgeAt(0, SkateConstants.GRIND_SNAP_RADIUS_BLOCKS + 1.0, 1, SkateConstants.GRIND_SNAP_RADIUS_BLOCKS + 1.0);
        GrindPath chosen = GrindAcquisition.selectBestCandidate(List.of(farAway), new Vec3(0, 0, 0), 0.0);
        assertNull(chosen, "a candidate farther than the snap radius must not be acquired");
    }

    @Test
    void candidateWithinSnapRadiusAndAlignedHeadingIsSelected() {
        // Player at origin, facing +Z (heading 0 degrees per SkateMovement's convention), edge
        // runs along Z right next to them.
        GrindPath nearby = edgeAt(0.5, 0, 0.5, 1);
        GrindPath chosen = GrindAcquisition.selectBestCandidate(List.of(nearby), new Vec3(0, 0, 0), 0.0);
        assertSame(nearby, chosen, "a nearby, aligned candidate within the snap radius must be acquired");
    }

    @Test
    void candidatePerpendicularToApproachIsRejected() {
        // Edge runs along X (east-west); player faces +Z (north-south) directly at it: a
        // perpendicular approach, well outside the generous approach tolerance.
        GrindPath perpendicular = edgeAt(0, 0.5, 1, 0.5);
        GrindPath chosen = GrindAcquisition.selectBestCandidate(List.of(perpendicular), new Vec3(0.5, 0, 0), 0.0);
        assertNull(chosen, "an edge approached nearly perpendicular must not be acquired");
    }

    @Test
    void tieBreakIsDeterministicRegardlessOfListOrder() {
        GrindPath a = edgeAt(-0.5, 0, -0.5, 1);
        GrindPath b = edgeAt(0.5, 0, 0.5, 1);
        Vec3 playerPos = new Vec3(0, 0, 0.5); // equidistant from both edges

        GrindPath firstOrder = GrindAcquisition.selectBestCandidate(List.of(a, b), playerPos, 0.0);
        GrindPath secondOrder = GrindAcquisition.selectBestCandidate(List.of(b, a), playerPos, 0.0);

        assertNotNull(firstOrder, "one of the two equidistant candidates must still be chosen");
        assertSame(firstOrder, secondOrder, "the tie-break must pick the same edge regardless of input order");
    }

    @Test
    void travelSignContinuesThePlayersOwnApproachDirection() {
        GrindPath path = edgeAt(0, 0, 0, 10);
        // Facing +Z (heading 0): should travel toward increasing distance-along-chain (+1).
        assertEquals(1, GrindAcquisition.travelSignAt(path, 0.0, 0.0));
        // Facing -Z (heading 180): should travel the other way (-1).
        assertEquals(-1, GrindAcquisition.travelSignAt(path, 5.0, 180.0));
    }
}
