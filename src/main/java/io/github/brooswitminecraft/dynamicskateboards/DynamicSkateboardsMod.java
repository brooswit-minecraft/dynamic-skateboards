package io.github.brooswitminecraft.dynamicskateboards;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entry point for story (a): the skateboard item and the hold-to-skate state (MINECRAFT-94 /
 * MINECRAFT-99). Skating is driven ONLY by the server, once per tick, from each
 * {@link net.minecraft.server.level.ServerPlayer}'s main-hand item — see
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

    /**
     * One {@link SkateController} per online player, server-side only. A player who logs out
     * mid-skate drops their controller here and simply starts GROUNDED on rejoin; there is no
     * state worth persisting across a disconnect.
     */
    private static final Map<UUID, SkateController> CONTROLLERS = new ConcurrentHashMap<>();

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
        event.registrar("1").playToClient(SkatingStatePayload.TYPE, SkatingStatePayload.STREAM_CODEC, SkatingStatePayload::handle);
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
     * Why the SERVER owns skating state, not the client: skating affects the player's rendered
     * stance (and, for story (b), gates movement/jump behavior) which every other player in the
     * world must agree on. A client-decided "I'm skating" would be exactly the kind of
     * client-side illusion the ticket rules out — a modified client could claim to skate (or not)
     * regardless of what it's holding, and other players would never see the truth. Instead the
     * server alone evaluates the held item each tick via {@link SkateController} (a pure,
     * unit-tested class — see {@code SkateControllerTest}) and pushes the authoritative result to
     * every tracking client with {@link SkatingStatePayload}; the client only ever mirrors it
     * (see {@link SkateClientState}) and never recomputes it.
     */
    @EventBusSubscriber(modid = MODID)
    public static final class ServerEvents {
        private ServerEvents() {}

        @SubscribeEvent
        public static void serverPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            SkateController controller = CONTROLLERS.computeIfAbsent(player.getUUID(), id -> new SkateController());
            boolean mainHandIsSkateboard = player.getItemInHand(InteractionHand.MAIN_HAND).is(SKATEBOARD);
            boolean wasSkating = controller.isSkating();
            boolean isSkating = controller.update(mainHandIsSkateboard) != SkateState.GROUNDED;
            if (isSkating != wasSkating) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SkatingStatePayload(player.getUUID(), isSkating));
            }
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            CONTROLLERS.remove(event.getEntity().getUUID());
        }
    }

    /** Applies the custom skate stance at render time; never touches sneak/crouch state. */
    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {}

        @SubscribeEvent
        public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
            if (SkateClientState.isSkating(event.getEntity().getUUID())) {
                event.getPoseStack().translate(0.0, -SkateConstants.SKATE_STANCE_HEIGHT_OFFSET, 0.0);
            }
        }
    }
}
