package io.github.brooswitminecraft.dynamicskateboards;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of the server-authoritative skating state, keyed by player UUID. Populated
 * only from {@link SkatingStatePayload}; never written to from client-side guesswork, so a
 * desynced render is impossible by construction. Read by the renderer to decide whether to apply
 * the skate stance.
 */
public final class SkateClientState {
    private static final Map<UUID, Boolean> SKATING = new ConcurrentHashMap<>();

    private SkateClientState() {}

    public static void set(UUID player, boolean skating) {
        if (skating) {
            SKATING.put(player, Boolean.TRUE);
        } else {
            SKATING.remove(player);
        }
    }

    public static boolean isSkating(UUID player) {
        return SKATING.getOrDefault(player, Boolean.FALSE);
    }
}
