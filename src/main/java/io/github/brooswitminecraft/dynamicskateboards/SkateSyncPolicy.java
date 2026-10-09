package io.github.brooswitminecraft.dynamicskateboards;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure Java sync-decision policy: no Minecraft types, so the two sync-gap fixes are unit-tested
 * without a running game. {@code DynamicSkateboardsMod}'s event handlers only read/write a
 * {@link SkateController} map and otherwise delegate every decision here.
 *
 * <p>Design chosen: send-on-transition (handled elsewhere, per tick) plus two explicit sends that
 * close the gaps a transition-only broadcast leaves:
 * <ul>
 *   <li>{@link #stateToSendOnStartTracking} &mdash; a client that starts tracking a player must
 *       learn that player's CURRENT state unconditionally, including GROUNDED. Gating this on
 *       "only if skating" (as the first version of this fix did) left a hole: an observer who
 *       stops tracking while the target is skating, outlives that skate session unseen, then
 *       starts tracking again, would never be told the target went back to GROUNDED and would
 *       render them skating forever.
 *   <li>{@link #onLogout} &mdash; a logging-out player's trackers must be told GROUNDED (if they
 *       weren't already) before the server stops evaluating that player, so no tracker is left
 *       holding a stale non-GROUNDED entry.
 * </ul>
 */
public final class SkateSyncPolicy {
    private final Map<UUID, SkateController> controllers;

    public SkateSyncPolicy(Map<UUID, SkateController> controllers) {
        this.controllers = controllers;
    }

    public SkateSyncPolicy() {
        this(new ConcurrentHashMap<>());
    }

    public Map<UUID, SkateController> controllers() {
        return controllers;
    }

    public SkateController controllerFor(UUID player) {
        return controllers.computeIfAbsent(player, id -> new SkateController());
    }

    /**
     * Same as {@link #controllerFor(UUID)}, but a freshly-created controller is wired to
     * {@code grindSeam} (story (e)) instead of {@link GrindSeam#NONE}. {@code grindSeam} is
     * ignored if a controller for {@code player} already exists &mdash; the seam a controller
     * uses is fixed at its own construction, same as any other constructor argument.
     */
    public SkateController controllerFor(UUID player, GrindSeam grindSeam) {
        return controllers.computeIfAbsent(player, id -> new SkateController(grindSeam));
    }

    /** The state to unconditionally send to a client that just started tracking {@code target}. */
    public SkateState stateToSendOnStartTracking(UUID target) {
        SkateController controller = controllers.get(target);
        return controller != null ? controller.state() : SkateState.GROUNDED;
    }

    /**
     * Removes {@code player}'s controller and returns the GROUNDED broadcast to send to their
     * trackers if (and only if) they weren't already GROUNDED &mdash; {@code null} otherwise.
     */
    public SkateState onLogout(UUID player) {
        SkateController controller = controllers.remove(player);
        if (controller != null && controller.state() != SkateState.GROUNDED) {
            return SkateState.GROUNDED;
        }
        return null;
    }
}
