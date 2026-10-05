package net.Gabou.createtrainmining.core;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.Gabou.createtrainmining.api.DriveDirection;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.Vec3;

/** Read-only graph scouts, including tool overhangs and every carriage through the slope exit. */
public final class TrainTrackProbe {
    private TrainTrackProbe() {}

    public static boolean hasSlopeNearTrain(Train train, DriveDirection direction, double lookAhead) {
        if (!Double.isFinite(lookAhead) || lookAhead < 0 || lookAhead > 64)
            throw new IllegalArgumentException("Slope lookahead must be between 0 and 64 blocks");
        if (train.graph == null || train.carriages.isEmpty()) return true;
        int sign = Math.abs(train.speed) > 1e-5 ? (train.speed < 0 ? -1 : 1) : direction.sign();
        // Cover the next movement tick as well as the configured distance ahead of the tools.
        double ahead = lookAhead + Math.abs(train.speed) * 2;
        for (int i = 0; i < train.carriages.size(); i++) {
            var carriage = train.carriages.get(i);
            var point = carriage.getLeadingPoint();
            if (point.edge == null || point.node1 == null || point.node2 == null
                    || carriage.leadingBogey().trailing().edge == null
                    || carriage.getTrailingPoint().edge == null) return true;
            double[] reach = toolReach(carriage);
            var forwardTarget = i > 0 ? train.carriages.get(i - 1).getTrailingPoint() : null;
            var backwardTarget = i + 1 < train.carriages.size()
                    ? train.carriages.get(i + 1).getLeadingPoint() : carriage.getTrailingPoint();
            if (scan(train, point, reach[0] + (sign > 0 ? ahead : 0), 1, forwardTarget)
                    || scan(train, point, reach[1] + (sign < 0 ? ahead : 0), -1, backwardTarget))
                return true;
        }
        return false;
    }

    private static double[] toolReach(Carriage carriage) {
        var bogey = carriage.leadingBogey();
        double halfWheelSpacing = bogey.leading().getPosition(carriage.train.graph)
                .distanceTo(bogey.trailing().getPosition(carriage.train.graph)) / 2;
        double[] reach = {2, carriage.bogeySpacing + halfWheelSpacing * 2 + 2};
        carriage.forEachPresentEntity(entity -> {
            if (!(entity.getContraption() instanceof CarriageContraption contraption)
                    || contraption.bounds == null) return;
            var bounds = contraption.bounds;
            var direction = contraption.getAssemblyDirection();
            boolean x = direction.getAxis() == Axis.X;
            double min = x ? bounds.minX : bounds.minZ;
            double max = x ? bounds.maxX : bounds.maxZ;
            double a = min * direction.getAxisDirection().getStep();
            double b = max * direction.getAxisDirection().getStep();
            // The contraption origin is the front bogey anchor, behind its leading wheel point.
            // Two extra blocks cover deployer reach and drill faces.
            reach[0] = Math.max(reach[0], Math.max(a, b) - halfWheelSpacing + 2);
            reach[1] = Math.max(reach[1], -Math.min(a, b) + halfWheelSpacing + 2);
        });
        return reach;
    }

    private static boolean scan(Train train, TravellingPoint point, double distance, int sign,
            TravellingPoint follow) {
        var scout = new TravellingPoint(point.node1, point.node2, point.edge, point.position,
                point.upsideDown);
        var selector = follow == null
                ? scout.steer(train.manualSteer, new Vec3(0, 1, 0)) : scout.follow(follow);
        for (double remaining = distance; remaining > 0; remaining -= .5) {
            double length = scout.edge.getLength();
            if (scout.edge.isInterDimensional()) return true;
            if (length > 0) {
                double t = scout.position / length;
                double step = .25 / length;
                double before = scout.edge.getPosition(train.graph, Math.max(0, t - step)).y;
                double after = scout.edge.getPosition(train.graph, Math.min(1, t + step)).y;
                if (Math.abs(after - before) > 1e-4) return true;
            }
            double moved = Math.abs(scout.travel(train.graph, Math.min(.5, remaining) * sign,
                    selector, scout.ignoreEdgePoints(), scout.ignoreTurns(), portal -> true));
            if (scout.blocked || moved < 1e-6) break;
        }
        return false;
    }
}
