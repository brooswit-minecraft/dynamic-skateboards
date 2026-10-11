package io.github.brooswitminecraft.dynamicskateboards;

import java.util.List;
import java.util.function.Consumer;

/**
 * Production registration and creative-discovery identities for slope pieces. Keeping this
 * data-free seam independent of NeoForge bootstrap lets the headless suite verify exactly what
 * {@link DynamicSkateboardsMod} registers and emits to Building Blocks.
 */
final class SlopeRegistrationCatalog {
    record Entry(SlopeKind kind, String blockId, String itemId, boolean currentBuildChoice) {}

    private static final List<Entry> ENTRIES = java.util.Arrays.stream(SlopeKind.values())
            .map(kind -> new Entry(kind, kind.blockName(), kind.blockName(), kind.currentBuildChoice()))
            .toList();

    private SlopeRegistrationCatalog() {}

    static List<Entry> entries() {
        return ENTRIES;
    }

    static void forEachCurrentBuildChoice(Consumer<Entry> consumer) {
        ENTRIES.stream().filter(Entry::currentBuildChoice).forEach(consumer);
    }
}
