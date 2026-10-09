package io.github.brooswitminecraft.dynamicskateboards;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Geometry for the curved step/wall family (MINECRAFT-97's "horizontal curves"). A vanilla
 * stair/wall corner turns a grind line by a hard 90 degrees, all at once, right at the block
 * boundary. These pieces instead taper their own depth gradually across their full width, so
 * their own leading edge turns by {@link #CHAMFER_ANGLE_DEGREES} continuously across the block
 * rather than jogging once — see {@code CurvedShapesTest#chamferDepthStepsBySingleVoxelsAcrossTheFullWidth}.
 *
 * <p><b>This does not, by itself, compose into a long smooth arc.</b> Each block authors its
 * taper in its own 0..16 local frame, which always restarts at the next block — chaining several
 * identical pieces straight along the run axis repeats the same bounded taper at every block
 * boundary rather than accumulating a larger-radius curve (see
 * {@code CurvedShapesTest#chainingIdenticalPiecesAlongTheRunDoesNotAccumulateATurn} and
 * docs/grindable-edges.md). The intended use is to soften a single 90-degree grid joint between
 * two differently-{@code FACING} pieces, not to replace an arbitrarily long chain of 90-degree
 * jogs with one smooth curve.
 *
 * <p>The taper reuses {@link SlopeShapes}'s stepped-line approximation, just applied to the Z
 * depth of each X column instead of to a Y height — authored for {@link Direction#SOUTH} with the
 * taper's far point at {@link ChamferSide#RIGHT}'s corner, then rotated the same way as
 * {@link SlopeShapes}.
 */
public final class CurvedShapes {
    private CurvedShapes() {}

    /** How many of the 16 voxels of depth the taper removes at its far edge. */
    static final int TAPER_VOXELS = 4;

    /** atan(TAPER_VOXELS / 16): the direction change one piece contributes to a curve. */
    public static final double CHAMFER_ANGLE_DEGREES = Math.toDegrees(Math.atan(TAPER_VOXELS / 16.0));

    private static List<int[]> southColumnsVoxels(int heightVoxels, ChamferSide side) {
        List<int[]> boxes = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            int col = side == ChamferSide.RIGHT ? x : (15 - x);
            int depth = 16 - SlopeShapes.stepHeight(col, TAPER_VOXELS);
            boxes.add(new int[] {x, 0, 0, x + 1, heightVoxels, depth});
        }
        return boxes;
    }

    public static Map<Direction, VoxelShape> buildFacingShapes(int heightVoxels, ChamferSide side) {
        List<int[]> south = southColumnsVoxels(heightVoxels, side);
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            shapes.put(facing, SlopeShapes.toShape(SlopeShapes.rotate(south, facing.get2DDataValue())));
        }
        return shapes;
    }
}
