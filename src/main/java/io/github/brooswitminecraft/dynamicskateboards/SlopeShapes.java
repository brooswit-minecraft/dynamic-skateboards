package io.github.brooswitminecraft.dynamicskateboards;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hand-authored 1/16-voxel ramp geometry shared by every {@link SlopeBlock}. See
 * docs/grindable-edges.md for the rationale behind "raised" and "inverted", which this class
 * implements literally:
 *
 * <ul>
 *   <li>A normal ramp is a solid column, per 1/16-wide slice along the rise axis, from the block
 *       floor (y=0) up to a stepped approximation of a straight line from
 *       {@code (0, verticalShift)} to {@code (16, verticalShift + riseVoxels)}. {@code
 *       verticalShift} is the ramp's ENTRY height, {@code verticalShift + riseVoxels} its EXIT
 *       height.
 *   <li>"Raised" is simply a second ramp of the same {@code riseVoxels} whose {@code
 *       verticalShift} equals the non-raised ramp's EXIT height, so the two butt together with no
 *       gap when placed side by side along the rise axis at the same world Y level.
 *   <li>"Inverted" mirrors the whole column about the block's horizontal midplane (y -&gt; 16-y),
 *       turning a floor-supported ramp into a ceiling-supported one for overhead continuations.
 * </ul>
 *
 * Every shape is authored for {@link Direction#SOUTH} (the ramp rises as local z increases: the
 * entry edge is the z=0 face, the exit edge is the z=1 face) and then rotated about the Y axis for
 * the other three horizontal facings.
 */
public final class SlopeShapes {
    private SlopeShapes() {}

    public static final double VOXEL = 1.0 / 16.0;

    /**
     * Stepped (1/16-voxel) height of the ramp surface at column {@code col} (0..15), 0..riseVoxels.
     * Uses floor, not round: that guarantees column 0 is always exactly 0 and column 15 is always
     * exactly {@code riseVoxels} (for any {@code riseVoxels < 16}), so two ramps placed side by
     * side with matching shift/exit values butt together with EXACTLY zero gap or overlap at
     * their shared face — round would occasionally round column 0 up by a voxel (e.g. rise=8),
     * which is precisely the seam this geometry exists to avoid.
     */
    static int stepHeight(int col, int riseVoxels) {
        return (int) Math.floor((col + 1) * riseVoxels / 16.0);
    }

    /** One south-facing solid box per column, in voxel units [x0,y0,z0,x1,y1,z1]. */
    private static List<int[]> southColumnsVoxels(int riseVoxels, int verticalShift) {
        List<int[]> boxes = new ArrayList<>();
        for (int z = 0; z < 16; z++) {
            int top = verticalShift + stepHeight(z, riseVoxels);
            if (top <= 0) {
                continue;
            }
            boxes.add(new int[] {0, 0, z, 16, top, z + 1});
        }
        return boxes;
    }

    /** Mirrors boxes about the block's horizontal midplane: y -&gt; 16-y. */
    private static List<int[]> invertVertically(List<int[]> boxes) {
        List<int[]> out = new ArrayList<>();
        for (int[] b : boxes) {
            out.add(new int[] {b[0], 16 - b[4], b[2], b[3], 16 - b[1], b[5]});
        }
        return out;
    }

    /**
     * Rotates south-authored voxel boxes about the Y axis, {@code steps} times, matching
     * {@link Direction#get2DDataValue()} (SOUTH=0, WEST=1, NORTH=2, EAST=3): each step applies
     * (x,z) -&gt; (16-z, x) — i.e. south's exit face (high z) rotates to WEST's own forward face
     * (low x) after one step, and to EAST's own forward face (high x) after three. Verified
     * directly against {@code Direction.getStepX()/getStepZ()} (not just assumed): the other
     * rotation handedness, (x,z) -&gt; (z, 16-x), silently swaps EAST and WEST — 180-degree
     * rotation (NORTH) looks correct either way, which is exactly why this is easy to get backwards
     * without checking the odd step counts against the real direction vectors.
     */
    static List<int[]> rotate(List<int[]> boxes, int steps) {
        List<int[]> current = boxes;
        for (int i = 0; i < ((steps % 4) + 4) % 4; i++) {
            List<int[]> next = new ArrayList<>();
            for (int[] b : current) {
                next.add(new int[] {16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]});
            }
            current = next;
        }
        return current;
    }

    static VoxelShape toShape(List<int[]> boxesVoxels) {
        VoxelShape shape = Shapes.empty();
        for (int[] b : boxesVoxels) {
            VoxelShape box = Shapes.box(
                    b[0] * VOXEL, b[1] * VOXEL, b[2] * VOXEL,
                    b[3] * VOXEL, b[4] * VOXEL, b[5] * VOXEL);
            shape = Shapes.joinUnoptimized(shape, box, BooleanOp.OR);
        }
        return shape.optimize();
    }

    /** Builds the shape for every horizontal facing of a ramp with the given parameters. */
    public static Map<Direction, VoxelShape> buildFacingShapes(int riseVoxels, int verticalShift, boolean inverted) {
        List<int[]> south = southColumnsVoxels(riseVoxels, verticalShift);
        if (inverted) {
            south = invertVertically(south);
        }
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            shapes.put(facing, toShape(rotate(south, facing.get2DDataValue())));
        }
        return shapes;
    }
}
