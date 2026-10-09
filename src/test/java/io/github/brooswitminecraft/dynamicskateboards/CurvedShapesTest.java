package io.github.brooswitminecraft.dynamicskateboards;

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
