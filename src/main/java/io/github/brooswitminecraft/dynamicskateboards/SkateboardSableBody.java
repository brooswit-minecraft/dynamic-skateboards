package io.github.brooswitminecraft.dynamicskateboards;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The loose skateboard's Sable rigid body: a thin box with four wheel-contact suspension rays,
 * no propulsion, no steering, no tire friction model &mdash; "a dropped object, not a vehicle"
 * per the ticket. Deliberately does not share any class with {@code SableCarBody}
 * (dynamic-vehicles) even though the overall shape of "raycast per wheel, apply a spring/damper
 * impulse at the contact point, sync the body's pose back onto the entity" is the same pattern
 * that class demonstrates for driving a Sable body from a tick; this is a fresh, independent
 * implementation scoped to a passive object.
 *
 * <p>Wheel mounts are the box's own four bottom corners (x = across the deck, z = along the
 * deck, y = down from the body's own centre to its bottom face) &mdash; see {@link #MOUNTS}.
 */
final class SkateboardSableBody {
    /** {x, y, z} in the body's own local frame: the four bottom corners of the deck. */
    private static final double[][] MOUNTS = {
        {-SkateConstants.BAIL_BOARD_HALF_WIDTH, -SkateConstants.BAIL_BOARD_HALF_HEIGHT, -SkateConstants.BAIL_BOARD_HALF_LENGTH},
        {SkateConstants.BAIL_BOARD_HALF_WIDTH, -SkateConstants.BAIL_BOARD_HALF_HEIGHT, -SkateConstants.BAIL_BOARD_HALF_LENGTH},
        {-SkateConstants.BAIL_BOARD_HALF_WIDTH, -SkateConstants.BAIL_BOARD_HALF_HEIGHT, SkateConstants.BAIL_BOARD_HALF_LENGTH},
        {SkateConstants.BAIL_BOARD_HALF_WIDTH, -SkateConstants.BAIL_BOARD_HALF_HEIGHT, SkateConstants.BAIL_BOARD_HALF_LENGTH},
    };

    private final ServerLevel level;
    private final BoxPhysicsObject box;
    private final RigidBodyHandle body;

    private SkateboardSableBody(ServerLevel level, BoxPhysicsObject box, RigidBodyHandle body) {
        this.level = level;
        this.box = box;
        this.body = body;
    }

    static SkateboardSableBody create(ServerLevel level, LooseSkateboardEntity board) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        // The entity's own origin is its bottom centre; the body's origin is the box centre.
        Vector3d centre = new Vector3d(board.getX(), board.getY() + SkateConstants.BAIL_BOARD_HALF_HEIGHT, board.getZ());
        Quaterniond orientation = board.savedOrientation() != null
                ? new Quaterniond(board.savedOrientation().x, board.savedOrientation().y, board.savedOrientation().z, board.savedOrientation().w)
                : new Quaterniond().rotateY(-Math.toRadians(board.getYRot()));
        Pose3d pose = new Pose3d(centre, orientation, new Vector3d(), new Vector3d(1, 1, 1));
        Vector3d halfExtents = new Vector3d(
                SkateConstants.BAIL_BOARD_HALF_WIDTH, SkateConstants.BAIL_BOARD_HALF_HEIGHT, SkateConstants.BAIL_BOARD_HALF_LENGTH);
        BoxPhysicsObject box = new BoxPhysicsObject(pose, halfExtents, SkateConstants.BAIL_BOARD_MASS_KG);
        container.physicsSystem().addObject(box);
        RigidBodyHandle handle = RigidBodyHandle.of(level, box);

        // Transfer the crash's momentum (Minecraft blocks/tick -> Sable metres/second is *20) plus
        // a tumble spin, so the board carries real motion into the world rather than appearing
        // static ("tumbles plausibly, not static" per the ticket).
        Vec3 crashVelocity = board.getDeltaMovement();
        Vector3d linear = new Vector3d(crashVelocity.x, crashVelocity.y, crashVelocity.z).mul(20.0);
        double horizontalSpeedPerTick = Math.hypot(crashVelocity.x, crashVelocity.z);
        Vector3d angular = board.initialTumbleAxis().mul(horizontalSpeedPerTick * SkateConstants.BAIL_TUMBLE_SPIN_FACTOR);
        handle.addLinearAndAngularVelocity(linear, angular);

        return new SkateboardSableBody(level, box, handle);
    }

    void remove() {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            container.physicsSystem().removeObject(box);
        }
    }

    /** Applies this tick's passive wheel-contact suspension forces, then moves the entity to the body's pose. */
    void tick(LooseSkateboardEntity board, double dt) {
        box.updatePose();
        Pose3d pose = new Pose3d(box.getPose());
        Vector3d position = pose.position();
        Quaterniond orientation = new Quaterniond(pose.orientation());
        Quaterniond inverse = new Quaterniond(orientation).invert();
        Vector3d linear = body.getLinearVelocity(new Vector3d());
        Vector3d angular = body.getAngularVelocity(new Vector3d());
        Vector3d down = orientation.transform(new Vector3d(0, -1, 0));

        for (double[] mount : MOUNTS) {
            Vector3d local = new Vector3d(mount[0], mount[1], mount[2]);
            Vector3d offset = orientation.transform(new Vector3d(local));
            Vector3d from = new Vector3d(position).add(offset);
            Vector3d to = new Vector3d(from).fma(SkateConstants.BAIL_WHEEL_REST_LENGTH + 0.1, down);
            BlockHitResult hit = level.clip(new ClipContext(new Vec3(from.x, from.y, from.z), new Vec3(to.x, to.y, to.z),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, board));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            double distance = hit.getLocation().distanceTo(new Vec3(from.x, from.y, from.z));
            double compression = SkateConstants.BAIL_WHEEL_REST_LENGTH - distance;
            if (compression <= 0) {
                continue;
            }
            // Velocity of the mount point: linear + angular x offset. Positive along "down" = squeezing.
            Vector3d pointVelocity = new Vector3d(angular).cross(offset).add(linear);
            double rate = pointVelocity.dot(down);
            double force = Math.max(0.0, Math.min(SkateConstants.BAIL_WHEEL_MAX_SPRING_FORCE,
                    compression * SkateConstants.BAIL_WHEEL_SPRING_RATE - rate * SkateConstants.BAIL_WHEEL_DAMPING_RATE));
            if (force <= 0) {
                continue;
            }
            Vector3d up = new Vector3d(down).negate();
            Vector3d impulseWorld = new Vector3d(up).mul(force * dt);
            Vector3d impulseLocal = inverse.transform(impulseWorld);
            Vector3d contactLocal = new Vector3d(local.x, local.y - distance, local.z);
            body.applyImpulseAtPoint(contactLocal, impulseLocal);
        }
        box.wakeUp();

        board.setPos(position.x, position.y - SkateConstants.BAIL_BOARD_HALF_HEIGHT, position.z);
        board.publishOrientation(new org.joml.Quaternionf((float) orientation.x, (float) orientation.y, (float) orientation.z, (float) orientation.w));
        Vector3d velocity = body.getLinearVelocity(new Vector3d());
        board.setDeltaMovement(velocity.x / 20.0, velocity.y / 20.0, velocity.z / 20.0);
    }

    org.joml.Quaternionf orientationF() {
        box.updatePose();
        org.joml.Quaterniondc q = box.getPose().orientation();
        return new org.joml.Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
    }

    double speed() {
        return body.getLinearVelocity(new Vector3d()).length();
    }
}
