package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/** Resource-level guard for world/item-compatible slope IDs and their per-facing render/data assets. */
class SlopeResourceCompatibilityTest {
    private static final Set<String> LEGACY_SAFE_IDS = Set.of(
            "shallow_slope", "raised_shallow_slope", "regular_slope", "steep_slope", "raised_steep_slope",
            "shallow_slope_inverted", "raised_shallow_slope_inverted", "regular_slope_inverted",
            "steep_slope_inverted", "raised_steep_slope_inverted");
    private static final Set<String> LEGACY_ONLY_IDS = Set.of(
            "regular_slope", "regular_slope_inverted", "raised_steep_slope", "raised_steep_slope_inverted");

    @Test
    void everyPreservedSlopeIdHasBlockstateModelsItemsAndLegacyData() {
        Set<String> actualIds = Arrays.stream(SlopeKind.values()).map(SlopeKind::blockName).collect(Collectors.toSet());
        assertEquals(LEGACY_SAFE_IDS, actualIds, "world/item registry names must remain stable");

        for (String id : LEGACY_SAFE_IDS) {
            String blockstate = readResource("assets/dynamicskateboards/blockstates/" + id + ".json");
            assertResource("assets/dynamicskateboards/models/item/" + id + ".json");
            assertResource("data/dynamicskateboards/loot_table/blocks/" + id + ".json");
            if (LEGACY_ONLY_IDS.contains(id)) {
                assertNoResource("data/dynamicskateboards/recipe/" + id + ".json");
            } else {
                assertResource("data/dynamicskateboards/recipe/" + id + ".json");
            }
            for (String facing : new String[] {"north", "south", "east", "west"}) {
                assertResource("assets/dynamicskateboards/models/block/" + id + "_" + facing + ".json");
                assertTrue(blockstate.contains("dynamicskateboards:block/" + id + "_" + facing),
                        "blockstate must select the matching " + facing + " model for " + id);
            }
        }
    }

    @Test
    void regularAndRaisedSteepRemainLegacyOnlyProfiles() {
        assertEquals(6, SlopeKind.REGULAR_SLOPE.riseVoxels());
        assertEquals(8, SlopeKind.RAISED_STEEP_SLOPE.entryHeight());
        assertEquals(16, SlopeKind.RAISED_STEEP_SLOPE.exitHeight());
        assertTrue(SlopeKind.STEEP_SLOPE.riseVoxels() == 16);
        for (SlopeKind kind : SlopeKind.values()) {
            assertEquals(!LEGACY_ONLY_IDS.contains(kind.blockName()), kind.currentBuildChoice(),
                    kind + " must be hidden from current creative discovery exactly when legacy-only");
        }
    }

    @Test
    void legacyAndCurrentSlopeIdsAreActuallyRegistered() {
        for (SlopeKind kind : SlopeKind.values()) {
            String id = kind.blockName();
            assertEquals(id, DynamicSkateboardsMod.SLOPES.get(kind).getId().getPath(),
                    "the registered block ID must preserve " + id);
            assertEquals(id, DynamicSkateboardsMod.BLOCK_ITEMS.get(id).getId().getPath(),
                    "the registered item ID must preserve " + id);
        }
    }

    @Test
    void productionCreativeOutputShowsCurrentChoicesButNotLegacyOnlyItems() {
        List<String> output = new ArrayList<>();
        DynamicSkateboardsMod.addCurrentBuildChoices(item -> output.add(item.getId().getPath()));

        for (SlopeKind kind : SlopeKind.values()) {
            assertEquals(kind.currentBuildChoice(), output.contains(kind.blockName()),
                    "production creative output must match the current-choice policy for " + kind);
        }
        assertTrue(output.contains("curved_step"));
        assertTrue(output.contains("curved_wall"));
        assertTrue(output.size() < DynamicSkateboardsMod.BLOCK_ITEMS.size(),
                "creative output must not re-expose every registered block item");
    }

    private static void assertResource(String path) {
        readResource(path);
    }

    private static void assertNoResource(String path) {
        try (InputStream stream = SlopeResourceCompatibilityTest.class.getClassLoader().getResourceAsStream(path)) {
            assertTrue(stream == null, "legacy-only profile must not retain a current recipe: " + path);
        } catch (Exception e) {
            throw new AssertionError("could not inspect recipe resource: " + path, e);
        }
    }

    private static String readResource(String path) {
        try (InputStream stream = SlopeResourceCompatibilityTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "missing compatibility resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("could not read compatibility resource: " + path, e);
        }
    }
}
