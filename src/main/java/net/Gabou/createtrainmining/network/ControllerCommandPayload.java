package net.Gabou.createtrainmining.network;

import net.Gabou.createtrainmining.Createtrainmining;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ControllerCommandPayload(
        int menuId, BlockPos pos, String action, String key, String value)
        implements CustomPacketPayload {
    public static final Type<ControllerCommandPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            Createtrainmining.MODID, "controller_command"));
    public static final StreamCodec<FriendlyByteBuf, ControllerCommandPayload> CODEC =
            StreamCodec.of(
                    (b, p) -> {
                        b.writeVarInt(p.menuId);
                        b.writeBlockPos(p.pos);
                        b.writeUtf(p.action, 32);
                        b.writeUtf(p.key, 128);
                        b.writeUtf(p.value, 256);
                    },
                    b ->
                            new ControllerCommandPayload(
                                    b.readVarInt(),
                                    b.readBlockPos(),
                                    b.readUtf(32),
                                    b.readUtf(128),
                                    b.readUtf(256)));

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
