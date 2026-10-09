package io.github.brooswitminecraft.dynamicskateboards;

/**
 * States of the skate animation state machine. Only GROUNDED and SKATING are reachable in this
 * story; CHARGING, AIRBORNE and LANDING exist so story (b)'s charge-crouch and airborne/landing
 * logic has somewhere to go without a second state machine being introduced later.
 */
public enum SkateState {
    GROUNDED,
    SKATING,
    CHARGING,
    AIRBORNE,
    LANDING
}
