package io.github.brooswitminecraft.dynamicskateboards;

import java.util.List;

import net.minecraft.world.phys.Vec3;

/**
 * Pure selection logic over a list of candidate {@link GrindPath}s: which one (if any) Shift
 * should acquire this tick, given the player's position and current heading. No Minecraft
 * world/entity types, so the generous-snapping and deterministic-tie-break requirements are
 * unit-testable without a running game &mdash; {@link WorldGrindSeam} is the only caller that
 * supplies real geometry.
 */
public final class GrindAcquisition {
    private GrindAcquisition() {}

    /**
     * The best candidate within {@link SkateConstants#GRIND_SNAP_RADIUS_BLOCKS} whose direction
     * is within {@link SkateConstants#GRIND_APPROACH_MAX_ANGLE_DEGREES} of the player's heading
     * (either way along the edge), or {@code null} if none qualify. Deterministic: ties on
     * distance are broken by comparing the candidates' own endpoints, never by iteration order,
     * list identity or object hash.
     */
    public static GrindPath selectBestCandidate(List<GrindPath> candidates, Vec3 playerPos, double headingDegrees) {
        GrindPath best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (GrindPath candidate : candidates) {
            double distance = candidate.distanceToNearestPoint(playerPos);
            if (distance > SkateConstants.GRIND_SNAP_RADIUS_BLOCKS) {
                continue;
            }
            if (!approachMatches(candidate, playerPos, headingDegrees)) {
                continue;
            }
            if (best == null || compare(distance, candidate, bestDistance, best) < 0) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * +1 if the player's current heading points toward increasing distance-along-chain at
     * {@code distanceAlong} (the point they are acquiring the path at), -1 if it points the
     * other way &mdash; so following starts out continuing the player's own approach direction
     * instead of reversing them onto the rail.
     */
    public static int travelSignAt(GrindPath path, double distanceAlong, double headingDegrees) {
        Vec3 edgeDirection = path.directionAt(distanceAlong);
        Vec3 facing = headingToFlatDirection(headingDegrees);
        double dot = facing.x * edgeDirection.x + facing.z * edgeDirection.z;
        return dot >= 0.0 ? 1 : -1;
    }

    private static boolean approachMatches(GrindPath candidate, Vec3 playerPos, double headingDegrees) {
        double nearestAlong = candidate.nearestDistanceAlong(playerPos);
        Vec3 edgeDirection = candidate.directionAt(nearestAlong);
        double horizontalLengthSqr = edgeDirection.x * edgeDirection.x + edgeDirection.z * edgeDirection.z;
        if (horizontalLengthSqr < 1.0e-9) {
            // A near-vertical edge has no meaningful horizontal approach angle; don't block it.
            return true;
        }
        Vec3 edgeFlat = new Vec3(edgeDirection.x, 0.0, edgeDirection.z).normalize();
        Vec3 facing = headingToFlatDirection(headingDegrees);
        double dot = Math.max(-1.0, Math.min(1.0, Math.abs(facing.dot(edgeFlat))));
        double angleDegrees = Math.toDegrees(Math.acos(dot));
        return angleDegrees <= SkateConstants.GRIND_APPROACH_MAX_ANGLE_DEGREES;
    }

    private static Vec3 headingToFlatDirection(double headingDegrees) {
        double yaw = Math.toRadians(headingDegrees);
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private static int compare(double distanceA, GrindPath a, double distanceB, GrindPath b) {
        int cmp = Double.compare(distanceA, distanceB);
        if (cmp != 0) {
            return cmp;
        }
        cmp = compareVec3(a.start(), b.start());
        if (cmp != 0) {
            return cmp;
        }
        cmp = compareVec3(a.end(), b.end());
        if (cmp != 0) {
            return cmp;
        }
        return Double.compare(a.length(), b.length());
    }

    private static int compareVec3(Vec3 a, Vec3 b) {
        int cmp = Double.compare(a.x, b.x);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Double.compare(a.y, b.y);
        if (cmp != 0) {
            return cmp;
        }
        return Double.compare(a.z, b.z);
    }
}
