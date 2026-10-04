package net.Gabou.createtrainmining.network;

import net.Gabou.createtrainmining.Createtrainmining;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ControllerStatusPayload(int menuId, CompoundTag status)
        implements CustomPacketPayload {
    public static final Type<ControllerStatusPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            Createtrainmining.MODID, "controller_status"));
    public static final StreamCodec<FriendlyByteBuf, ControllerStatusPayload> CODEC =
            StreamCodec.of(
                    (b, p) -> {
                        b.writeVarInt(p.menuId);
                        b.writeNbt(p.status);
                    },
                    b -> new ControllerStatusPayload(b.readVarInt(), b.readNbt()));

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
