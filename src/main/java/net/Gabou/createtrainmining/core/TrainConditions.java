package net.Gabou.createtrainmining.core;

import net.Gabou.createtrainmining.api.TrainCondition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class TrainConditions {
    private TrainConditions() {}

    public static TrainCondition inventoryPercent(double minimum) {
        return c -> c.inventory().getUsageRatio() >= minimum;
    }

    public static TrainCondition inventoryItem(ResourceLocation item, long count) {
        return c ->
                c
                                .inventory()
                                .findItems(
                                        s ->
                                                BuiltInRegistries.ITEM
                                                        .getKey(s.getItem())
                                                        .equals(item))
                                .stream()
                                .mapToLong(s -> s.getCount())
                                .sum()
                        >= count;
    }

    public static TrainCondition atStation(String station) {
        return c -> station.equals(c.train().getCurrentStation());
    }

    public static TrainCondition stopped() {
        return c -> Math.abs(c.train().getSpeed()) < 1e-4;
    }

    public static TrainCondition timer(long started, long duration) {
        return c -> c.currentTime() - started >= duration;
    }

    public static TrainCondition redstone(int minimum) {
        return c -> c.redstonePower() >= minimum;
    }

    public static TrainCondition speed(double minimum) {
        return c -> Math.abs(c.train().getSpeed()) >= minimum;
    }

    public static TrainCondition scheduleFinished() {
        return c -> c.train().isScheduleFinished();
    }
}
