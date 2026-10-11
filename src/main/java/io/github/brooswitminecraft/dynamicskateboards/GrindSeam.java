package io.github.brooswitminecraft.dynamicskateboards;

/**
 * THE NAMED SEAM story (e) attaches grind detection/acquisition/following to. {@link
 * SkateController} calls {@link #tryGrind} whenever Shift is held while airborne or while seeking
 * a grounded edge. The supplied board heading, rather than the independently movable camera yaw,
 * is the acquisition and travel-direction authority. This story does not implement grind detection: {@link
 * #NONE} is a no-op stub that always declines, so the route exists and is asserted by test without
 * this story guessing at (e)'s own mechanics.
 *
 * <p>Story (e) should implement this interface (reading whatever grind-edge/approach state it
 * needs from the world itself &mdash; {@code SkateInput} is intentionally not grind-aware) and wire
 * it into {@link SkateController} in place of {@link #NONE}.
 */
public interface GrindSeam {
    /**
     * Called when Shift seeks a grind. {@code boardHeadingDegrees} is the controller's board
     * travel heading, not {@link SkateInput#facingHeadingDegrees() camera facing}. Returns
     * {@code true} if a grind was acquired this tick; {@link #NONE} always returns {@code false}.
     */
    boolean tryGrind(SkateInput input, double boardHeadingDegrees);

    /** The no-op stub this story ships: the seam exists and is routed to, but never engages. */
    GrindSeam NONE = (input, boardHeadingDegrees) -> false;
}
