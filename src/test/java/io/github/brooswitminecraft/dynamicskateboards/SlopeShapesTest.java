package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Deterministic, headless tests over the vertical-transition family's collision shapes
 * (MINECRAFT-97 acceptance criteria): every shape is non-empty and within the block, and the
 * raised/non-raised pair butts together with no gap or overlap. See {@link SlopeShapes} and
 * {@link SlopeKind} for the geometry this asserts against, and docs/grindable-edges.md for the
 * authored angles.
 */
class SlopeShapesTest {
    private static final double EPS = 1.0e-7;

    /** The solid Y-interval present at one face of a built shape, or {@code null} if that face is empty. */
    private record Interval(double minY, double maxY) {}

    /**
     * Scans the shape's actual {@link AABB}s (never the authored parameters) for whatever touches
     * the face a piece's {@code FACING} points at ({@code exitFace=true}) or the opposite face
     * ({@code exitFace=false}), for the given facing. Generalizes across all four horizontal
     * facings: the "exit"/"entry" planes rotate with {@code facing}, exactly as a builder's
     * rotated placement would.
     */
    private static Interval faceInterval(VoxelShape shape, Direction facing, boolean exitFace) {
        boolean useX = facing.getAxis() == Direction.Axis.X;
        int step = useX ? facing.getStepX() : facing.getStepZ();
        boolean targetIsMaxPlane = exitFace ? step > 0 : step < 0;

        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        boolean found = false;
        for (AABB box : shape.toAabbs()) {
            double boxMin = useX ? box.minX : box.minZ;
            double boxMax = useX ? box.maxX : box.maxZ;
            boolean touches = targetIsMaxPlane ? boxMax >= 1.0 - EPS : boxMin <= EPS;
            if (touches) {
                found = true;
                minY = Math.min(minY, box.minY);
                maxY = Math.max(maxY, box.maxY);
            }
        }
        return found ? new Interval(minY, maxY) : null;
    }

    private static void assertIntervalsExactlyMatch(Interval a, Interval b, String message) {
        if (a == null || b == null) {
            assertEquals(a, b, message + " (one side has nothing touching the face, the other does)");
            return;
        }
        assertEquals(a.minY(), b.minY(), EPS, message + " (bottom bound differs)");
        assertEquals(a.maxY(), b.maxY(), EPS, message + " (top bound differs)");
    }

