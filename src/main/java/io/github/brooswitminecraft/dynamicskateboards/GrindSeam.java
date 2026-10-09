package io.github.brooswitminecraft.dynamicskateboards;

/**
 * THE NAMED SEAM story (e) attaches grind detection/acquisition/following to. {@link
 * SkateController} calls {@link #tryGrind} exactly once per tick whenever Shift is held while
 * {@link SkateState#AIRBORNE} &mdash; the "airborne/approaching" half of the contextual Shift
 * dispatch this story owns (the other half, Shift while {@link SkateState#SKATING}, goes to
 * MANUAL and never reaches this seam). This story does not implement grind detection: {@link
 * #NONE} is a no-op stub that always declines, so the route exists and is asserted by test without
 * this story guessing at (e)'s own mechanics.
 *
 * <p>Story (e) should implement this interface (reading whatever grind-edge/approach state it
 * needs from the world itself &mdash; {@code SkateInput} is intentionally not grind-aware) and wire
 * it into {@link SkateController} in place of {@link #NONE}.
 */
public interface GrindSeam {
    /**
     * Called once per tick when Shift is held while AIRBORNE. Returns {@code true} if a grind was
     * acquired this tick (story (e)'s concern); {@link #NONE} always returns {@code false}.
     */
    boolean tryGrind(SkateInput input);

    /** The no-op stub this story ships: the seam exists and is routed to, but never engages. */
    GrindSeam NONE = input -> false;
}
