package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

/**
 * Deterministic, headless tests over {@link GrindSession} &mdash; the review fix for
 * MINECRAFT-166: a grind active the tick riding goes GROUNDED outright (board unequipped
 * mid-grind, death/respawn, or any other reason riding ends) must be dropped immediately, not
 * silently resumed the next time a grind is acquired.
 */
class GrindSessionTest {
    private static GrindFollower aFollower() {
        GrindPath path = new GrindPath(List.of(new GrindEdge(new Vec3(0, 0, 0), new Vec3(0, 0, 1))));
        return new GrindFollower(path, 0.0, 1);
    }

    @Test
    void startsInactiveAndAcquirable() {
        GrindSession session = new GrindSession();
        assertFalse(session.isActive());
        assertTrue(session.canAcquire());
    }

    @Test
    void activeGrindSurvivesContinuedRiding() {
        GrindSession session = new GrindSession();
        GrindFollower follower = aFollower();
        session.start(follower);

        session.onRidingStateChanged(true); // still riding (AIRBORNE, SKATING, etc.)

        assertTrue(session.isActive());
        assertSame(follower, session.current());
    }

    @Test
    void goingGroundedMidGrindDropsItImmediately() {
        GrindSession session = new GrindSession();
        session.start(aFollower());

        session.onRidingStateChanged(false); // the reviewed bug: riding ends outright mid-grind

        assertFalse(session.isActive(), "a stale grind must not survive riding going GROUNDED");
        assertNull(session.current());
    }

    @Test
    void droppedGrindStartsFreshAfterTheCooldownElapses() {
        GrindSession session = new GrindSession();
        session.start(aFollower());
        session.onRidingStateChanged(false);

        assertFalse(session.canAcquire(), "immediately after dropping, the reacquire cooldown must be active");
        for (int i = 0; i < SkateConstants.GRIND_REACQUIRE_COOLDOWN_TICKS; i++) {
            session.tickCooldown();
        }
        assertTrue(session.canAcquire(), "a later acquisition must start fresh once the cooldown elapses");

        GrindFollower freshFollower = aFollower();
        session.start(freshFollower);
        assertTrue(session.isActive());
        assertSame(freshFollower, session.current());
    }

    @Test
    void normalReleaseAlsoStartsTheCooldown() {
        GrindSession session = new GrindSession();
        session.start(aFollower());

        session.release();

        assertFalse(session.isActive());
        assertFalse(session.canAcquire());
    }
}
