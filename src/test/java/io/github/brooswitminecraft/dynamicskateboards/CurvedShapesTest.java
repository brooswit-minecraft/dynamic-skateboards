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
 * Deterministic, headless tests over the curved step/wall family's collision shapes
 * (MINECRAFT-97 acceptance criteria): every shape is non-empty and within the block, and the
 * turning edge changes direction by a small increment, never a 90-degree jog. See
 * {@link CurvedShapes} for the geometry and docs/grindable-edges.md for the authored angle.
 */
class CurvedShapesTest {
    private static final double EPS = 1.0e-7;
    private static final int[] HEIGHTS = {8, 16};

    @Test
    void everyHeightEverySideEveryFacingIsNonEmptyAndWithinTheBlock() {
        for (int height : HEIGHTS) {
            for (ChamferSide side : ChamferSide.values()) {
                Map<Direction, VoxelShape> shapes = CurvedShapes.buildFacingShapes(height, side);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    VoxelShape shape = shapes.get(facing);
                    assertFalse(shape.isEmpty(), height + " " + side + " " + facing + " must not be empty");
                    AABB bounds = shape.bounds();
                    assertTrue(bounds.minX >= -EPS && bounds.minY >= -EPS && bounds.minZ >= -EPS,
                            height + " " + side + " " + facing + " min bound below 0: " + bounds);
                    assertTrue(bounds.maxX <= 1 + EPS && bounds.maxY <= 1 + EPS && bounds.maxZ <= 1 + EPS,
                            height + " " + side + " " + facing + " max bound above 1: " + bounds);
                }
            }
        }
    }

    /**
     * The per-piece turning angle is small, nowhere near the 90-degree jog a vanilla stair/wall
     * corner would produce: chaining pieces of the same {@link ChamferSide} around a curve turns
     * the travel direction by this amount per piece, not all at once.
     */
    @Test
    void turningAngleIsSmallNotA90DegreeJog() {
        assertTrue(CurvedShapes.CHAMFER_ANGLE_DEGREES > 0.0, "a curved piece must actually turn");
        assertTrue(CurvedShapes.CHAMFER_ANGLE_DEGREES < 45.0,
                "one piece's turn (" + CurvedShapes.CHAMFER_ANGLE_DEGREES + " deg) must be well short of a 90-degree jog");
    }

    /**
     * What chaining several identical pieces end to end along the run axis (same facing, same
     * {@link ChamferSide}) actually gives: NOT an accumulating arc. Each block's taper is
     * authored in that block's own 0..16 local frame and always restarts there, so the global
     * depth profile along a chain of N blocks is exactly periodic with period 16 (one block) —
     * the same {@link CurvedShapes#CHAMFER_ANGLE_DEGREES}-ish bevel repeats at every block
     * boundary instead of compounding into a larger-radius curve. See docs/grindable-edges.md for
     * what this means for building an actual turn (the bevel softens a single 90-degree grid
     * joint between two differently-FACING pieces; it does not, by itself, replace a long chain
     * of jogs with a smooth arc).
     */
    @Test
    void chainingIdenticalPiecesAlongTheRunDoesNotAccumulateATurn() {
        for (int height : HEIGHTS) {
            for (ChamferSide side : ChamferSide.values()) {
                VoxelShape south = CurvedShapes.buildFacingShapes(height, side).get(Direction.SOUTH);
                double[] onePeriod = sampleDepthProfile(south);

                int chainLength = 5;
                for (int block = 1; block < chainLength; block++) {
                    // A fresh block placed next in the chain recomputes the identical shape —
                    // there is no block-to-block state to carry an accumulating offset forward.
                    VoxelShape repeated = CurvedShapes.buildFacingShapes(height, side).get(Direction.SOUTH);
                    double[] repeatedPeriod = sampleDepthProfile(repeated);
                    for (int x = 0; x < 16; x++) {
                        assertEquals(onePeriod[x], repeatedPeriod[x], EPS,
                                height + " " + side + " block " + block + " column " + x
                                        + " must repeat the same local depth, not continue accumulating");
                    }
                }

                double maxDepth = 0;
                double minDepth = Double.POSITIVE_INFINITY;
                for (double d : onePeriod) {
                    maxDepth = Math.max(maxDepth, d);
                    minDepth = Math.min(minDepth, d);
                }
                double excursionVoxels = (maxDepth - minDepth) * 16.0;
                assertEquals(CurvedShapes.TAPER_VOXELS, Math.round(excursionVoxels),
                        height + " " + side + ": the per-block excursion (the only \"radius\" this chain gives) must stay bounded, not grow with chain length");
            }
        }
    }

    private static double[] sampleDepthProfile(VoxelShape south) {
        double[] depths = new double[16];
        for (int x = 0; x < 16; x++) {
            double sampleX = (x + 0.5) / 16.0;
            double depth = -1;
            for (AABB box : south.toAabbs()) {
                if (box.minX <= sampleX && sampleX <= box.maxX) {
                    depth = Math.max(depth, box.maxZ);
                }
            }
            depths[x] = depth;
        }
        return depths;
    }

    /**
     * The chamfer tapers the block's own depth continuously across its full width — not a flat
     * face broken by one abrupt step — so consecutive 1/16-wide slices never differ in depth by
     * more than a single voxel.
     */
    @Test
    void chamferDepthStepsBySingleVoxelsAcrossTheFullWidth() {
        for (int height : HEIGHTS) {
            for (ChamferSide side : ChamferSide.values()) {
                VoxelShape south = CurvedShapes.buildFacingShapes(height, side).get(Direction.SOUTH);
                int previousDepthVoxels = -1;
                for (int x = 0; x < 16; x++) {
                    double sampleX = (x + 0.5) / 16.0;
                    double depth = -1;
                    for (AABB box : south.toAabbs()) {
                        if (box.minX <= sampleX && sampleX <= box.maxX) {
                            depth = Math.max(depth, box.maxZ);
                        }
                    }
                    assertTrue(depth >= 0, height + " " + side + " column " + x + " has no geometry");
                    int depthVoxels = Math.round((float) (depth * 16.0));
                    if (previousDepthVoxels >= 0) {
                        assertTrue(Math.abs(depthVoxels - previousDepthVoxels) <= 1,
                                height + " " + side + " depth jumped by more than one voxel between columns "
                                        + (x - 1) + " and " + x);
                    }
                    previousDepthVoxels = depthVoxels;
                }
            }
        }
    }
}
