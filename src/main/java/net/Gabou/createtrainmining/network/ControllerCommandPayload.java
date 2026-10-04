package net.Gabou.createtrainmining.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record ControllerCommandPayload(
        int menuId, BlockPos pos, String action, String key, String value) {
    public static ControllerCommandPayload decode(FriendlyByteBuf buffer) {
        return new ControllerCommandPayload(buffer.readVarInt(), buffer.readBlockPos(),
                buffer.readUtf(32), buffer.readUtf(128), buffer.readUtf(256));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menuId);
        buffer.writeBlockPos(pos);
        buffer.writeUtf(action, 32);
        buffer.writeUtf(key, 128);
        buffer.writeUtf(value, 256);
    }
}
