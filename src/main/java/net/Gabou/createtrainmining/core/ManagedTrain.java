package net.Gabou.createtrainmining.core;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;

import net.Gabou.createtrainmining.api.DriveDirection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Resolves the live Create object each access. No copied motion/navigation state. */
public final class ManagedTrain {
    private final UUID id;

    public ManagedTrain(UUID id) {
        this.id = id;
    }

    Train unwrap() {
        Train train = Create.RAILWAYS.trains.get(id);
        if (train == null || train.invalid)
            throw new IllegalStateException("Selected train is unavailable: " + id);
        return train;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return unwrap().name.getString();
    }

    public UUID getCurrentGraph() {
        var graph = unwrap().graph;
        return graph == null ? null : graph.id;
    }

    public String getCurrentStation() {
        var station = unwrap().getCurrentStation();
        return station == null ? null : station.name;
    }

    public double getSpeed() {
        return unwrap().speed;
    }

    public double getTargetSpeed() {
        return unwrap().targetSpeed;
    }

    public record TrackPosition(ResourceKey<Level> dimension, Vec3 position) {}

    /** Actual graph position, independent of requested/reported speed. Portals are not sampled. */
    public TrackPosition getTrackPosition() {
        var train = unwrap();
        if (train.graph == null || train.carriages.isEmpty()) return null;
        var point = train.carriages.getFirst().getLeadingPoint();
        if (point.edge == null || point.node1 == null || point.node2 == null
                || point.edge.isInterDimensional()) return null;
        return new TrackPosition(point.node1.getLocation().getDimension(), point.getPosition(train.graph));
    }

    /** Check every loaded carriage so a long train remains protected while leaving a station. */
    public boolean isNearStation(double radius) {
        boolean[] near = {false};
        var stations = Create.RAILWAYS.trackNetworks.values().stream()
                .flatMap(g -> g.getPoints(com.simibubi.create.content.trains.graph.EdgePointType.STATION).stream())
                .toList();
        unwrap().carriages.forEach(c -> c.forEachPresentEntity(e -> {
            for (var station : stations)
                if (station.getBlockEntityPos() != null
                        && e.level().dimension().equals(station.getBlockEntityDimension())
                        && e.position().distanceToSqr(Vec3.atCenterOf(station.getBlockEntityPos())) <= radius * radius)
                    near[0] = true;
        }));
        return near[0];
    }

    /** Remove only lava blocks immediately around enabled drill faces; never delete solid blocks. */
    public void clearLavaAroundDrills() {
        unwrap().carriages.forEach(c -> c.forEachPresentEntity(e -> {
            var contraption = e.getContraption();
            if (contraption == null || e.level().isClientSide) return;
            for (var actor : contraption.getActors()) {
                var ctx = actor.getRight();
                if (ctx == null || ctx.disabled || ctx.position == null
                        || !(actor.getLeft().state().getBlock() instanceof com.simibubi.create.content.kinetics.drill.DrillBlock)) continue;
                var center = net.minecraft.core.BlockPos.containing(ctx.position);
                for (var pos : net.minecraft.core.BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
                    var state = e.level().getBlockState(pos);
                    if (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                            && state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA))
                        e.level().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }));
    }

    public boolean isWaitingForSignal() {
        return unwrap().navigation.waitingForSignal != null;
    }

    public DriveDirection getDirection() {
        return (unwrap().speed == 0 ? unwrap().currentlyBackwards : unwrap().speed < 0)
                ? DriveDirection.BACKWARD
                : DriveDirection.FORWARD;
    }

    public boolean isDerailed() {
        return unwrap().derailed;
    }

    public boolean isWaitingForChunks() {
        return unwrap().carriageWaitingForChunks != -1;
    }

    public boolean isNavigating() {
        return unwrap().navigation.destination != null;
    }

    /** A station name alone can be stale during a navigation handoff. */
    public boolean isDockedAt(String station) {
        return station.equals(getCurrentStation()) && !isNavigating() && Math.abs(getSpeed()) < 1e-6;
    }

    public boolean hasSchedule() {
        return unwrap().runtime.getSchedule() != null;
    }

    public boolean isSchedulePaused() {
        return unwrap().runtime.paused;
    }

    public boolean isScheduleFinished() {
        return unwrap().runtime.completed;
    }

    public int getCarriageCount() {
        return unwrap().carriages.size();
    }

    public boolean hasForwardConductor() {
        return unwrap().hasForwardConductor();
    }

    public boolean hasBackwardConductor() {
        return unwrap().hasBackwardConductor();
    }

    public boolean hasPlayerControl() {
        boolean[] controlled = {false};
        unwrap().carriages
                .forEach(
                        c ->
                                c.forEachPresentEntity(
                                        e ->
                                                controlled[0] |=
                                                        e.getControllingPlayer().isPresent()));
        return controlled[0];
    }

    public TrainInventoryView getInventory() {
        return new TrainInventoryView(this);
    }
}
