package io.github.brooswitminecraft.dynamicskateboards;

import java.util.Objects;

/**
 * Generic "recognized, but not yet actionable" input buffer: pure Java, no Minecraft types, so it
 * is reusable shared machinery &mdash; this story binds it only to the ollie's early-press case,
 * but stories (c)/(e) bind flips, grabs, manuals and grinds on the same mechanism.
 *
 * <p>Contract: {@link #buffer} marks a value as pending for {@code windowTicks} ticks (the press
 * tick itself counts as the first of those ticks). Call {@link #tick()} exactly once per game
 * tick to advance time. {@link #consume()} returns the pending value and clears it if one is
 * still within its window; otherwise it returns {@code null}. An input consumed inside the window
 * is recognized; one whose window has elapsed is dropped silently, the same way a key release
 * outside a recognition window would be.
 */
public final class InputBuffer<T> {
    private final int windowTicks;
    private T pending;
    private int ticksLeft;

    public InputBuffer(int windowTicks) {
        if (windowTicks < 0) {
            throw new IllegalArgumentException("windowTicks must be >= 0");
        }
        this.windowTicks = windowTicks;
    }

    /** Marks {@code value} as pending, recognizable for this buffer's window. */
    public void buffer(T value) {
        this.pending = Objects.requireNonNull(value);
        this.ticksLeft = windowTicks;
    }

    public boolean hasPending() {
        return pending != null;
    }

    /** Advances time by one tick, dropping the pending value once its window has elapsed. */
    public void tick() {
        if (pending == null) {
            return;
        }
        if (ticksLeft <= 0) {
            pending = null;
        } else {
            ticksLeft--;
        }
    }

    /** Returns and clears the pending value if still within its window, else {@code null}. */
    public T consume() {
        T value = pending;
        pending = null;
        return value;
    }
}
