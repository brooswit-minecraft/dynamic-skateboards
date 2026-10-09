package io.github.brooswitminecraft.dynamicskateboards;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
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

    public DynamicSkateboardsMod(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
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
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SKATEBOARD);
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

        /** One {@link SkateController} per online player, server-side only. */
        private static final Map<UUID, SkateController> CONTROLLERS = new ConcurrentHashMap<>();

        /**
         * The server's authoritative mirror of each online player's Jump key, kept live only by
         * {@link SkateJumpInputPayload} &mdash; see that class's javadoc for why a normal
         * (non-vehicle) {@code ServerPlayer} has no other continuously-live source for it.
         */
        private static final Map<UUID, Boolean> JUMP_HELD = new ConcurrentHashMap<>();

        public static void setJumpHeld(UUID player, boolean jumping) {
            JUMP_HELD.put(player, jumping);
        }

        @SubscribeEvent
        public static void serverPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            SkateController controller = CONTROLLERS.computeIfAbsent(player.getUUID(), id -> new SkateController());

            boolean mainHandIsSkateboard = player.getItemInHand(InteractionHand.MAIN_HAND).is(SKATEBOARD);
            boolean jumpHeld = JUMP_HELD.getOrDefault(player.getUUID(), Boolean.FALSE);
            boolean onGround = player.onGround();
            Vec3 delta = player.getDeltaMovement();
            double observedHorizontalSpeed = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            double facingHeadingDegrees = player.getYRot();

            SkateState previousState = controller.state();
            SkateState newState = controller.update(new SkateInput(
                    mainHandIsSkateboard, jumpHeld, onGround, observedHorizontalSpeed, facingHeadingDegrees));

            if (newState != SkateState.GROUNDED) {
                applyRidingVelocity(player, controller, delta);
            }

            if (newState != previousState) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SkatingStatePayload(player.getUUID(), newState));
            }
        }

        private static void applyRidingVelocity(ServerPlayer player, SkateController controller, Vec3 currentDelta) {
            double yawRadians = Math.toRadians(controller.headingDegrees());
            double forwardX = -Math.sin(yawRadians);
            double forwardZ = Math.cos(yawRadians);
            double speed = controller.speed();

            Double ollieImpulse = controller.takePendingOllieImpulse();
            double verticalVelocity = ollieImpulse != null ? ollieImpulse : currentDelta.y;

            player.setDeltaMovement(new Vec3(forwardX * speed, verticalVelocity, forwardZ * speed));
        }

        @SubscribeEvent
        public static void onStartTracking(PlayerEvent.StartTracking event) {
            if (!(event.getTarget() instanceof ServerPlayer target) || !(event.getEntity() instanceof ServerPlayer tracker)) {
                return;
            }
            SkateController controller = CONTROLLERS.get(target.getUUID());
            SkateState state = controller != null ? controller.state() : SkateState.GROUNDED;
            if (state != SkateState.GROUNDED) {
                PacketDistributor.sendToPlayer(tracker, new SkatingStatePayload(target.getUUID(), state));
            }
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            Entity entity = event.getEntity();
            SkateController controller = CONTROLLERS.remove(entity.getUUID());
            JUMP_HELD.remove(entity.getUUID());
            if (controller != null && controller.state() != SkateState.GROUNDED) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new SkatingStatePayload(entity.getUUID(), SkateState.GROUNDED));
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
                case SKATING -> SkateConstants.SKATE_STANCE_HEIGHT_OFFSET;
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
        }
    }
}
