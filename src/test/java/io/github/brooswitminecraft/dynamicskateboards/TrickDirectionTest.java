package io.github.brooswitminecraft.dynamicskateboards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrickDirectionTest {
    @Test
    void noKeysIsNeutral() {
        assertEquals(TrickDirection.NEUTRAL, TrickDirection.fromKeys(false, false, false, false));
    }

    @Test
    void eachSingleKeyResolves() {
        assertEquals(TrickDirection.FORWARD, TrickDirection.fromKeys(true, false, false, false));
        assertEquals(TrickDirection.BACK, TrickDirection.fromKeys(false, true, false, false));
        assertEquals(TrickDirection.LEFT, TrickDirection.fromKeys(false, false, true, false));
        assertEquals(TrickDirection.RIGHT, TrickDirection.fromKeys(false, false, false, true));
    }

    @Test
    void oppositeKeysCancelToNeutral() {
        assertEquals(TrickDirection.NEUTRAL, TrickDirection.fromKeys(true, true, false, false));
        assertEquals(TrickDirection.NEUTRAL, TrickDirection.fromKeys(false, false, true, true));
    }

    @Test
    void forwardBackTakePriorityOverLeftRight() {
        assertEquals(TrickDirection.FORWARD, TrickDirection.fromKeys(true, false, true, false));
        assertEquals(TrickDirection.BACK, TrickDirection.fromKeys(false, true, false, true));
    }
}
