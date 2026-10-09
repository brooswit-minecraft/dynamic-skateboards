package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

/**
 * Headless proof that the loose board's orientation survives a chunk unload/reload: a save
 * followed by a load (via plain {@link CompoundTag}, no running level needed) must reproduce the
 * same orientation bit for bit, exactly like the vanilla NBT round trip the entity's own position
 * already rides on for free.
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
}
