package io.github.brooswitminecraft.dynamicskateboards;

/**
 * States of the skate animation state machine. MANUAL (story (c): Shift while riding) extends this
 * same enum rather than forking a second state machine &mdash; flips and grabs do not get their
 * own states because they animate as an overlay on AIRBORNE instead (see
 * {@link SkateController#activeFlip()}/{@link SkateController#activeGrab()}).
 */
public enum SkateState {
    GROUNDED,
    SKATING,
    CHARGING,
    AIRBORNE,
    LANDING,
    MANUAL
}
