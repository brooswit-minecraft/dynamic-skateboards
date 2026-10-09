package io.github.brooswitminecraft.dynamicskateboards;

import java.util.function.Consumer;

import net.minecraft.world.item.ItemStack;

/**
 * Review fix (item loss on a full inventory): the "give the recovered board back, never let it
 * vanish" decision pulled out of {@link LooseSkateboardEntity#giveBoardOrDrop} as its own pure(ish)
 * seam, so it is unit-testable against a real {@link ItemStack} without a running {@code Level}/
 * {@code Player} &mdash; only {@code ItemStack} itself is a Minecraft type here, and it needs
 * neither a level nor a registry bootstrap to construct or mutate.
 *
 * <p>{@code receiver} mirrors the one contract that matters about {@code
 * net.minecraft.world.entity.player.Inventory#add(ItemStack)}: it may mutate {@code stack} down
 * to whatever it couldn't place (a full inventory leaves it non-empty; anything else leaves it
 * empty), and this class decides what happens next purely from the resulting stack state &mdash;
 * never from {@code Inventory#add}'s own boolean return value, which (per its own contract) is
 * {@code true} even for a partial add. The real call site ({@code giveBoardOrDrop}) wires the
 * actual {@code Inventory#add} and {@code Player#drop} in as {@code receiver}/{@code
 * dropIfAnyLeft}; this class's tests wire in fakes that simulate "inventory full", "inventory has
 * room" and "inventory has partial room" without touching either.
 */
final class BoardPickupTransfer {
    private BoardPickupTransfer() {}

    /**
     * Hands {@code stack} to {@code receiver}; whatever it didn't take goes to {@code
     * dropIfAnyLeft} instead of being discarded. Returns {@code true} iff nothing was left to
     * drop (the item was fully absorbed).
     */
    static boolean give(ItemStack stack, Consumer<ItemStack> receiver, Consumer<ItemStack> dropIfAnyLeft) {
        receiver.accept(stack);
        if (!stack.isEmpty()) {
            dropIfAnyLeft.accept(stack);
            return false;
        }
        return true;
    }
}
