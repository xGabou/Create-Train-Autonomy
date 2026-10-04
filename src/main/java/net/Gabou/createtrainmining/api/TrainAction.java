package net.Gabou.createtrainmining.api;

@FunctionalInterface
public interface TrainAction {
    void execute(AutomationContext context);
}
