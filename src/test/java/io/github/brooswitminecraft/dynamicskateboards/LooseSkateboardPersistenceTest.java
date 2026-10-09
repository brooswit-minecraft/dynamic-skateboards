package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * Headless proof that the loose board's orientation AND position survive a chunk unload/reload:
 * a save followed by a load (via plain {@link CompoundTag}, no running level needed) must
 * reproduce both bit for bit. Position is carried explicitly through this same pure seam (review
 * fix) because vanilla {@code Entity}'s own {@code Pos}-tag round trip, while real, is not
 * exercisable in this headless suite without a running {@code Level} &mdash; see {@link
 * LooseSkateboardPersistence}'s javadoc for why.
 */
class LooseSkateboardPersistenceTest {
    @Test
    void orientationSurvivesASaveLoadRoundTrip() {
        Quaternionf tumbling = new Quaternionf(0.1f, 0.2f, 0.3f, 0.9273f);
        CompoundTag tag = new CompoundTag();
        LooseSkateboardPersistence.write(tag, tumbling);

        Quaternionf restored = LooseSkateboardPersistence.read(tag);

        assertEquals(tumbling.x, restored.x, 1e-6f);
        assertEquals(tumbling.y, restored.y, 1e-6f);
        assertEquals(tumbling.z, restored.z, 1e-6f);
        assertEquals(tumbling.w, restored.w, 1e-6f);
    }

    @Test
    void missingOrientationReadsAsNull() {
        assertNull(LooseSkateboardPersistence.read(new CompoundTag()), "a tag that never had an orientation written must read back null, not a default");
    }

    @Test
    void positionSurvivesASaveLoadRoundTrip() {
        Vec3 where = new Vec3(123.5, 64.0, -987.25);
        CompoundTag tag = new CompoundTag();
        LooseSkateboardPersistence.writePosition(tag, where);

        // Simulate the unload/reload boundary: nothing survives except the tag itself.
        CompoundTag reloaded = tag.copy();
        Vec3 restored = LooseSkateboardPersistence.readPosition(reloaded);

        assertEquals(where.x, restored.x, 1e-9);
        assertEquals(where.y, restored.y, 1e-9);
        assertEquals(where.z, restored.z, 1e-9);
    }

    @Test
    void missingPositionReadsAsNull() {
        assertNull(LooseSkateboardPersistence.readPosition(new CompoundTag()), "a tag that never had a position written must read back null, not a default");
    }
}
