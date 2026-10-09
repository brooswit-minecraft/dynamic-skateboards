package io.github.brooswitminecraft.dynamicskateboards;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entry point for the skateboard item and the arcade riding controller (MINECRAFT-94/95/99).
 * Riding is driven ONLY by the server, once per tick, from each
 * {@link net.minecraft.server.level.ServerPlayer}'s main-hand item plus the authoritative mirror
 * of their Jump key (see {@link SkateJumpInputPayload}) &mdash; see
 * {@link ServerEvents#serverPlayerTick} for why the server (not the client) owns this state.
 */
@Mod(DynamicSkateboardsMod.MODID)
public class DynamicSkateboardsMod {
    public static final String MODID = "dynamicskateboards";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<SkateboardItem> SKATEBOARD =
            ITEMS.register("skateboard", () -> new SkateboardItem(new Item.Properties().stacksTo(1)));

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    private static BlockBehaviour.Properties cobbleProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .sound(SoundType.STONE)
                .strength(2.0F, 6.0F);
    }

    /**
     * The 10-piece vertical transition family (MINECRAFT-97 "Prototype skate geometry" /
     * "Vertical transition pieces"). One {@link SlopeBlock} per {@link SlopeKind}; see that enum
     * and {@link SlopeShapes} for the authored geometry.
     */
    public static final Map<SlopeKind, DeferredBlock<SlopeBlock>> SLOPES = new EnumMap<>(SlopeKind.class);

    static {
        for (SlopeKind kind : SlopeKind.values()) {
            SLOPES.put(kind, BLOCKS.register(kind.blockName(), () -> new SlopeBlock(cobbleProperties(), kind)));
        }
    }

    /** Curved cobblestone step and wall (MINECRAFT-97 "Horizontal curves"). See {@link CurvedShapes}. */
    public static final DeferredBlock<CurvedBlock> CURVED_STEP =
            BLOCKS.register("curved_step", () -> new CurvedBlock(cobbleProperties(), 8));
    public static final DeferredBlock<CurvedBlock> CURVED_WALL =
            BLOCKS.register("curved_wall", () -> new CurvedBlock(cobbleProperties(), 16));

    public static final Map<String, DeferredItem<BlockItem>> BLOCK_ITEMS = new java.util.LinkedHashMap<>();

    private static DeferredItem<BlockItem> registerBlockItem(String name, DeferredBlock<? extends Block> block) {
        DeferredItem<BlockItem> item = ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        BLOCK_ITEMS.put(name, item);
        return item;
    }

    static {
        for (SlopeKind kind : SlopeKind.values()) {
            registerBlockItem(kind.blockName(), SLOPES.get(kind));
        }
        registerBlockItem("curved_step", CURVED_STEP);
        registerBlockItem("curved_wall", CURVED_WALL);
    }

    public DynamicSkateboardsMod(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerPayloads);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Dynamic Skateboards loaded");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(SkatingStatePayload.TYPE, SkatingStatePayload.STREAM_CODEC, SkatingStatePayload::handle);
        registrar.playToServer(SkateJumpInputPayload.TYPE, SkateJumpInputPayload.STREAM_CODEC, SkateJumpInputPayload::handle);
        registrar.playToServer(SkateTrickInputPayload.TYPE, SkateTrickInputPayload.STREAM_CODEC, SkateTrickInputPayload::handle);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SKATEBOARD);
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            BLOCK_ITEMS.values().forEach(event::accept);
        }
    }

    /**
     * Why the SERVER owns riding state, not the client: it affects the player's rendered stance
     * and, now, actual movement (speed/steering/ollie/landing), which every other player in the
     * world must agree on. A client-decided result would be exactly the kind of client-side
     * illusion the ticket rules out. Instead the server alone evaluates
     * {@link SkateController} (a pure, unit-tested class) from the held item and the
     * continuously-synced {@link SkateJumpInputPayload}, applies the resulting velocity, and
     * pushes the animation state to every tracking client with {@link SkatingStatePayload}; the
     * client only ever mirrors it (see {@link SkateClientState}) and never recomputes it.
     */
    @EventBusSubscriber(modid = MODID)
    public static final class ServerEvents {
        private ServerEvents() {}

        /**
         * One {@link SkateController} per online player, server-side only, plus the sync-gap
         * decisions over it &mdash; see {@link SkateSyncPolicy}.
         */
        private static final SkateSyncPolicy SYNC_POLICY = new SkateSyncPolicy();

        /**
         * One {@link WorldGrindSeam} per online player (story (e)), wired into that player's
         * {@link SkateController} in place of {@link GrindSeam#NONE} the first time a controller
         * is created for them. Kept here rather than inside {@link SkateSyncPolicy} because it
         * needs real Minecraft types ({@code ServerPlayer}/{@code Level}); {@code SkateSyncPolicy}
         * stays pure.
         */
        private static final Map<UUID, WorldGrindSeam> GRIND_SEAMS = new ConcurrentHashMap<>();

        /**
         * The server's authoritative mirror of each online player's Jump key, kept live only by
         * {@link SkateJumpInputPayload} &mdash; see that class's javadoc for why a normal
         * (non-vehicle) {@code ServerPlayer} has no other continuously-live source for it.
         */
        private static final Map<UUID, Boolean> JUMP_HELD = new ConcurrentHashMap<>();

        /**
         * The server's authoritative mirror of each online player's Shift/attack/use/WASD keys,
         * kept live only by {@link SkateTrickInputPayload} &mdash; same reasoning as
         * {@link #JUMP_HELD}, one payload for the rest of this story's inputs.
         */
        private static final Map<UUID, SkateTrickInputPayload> TRICK_INPUT = new ConcurrentHashMap<>();

        /** Last tick's attack-held value, so {@code attackJustPressed} can be derived as an edge. */
        private static final Map<UUID, Boolean> PREVIOUS_ATTACK_HELD = new ConcurrentHashMap<>();

        public static void setJumpHeld(UUID player, boolean jumping) {
            JUMP_HELD.put(player, jumping);
        }

        public static void setTrickInput(UUID player, SkateTrickInputPayload payload) {
            TRICK_INPUT.put(player, payload);
        }

        @SubscribeEvent
        public static void serverPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            WorldGrindSeam grindSeam = GRIND_SEAMS.computeIfAbsent(player.getUUID(), id -> new WorldGrindSeam());
            SkateController controller = SYNC_POLICY.controllerFor(player.getUUID(), grindSeam);
            grindSeam.prepare(player);

            boolean mainHandIsSkateboard = player.getItemInHand(InteractionHand.MAIN_HAND).is(SKATEBOARD);
            boolean jumpHeld = JUMP_HELD.getOrDefault(player.getUUID(), Boolean.FALSE);
            boolean onGround = player.onGround();
            Vec3 delta = player.getDeltaMovement();
            double observedHorizontalSpeed = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            double facingHeadingDegrees = player.getYRot();

            SkateTrickInputPayload trickInput = TRICK_INPUT.getOrDefault(player.getUUID(), EMPTY_TRICK_INPUT);
            boolean attackHeld = trickInput.attackHeld();
            boolean attackJustPressed = attackHeld && !PREVIOUS_ATTACK_HELD.getOrDefault(player.getUUID(), Boolean.FALSE);
            PREVIOUS_ATTACK_HELD.put(player.getUUID(), attackHeld);
            TrickDirection direction = TrickDirection.fromKeys(
                    trickInput.forwardHeld(), trickInput.backHeld(), trickInput.leftHeld(), trickInput.rightHeld());

            SkateState previousState = controller.state();
            SkateState newState = controller.update(new SkateInput(
                    mainHandIsSkateboard, jumpHeld, onGround, observedHorizontalSpeed, facingHeadingDegrees,
                    trickInput.shiftHeld(), attackJustPressed, trickInput.useHeld(), direction));

            // Review fix: a grind active the tick riding goes GROUNDED outright (board
            // unequipped mid-grind, etc.) must be dropped here - tick() below only ever runs
            // while non-GROUNDED, so without this a stale follower would silently resume (and
            // teleport the player back onto the old path) the next time they mount and go
            // airborne.
            grindSeam.clearIfNotRiding(newState != SkateState.GROUNDED);

            if (newState != SkateState.GROUNDED) {
                if (grindSeam.isGrinding()) {
                    // Overrides position/velocity directly along the acquired edge; never also
                    // run the normal riding velocity model for the same tick (they'd fight).
                    grindSeam.tick(player, controller.speed(), trickInput.shiftHeld(), jumpHeld);
                } else {
                    applyRidingVelocity(player, controller, delta);
                }
            }

            if (newState != previousState) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SkatingStatePayload(player.getUUID(), newState));
            }
        }

        private static final SkateTrickInputPayload EMPTY_TRICK_INPUT =
                new SkateTrickInputPayload(false, false, false, false, false, false, false);

        /** Whether vanilla left/right click must be suppressed for {@code player} right now. */
        public static boolean suppressVanillaClicks(ServerPlayer player) {
            return ClickSuppressionPolicy.suppressVanillaClicks(SYNC_POLICY.controllerFor(player.getUUID()).state());
        }

        private static void applyRidingVelocity(ServerPlayer player, SkateController controller, Vec3 currentDelta) {
            double yawRadians = Math.toRadians(controller.headingDegrees());
            double forwardX = -Math.sin(yawRadians);
            double forwardZ = Math.cos(yawRadians);
            double speed = controller.speed();

            Double ollieImpulse = controller.takePendingOllieImpulse();
            double verticalVelocity = ollieImpulse != null ? ollieImpulse : currentDelta.y;

            player.setDeltaMovement(new Vec3(forwardX * speed, verticalVelocity, forwardZ * speed));
            // Player movement is otherwise client-authoritative: ServerEntity only sends
            // ClientboundSetEntityMotionPacket (to trackers AND the owning client's own
            // connection) when hurtMarked is true, same as vanilla knockback. Without this,
            // setDeltaMovement above would silently never reach any client.
            player.hurtMarked = true;
        }

        /**
         * Unconditional per {@link SkateSyncPolicy}: a stale-skating hole would otherwise remain
         * for an observer who stops tracking while the target skates, outlives that unseen, then
         * starts tracking again &mdash; they'd never learn the target went back to GROUNDED.
         */
        @SubscribeEvent
        public static void onStartTracking(PlayerEvent.StartTracking event) {
            if (!(event.getTarget() instanceof ServerPlayer target) || !(event.getEntity() instanceof ServerPlayer tracker)) {
                return;
            }
            SkateState state = SYNC_POLICY.stateToSendOnStartTracking(target.getUUID());
            PacketDistributor.sendToPlayer(tracker, new SkatingStatePayload(target.getUUID(), state));
        }

        /**
         * Death/dimension-change respawn repositions the player outright; any grind active at
         * that moment is exactly the same stale-follower hazard {@link #serverPlayerTick}'s
         * {@code clearIfNotRiding} call fixes for GROUNDED, so drop it here too rather than let
         * it resume relative to wherever the respawn moved the player.
         */
        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                WorldGrindSeam grindSeam = GRIND_SEAMS.get(player.getUUID());
                if (grindSeam != null) {
                    grindSeam.clearIfNotRiding(false);
                }
            }
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            Entity entity = event.getEntity();
            JUMP_HELD.remove(entity.getUUID());
            TRICK_INPUT.remove(entity.getUUID());
            PREVIOUS_ATTACK_HELD.remove(entity.getUUID());
            GRIND_SEAMS.remove(entity.getUUID());
            SkateState broadcast = SYNC_POLICY.onLogout(entity.getUUID());
            if (broadcast != null) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new SkatingStatePayload(entity.getUUID(), broadcast));
            }
        }

        /**
         * VANILLA CLICK SUPPRESSION (server-authoritative half): left click must not attack while
         * skating &mdash; the player is kickflipping, not swinging the board at mobs. Decision is
         * {@link ClickSuppressionPolicy}, a pure function of {@link SkateState}; this handler only
         * wires it to the event. Restored automatically the instant {@link SkateController} itself
         * returns to GROUNDED (see its own {@code !mainHandIsSkateboard} exit path) &mdash; there is
         * nothing extra to "turn back on" here.
         */
        @SubscribeEvent
        public static void onAttackEntity(AttackEntityEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && suppressVanillaClicks(player)) {
                event.setCanceled(true);
            }
        }

        /** Left click on a block must not mine while skating. */
        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            if (event.getEntity() instanceof ServerPlayer player && suppressVanillaClicks(player)) {
                event.setCanceled(true);
            }
        }

        /** Right click on a block must not place/use while skating. */
        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (event.getEntity() instanceof ServerPlayer player && suppressVanillaClicks(player)) {
                event.setCanceled(true);
            }
        }

        /** Right click with an item (eating, using) must not fire while skating. */
        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (event.getEntity() instanceof ServerPlayer player && suppressVanillaClicks(player)) {
                event.setCanceled(true);
            }
        }
    }

    /** Applies the custom skate animation pose at render time; never touches sneak/crouch state. */
    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {}

        @SubscribeEvent
        public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
            SkateState state = SkateClientState.state(event.getEntity().getUUID());
            float offset = switch (state) {
                case GROUNDED -> 0.0f;
                case SKATING, MANUAL -> SkateConstants.SKATE_STANCE_HEIGHT_OFFSET;
                case CHARGING -> SkateConstants.SKATE_STANCE_HEIGHT_OFFSET + SkateConstants.CHARGE_STANCE_EXTRA_HEIGHT_OFFSET;
                case AIRBORNE -> SkateConstants.AIRBORNE_HEIGHT_OFFSET;
                case LANDING -> SkateConstants.LANDING_HEIGHT_OFFSET;
            };
            if (offset != 0.0f) {
                event.getPoseStack().translate(0.0, -offset, 0.0);
            }
        }

        /**
         * Forwards the vanilla Jump key's held state to the server every client tick &mdash; see
         * {@link SkateJumpInputPayload} for why a normal player needs this at all, and why
         * sending it continuously (not on a request) is what keeps charge/release responsive.
         */
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.getConnection() == null) {
                return;
            }
            PacketDistributor.sendToServer(new SkateJumpInputPayload(client.options.keyJump.isDown()));
            PacketDistributor.sendToServer(new SkateTrickInputPayload(
                    client.options.keyShift.isDown(),
                    client.options.keyAttack.isDown(),
                    client.options.keyUse.isDown(),
                    client.options.keyUp.isDown(),
                    client.options.keyDown.isDown(),
                    client.options.keyLeft.isDown(),
                    client.options.keyRight.isDown()));
        }

        /**
         * Clears the client-side mirror on disconnect so a UUID that happens to reappear on a
         * different server (or a fresh join) never starts out rendering a stance left over from
         * a previous connection.
         */
        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            SkateClientState.clearAll();
        }

        /**
         * VANILLA SNEAK SUPPRESSION: Shift's raw keybinding state is read above for this mod's own
         * purposes (manual/grind dispatch), but that same physical key independently drives
         * vanilla's OWN sneak input path &mdash; avoiding Minecraft's sneak-state setter is not
         * enough by itself if the vanilla INPUT still sneaks. Clear it here, after everything else
         * has already read it, only while the client's own mirror of this player's state is
         * non-GROUNDED; restored automatically the instant that mirror reports GROUNDED again, same
         * restoration shape as {@link ClickSuppressionPolicy}.
         */
        @SubscribeEvent
        public static void onMovementInput(MovementInputUpdateEvent event) {
            SkateState state = SkateClientState.state(event.getEntity().getUUID());
            if (SneakSuppressionPolicy.suppressVanillaSneak(state)) {
                event.getInput().shiftKeyDown = false;
            }
        }
    }
}
