package io.github.brooswitminecraft.dynamicskateboards;

import net.minecraft.world.phys.Vec3;

/**
 * One straight world-space segment of a grindable edge. A single segment is enough for a plain
 * straight or diagonal edge (a chord from entry corner to exit corner, per
 * docs/grindable-edges.md's "approximated by the diagonal line" reasoning); a curved edge is
 * represented as several short consecutive {@link GrindEdge}s chained into a {@link GrindPath} so
 * following it can actually change heading incrementally instead of snapping once per block.
 */
public record GrindEdge(Vec3 start, Vec3 end) {
    public double length() {
        return start.distanceTo(end);
    }

    /** Unit vector from {@link #start} to {@link #end}; never queried on a zero-length edge. */
    public Vec3 direction() {
        return end.subtract(start).normalize();
    }

    public Vec3 pointAt(double distanceFromStart) {
        double len = length();
        double t = len <= 1.0e-9 ? 0.0 : clamp01(distanceFromStart / len);
        return start.add(end.subtract(start).scale(t));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
