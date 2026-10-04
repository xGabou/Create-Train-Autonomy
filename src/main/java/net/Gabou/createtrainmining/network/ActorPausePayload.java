package net.Gabou.createtrainmining.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** Sync temporary tool inactivity without changing Create's saved control filters. */
public record ActorPausePayload(int entityId, ItemStack filter, boolean paused) {
    public static ActorPausePayload decode(FriendlyByteBuf buffer) {
        return new ActorPausePayload(buffer.readVarInt(), buffer.readItem(), buffer.readBoolean());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeItem(filter);
        buffer.writeBoolean(paused);
    }
}
