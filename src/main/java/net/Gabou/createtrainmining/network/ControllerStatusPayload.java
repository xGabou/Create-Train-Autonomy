package net.Gabou.createtrainmining.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record ControllerStatusPayload(int menuId, CompoundTag status) {
    public static ControllerStatusPayload decode(FriendlyByteBuf buffer) {
        return new ControllerStatusPayload(buffer.readVarInt(), buffer.readNbt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menuId);
        buffer.writeNbt(status);
    }
}
