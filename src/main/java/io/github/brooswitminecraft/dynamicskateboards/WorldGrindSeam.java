package io.github.brooswitminecraft.dynamicskateboards;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Story (e)'s real {@link GrindSeam}: one instance per online player (see
 * {@code DynamicSkateboardsMod.ServerEvents}), holding just enough live state to detect, acquire
 * and follow a grind on top of the pure {@link GrindEdgeDetector}/{@link GrindAcquisition}/
 * {@link GrindFollower}/{@link GrindSession} classes &mdash; those classes don't know Minecraft
 * exists.
 *
 * <p>{@link #tryGrind} is the {@link GrindSeam} contract method: {@code SkateController} calls it
 * once per tick while AIRBORNE and Shift is held, and it only ever ACQUIRES &mdash; see that
 * interface's javadoc. Because {@code SkateController} gates that call on Shift being held, it
 * never fires the tick Shift is released, so release-on-Shift-release can't live there: the
 * actual per-tick following and every release path is driven directly by {@code ServerEvents}
 * calling {@link #tick} every tick a grind is active, independent of the {@code GrindSeam}
 * plumbing. {@link #prepare} must be called once per tick, before {@code SkateController.update},
 * so {@link #tryGrind} has a live player/level reference to query.
 *
 * <p>{@link #clearIfNotRiding} closes the gap a formal review caught: {@code tick} only ever runs
 * while {@code SkateController}'s state is non-GROUNDED, so a grind that's active the tick the
 * skater goes GROUNDED outright (board unequipped mid-grind, or any other reason riding ends) was
 * never told to stop &mdash; {@link #isGrinding} stayed {@code true} with a stale
 * {@code GrindFollower}, silently resumed (teleporting the player back onto the old path) the
 * next time they mounted and went airborne. {@code ServerEvents} must call it every tick with
 * whatever {@code SkateController}'s own state says, GROUNDED or not.
 */
public final class WorldGrindSeam implements GrindSeam {
    private ServerPlayer preparedPlayer;
    private final GrindSession session = new GrindSession();

    /** Call once per player per tick, before {@code SkateController#update}. */
    public void prepare(ServerPlayer player) {
        this.preparedPlayer = player;
        session.tickCooldown();
    }

    public boolean isGrinding() {
        return session.isActive();
    }

    /** See the class javadoc: must be called every tick with {@code SkateController}'s own state. */
    public void clearIfNotRiding(boolean currentlyRiding) {
        session.onRidingStateChanged(currentlyRiding);
    }

    @Override
    public boolean tryGrind(SkateInput input) {
        if (session.isActive()) {
            // Already grinding; this tick's actual following/release is ServerEvents' job via
            // tick(), not this acquisition-only contract method. Keep reporting "engaged."
            return true;
        }
        if (preparedPlayer == null || !session.canAcquire()) {
            return false;
        }

        Level level = preparedPlayer.level();
        BlockPos center = preparedPlayer.blockPosition();
        GrindCollisionSource source = pos -> level.getBlockState(pos).getCollisionShape(level, pos);
        List<GrindPath> candidates =
                GrindEdgeDetector.findCandidatePaths(source, center, SkateConstants.GRIND_SCAN_RADIUS_BLOCKS);

        Vec3 playerPos = preparedPlayer.position();
        GrindPath chosen = GrindAcquisition.selectBestCandidate(candidates, playerPos, input.facingHeadingDegrees());
        if (chosen == null) {
            return false;
        }

        double distanceAlong = chosen.nearestDistanceAlong(playerPos);
        int travelSign = GrindAcquisition.travelSignAt(chosen, distanceAlong, input.facingHeadingDegrees());
        session.start(new GrindFollower(chosen, distanceAlong, travelSign));
        preparedPlayer.displayClientMessage(Component.literal("Grinding"), true);
        return true;
    }

    /**
     * Drives one tick of following (or releases) an active grind. No-op if not currently
     * grinding. {@code speedBlocksPerTick} is {@code SkateController#speed()} &mdash; the same
     * value riding uses &mdash; so speed carries into and along the grind exactly as it does onto
     * a ramp; feeding the resulting velocity back via {@code setDeltaMovement} keeps that speed
     * self-consistent on the next tick's {@code observedHorizontalSpeed} without this story
     * touching {@code SkateController} at all.
     */
    public void tick(ServerPlayer player, double speedBlocksPerTick, boolean shiftHeld, boolean jumpHeld) {
        GrindFollower follower = session.current();
        if (follower == null) {
            return;
        }
        GrindFollower.Step step = follower.advance(speedBlocksPerTick, shiftHeld, jumpHeld);
        Vec3 position = step.position();
        player.setPos(position.x, position.y, position.z);

        if (step.released()) {
            Vec3 exitHorizontal = horizontalVelocityFor(step.headingDegrees(), speedBlocksPerTick);
            double verticalVelocity = jumpHeld ? SkateConstants.OLLIE_MIN_IMPULSE : 0.0;
            player.setDeltaMovement(new Vec3(exitHorizontal.x, verticalVelocity, exitHorizontal.z));
            session.release();
        } else {
            Vec3 horizontal = horizontalVelocityFor(step.headingDegrees(), speedBlocksPerTick);
            player.setDeltaMovement(new Vec3(horizontal.x, 0.0, horizontal.z));
        }
        player.hurtMarked = true;
    }

    private static Vec3 horizontalVelocityFor(double headingDegrees, double speed) {
        double yaw = Math.toRadians(headingDegrees);
        return new Vec3(-Math.sin(yaw) * speed, 0.0, Math.cos(yaw) * speed);
    }
}
