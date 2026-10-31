package net.Gabou.createtrainmining.core;

import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Moving entity-ticking footprint, owned by the controller; old chunks are released. */
final class TrainChunkLoader {
    private record LoadedChunk(ResourceKey<Level> dimension, ChunkPos position) {}

    private final MinecraftServer server;
    private final Set<LoadedChunk> loaded = new HashSet<>();

    TrainChunkLoader(MinecraftServer server) {
        this.server = server;
    }

    void update(Train train, UUID owner) {
        Set<LoadedChunk> wanted = new HashSet<>();
        if (train.graph != null && !train.derailed && !train.invalid) {
            for (var carriage : train.carriages) {
                carriage.bogeys.forEach(bogey -> {
                    if (bogey == null) return;
                    List.of(bogey.leading(), bogey.trailing()).forEach(point -> {
                        if (point.node1 == null || point.node2 == null || point.edge == null) return;
                        if (point.edge.isInterDimensional()) {
                            addAround(wanted, point.node1.getLocation().getDimension(), point.node1.getLocation().getLocation());
                            addAround(wanted, point.node2.getLocation().getDimension(), point.node2.getLocation().getLocation());
                        } else addAround(wanted, point.node1.getLocation().getDimension(), point.getPosition(train.graph));
                    });
                });
                // Anchors remain available when Create has discarded the unloaded carriage entity.
                for (var dimension : carriage.getPresentDimensions()) {
                    var anchor = carriage.getDimensional(dimension).positionAnchor;
                    if (anchor != null) addAround(wanted, dimension, anchor);
                }
                carriage.forEachPresentEntity(entity -> {
                    AABB bounds = entity.getBoundingBox().inflate(16);
                    var low = new ChunkPos(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ));
                    var high = new ChunkPos(BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ));
                    for (int x = low.x; x <= high.x; x++)
                        for (int z = low.z; z <= high.z; z++)
                            wanted.add(new LoadedChunk(entity.level().dimension(), new ChunkPos(x, z)));
                });
            }
        }
        // Load the new footprint before releasing the old one, avoiding gaps at chunk borders.
        for (var chunk : wanted) {
            var level = server.getLevel(chunk.dimension());
            if (level != null && loaded.add(chunk))
                ControllerChunkLoader.setTrainLoaded(level, owner, chunk.position(), true);
        }
        for (var chunk : Set.copyOf(loaded)) {
            if (!wanted.contains(chunk)) {
                var level = server.getLevel(chunk.dimension());
                if (level != null) ControllerChunkLoader.setTrainLoaded(level, owner, chunk.position(), false);
                loaded.remove(chunk);
            }
        }
    }

    private static void addAround(Set<LoadedChunk> chunks, ResourceKey<Level> dimension, Vec3 position) {
        var center = new ChunkPos(BlockPos.containing(position));
        for (int x = center.x - 1; x <= center.x + 1; x++)
            for (int z = center.z - 1; z <= center.z + 1; z++)
                chunks.add(new LoadedChunk(dimension, new ChunkPos(x, z)));
    }

    void release(UUID owner) {
        for (var chunk : loaded) {
            var level = server.getLevel(chunk.dimension());
            if (level != null) ControllerChunkLoader.setTrainLoaded(level, owner, chunk.position(), false);
        }
        loaded.clear();
    }
}
