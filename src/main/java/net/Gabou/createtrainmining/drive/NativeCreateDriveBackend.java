package net.Gabou.createtrainmining.drive;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.Gabou.createtrainmining.api.*;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uses Create's manual steering and acceleration once immediately before Train runtime/navigation.
 */
public class NativeCreateDriveBackend implements TrainDriveBackend {
    protected DriveRequest request;
    private UUID activeTrain;

    public boolean isAvailable(Train train) {
        return train.graph != null
                && !train.derailed
                && !train.invalid
                && !train.carriages.isEmpty();
    }

    public void start(Train train, DriveRequest request) {
        if (!isAvailable(train))
            throw new IllegalStateException("Train is derailed or has no track graph");
        if (activeTrain != null && !activeTrain.equals(train.id))
            throw new IllegalStateException("Backend already attached to another train");
        this.request = request;
        activeTrain = train.id;
    }

    public void stop(Train train) {
        if (activeTrain != null && !activeTrain.equals(train.id)) return;
        request = null;
        activeTrain = null;
        train.targetSpeed = 0;
        train.speed = 0;
        train.cancelStall();
        train.manualTick = false;
        train.manualSteer = SteerDirection.NONE;
        train.backwardsDriver = null;
        train.navigation.waitingForSignal = null;
        train.navigation.distanceToSignal = Double.MAX_VALUE;
        releaseReservations(train);
    }

    public void tick(Train train) {
        if (!isDriving(train)) return;
        if (!isAvailable(train))
            throw new IllegalStateException("Direct drive lost its track graph or derailed");
        if (!train.runtime.paused || train.navigation.destination != null)
            throw new IllegalStateException(
                    "Create navigation acquired movement during direct control");
        if (!request.allowChunkWaiting() && train.carriageWaitingForChunks != -1)
            throw new IllegalStateException("Train is waiting for unloaded chunks");
        if (train.speedBeforeStall != null
                && request.speed() > 0
                && Math.signum(train.speedBeforeStall) * request.direction().sign() < 0)
            train.cancelStall();
        if (train.carriages.stream().anyMatch(c -> c.stalled)
                || train.carriageWaitingForChunks != -1) {
            train.targetSpeed = 0;
            train.manualTick = true;
            return;
        }
        train.manualSteer =
                switch (request.switchStrategy()) {
                    case STRAIGHT -> SteerDirection.NONE;
                    case LEFT -> SteerDirection.LEFT;
                    case RIGHT -> SteerDirection.RIGHT;
                };
        int requestedSign = request.direction().sign();
        // Fuel may expire or be acquired this tick; compute braking using the resulting
        // acceleration.
        if (request.speed() > 0) train.burnFuel();
        // Brake in the current travel direction before switching ends. Scout the actual leading
        // end.
        int motionSign = Math.abs(train.speed) > 1e-5 ? (train.speed < 0 ? -1 : 1) : requestedSign;
        double top = train.maxSpeed() * AllConfigs.server().trains.manualTrainSpeedModifier.getF();
        double desired = Math.min(top, top * train.throttle) * request.speed();
        if (requestedSign < 0) desired = Math.min(desired, top / 4);
        if (motionSign != requestedSign) desired = 0;
        var leading =
                motionSign > 0
                        ? train.carriages.get(0).getLeadingPoint()
                        : train.carriages.get(train.carriages.size() - 1).getTrailingPoint();
        if (leading.edge == null || leading.node1 == null || leading.node2 == null)
            throw new IllegalStateException("Train has no valid travelling point");
        releaseReservations(train);
        // Reserve enough room even if fuel expires before the next tick.
        double acceleration =
                Math.min(
                        train.acceleration(),
                        Math.min(
                                        AllConfigs.server().trains.trainAcceleration.getF(),
                                        AllConfigs.server().trains.poweredTrainAcceleration.getF())
                                / 400.0);
        if (!(acceleration > 0))
            throw new IllegalStateException("Create train acceleration must be positive");
        double lookAhead =
                Math.max(
                        4.5,
                        Math.max(train.speed * train.speed, desired * desired) / (2 * acceleration)
                                + Math.abs(train.speed)
                                + 3);
        var scout =
                new TravellingPoint(
                        leading.node1,
                        leading.node2,
                        leading.edge,
                        leading.position,
                        leading.upsideDown);
        var scan =
                new Scan(
                        train,
                        lookAhead,
                        request.signalBehavior() == DriveRequest.SignalBehavior.OBEY);
        double travelled =
                Math.abs(
                        scout.travel(
                                train.graph,
                                Math.max(lookAhead, 256) * motionSign,
                                scout.steer(train.manualSteer, new Vec3(0, 1, 0)),
                                scan::visit,
                                (distance, edge) -> {
                                    if (distance < lookAhead) scan.curve = true;
                                }));
        // Unknown/unbounded cross-signal chains are conservatively blocked at their entry.
        if (scan.chain != null) scan.block(scan.chain, scan.chainSide, scan.chainDistance);
        train.navigation.waitingForSignal = scan.blockedSignal;
        train.navigation.distanceToSignal = scan.stopDistance;
        if (scan.blockedSignal != null)
            desired =
                    Math.min(desired, DriveSafety.safeSpeed(acceleration, scan.stopDistance, 0.5));
        if (scout.blocked) {
            desired = Math.min(desired, DriveSafety.safeSpeed(acceleration, travelled, 0.125));
            if (request.stopAtTrackEnd() && travelled <= 0.15 && Math.abs(train.speed) < 0.01) {
                stop(train);
                return;
            }
        }
        // A curve in the braking envelope or beneath any bogey imposes Create's turn speed.
        boolean onCurve =
                train.carriages.stream()
                        .anyMatch(
                                c ->
                                        c.getLeadingPoint().edge != null
                                                        && c.getLeadingPoint().edge.isTurn()
                                                || c.getTrailingPoint().edge != null
                                                        && c.getTrailingPoint().edge.isTurn());
        if (scan.curve || onCurve) desired = Math.min(desired, train.maxTurnSpeed());
        train.targetSpeed = desired * (motionSign != requestedSign ? motionSign : requestedSign);
        train.currentlyBackwards = motionSign < 0;
        train.manualTick = true;
        train.approachTargetSpeed(1);
    }

