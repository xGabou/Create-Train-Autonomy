package net.Gabou.createtrainmining.api;

@FunctionalInterface
public interface TrainCondition {
    boolean test(AutomationContext context);
}
