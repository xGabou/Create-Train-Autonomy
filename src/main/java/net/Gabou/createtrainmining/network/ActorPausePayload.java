package net.Gabou.createtrainmining.network;

import net.Gabou.createtrainmining.Createtrainmining;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Sync temporary tool inactivity without changing Create's saved control filters. */
public record ActorPausePayload(int entityId, ItemStack filter, boolean paused)
        implements CustomPacketPayload {
    public static final Type<ActorPausePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Createtrainmining.MODID, "actor_pause"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActorPausePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ActorPausePayload::entityId,
                    ItemStack.STREAM_CODEC, ActorPausePayload::filter,
                    ByteBufCodecs.BOOL, ActorPausePayload::paused,
                    ActorPausePayload::new);

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
