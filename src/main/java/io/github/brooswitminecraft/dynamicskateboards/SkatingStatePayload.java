package io.github.brooswitminecraft.dynamicskateboards;

import java.util.UUID;
import java.util.function.IntFunction;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client notification that one player's skate animation state changed. The server is
 * the sole authority: it derives this from {@link SkateController} (fed by the player's main-hand
 * item and the authoritative jump-input mirror) and pushes it out, rather than letting clients
 * decide for themselves. Carries the full {@link SkateState}, not just a skating boolean, so the
 * client can pick the right pose (charged crouch, airborne extension, landing return).
 *
 * <p>Sent on every state transition, to every client tracking the player (self included) &mdash;
 * see {@code DynamicSkateboardsMod.ServerEvents#serverPlayerTick}. Two additional sends close the
 * gaps a transition-only broadcast leaves, both decided by {@link SkateSyncPolicy}:
 * {@code onStartTracking} sends the target's CURRENT state once, unconditionally (including
 * GROUNDED), directly to the client that just started tracking them &mdash; unconditional on
 * purpose, since gating it on "only if skating" would leave an observer who stopped tracking
 * while the target skated, then reacquired them after they went back to GROUNDED unseen, stuck
 * believing they're still skating forever; {@code onLogout} sends GROUNDED before the server
 * stops caring about that player, so no client is left holding a stale non-GROUNDED state.
 */
public record SkatingStatePayload(UUID player, SkateState state) implements CustomPacketPayload {
    public static final Type<SkatingStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicSkateboardsMod.MODID, "skating_state"));

    private static final StreamCodec<ByteBuf, SkateState> STATE_CODEC =
            ByteBufCodecs.idMapper((IntFunction<SkateState>) (i -> SkateState.values()[i]), SkateState::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, SkatingStatePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SkatingStatePayload::player,
            STATE_CODEC, SkatingStatePayload::state,
            SkatingStatePayload::new);

    @Override
    public Type<SkatingStatePayload> type() {
        return TYPE;
    }

    public static void handle(SkatingStatePayload payload, IPayloadContext context) {
        SkateClientState.set(payload.player(), payload.state());
    }
}
