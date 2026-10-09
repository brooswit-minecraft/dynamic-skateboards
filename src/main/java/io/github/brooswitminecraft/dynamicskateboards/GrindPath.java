package io.github.brooswitminecraft.dynamicskateboards;

import java.util.List;

import net.minecraft.world.phys.Vec3;

/**
 * An ordered chain of connected {@link GrindEdge} segments: one segment for a plain straight or
 * diagonal edge, several short segments for a curved edge (each one block's worth of taper, see
 * {@link GrindEdgeDetector}) so following it changes heading incrementally rather than snapping
 * once per block. Distances below are measured along the chain from its first point (segment 0's
 * {@code start}).
 */
public final class GrindPath {
    private final List<GrindEdge> segments;
    private final double[] cumulativeLengthAtSegmentStart;
    private final double totalLength;

    public GrindPath(List<GrindEdge> segments) {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("a grind path needs at least one segment");
        }
        this.segments = List.copyOf(segments);
        this.cumulativeLengthAtSegmentStart = new double[segments.size()];
        double running = 0.0;
        for (int i = 0; i < segments.size(); i++) {
            cumulativeLengthAtSegmentStart[i] = running;
            running += segments.get(i).length();
        }
        this.totalLength = running;
    }

    public List<GrindEdge> segments() {
        return segments;
    }

    public double length() {
        return totalLength;
    }

    public Vec3 start() {
        return segments.get(0).start();
    }

    public Vec3 end() {
        return segments.get(segments.size() - 1).end();
    }

    private int segmentIndexAt(double distanceAlong) {
        int index = 0;
        for (int i = 0; i < segments.size(); i++) {
            if (distanceAlong + 1.0e-9 >= cumulativeLengthAtSegmentStart[i]) {
                index = i;
            }
        }
        return index;
    }

    /** World position {@code distanceAlong} the chain from {@link #start()}, clamped to the path. */
    public Vec3 pointAt(double distanceAlong) {
        double clamped = Math.max(0.0, Math.min(totalLength, distanceAlong));
        int index = segmentIndexAt(clamped);
        return segments.get(index).pointAt(clamped - cumulativeLengthAtSegmentStart[index]);
    }

    /** Unit travel direction of the segment containing {@code distanceAlong}. */
    public Vec3 directionAt(double distanceAlong) {
        double clamped = Math.max(0.0, Math.min(totalLength, distanceAlong));
        int index = segmentIndexAt(clamped);
        return segments.get(index).direction();
    }

    /** Perpendicular distance from {@code point} to the nearest point anywhere on this path. */
    public double distanceToNearestPoint(Vec3 point) {
        double best = Double.POSITIVE_INFINITY;
        for (GrindEdge segment : segments) {
            best = Math.min(best, distanceToSegment(point, segment));
        }
        return best;
    }

    /** The distance-along-chain value of the point on this path nearest to {@code point}. */
    public double nearestDistanceAlong(Vec3 point) {
        double best = Double.POSITIVE_INFINITY;
        double bestDistanceAlong = 0.0;
        for (int i = 0; i < segments.size(); i++) {
            GrindEdge segment = segments.get(i);
            double t = projectParameter(point, segment);
            Vec3 closest = segment.start().add(segment.end().subtract(segment.start()).scale(t));
            double distance = closest.distanceTo(point);
            if (distance < best) {
                best = distance;
                bestDistanceAlong = cumulativeLengthAtSegmentStart[i] + t * segment.length();
            }
        }
        return bestDistanceAlong;
    }

    private static double distanceToSegment(Vec3 point, GrindEdge segment) {
        double t = projectParameter(point, segment);
        Vec3 closest = segment.start().add(segment.end().subtract(segment.start()).scale(t));
        return closest.distanceTo(point);
    }

    private static double projectParameter(Vec3 point, GrindEdge segment) {
        Vec3 ab = segment.end().subtract(segment.start());
        double lengthSquared = ab.lengthSqr();
        if (lengthSquared <= 1.0e-12) {
            return 0.0;
        }
        Vec3 ap = point.subtract(segment.start());
        double t = ap.dot(ab) / lengthSquared;
        return Math.max(0.0, Math.min(1.0, t));
    }
}
