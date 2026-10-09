package io.github.brooswitminecraft.dynamicskateboards;

import net.minecraft.world.item.Item;

/**
 * The skateboard item itself. It carries no behavior of its own: whether holding one means
 * skating is decided server-side, once per tick, by {@link DynamicSkateboardsMod}'s per-player
 * {@link SkateController} looking at the main hand — see that class for why.
 */
public class SkateboardItem extends Item {
    public SkateboardItem(Properties properties) {
        super(properties);
    }
}
