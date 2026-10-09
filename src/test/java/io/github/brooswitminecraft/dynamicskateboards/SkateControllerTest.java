package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkateControllerTest {
    @Test
    void mainHandEntersSkating() {
        SkateController c = new SkateController();
        assertEquals(SkateState.SKATING, c.update(true));
        assertTrue(c.isSkating());
    }

    @Test
    void offHandIsNotSkating() {
        SkateController c = new SkateController();
        c.update(true);
        // Moving the board to the off hand means it is no longer the main-hand item;
        // the caller always reports "is the skateboard the main-hand item", so this is false.
        SkateState state = c.update(false);
        assertEquals(SkateState.GROUNDED, state, "off hand must not be skating");
        assertFalse(c.isSkating(), "off hand must not be skating");
    }

    @Test
    void switchingAwayExitsSkating() {
        SkateController c = new SkateController();
        c.update(true);
        c.update(false);
        assertEquals(SkateState.GROUNDED, c.state());
        assertFalse(c.isSkating());
    }

    @Test
    void reselectingResumesSkating() {
        SkateController c = new SkateController();
        c.update(true);
        c.update(false);
        SkateState state = c.update(true);
        assertEquals(SkateState.SKATING, state);
        assertTrue(c.isSkating());
    }

    @Test
    void startsGrounded() {
        assertEquals(SkateState.GROUNDED, new SkateController().state());
    }
}
