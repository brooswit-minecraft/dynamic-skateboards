package io.github.brooswitminecraft.dynamicskateboards;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Finds grind-candidate edges from raw world collision geometry ({@link VoxelShape}), not from
 * any list of known block ids &mdash; see docs/grindable-edges.md for the geometry this is judged
 * against. Reads every candidate block's shape as its decomposed {@link AABB} boxes
 * ({@code VoxelShape#toAabbs()}) and extracts two independent families of edge, both of which
 * fall out of the same box data without special-casing any particular block:
 *
 * <ul>
 *   <li><b>Height-profile edges</b> (straight and diagonal): for each of the two horizontal axes
 *       acting as the "run" and the other as the "lateral" side (sampled at local 0 and 1, i.e.
 *       the block's own west/east or north/south face), the height of the solid material is
 *       sampled as a function of position along the run axis. A flat run (slab top, stair tread)
 *       yields one straight chord; a steadily rising run (a slope's side edge) yields one
 *       diagonal chord from entry corner to exit corner, exactly the "approximated by the
 *       diagonal line" reasoning in docs/grindable-edges.md. A hard jump (a stair's riser)
 *       splits into separate chords instead of faking one smooth diagonal through it.
 *   <li><b>Depth-profile paths</b> (curved): for the single highest-roof cluster of boxes, when
 *       the horizontal extent perpendicular to a run axis (the taper depth, see
 *       {@link CurvedShapes}) actually varies along it, the taper boundary is sampled the same
 *       way the height-profile is &mdash; by breakpoint, never by assuming any particular box
 *       count or width &mdash; and chained into a short-segmented {@link GrindPath} without
 *       merging consecutive samples, so following it turns incrementally rather than snapping
 *       once per block.
 * </ul>
 *
 * Exposure (not buried against another block, and not covered flush from above) is checked with
 * one bounded neighbor-shape query per candidate, never a wide scan: see
 * {@link #isExposedLateral} and {@link #isClearAbove}.
 */
public final class GrindEdgeDetector {
    private GrindEdgeDetector() {}

    private static final double EPS = 1.0e-7;

    private record LocalSegment(double[] a, double[] b) {}

    /**
     * Scans a bounded cube of blocks (radius {@code radiusBlocks}, in blocks) around
     * {@code center} and returns every candidate {@link GrindPath} that clears
     * {@link SkateConstants#GRIND_MIN_EDGE_LENGTH_VOXELS} and is exposed. Bounded and local by
     * construction: the loop only ever touches {@code (2*radiusBlocks+1)^3} block positions.
     */
    public static List<GrindPath> findCandidatePaths(GrindCollisionSource source, BlockPos center, int radiusBlocks) {
        List<GrindPath> out = new ArrayList<>();
        double minLengthBlocks = SkateConstants.GRIND_MIN_EDGE_LENGTH_VOXELS / 16.0;

        for (int dx = -radiusBlocks; dx <= radiusBlocks; dx++) {
            for (int dy = -radiusBlocks; dy <= radiusBlocks; dy++) {
                for (int dz = -radiusBlocks; dz <= radiusBlocks; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    VoxelShape shape = source.collisionShapeAt(pos);
                    if (shape == null || shape.isEmpty()) {
                        continue;
                    }
                    List<AABB> boxes = shape.toAabbs();
                    if (boxes.isEmpty() || !isClearAbove(source, pos)) {
                        continue;
                    }

                    collectHeightProfilePaths(source, pos, boxes, minLengthBlocks, out);

                    GrindPath curved = depthProfilePath(boxes, pos);
                    if (curved != null && curved.length() >= minLengthBlocks) {
                        out.add(curved);
                    }
                }
            }
        }
        return out;
    }

    private static void collectHeightProfilePaths(
            GrindCollisionSource source, BlockPos pos, List<AABB> boxes, double minLengthBlocks, List<GrindPath> out) {
        Direction.Axis[] runAxes = {Direction.Axis.Z, Direction.Axis.X};
        for (Direction.Axis runAxis : runAxes) {
            Direction.Axis lateralAxis = runAxis == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
            for (double lateralValue : new double[] {0.0, 1.0}) {
                for (LocalSegment seg : heightProfileSegments(boxes, runAxis, lateralAxis, lateralValue)) {
                    double minHeightLocal = Math.min(seg.a()[1], seg.b()[1]);
                    if (!isExposedLateral(source, pos, lateralAxis, lateralValue, minHeightLocal)) {
                        continue;
                    }
                    GrindEdge edge = new GrindEdge(toWorld(pos, seg.a()), toWorld(pos, seg.b()));
                    if (edge.length() >= minLengthBlocks) {
                        out.add(new GrindPath(List.of(edge)));
                    }
                }
            }
        }
    }

    /**
     * Height(run) sampled at the block's {@code lateralAxis} face ({@code lateralValue} 0 or 1),
     * merged into straight/diagonal chords and split wherever the height jumps by more than
     * {@link SkateConstants#GRIND_PROFILE_MAX_SMOOTH_STEP_VOXELS}.
     */
    private static List<LocalSegment> heightProfileSegments(
            List<AABB> boxes, Direction.Axis runAxis, Direction.Axis lateralAxis, double lateralValue) {
        List<AABB> touching = new ArrayList<>();
        for (AABB box : boxes) {
            boolean touches = lateralValue < 0.5
                    ? axisMin(box, lateralAxis) <= EPS
                    : axisMax(box, lateralAxis) >= 1.0 - EPS;
            if (touches) {
                touching.add(box);
            }
        }
        if (touching.isEmpty()) {
            return List.of();
        }

        TreeSet<Double> breakSet = new TreeSet<>();
        breakSet.add(0.0);
        breakSet.add(1.0);
        for (AABB box : touching) {
            breakSet.add(axisMin(box, runAxis));
            breakSet.add(axisMax(box, runAxis));
        }
        List<Double> breaks = new ArrayList<>(breakSet);

        // [intervalStart, intervalEnd, height] per contiguous sampled interval that has material.
        List<double[]> samples = new ArrayList<>();
        for (int i = 0; i < breaks.size() - 1; i++) {
            double a = breaks.get(i);
            double b = breaks.get(i + 1);
            double mid = (a + b) / 2.0;
            double height = -1.0;
            for (AABB box : touching) {
                if (axisMin(box, runAxis) - EPS <= mid && mid <= axisMax(box, runAxis) + EPS) {
                    height = Math.max(height, box.maxY);
                }
            }
            if (height >= 0.0) {
                samples.add(new double[] {a, b, height});
            }
        }
        if (samples.isEmpty()) {
            return List.of();
        }

        double smoothStepLocal = SkateConstants.GRIND_PROFILE_MAX_SMOOTH_STEP_VOXELS / 16.0;
        List<LocalSegment> out = new ArrayList<>();
        int start = 0;
        while (start < samples.size()) {
            int end = start;
            while (end + 1 < samples.size()
                    && Math.abs(samples.get(end)[1] - samples.get(end + 1)[0]) <= EPS
                    && Math.abs(samples.get(end + 1)[2] - samples.get(end)[2]) <= smoothStepLocal) {
                end++;
            }
            double runStart = samples.get(start)[0];
            double heightStart = samples.get(start)[2];
            double runEnd = samples.get(end)[1];
            double heightEnd = samples.get(end)[2];

            double[] pA = new double[3];
            double[] pB = new double[3];
            setAxis(pA, runAxis, runStart);
            setAxis(pA, lateralAxis, lateralValue);
            setAxis(pA, Direction.Axis.Y, heightStart);
            setAxis(pB, runAxis, runEnd);
            setAxis(pB, lateralAxis, lateralValue);
            setAxis(pB, Direction.Axis.Y, heightEnd);
            out.add(new LocalSegment(pA, pB));

            start = end + 1;
        }
        return out;
    }

    /**
     * The curved family's top-perimeter edge: the single highest-roof cluster of boxes, sampled
     * along a run axis exactly like {@link #heightProfileSegments} but reading each interval's
     * depth-axis bound instead of its height &mdash; deliberately NOT assuming any particular
     * box decomposition (e.g. "one box per column"): {@code VoxelShape#optimize()} is free to
     * merge columns sharing the same depth into wider bands, and this still samples the real
     * boundary at whatever breakpoints the boxes actually have. Unlike the height-profile family,
     * consecutive samples are never merged into one chord &mdash; each interval-to-interval step
     * is kept as its own short segment, so following it turns incrementally. Returns {@code null}
     * when this block's geometry doesn't look like that (e.g. a flat-topped shape with no taper).
     */
    private static GrindPath depthProfilePath(List<AABB> boxes, BlockPos pos) {
        if (boxes.size() < 2) {
            return null;
        }
        double roofY = Double.NEGATIVE_INFINITY;
        for (AABB box : boxes) {
            roofY = Math.max(roofY, box.maxY);
        }
        List<AABB> cluster = new ArrayList<>();
        for (AABB box : boxes) {
            if (box.maxY >= roofY - EPS) {
                cluster.add(box);
            }
        }
        if (cluster.size() < 2) {
            return null;
        }

        for (Direction.Axis runAxis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            Direction.Axis depthAxis = runAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            double minRange = range(cluster, box -> axisMin(box, depthAxis));
            double maxRange = range(cluster, box -> axisMax(box, depthAxis));
            if (Math.max(minRange, maxRange) <= EPS) {
                continue;
            }
            boolean useMax = maxRange >= minRange;

            TreeSet<Double> breakSet = new TreeSet<>();
            breakSet.add(0.0);
            breakSet.add(1.0);
            for (AABB box : cluster) {
                breakSet.add(axisMin(box, runAxis));
                breakSet.add(axisMax(box, runAxis));
            }
            List<Double> breaks = new ArrayList<>(breakSet);

            List<double[]> samples = new ArrayList<>(); // {runMid, depthValue}
            for (int i = 0; i < breaks.size() - 1; i++) {
                double a = breaks.get(i);
                double b = breaks.get(i + 1);
                double mid = (a + b) / 2.0;
                Double value = null;
                for (AABB box : cluster) {
                    if (axisMin(box, runAxis) - EPS <= mid && mid <= axisMax(box, runAxis) + EPS) {
                        double candidate = useMax ? axisMax(box, depthAxis) : axisMin(box, depthAxis);
                        if (value == null || (useMax ? candidate > value : candidate < value)) {
                            value = candidate;
                        }
                    }
                }
                if (value != null) {
                    samples.add(new double[] {mid, value});
                }
            }
            if (samples.size() < 2) {
                continue;
            }

            List<GrindEdge> segments = new ArrayList<>();
            double[] previous = null;
            for (double[] sample : samples) {
                double[] point = new double[3];
                setAxis(point, runAxis, sample[0]);
                setAxis(point, depthAxis, sample[1]);
                setAxis(point, Direction.Axis.Y, roofY);
                if (previous != null) {
                    segments.add(new GrindEdge(toWorld(pos, previous), toWorld(pos, point)));
                }
                previous = point;
            }
            if (!segments.isEmpty()) {
                return new GrindPath(segments);
            }
        }
        return null;
    }

    /** Whether the block above {@code pos} leaves clear headroom (not flush, solid, overhead). */
    private static boolean isClearAbove(GrindCollisionSource source, BlockPos pos) {
        VoxelShape above = source.collisionShapeAt(pos.above());
        if (above == null || above.isEmpty()) {
            return true;
        }
        return above.bounds().minY > EPS;
    }

    /**
     * Whether the face at {@code lateralAxis}={@code lateralValue} is open, not buried against a
     * neighbor block that reaches at least as high as {@code minHeightLocal} (this block's own
     * local 0..1 height) &mdash; i.e. not an interior/occluded edge.
     */
    private static boolean isExposedLateral(
            GrindCollisionSource source, BlockPos pos, Direction.Axis lateralAxis, double lateralValue, double minHeightLocal) {
        Direction direction = neighborDirection(lateralAxis, lateralValue >= 0.5);
        BlockPos neighborPos = pos.relative(direction);
        VoxelShape neighborShape = source.collisionShapeAt(neighborPos);
        if (neighborShape == null || neighborShape.isEmpty()) {
            return true;
        }
        double neighborMaxYWorld = neighborPos.getY() + neighborShape.bounds().maxY;
        double ourMinHeightWorld = pos.getY() + minHeightLocal;
        return neighborMaxYWorld < ourMinHeightWorld - EPS;
    }

    private static Direction neighborDirection(Direction.Axis lateralAxis, boolean atMax) {
        if (lateralAxis == Direction.Axis.X) {
            return atMax ? Direction.EAST : Direction.WEST;
        }
        return atMax ? Direction.SOUTH : Direction.NORTH;
    }

    private static double range(List<AABB> boxes, ToDoubleFunction<AABB> f) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (AABB box : boxes) {
            double v = f.applyAsDouble(box);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min;
    }

    private static double axisMin(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.minX;
            case Y -> box.minY;
            case Z -> box.minZ;
        };
    }

    private static double axisMax(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.maxX;
            case Y -> box.maxY;
            case Z -> box.maxZ;
        };
    }

    private static void setAxis(double[] local, Direction.Axis axis, double value) {
        local[axis.ordinal()] = value;
    }

    private static Vec3 toWorld(BlockPos pos, double[] local) {
        return new Vec3(pos.getX() + local[0], pos.getY() + local[1], pos.getZ() + local[2]);
    }
}