    @Test
    void everyKindEveryFacingIsNonEmptyAndWithinTheBlock() {
        for (SlopeKind kind : SlopeKind.values()) {
            Map<Direction, VoxelShape> shapes =
                    SlopeShapes.buildFacingShapes(kind.riseVoxels(), kind.verticalShift(), kind.inverted());
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape shape = shapes.get(facing);
                assertFalse(shape.isEmpty(), kind + " " + facing + " must not be empty");
                AABB bounds = shape.bounds();
                assertTrue(bounds.minX >= -EPS && bounds.minY >= -EPS && bounds.minZ >= -EPS,
                        kind + " " + facing + " min bound below 0: " + bounds);
                assertTrue(bounds.maxX <= 1 + EPS && bounds.maxY <= 1 + EPS && bounds.maxZ <= 1 + EPS,
                        kind + " " + facing + " max bound above 1: " + bounds);
            }
        }
    }

    /**
     * The acceptance criterion, asserted on the authored parameters: this is the designed
     * invariant restated, not proof the built shapes honor it — {@link #slopeChainingTilesExactlyAcrossAllFacings}
     * below is the test that actually inspects the {@link VoxelShape}s.
     */
    @Test
    void shallowAndSteepPairsAreDesignedToTileExactly() {
        assertEquals(SlopeKind.SHALLOW_SLOPE.exitHeight(), SlopeKind.RAISED_SHALLOW_SLOPE.entryHeight());
        assertEquals(SlopeKind.STEEP_SLOPE.exitHeight(), SlopeKind.RAISED_STEEP_SLOPE.entryHeight());
    }

    /**
     * The real acceptance-criterion test: for each raised/non-raised pair (including both
     * inverted overhead counterparts), and for every one of the four facings a builder can place
     * them in, piece A's exit face and piece B's entry face expose the EXACT same solid Y-interval
     * — not within a voxel, exactly — so placing B immediately after A along the facing axis
     * leaves no gap and no overlap at the seam.
     */
    @Test
    void slopeChainingTilesExactlyAcrossAllFacings() {
        SlopeKind[][] pairs = {
                {SlopeKind.SHALLOW_SLOPE, SlopeKind.RAISED_SHALLOW_SLOPE},
                {SlopeKind.STEEP_SLOPE, SlopeKind.RAISED_STEEP_SLOPE},
                {SlopeKind.SHALLOW_SLOPE_INVERTED, SlopeKind.RAISED_SHALLOW_SLOPE_INVERTED},
                {SlopeKind.STEEP_SLOPE_INVERTED, SlopeKind.RAISED_STEEP_SLOPE_INVERTED},
        };
        for (SlopeKind[] pair : pairs) {
            SlopeKind a = pair[0];
            SlopeKind b = pair[1];
            Map<Direction, VoxelShape> shapesA = SlopeShapes.buildFacingShapes(a.riseVoxels(), a.verticalShift(), a.inverted());
            Map<Direction, VoxelShape> shapesB = SlopeShapes.buildFacingShapes(b.riseVoxels(), b.verticalShift(), b.inverted());
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                Interval exitA = faceInterval(shapesA.get(facing), facing, true);
                Interval entryB = faceInterval(shapesB.get(facing), facing, false);
                assertIntervalsExactlyMatch(exitA, entryB,
                        a + " exit face (facing " + facing + ") must exactly match " + b + "'s entry face");
            }
        }
    }

    /**
     * {@code regular_slope} has no raised partner (see docs/grindable-edges.md): it's a
     * standalone bank from flat ground up to its own exit height, not a chained pair. Its entry
     * face must therefore be flush with flat ground (nothing solid there — the ramp hasn't risen
     * yet) and its exit face must expose exactly its declared rise.
     */
    @Test
    void regularSlopeIsFlushWithFlatGroundAtItsEntryAcrossAllFacings() {
        SlopeKind regular = SlopeKind.REGULAR_SLOPE;
        Map<Direction, VoxelShape> shapes =
                SlopeShapes.buildFacingShapes(regular.riseVoxels(), regular.verticalShift(), regular.inverted());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Interval entry = faceInterval(shapes.get(facing), facing, false);
            assertNull(entry, "regular_slope's entry face (facing " + facing + ") must be flush with flat ground");

            Interval exit = faceInterval(shapes.get(facing), facing, true);
            assertEquals(0.0, exit.minY(), EPS);
            assertEquals(regular.exitHeight() / 16.0, exit.maxY(), EPS);
        }
    }

    @Test
    void raisedSteepSlopeReachesTheBlockCeiling() {
        // So a flat block/wall placed one world-level up can cap it flush — see
        // docs/grindable-edges.md for why this does NOT, on its own, reach true vertical.
        assertEquals(16, SlopeKind.RAISED_STEEP_SLOPE.exitHeight());
    }

    @Test
    void angleOrderingIsShallowThenRegularThenSteep() {
        assertTrue(SlopeKind.SHALLOW_SLOPE.angleDegrees() < SlopeKind.REGULAR_SLOPE.angleDegrees());
        assertTrue(SlopeKind.REGULAR_SLOPE.angleDegrees() < SlopeKind.STEEP_SLOPE.angleDegrees());
    }

    @Test
    void heightProfileIsMonotonicNonDecreasing() {
        for (int rise : new int[] {4, 6, 8}) {
            int previous = 0;
            for (int col = 0; col < 16; col++) {
                int height = SlopeShapes.stepHeight(col, rise);
                assertTrue(height >= previous, "stepHeight must never decrease along the run");
                previous = height;
            }
            assertEquals(rise, previous, "the last column must reach the full rise");
        }
    }

    @Test
    void firstColumnIsAlwaysExactlyZero() {
        // The other half of the exact-tiling guarantee: stepHeight uses floor (not round)
        // specifically so this holds for every rise this family uses — see SlopeShapes.stepHeight.
        for (int rise : new int[] {4, 6, 8}) {
            assertEquals(0, SlopeShapes.stepHeight(0, rise));
        }
    }
}
