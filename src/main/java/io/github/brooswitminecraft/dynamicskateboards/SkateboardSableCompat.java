package io.github.brooswitminecraft.dynamicskateboards;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/**
 * The only door to Sable for the loose skateboard. Sable is an optional (compile-only) runtime
 * dependency, so nothing outside this class may mention a Sable type &mdash; the body is passed
 * around as {@code Object}, mirroring dynamic-vehicles' own {@code SableCompat}/{@code
 * SableCarBody} split; the real work lives in {@link SkateboardSableBody}, loaded only after
 * {@link #usable()} says Sable is present. This is a fresh, local implementation written against
 * the same Sable API dynamic-vehicles uses &mdash; it shares no code or class with that mod and
 * adds no dependency on it.
 */
final class SkateboardSableCompat {
    private SkateboardSableCompat() {}

    static boolean usable() {
        return ModList.get().isLoaded("sable");
    }

    static Object create(ServerLevel level, LooseSkateboardEntity board) {
        return SkateboardSableBody.create(level, board);
    }

    /** Drives one tick of passive (no-propulsion) wheel-contact physics, then syncs the entity to the body's pose. */
    static void tick(Object body, LooseSkateboardEntity board, double dt) {
        ((SkateboardSableBody) body).tick(board, dt);
    }

    static org.joml.Quaternionf orientation(Object body) {
        return ((SkateboardSableBody) body).orientationF();
    }

    static double speed(Object body) {
        return ((SkateboardSableBody) body).speed();
    }

    static void remove(Object body) {
        ((SkateboardSableBody) body).remove();
    }
}
