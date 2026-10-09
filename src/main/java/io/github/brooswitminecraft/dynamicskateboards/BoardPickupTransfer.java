package io.github.brooswitminecraft.dynamicskateboards;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Review fix (item loss on a full inventory): the "give the recovered board back, never let it
 * vanish" control flow pulled out of {@link LooseSkateboardEntity#giveBoardOrDrop} as its own
 * pure, generic seam.
 *
 * <p>This is deliberately generic over {@code T} rather than typed directly to {@code ItemStack}:
 * an earlier version of this class (and its test) used a real {@code ItemStack} plus vanilla
 * {@code Items.STICK} as a stand-in, on the theory that {@code ItemStack}/{@code Items} need
 * neither a level nor an explicit bootstrap to construct. That theory was wrong &mdash; CI caught
 * it: referencing {@code Items.*} outside a running NeoForge test/game environment throws {@code
 * ExceptionInInitializerError}/{@code IllegalArgumentException} during the vanilla registry's own
 * static init, because that init expects machinery (e.g. {@code Bootstrap.bootStrap()}) this
 * headless JUnit run never provides. There's no existing fixture or precedent anywhere in this
 * repo (or any sibling mod repo checked) for constructing {@code ItemStack}/{@code Items} outside
 * a running game, consistent with every other Minecraft-glue class here never getting its own
 * entity/item-level test. So this class proves the control-flow invariant &mdash; "whatever the
 * receiver didn't take gets dropped, nothing is ever silently swallowed" &mdash; against a plain,
 * self-contained {@code T} instead; {@link LooseSkateboardEntity#giveBoardOrDrop} is the thin,
 * untested-but-trivial Minecraft-side glue that wires a real {@code ItemStack} through the exact
 * same shape ({@code Consumer<T>} mutates it, {@code Predicate<T>} reads it back, {@code
 * Consumer<T>} drops the leftover).
 */
final class BoardPickupTransfer {
    private BoardPickupTransfer() {}

    /**
     * Hands {@code stack} to {@code receiver} (which may mutate it down to whatever it couldn't
     * place &mdash; mirroring {@code Inventory#add(ItemStack)}'s own contract). Whatever {@code
     * isEmpty} says is left afterward goes to {@code dropIfAnyLeft} instead of being discarded.
     * Returns {@code true} iff nothing was left to drop (the item was fully absorbed). Never
     * trusts a boolean "did you take anything" return from {@code receiver} &mdash; {@code
     * Inventory#add} returns {@code true} even for a partial add, so only the post-state
     * ({@code isEmpty}) is trustworthy here.
     */
    static <T> boolean give(T stack, Consumer<T> receiver, Predicate<T> isEmpty, Consumer<T> dropIfAnyLeft) {
        receiver.accept(stack);
        if (!isEmpty.test(stack)) {
            dropIfAnyLeft.accept(stack);
            return false;
        }
        return true;
    }
}
