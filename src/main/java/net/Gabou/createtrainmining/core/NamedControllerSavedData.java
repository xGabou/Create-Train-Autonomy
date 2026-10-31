package net.Gabou.createtrainmining.core;

import net.Gabou.createtrainmining.block.TrainAutomationControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Durable addresses, including inactive controllers in unloaded chunks and other dimensions. */
public final class NamedControllerSavedData extends SavedData {
    public record Address(ResourceKey<Level> dimension, BlockPos pos, UUID controllerId) {}

    private final Map<String, Address> stations = new TreeMap<>();

    public static NamedControllerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(NamedControllerSavedData::new, NamedControllerSavedData::load),
                "createtrainmining_named_controllers");
    }

    public Map<String, Address> stations() {
        return Collections.unmodifiableMap(stations);
    }

    public void name(String name, ServerLevel level, TrainAutomationControllerBlockEntity entity) {
        if (name.isBlank() || name.length() > 64 || !name.equals(name.trim()))
            throw new IllegalArgumentException("Use a station name of 1–64 characters without surrounding spaces");
        var address = new Address(level.dimension(), entity.getBlockPos().immutable(),
                entity.controller().getControllerId());
        var existing = stations.get(name);
        if (existing != null && !existing.equals(address))
            throw new IllegalArgumentException("A controller is already named " + name);
        // Naming the same block again renames it instead of leaving aliases behind.
        stations.entrySet().removeIf(entry -> entry.getValue().equals(address));
        stations.put(name, address);
        entity.setChanged();
        setDirty();
    }

    public void forget(ServerLevel level, BlockPos pos) {
        if (stations.entrySet().removeIf(entry -> entry.getValue().dimension().equals(level.dimension())
                && entry.getValue().pos().equals(pos))) setDirty();
    }

    public TrainAutomationControllerBlockEntity resolve(MinecraftServer server, String name) {
        var address = stations.get(name);
        if (address == null) throw new IllegalArgumentException("No controller named " + name);
        var level = server.getLevel(address.dimension());
        if (level == null) throw new IllegalStateException("The controller's dimension is unavailable");
        // A single synchronous load permits recovery of an inactive controller without a lasting ticket.
        level.getChunkAt(address.pos());
        if (level.getBlockEntity(address.pos()) instanceof TrainAutomationControllerBlockEntity entity
                && entity.controller().getControllerId().equals(address.controllerId())) return entity;
        stations.remove(name);
        setDirty();
        throw new IllegalStateException("Named controller was removed or replaced; register its name again");
    }

    public static NamedControllerSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        var result = new NamedControllerSavedData();
        for (var value : tag.getList("Stations", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            var dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension != null && entry.hasUUID("Controller") && !entry.getString("Name").isBlank())
                result.stations.put(entry.getString("Name"), new Address(
                        ResourceKey.create(Registries.DIMENSION, dimension),
                        BlockPos.of(entry.getLong("Pos")), entry.getUUID("Controller")));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        var list = new ListTag();
        stations.forEach((name, address) -> {
            var entry = new CompoundTag();
            entry.putString("Name", name);
            entry.putString("Dimension", address.dimension().location().toString());
            entry.putLong("Pos", address.pos().asLong());
            entry.putUUID("Controller", address.controllerId());
            list.add(entry);
        });
        tag.put("Stations", list);
        return tag;
    }
}
