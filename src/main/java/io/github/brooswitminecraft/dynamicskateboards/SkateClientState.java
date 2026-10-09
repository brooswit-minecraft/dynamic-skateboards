package io.github.brooswitminecraft.dynamicskateboards;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of the server-authoritative skate animation state, keyed by player UUID.
 * Populated only from {@code SkatingStatePayload}; never written to from client-side guesswork.
 *
 * <p>This mirror can only be as fresh as the last packet the server chose to send. It is NOT
 * "desync impossible by construction" &mdash; that would require either an absolute state on
 * every tick (bandwidth nobody wants) or a transport guarantee this mod doesn't have. What it
 * actually relies on, see {@code SkatingStatePayload}'s own javadoc: send on every transition,
 * plus explicit sends on start-tracking (so a late-joining observer isn't stuck never hearing
 * about an already-skating player) and on logout (so no tracker is left holding a stale
 * non-GROUNDED entry once the server stops evaluating that player). Absent those two extra
 * sends, both gaps were real; with them, every path that changes who-sees-what now carries a
 * packet.
 */
public final class SkateClientState {
    private static final Map<UUID, SkateState> STATES = new ConcurrentHashMap<>();

    private SkateClientState() {}

    public static void set(UUID player, SkateState state) {
        if (state == SkateState.GROUNDED) {
            STATES.remove(player);
        } else {
            STATES.put(player, state);
        }
    }

    public static SkateState state(UUID player) {
        return STATES.getOrDefault(player, SkateState.GROUNDED);
    }

    public static boolean isSkating(UUID player) {
        return state(player) != SkateState.GROUNDED;
    }
}