    private static void releaseReservations(Train train) {
        for (UUID id : train.reservedSignalBlocks) {
            var group = Create.RAILWAYS.signalEdgeGroups.get(id);
            if (group != null && !train.occupiedSignalBlocks.containsKey(id))
                group.trains.remove(train);
        }
        train.reservedSignalBlocks.clear();
    }

    private static final class Scan {
        final Train train;
        final double horizon;
        final boolean obey;
        boolean curve;
        double stopDistance = Double.MAX_VALUE;
        Pair<UUID, Boolean> blockedSignal;
        SignalBoundary chain;
        boolean chainSide;
        double chainDistance;
        final List<Pair<SignalEdgeGroup, SignalBoundary>> chainGroups = new ArrayList<>();

        Scan(Train train, double horizon, boolean obey) {
            this.train = train;
            this.horizon = horizon;
            this.obey = obey;
        }

        void block(SignalBoundary signal, boolean side, double distance) {
            if (distance < stopDistance) {
                stopDistance = distance;
                blockedSignal = Pair.of(signal.id, side);
            }
        }

        boolean visit(
                Double distance,
                Pair<
                                com.simibubi.create.content.trains.signal.TrackEdgePoint,
                                net.createmod.catnip.data.Couple<
                                        com.simibubi.create.content.trains.graph.TrackNode>>
                        point) {
            if (distance > horizon && chain == null) return true;
            if (!obey || !(point.getFirst() instanceof SignalBoundary signal)) return false;
            var enteringNode = point.getSecond().getSecond();
            UUID groupId = signal.getGroup(enteringNode);
            var group = Create.RAILWAYS.signalEdgeGroups.get(groupId);
            if (group == null) {
                block(signal, true, distance);
                return true;
            }
            boolean side = groupId.equals(signal.groups.getFirst());
            boolean cross = signal.types.get(side) == SignalType.CROSS_SIGNAL;
            boolean occupied = signal.isForcedRed(enteringNode) || group.isOccupiedUnless(train);
            if (chain == null && cross) {
                chain = signal;
                chainSide = side;
                chainDistance = distance;
            }
            if (occupied) {
                block(
                        chain == null ? signal : chain,
                        chain == null ? side : chainSide,
                        chain == null ? distance : chainDistance);
                return true;
            }
            if (chain != null) {
                chainGroups.add(Pair.of(group, signal));
                if (!cross) {
                    chainGroups.forEach(p -> reserve(p.getFirst(), p.getSecond()));
                    chainGroups.clear();
                    chain = null;
                }
            } else reserve(group, signal);
            return false;
        }

        void reserve(SignalEdgeGroup group, SignalBoundary signal) {
            group.reserved = signal;
            train.reservedSignalBlocks.add(group.id);
        }
    }

    public void setSpeed(Train train, double speed) {
        requireDriving(train);
        request = request.withSpeed(speed);
    }

    public void setDirection(Train train, DriveDirection direction) {
        requireDriving(train);
        request = request.withDirection(direction);
    }

    private void requireDriving(Train train) {
        if (!isDriving(train)) throw new IllegalStateException("Train is not under direct control");
    }

    public double getSpeed(Train train) {
        return train.speed;
    }

    public DriveDirection getDirection(Train train) {
        return request == null
                ? (train.currentlyBackwards ? DriveDirection.BACKWARD : DriveDirection.FORWARD)
                : request.direction();
    }

    public boolean isDriving(Train train) {
        return request != null && train.id.equals(activeTrain);
    }

    public String getId() {
        return "native";
    }

    public String getDisplayName() {
        return "Native Create controls";
    }
}
