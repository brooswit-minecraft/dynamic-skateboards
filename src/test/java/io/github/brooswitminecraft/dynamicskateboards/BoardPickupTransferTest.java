package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Review fix (item loss): proves {@link BoardPickupTransfer}'s never-lose-it control flow using a
 * plain, self-contained stand-in ({@link CountedStack}) for "a stack of items" &mdash; no real
 * {@code ItemStack} here, see {@link BoardPickupTransfer}'s javadoc for exactly why that was
 * tried first and reverted (it threw {@code ExceptionInInitializerError} in CI: {@code
 * Items.STICK} isn't safely referenceable outside a running game). The fakes passed in for
 * "inventory" and "drop it in the world" mimic the one contract that matters about {@code
 * Inventory#add(ItemStack)}: mutate the stack down to the leftover, and never trust its own
 * boolean return.
 */
class BoardPickupTransferTest {
    /** Minimal stand-in for "a stack of items": just a mutable count, no Minecraft type at all. */
    private static final class CountedStack {
        int count;

        CountedStack(int count) {
            this.count = count;
        }

        boolean isEmptyNow() {
            return count <= 0;
        }
    }

    @Test
    void fullyAbsorbedStackIsNeverDropped() {
        CountedStack stack = new CountedStack(1);
        boolean[] dropped = {false};

        boolean fullyAdded = BoardPickupTransfer.give(stack, s -> s.count = 0, CountedStack::isEmptyNow, s -> dropped[0] = true);

        assertTrue(fullyAdded);
        assertFalse(dropped[0], "a fully absorbed item must never also be dropped");
    }

    @Test
    void rejectedStackIsDroppedInsteadOfLost() {
        CountedStack stack = new CountedStack(1);
        CountedStack[] droppedStack = {null};

        // Simulates a full inventory: the receiver takes nothing, stack is untouched.
        boolean fullyAdded = BoardPickupTransfer.give(stack, s -> { }, CountedStack::isEmptyNow, s -> droppedStack[0] = s);

        assertFalse(fullyAdded);
        assertSame(stack, droppedStack[0], "the exact leftover stack must be the one handed to the dropper");
        assertEquals(1, droppedStack[0].count, "the untaken item must be dropped, not vanish");
    }

    @Test
    void partialAbsorptionDropsOnlyTheRemainder() {
        CountedStack stack = new CountedStack(3);
        CountedStack[] droppedStack = {null};

        boolean fullyAdded = BoardPickupTransfer.give(stack, s -> s.count = 1, CountedStack::isEmptyNow, s -> droppedStack[0] = s);

        assertFalse(fullyAdded, "any leftover counts as not fully added");
        assertEquals(1, droppedStack[0].count, "only the leftover must be dropped, the rest was already absorbed");
    }
}
