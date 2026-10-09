package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InputBufferTest {
    @Test
    void negativeWindowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new InputBuffer<Boolean>(-1));
    }

    @Test
    void emptyBufferHasNoPending() {
        InputBuffer<String> buffer = new InputBuffer<>(3);
        assertFalse(buffer.hasPending());
        assertNull(buffer.consume());
    }

    @Test
    void consumingImmediatelyAfterPressSucceedsEvenWithZeroWindow() {
        InputBuffer<String> buffer = new InputBuffer<>(0);
        buffer.buffer("jump");
        assertEquals("jump", buffer.consume());
    }

    @Test
    void acceptsConsumeWithinWindow() {
        InputBuffer<String> buffer = new InputBuffer<>(3);
        buffer.buffer("jump");
        buffer.tick();
        buffer.tick();
        buffer.tick();
        assertTrue(buffer.hasPending());
        assertEquals("jump", buffer.consume());
    }

    @Test
    void dropsOutOfWindowInput() {
        InputBuffer<String> buffer = new InputBuffer<>(3);
        buffer.buffer("jump");
        buffer.tick();
        buffer.tick();
        buffer.tick();
        buffer.tick(); // one tick past the window
        assertFalse(buffer.hasPending());
        assertNull(buffer.consume());
    }

    @Test
    void consumeClearsPendingSoItCannotBeRecognizedTwice() {
        InputBuffer<String> buffer = new InputBuffer<>(5);
        buffer.buffer("jump");
        assertEquals("jump", buffer.consume());
        assertNull(buffer.consume());
    }

    @Test
    void reBufferingResetsTheWindow() {
        InputBuffer<String> buffer = new InputBuffer<>(2);
        buffer.buffer("jump");
        buffer.tick();
        buffer.tick();
        buffer.buffer("jump"); // re-pressed right as the old one was about to expire
        buffer.tick();
        buffer.tick();
        assertTrue(buffer.hasPending(), "re-buffering must restart the window");
    }
}
