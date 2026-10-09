package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void shallowSlopeExitMeetsRaisedShallowSlopeEntry() {
        assertEquals(SlopeKind.SHALLOW_SLOPE.exitHeight(), SlopeKind.RAISED_SHALLOW_SLOPE.entryHeight(),
                "raised_shallow_slope must pick up exactly where shallow_slope ends, with no gap or overlap");
    }

    @Test
    void steepSlopeExitMeetsRaisedSteepSlopeEntry() {
        assertEquals(SlopeKind.STEEP_SLOPE.exitHeight(), SlopeKind.RAISED_STEEP_SLOPE.entryHeight(),
                "raised_steep_slope must pick up exactly where steep_slope ends, with no gap or overlap");
    }

    @Test
    void raisedSteepSlopeReachesTheBlockCeiling() {
        // So it can butt flush against a flat block/floor one world-level up.
        assertEquals(16, SlopeKind.RAISED_STEEP_SLOPE.exitHeight());
    }

    @Test
    void angleOrderingIsShallowThenRegularThenSteep() {
        assertTrue(SlopeKind.SHALLOW_SLOPE.angleDegrees() < SlopeKind.REGULAR_SLOPE.angleDegrees());
        assertTrue(SlopeKind.REGULAR_SLOPE.angleDegrees() < SlopeKind.STEEP_SLOPE.angleDegrees());
    }

    /**
     * Inspects the built SOUTH shape's actual {@link AABB}s (not just the authored parameters)
     * at its entry (z=0) and exit (z=1) faces: the exit face must reach exactly the declared
     * exit height (stepHeight's last column always equals the full rise, exactly), and any box
     * touching the entry face must be within one voxel of the declared entry height (the stepped
     * approximation's only source of slack, from rounding the first column).
     */
    @Test
    void builtShapeSurfaceMatchesDeclaredEntryAndExitHeights() {
        for (SlopeKind kind : new SlopeKind[] {
                SlopeKind.SHALLOW_SLOPE, SlopeKind.RAISED_SHALLOW_SLOPE,
                SlopeKind.REGULAR_SLOPE, SlopeKind.STEEP_SLOPE, SlopeKind.RAISED_STEEP_SLOPE}) {
            VoxelShape south = SlopeShapes.buildFacingShapes(kind.riseVoxels(), kind.verticalShift(), kind.inverted())
                    .get(Direction.SOUTH);

            double exitTop = -1;
            double entryTop = -1;
            for (AABB box : south.toAabbs()) {
                if (box.maxZ >= 1.0 - EPS) {
                    exitTop = Math.max(exitTop, box.maxY);
                }
                if (box.minZ <= EPS) {
                    entryTop = Math.max(entryTop, box.maxY);
                }
            }

            assertEquals(kind.exitHeight() / 16.0, exitTop, EPS,
                    kind + " exit face must exactly reach the declared exit height");
            double entryTopVoxels = entryTop < 0 ? 0 : entryTop * 16.0;
            assertTrue(Math.abs(entryTopVoxels - kind.entryHeight()) <= 1.0 + EPS,
                    kind + " entry face " + entryTopVoxels + " too far from declared " + kind.entryHeight());
        }
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
}
