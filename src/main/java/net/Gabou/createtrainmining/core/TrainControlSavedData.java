package net.Gabou.createtrainmining.core;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persist leases so an owned train cannot resume an old schedule before its controller reloads. */
public final class TrainControlSavedData extends SavedData {
    final Map<UUID, UUID> owners = new HashMap<>();

    public static TrainControlSavedData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(
                        TrainControlSavedData::load, TrainControlSavedData::new,
                        "createtrainmining_train_ownership");
    }

    private static TrainControlSavedData load(CompoundTag tag) {
        var result = new TrainControlSavedData();
        for (var value : tag.getList("Owners", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            if (entry.hasUUID("Train") && entry.hasUUID("Controller"))
                result.owners.put(entry.getUUID("Train"), entry.getUUID("Controller"));
        }
        return result;
    }

    public CompoundTag save(CompoundTag tag) {
        var list = new ListTag();
        owners.forEach(
                (train, controller) -> {
                    var entry = new CompoundTag();
                    entry.putUUID("Train", train);
                    entry.putUUID("Controller", controller);
                    list.add(entry);
                });
        tag.put("Owners", list);
        return tag;
    }
}
