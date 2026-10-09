package io.github.brooswitminecraft.dynamicskateboards;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client notification that one player's skating state changed. The server is the sole
 * authority: it derives this from the player's main-hand item ({@link SkateController}) and
 * pushes it out whenever it changes, rather than letting clients decide for themselves. Sent to
 * every client tracking the player (self included), so remote clients render the stance too.
 */
public record SkatingStatePayload(UUID player, boolean skating) implements CustomPacketPayload {
    public static final Type<SkatingStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicSkateboardsMod.MODID, "skating_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkatingStatePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SkatingStatePayload::player,
            ByteBufCodecs.BOOL, SkatingStatePayload::skating,
            SkatingStatePayload::new);

    @Override
    public Type<SkatingStatePayload> type() {
        return TYPE;
    }

    public static void handle(SkatingStatePayload payload, IPayloadContext context) {
        SkateClientState.set(payload.player(), payload.skating());
    }
}
