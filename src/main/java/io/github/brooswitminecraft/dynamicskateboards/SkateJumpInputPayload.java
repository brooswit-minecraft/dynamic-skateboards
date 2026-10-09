package io.github.brooswitminecraft.dynamicskateboards;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server: the vanilla Jump key's currently-held state (no new keybind &mdash; this is
 * {@code Options.keyJump}, read client-side every client tick, same as any other vanilla input).
 *
 * <p>Why this exists: a normal (non-vehicle) {@code ServerPlayer}'s own {@code jumping} field is
 * only kept live by vanilla for a passenger of a ridden entity, not for a player walking around
 * on their own two feet, so the server has no other authoritative view of "is Jump currently
 * held" to drive the ollie charge from. This payload supplies exactly that, continuously, the
 * same way vanilla's own held-key packets work: because it is sent every client tick rather than
 * requested on demand, the server's view of the key lags the client's own by roughly one network
 * RTT at most &mdash; not a request/response round trip &mdash; so charge-and-release timing
 * stays responsive under normal multiplayer latency. The server still owns every decision made
 * from it ({@link SkateController} runs only on the server); the client never charges or pops an
 * ollie on its own.
 */
public record SkateJumpInputPayload(boolean jumping) implements CustomPacketPayload {
    public static final Type<SkateJumpInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicSkateboardsMod.MODID, "skate_jump_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkateJumpInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SkateJumpInputPayload::jumping,
            SkateJumpInputPayload::new);

    @Override
    public Type<SkateJumpInputPayload> type() {
        return TYPE;
    }

    public static void handle(SkateJumpInputPayload payload, IPayloadContext context) {
        DynamicSkateboardsMod.ServerEvents.setJumpHeld(context.player().getUUID(), payload.jumping());
    }
}
