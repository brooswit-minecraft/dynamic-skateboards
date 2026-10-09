package io.github.brooswitminecraft.dynamicskateboards;

import java.util.EnumMap;
import java.util.Map;

/**
 * THE initial directional trick table (story (c)'s own DATA/CONFIG, same role as
 * {@link SkateConstants} plays for tuning numbers): the ONE place direction-&gt;trick is decided
 * for both families, so story (g) can revise names/mappings after playtest without touching
 * {@link SkateController}'s dispatch logic. {@link #flipFor} and {@link #grabFor} are pure
 * functions over this data, every {@link TrickDirection} has an entry in both families, and
 * neither ever returns {@code null}.
 *
 * <p>Shipped as of this story:
 * <pre>
 *   direction  | flip (left click)   | grab (right click, held)
 *   -----------+---------------------+--------------------------
 *   NEUTRAL    | KICKFLIP            | INDY
 *   FORWARD(W) | POP_SHUVIT          | NOSE
 *   BACK(S)    | FAKIE_FLIP          | TAIL
 *   LEFT(A)    | HEELFLIP            | MUTE
 *   RIGHT(D)   | VARIAL_KICKFLIP     | CRAIL
 * </pre>
 */
public final class TrickTable {
    private static final Map<TrickDirection, FlipTrick> FLIPS = buildFlips();
    private static final Map<TrickDirection, GrabTrick> GRABS = buildGrabs();

    private TrickTable() {}

    private static Map<TrickDirection, FlipTrick> buildFlips() {
        Map<TrickDirection, FlipTrick> table = new EnumMap<>(TrickDirection.class);
        table.put(TrickDirection.NEUTRAL, FlipTrick.KICKFLIP);
        table.put(TrickDirection.FORWARD, FlipTrick.POP_SHUVIT);
        table.put(TrickDirection.BACK, FlipTrick.FAKIE_FLIP);
        table.put(TrickDirection.LEFT, FlipTrick.HEELFLIP);
        table.put(TrickDirection.RIGHT, FlipTrick.VARIAL_KICKFLIP);
        return Map.copyOf(table);
    }

    private static Map<TrickDirection, GrabTrick> buildGrabs() {
        Map<TrickDirection, GrabTrick> table = new EnumMap<>(TrickDirection.class);
        table.put(TrickDirection.NEUTRAL, GrabTrick.INDY);
        table.put(TrickDirection.FORWARD, GrabTrick.NOSE);
        table.put(TrickDirection.BACK, GrabTrick.TAIL);
        table.put(TrickDirection.LEFT, GrabTrick.MUTE);
        table.put(TrickDirection.RIGHT, GrabTrick.CRAIL);
        return Map.copyOf(table);
    }

    /** The flip trick selected by left click + {@code direction}. Never {@code null}. */
    public static FlipTrick flipFor(TrickDirection direction) {
        return FLIPS.get(direction);
    }

    /** The grab selected by right click + {@code direction}. Never {@code null}. */
    public static GrabTrick grabFor(TrickDirection direction) {
        return GRABS.get(direction);
    }
}
