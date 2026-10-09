package io.github.brooswitminecraft.dynamicskateboards;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server: the vanilla Shift, attack (left click) and use (right click) keys' currently-
 * held state, plus the four WASD keys, read raw off {@code Options} every client tick &mdash; no
 * new keybinds, same pattern and same reasoning as {@link SkateJumpInputPayload} (a normal
 * {@code ServerPlayer} has no other continuously-live authoritative source for any of these). The
 * server derives {@code attackJustPressed} itself by comparing this tick's held value against the
 * last one it stored (see {@code DynamicSkateboardsMod.ServerEvents}); the client never decides a
 * trick, a grab, or a manual on its own &mdash; it only ever reports held-key state.
 */
public record SkateTrickInputPayload(
        boolean shiftHeld,
        boolean attackHeld,
        boolean useHeld,
        boolean forwardHeld,
        boolean backHeld,
        boolean leftHeld,
        boolean rightHeld) implements CustomPacketPayload {
    public static final Type<SkateTrickInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicSkateboardsMod.MODID, "skate_trick_input"));

    // StreamCodec.composite only has overloads up to 6 fields; packed into one byte bitmask
    // instead of adding an 8th codec/getter pair.
    private static final int SHIFT_BIT = 1;
    private static final int ATTACK_BIT = 1 << 1;
    private static final int USE_BIT = 1 << 2;
    private static final int FORWARD_BIT = 1 << 3;
    private static final int BACK_BIT = 1 << 4;
    private static final int LEFT_BIT = 1 << 5;
    private static final int RIGHT_BIT = 1 << 6;

    public static final StreamCodec<RegistryFriendlyByteBuf, SkateTrickInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, SkateTrickInputPayload::toBitmask,
            SkateTrickInputPayload::fromBitmask);

    private byte toBitmask() {
        int bits = (shiftHeld ? SHIFT_BIT : 0)
                | (attackHeld ? ATTACK_BIT : 0)
                | (useHeld ? USE_BIT : 0)
                | (forwardHeld ? FORWARD_BIT : 0)
                | (backHeld ? BACK_BIT : 0)
                | (leftHeld ? LEFT_BIT : 0)
                | (rightHeld ? RIGHT_BIT : 0);
        return (byte) bits;
    }

    private static SkateTrickInputPayload fromBitmask(byte bits) {
        return new SkateTrickInputPayload(
                (bits & SHIFT_BIT) != 0,
                (bits & ATTACK_BIT) != 0,
                (bits & USE_BIT) != 0,
                (bits & FORWARD_BIT) != 0,
                (bits & BACK_BIT) != 0,
                (bits & LEFT_BIT) != 0,
                (bits & RIGHT_BIT) != 0);
    }

    @Override
    public Type<SkateTrickInputPayload> type() {
        return TYPE;
    }

    public static void handle(SkateTrickInputPayload payload, IPayloadContext context) {
        DynamicSkateboardsMod.ServerEvents.setTrickInput(context.player().getUUID(), payload);
    }
}
