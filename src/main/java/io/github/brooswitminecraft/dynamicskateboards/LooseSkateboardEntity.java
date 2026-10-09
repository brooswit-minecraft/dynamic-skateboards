package io.github.brooswitminecraft.dynamicskateboards;

import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3d;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The loose, physical skateboard a bail leaves behind (MINECRAFT-181 / story f). Carries the
 * crash's momentum as a Sable rigid body (four passive wheel-contact points, no propulsion, no
 * skating controller &mdash; see {@link SkateboardSableBody}); falls back to plain vanilla
 * gravity/collision if Sable is not loaded, so a dev or test environment without the optional
 * dependency never crashes, just loses the tumble fidelity.
 *
 * <p>Server-authoritative: {@link #tick()} only ever drives position/velocity on the server; the
 * client mirrors position/rotation via vanilla entity tracking and this entity's own
 * {@code DATA_ORIENTATION} (a full quaternion, for pitch/roll) via {@link #publishOrientation}.
 *
 * <p>Pickup is walk-over, not an interaction: every server tick this entity checks for a
 * touching {@link Player} and, on the first one found, atomically claims itself via
 * {@link LooseBoardClaim} (see that class for why the single-threaded server tick already makes
 * this race-safe, and why the latch exists anyway) before handing the skateboard item back and
 * discarding itself &mdash; no keybind, no interact prompt, matching the spec's "collects it like
 * a dropped item" requirement. Claims are refused for {@link SkateConstants#BAIL_PICKUP_GRACE_TICKS}
 * ticks after spawn (review fix) so the bailing player, who spawns the board right under their own
 * feet, actually sees it come loose instead of it returning to their inventory the very next tick.
 */
public class LooseSkateboardEntity extends Entity {
    private static final EntityDataAccessor<Quaternionf> DATA_ORIENTATION =
            SynchedEntityData.defineId(LooseSkateboardEntity.class, EntityDataSerializers.QUATERNION);

    private final LooseBoardClaim claim = new LooseBoardClaim();
    private Object sableBody;
    private Quaternionf savedOrientation;
    private Vector3d initialTumbleAxis = new Vector3d(1, 0, 0);
    /**
     * Server ticks this loose board has been alive, counted by us (not vanilla's inherited
     * {@code tickCount}, which this class never relies on) so {@link #tryPickup} has something
     * precise to gate {@link SkateConstants#BAIL_PICKUP_GRACE_TICKS} against.
     */
    private int ticksAlive;

    public LooseSkateboardEntity(EntityType<? extends LooseSkateboardEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Spawns a loose board at the bailing player's position, carrying their current velocity as
     * crash momentum plus a tumble spin scaled to their horizontal speed. This, together with
     * removing the item from the player's hand (done by the caller, immediately before this),
     * is the ONLY place in the mod a loose board is created &mdash; see
     * {@code DynamicSkateboardsMod.ServerEvents} for the bail call site.
     */
    public static LooseSkateboardEntity spawnFromBail(ServerLevel level, ServerPlayer player) {
        LooseSkateboardEntity board = new LooseSkateboardEntity(DynamicSkateboardsMod.LOOSE_SKATEBOARD.get(), level);
        board.setPos(player.getX(), player.getY(), player.getZ());
        board.setYRot(player.getYRot());
        board.setDeltaMovement(player.getDeltaMovement());
        // Tumble about a horizontal axis perpendicular to the direction of travel, so a board
        // with real speed behind it flips forward/backward rather than spinning flat like a coin.
        double yawRadians = Math.toRadians(player.getYRot());
        board.initialTumbleAxis = new Vector3d(Math.cos(yawRadians), 0.0, Math.sin(yawRadians));
        level.addFreshEntity(board);
        return board;
    }

    /** Consumed once, at body creation, by {@link SkateboardSableBody#create}. */
    Vector3d initialTumbleAxis() {
        return initialTumbleAxis;
    }

    Quaternionf savedOrientation() {
        return savedOrientation;
    }

    /** Server: publish the body's orientation to clients, for pitch/roll rendering. */
    void publishOrientation(Quaternionf orientation) {
        entityData.set(DATA_ORIENTATION, orientation);
    }

    /** Client: the orientation last published by the server. */
    public Quaternionf orientation() {
        return entityData.get(DATA_ORIENTATION);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ORIENTATION, new Quaternionf());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            ticksAlive++;
            if (SkateboardSableCompat.usable()) {
                tickSable(serverLevel);
            } else {
                tickFallback();
            }
            tryPickup();
        }
    }

    private void tickSable(ServerLevel serverLevel) {
        if (sableBody == null) {
            sableBody = SkateboardSableCompat.create(serverLevel, this);
            if (sableBody == null) {
                tickFallback();
                return;
            }
        }
        try {
            SkateboardSableCompat.tick(sableBody, this, 1.0 / 20.0);
        } catch (RuntimeException gone) {
            // Sable released the physics body out from under us (e.g. its sub-level unloaded
            // and reloaded between ticks). Drop it so the next tick rebuilds fresh from the
            // entity's current transform, instead of crashing the server.
            DynamicSkateboardsMod.LOGGER.warn("loose skateboard {} lost its Sable body ({}); rebuilding", getUUID(), gone.toString());
            try {
                SkateboardSableCompat.remove(sableBody);
            } catch (RuntimeException ignored) {
                // already gone
            }
            sableBody = null;
        }
    }

    /** Plain vanilla gravity/collision, used only when Sable isn't loaded: not a tumble, just "doesn't crash." */
    private void tickFallback() {
        if (!onGround()) {
            setDeltaMovement(getDeltaMovement().add(0.0, -0.04, 0.0));
        } else {
            setDeltaMovement(getDeltaMovement().multiply(0.6, 0.0, 0.6));
        }
        move(MoverType.SELF, getDeltaMovement());
    }

    /**
     * Walk-over pickup: no keybind, no interact prompt. See the class javadoc for the race-safety
     * argument. Refuses every claim for {@link SkateConstants#BAIL_PICKUP_GRACE_TICKS} ticks after
     * spawn (review fix) so the bailing player &mdash; standing right where the board spawned
     * &mdash; doesn't re-pickup it on the very next tick; the latch stays open through the grace
     * window, so the first real claim after it still wins normally.
     */
    private void tryPickup() {
        if (claim.isClaimed()) {
            return;
        }
        AABB reach = getBoundingBox().inflate(SkateConstants.BAIL_PICKUP_REACH_BLOCKS);
        List<Player> touching = level().getEntitiesOfClass(Player.class, reach);
        for (Player player : touching) {
            if (claim.tryClaim(ticksAlive, SkateConstants.BAIL_PICKUP_GRACE_TICKS)) {
                giveBoardOrDrop(player);
                discard();
                return;
            }
        }
    }

    /**
     * Review fix (item loss): {@code Inventory.add} mutates the passed stack down to whatever it
     * couldn't take, so a full inventory can leave a non-empty remainder even though {@code add}
     * itself didn't throw (its boolean return is {@code true} even for a partial add, so it can't
     * be trusted here &mdash; see {@link BoardPickupTransfer}). We always finish the pickup
     * (claim/discard) once a player has walked over the board &mdash; "the board is handled" per
     * spec &mdash; but never let the item itself vanish: anything {@code add} couldn't place goes
     * on the ground at the player's feet instead of into the void. The actual decision logic
     * lives in {@link BoardPickupTransfer}, which is unit-tested directly against fakes standing
     * in for a full/partial/empty inventory.
     */
    private static void giveBoardOrDrop(Player player) {
        ItemStack stack = new ItemStack(DynamicSkateboardsMod.SKATEBOARD.get());
        BoardPickupTransfer.give(stack, s -> player.getInventory().add(s), ItemStack::isEmpty, s -> player.drop(s, false));
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (sableBody != null) {
            try {
                SkateboardSableCompat.remove(sableBody);
            } catch (RuntimeException ignored) {
                // already gone (e.g. chunk unload beat us to it)
            }
            sableBody = null;
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        savedOrientation = LooseSkateboardPersistence.read(tag);
        // Review fix (position persistence): vanilla Entity#load already restored position from
        // its own "Pos" tag before this method runs; this is a redundant, explicit re-apply from
        // our own tag so the guarantee is backed by something LooseSkateboardPersistenceTest can
        // assert without a running Level (see that class's javadoc for why vanilla's own path
        // isn't directly testable here).
        net.minecraft.world.phys.Vec3 explicitPos = LooseSkateboardPersistence.readPosition(tag);
        if (explicitPos != null) {
            setPos(explicitPos.x, explicitPos.y, explicitPos.z);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        Quaternionf q = savedOrientation;
        if (sableBody != null) {
            try {
                q = SkateboardSableCompat.orientation(sableBody);
            } catch (RuntimeException gone) {
                // Sable already released the body (e.g. chunk unload); keep the last known orientation.
            }
        }
        if (q != null) {
            LooseSkateboardPersistence.write(tag, q);
        }
        LooseSkateboardPersistence.writePosition(tag, position());
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return false;
    }
}
