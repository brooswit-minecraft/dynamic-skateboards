package io.github.brooswitminecraft.dynamicskateboards;

import net.minecraft.world.phys.Vec3;

/**
 * Pure per-tick following/release state machine over one acquired {@link GrindPath}: no
 * Minecraft entity/world types, so it is unit-tested headlessly like {@link SkateController}.
 * {@link WorldGrindSeam} owns one of these per active grind and turns its output into actual
 * entity movement.
 *
 * <p>Release (documented per the ticket's "your call which, document it"): any of (a) running off
 * either end of the path, (b) Shift being released, or (c) Jump being pressed (an intentional
 * jump off the rail) ends the grind. All three simply stop the override here and hand back
 * whatever position/heading the skater was at &mdash; {@code SkateController} itself was never
 * touched, so the very next tick resolves AIRBORNE/landing exactly as it would for any other
 * falling player (story (b)'s valid riding states), including running off the end of an edge.
 */
public final class GrindFollower {
    private final GrindPath path;
    private final int travelSign;
    private double progress;

    public GrindFollower(GrindPath path, double startProgress, int travelSign) {
        this.path = path;
        this.travelSign = travelSign == 0 ? 1 : Integer.signum(travelSign);
        this.progress = Math.max(0.0, Math.min(path.length(), startProgress));
    }

    public record Step(Vec3 position, double headingDegrees, boolean released) {}

    /** Advances one tick by {@code speedBlocksPerTick}, or releases immediately per the rules above. */
    public Step advance(double speedBlocksPerTick, boolean shiftHeld, boolean jumpPressed) {
        if (!shiftHeld || jumpPressed) {
            return new Step(path.pointAt(progress), headingAt(progress), true);
        }
        double next = progress + travelSign * speedBlocksPerTick;
        boolean ranOffEnd = next < 0.0 || next > path.length();
        progress = Math.max(0.0, Math.min(path.length(), next));
        return new Step(path.pointAt(progress), headingAt(progress), ranOffEnd);
    }

    public double progress() {
        return progress;
    }

    public GrindPath path() {
        return path;
    }

    private double headingAt(double distanceAlong) {
        double sampleAt = Math.max(0.0, Math.min(path.length() - 1.0e-6, distanceAlong));
        Vec3 direction = path.directionAt(sampleAt).scale(travelSign);
        return Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }
}
