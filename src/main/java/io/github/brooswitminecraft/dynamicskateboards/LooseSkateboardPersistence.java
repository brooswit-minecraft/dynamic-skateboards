package io.github.brooswitminecraft.dynamicskateboards;

import org.joml.Quaternionf;

import net.minecraft.nbt.CompoundTag;

/**
 * NBT read/write for the loose board's own extra save data (its physics orientation), kept
 * separate from {@link LooseSkateboardEntity} so the chunk-unload/reload persistence contract is
 * unit-testable without a running level &mdash; {@link CompoundTag} is a plain data structure, so
 * a round trip through this class alone proves the orientation survives serialization bit for
 * bit. Position, UUID and velocity ride on vanilla {@code Entity} NBT and are not this class's
 * concern.
 */
public final class LooseSkateboardPersistence {
    private LooseSkateboardPersistence() {}

    private static final String KEY_X = "OrientationX";
    private static final String KEY_Y = "OrientationY";
    private static final String KEY_Z = "OrientationZ";
    private static final String KEY_W = "OrientationW";

    public static void write(CompoundTag tag, Quaternionf orientation) {
        tag.putFloat(KEY_X, orientation.x);
        tag.putFloat(KEY_Y, orientation.y);
        tag.putFloat(KEY_Z, orientation.z);
        tag.putFloat(KEY_W, orientation.w);
    }

    /** Returns the saved orientation, or {@code null} if {@code tag} never had one written. */
    public static Quaternionf read(CompoundTag tag) {
        if (!tag.contains(KEY_W)) {
            return null;
        }
        return new Quaternionf(tag.getFloat(KEY_X), tag.getFloat(KEY_Y), tag.getFloat(KEY_Z), tag.getFloat(KEY_W));
    }
}
