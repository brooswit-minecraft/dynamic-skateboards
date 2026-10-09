package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SkateSyncPolicyTest {
    private static SkateInput held(boolean board, boolean jump, boolean onGround) {
        return new SkateInput(board, jump, onGround, 0.0, 0.0);
    }

    @Test
    void lateJoiningObserverOfAlreadySkatingPlayerGetsThatState() {
        SkateSyncPolicy policy = new SkateSyncPolicy();
        UUID skater = UUID.randomUUID();

        // Skater mounts the board before the observer ever starts tracking them.
        policy.controllerFor(skater).update(held(true, false, true));

        assertEquals(SkateState.SKATING, policy.stateToSendOnStartTracking(skater));
    }

    @Test
    void startTrackingAGroundedPlayerSendsGroundedUnconditionally() {
        // The fix the review required: this must not be gated on "only if skating".
        SkateSyncPolicy policy = new SkateSyncPolicy();
        UUID player = UUID.randomUUID();

        assertEquals(SkateState.GROUNDED, policy.stateToSendOnStartTracking(player));
    }

    @Test
    void observerReacquiredAfterTargetWentBackToGroundedUnseenIsNotLeftStale() {
        // Start-tracking must reflect CURRENT state, not "skating at some point", so an observer
        // who stopped tracking while skating and reacquires after the target went back to
        // GROUNDED (unseen) is told GROUNDED, not left with a stale skating assumption.
        SkateSyncPolicy policy = new SkateSyncPolicy();
        UUID skater = UUID.randomUUID();
        SkateController controller = policy.controllerFor(skater);
        controller.update(held(true, false, true)); // SKATING
        controller.update(held(false, false, true)); // back to GROUNDED, unseen by the observer

        assertEquals(SkateState.GROUNDED, policy.stateToSendOnStartTracking(skater));
    }

    @Test
    void logoutOfNonGroundedPlayerYieldsGroundedBroadcastAndRemovesController() {
        SkateSyncPolicy policy = new SkateSyncPolicy();
        UUID skater = UUID.randomUUID();
        policy.controllerFor(skater).update(held(true, false, true)); // SKATING

        SkateState broadcast = policy.onLogout(skater);

        assertEquals(SkateState.GROUNDED, broadcast, "logging out while skating must broadcast GROUNDED");
        assertFalse(policy.controllers().containsKey(skater), "the controller must be removed on logout");
    }

    @Test
    void logoutOfAlreadyGroundedPlayerSendsNoRedundantBroadcast() {
        SkateSyncPolicy policy = new SkateSyncPolicy();
        UUID player = UUID.randomUUID();
        policy.controllerFor(player); // never mounted; stays GROUNDED

        assertNull(policy.onLogout(player), "no broadcast needed if nothing ever saw them as skating");
    }

    @Test
    void rejoinWithoutBoardStaysGroundedEverywhere() {
        // Case 2's full lifecycle: skate, log out (broadcasts GROUNDED), rejoin without the board.
        SkateSyncPolicy policy = new SkateSyncPolicy();
        UUID player = UUID.randomUUID();
        policy.controllerFor(player).update(held(true, false, true)); // SKATING
        policy.onLogout(player);

        // Rejoin: a fresh controller is created, starting GROUNDED, board not in hand.
        SkateController rejoined = policy.controllerFor(player);
        rejoined.update(held(false, false, true));

        assertTrue(rejoined.state() == SkateState.GROUNDED);
        assertEquals(SkateState.GROUNDED, policy.stateToSendOnStartTracking(player));
    }
}
