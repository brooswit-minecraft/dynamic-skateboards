package io.github.brooswitminecraft.dynamicskateboards;

import org.joml.Quaternionf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * NBT read/write for the loose board's own extra save data, kept separate from {@link
 * LooseSkateboardEntity} so the chunk-unload/reload persistence contract is unit-testable without
 * a running level &mdash; {@link CompoundTag} is a plain data structure, so a round trip through
 * this class alone proves orientation (and, see below, position) survive serialization bit for
 * bit. UUID and velocity ride on vanilla {@code Entity} NBT alone and are not this class's
 * concern.
 *
 * <p>Review fix (position persistence coverage): vanilla {@code Entity#saveWithoutId}/{@code
 * #load} already carry position for free via the standard {@code Pos} tag &mdash; that was never
 * the gap. The gap was that nothing <em>tested</em> it, and {@code Entity#saveWithoutId}/{@code
 * #load} aren't exercisable here without a running {@code Level} (they call {@code
 * registryAccess()}, which NPEs off a level-less entity) &mdash; this repo has no fixture for that
 * and no other entity test in it attempts one (see {@code LooseSkateboardPersistenceTest}'s
 * javadoc). So position is additionally &mdash; redundantly with vanilla, intentionally &mdash;
 * carried through this same pure-{@code CompoundTag} seam, giving the existing tested-without-a-
 * Level pattern something concrete to assert survives a round trip. {@link
 * LooseSkateboardEntity#readAdditionalSaveData} applies it defensively on top of whatever vanilla
 * already restored; the two are expected to always agree.
 */
public final class LooseSkateboardPersistence {
    private LooseSkateboardPersistence() {}

    private static final String KEY_X = "OrientationX";
    private static final String KEY_Y = "OrientationY";
    private static final String KEY_Z = "OrientationZ";
    private static final String KEY_W = "OrientationW";

    private static final String KEY_POS_X = "ExplicitPositionX";
    private static final String KEY_POS_Y = "ExplicitPositionY";
    private static final String KEY_POS_Z = "ExplicitPositionZ";

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

    public static void writePosition(CompoundTag tag, Vec3 position) {
        tag.putDouble(KEY_POS_X, position.x);
        tag.putDouble(KEY_POS_Y, position.y);
        tag.putDouble(KEY_POS_Z, position.z);
    }

    /** Returns the saved position, or {@code null} if {@code tag} never had one written. */
    public static Vec3 readPosition(CompoundTag tag) {
        if (!tag.contains(KEY_POS_X)) {
            return null;
        }
        return new Vec3(tag.getDouble(KEY_POS_X), tag.getDouble(KEY_POS_Y), tag.getDouble(KEY_POS_Z));
    }
}
