package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

/**
 * Deterministic, headless tests over {@link GrindFollower}'s release rules (MINECRAFT-98: "release
 * from a grind returns to a valid riding state in every path you implement, including running off
 * the end of an edge"). No Minecraft entity/world types are involved, matching
 * {@link SkateController}'s own pure-core testing style.
 */
class GrindFollowerTest {
    private static GrindPath straightPath() {
        return new GrindPath(List.of(new GrindEdge(new Vec3(0, 0, 0), new Vec3(0, 0, 1))));
    }

    @Test
    void releasesWhenShiftIsNoLongerHeld() {
        GrindFollower follower = new GrindFollower(straightPath(), 0.0, 1);
        GrindFollower.Step step = follower.advance(0.1, false, false);
        assertTrue(step.released(), "releasing Shift must release the grind");
    }

    @Test
    void releasesOnJumpEvenIfShiftIsStillHeld() {
        GrindFollower follower = new GrindFollower(straightPath(), 0.0, 1);
        GrindFollower.Step step = follower.advance(0.1, true, true);
        assertTrue(step.released(), "pressing Jump must release the grind (jump off)");
    }

    @Test
    void runningOffTheEndOfTheEdgeReleases() {
        GrindPath path = straightPath();
        GrindFollower follower = new GrindFollower(path, 0.0, 1);
        GrindFollower.Step step = follower.advance(path.length() + 1.0, true, false);
        assertTrue(step.released(), "running off the end of the edge must release the grind");
    }

    @Test
    void runningOffTheStartOfTheEdgeReleases() {
        GrindPath path = straightPath();
        GrindFollower follower = new GrindFollower(path, 0.0, -1);
        GrindFollower.Step step = follower.advance(path.length() + 1.0, true, false);
        assertTrue(step.released(), "running off the start of the edge (traveling backward) must release too");
    }

    @Test
    void normalAdvanceWhileHeldDoesNotRelease() {
        GrindPath path = straightPath();
        GrindFollower follower = new GrindFollower(path, 0.0, 1);
        GrindFollower.Step step = follower.advance(0.05, true, false);
        assertFalse(step.released(), "a normal tick well within the edge must not release");
        assertEquals(0.05, follower.progress(), 1.0e-9);
    }

    @Test
    void releasedStepStillReportsAWellDefinedPosition() {
        GrindPath path = straightPath();
        GrindFollower follower = new GrindFollower(path, 0.2, 1);
        GrindFollower.Step step = follower.advance(0.1, false, false);
        // "Returns to a valid riding state" at this pure layer means: a finite, path-bound
        // position/heading the caller can safely hand to normal physics next tick.
        assertTrue(Double.isFinite(step.position().x) && Double.isFinite(step.position().y) && Double.isFinite(step.position().z));
        assertTrue(Double.isFinite(step.headingDegrees()));
    }
}
