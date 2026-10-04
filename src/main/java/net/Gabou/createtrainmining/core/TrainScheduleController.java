package net.Gabou.createtrainmining.core;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;

import net.Gabou.createtrainmining.api.TrainSchedule;

import java.util.ArrayList;

/** Create schedule construction stays inside this adapter. */
public final class TrainScheduleController {
    public void validate(Train train, TrainSchedule request) {
        if (train.graph == null || train.derailed)
            throw new IllegalStateException("Train must be on a valid track graph");
        for (String name : request.stations()) {
            var instruction = destination(name);
            var matches = new ArrayList<com.simibubi.create.content.trains.station.GlobalStation>();
            for (var station : train.graph.getPoints(EdgePointType.STATION))
                if (station.name.matches(instruction.getFilterForRegex())) matches.add(station);
            if (matches.isEmpty())
                throw new IllegalArgumentException("No matching station on this graph: " + name);
            if (train.navigation.findPathTo(matches, Double.MAX_VALUE) == null)
                throw new IllegalStateException("No reachable route to " + name);
        }
        if (!train.hasForwardConductor() && !train.hasBackwardConductor())
            throw new IllegalStateException("Create schedules require a conductor");
    }

    private DestinationInstruction destination(String name) {
        var instruction = new DestinationInstruction();
        instruction.getData().putString("Text", name);
        return instruction;
    }

    public void applySchedule(Train train, TrainSchedule request) {
        var schedule = new Schedule();
        schedule.cyclic = request.cyclic();
        for (String name : request.stations()) {
            var entry = new ScheduleEntry();
            entry.instruction = destination(name);
            entry.conditions.add(new ArrayList<>());
            schedule.entries.add(entry);
        }
        train.runtime.setSchedule(schedule, false);
    }

    public void goToStation(Train train, String name) {
        applySchedule(train, TrainSchedule.toStation(name));
    }

    public void clearSchedule(Train train) {
        train.runtime.discardSchedule();
    }

    public boolean hasSchedule(Train train) {
        return train.runtime.getSchedule() != null;
    }

    public String getCurrentDestination(Train train) {
        return train.navigation.destination == null ? null : train.navigation.destination.name;
    }

    public boolean isNavigating(Train train) {
        return train.navigation.destination != null;
    }

    public void pauseSchedule(Train train) {
        train.runtime.paused = true;
        train.navigation.cancelNavigation();
    }

    public void resumeSchedule(Train train) {
        if (train.runtime.getSchedule() == null)
            throw new IllegalStateException("No schedule to resume");
        train.runtime.completed = false;
        train.runtime.paused = false;
    }
}
