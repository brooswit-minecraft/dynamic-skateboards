package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Review fix (item loss): proves the recovered-board hand-off against a REAL {@link ItemStack}
 * (vanilla {@code Items.STICK} stands in for our own skateboard item &mdash; the real item
 * requires a loaded mod's {@code DeferredHolder} and so can't be constructed headlessly, but the
 * logic under test doesn't care what the item is), with fakes standing in for "a player's
 * inventory" and "drop it in the world" so no running {@code Level}/{@code Player} is needed. See
 * {@link BoardPickupTransfer}'s javadoc for exactly what contract the fakes mimic.
 */
class BoardPickupTransferTest {
    @Test
    void fullyAbsorbedStackIsNeverDropped() {
        ItemStack stack = new ItemStack(Items.STICK);
        boolean[] dropped = {false};

        boolean fullyAdded = BoardPickupTransfer.give(stack, s -> s.setCount(0), s -> dropped[0] = true);

        assertTrue(fullyAdded);
        assertFalse(dropped[0], "a fully absorbed item must never also be dropped");
    }

    @Test
    void rejectedStackIsDroppedInsteadOfLost() {
        ItemStack stack = new ItemStack(Items.STICK);
        ItemStack[] droppedStack = {null};

        // Simulates a full inventory: the receiver takes nothing, stack is untouched.
        boolean fullyAdded = BoardPickupTransfer.give(stack, s -> { }, s -> droppedStack[0] = s);

        assertFalse(fullyAdded);
        assertSame(stack, droppedStack[0], "the exact leftover stack must be the one handed to the dropper");
        assertEquals(1, droppedStack[0].getCount(), "the untaken item must be dropped, not vanish");
        assertTrue(droppedStack[0].is(Items.STICK));
    }

    @Test
    void partialAbsorptionDropsOnlyTheRemainder() {
        ItemStack stack = new ItemStack(Items.STICK, 3);
        ItemStack[] droppedStack = {null};

        boolean fullyAdded = BoardPickupTransfer.give(stack, s -> s.setCount(1), s -> droppedStack[0] = s);

        assertFalse(fullyAdded, "any leftover counts as not fully added");
        assertEquals(1, droppedStack[0].getCount(), "only the leftover must be dropped, the rest was already absorbed");
    }
}
