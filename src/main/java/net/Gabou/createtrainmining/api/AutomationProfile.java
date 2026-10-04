package net.Gabou.createtrainmining.api;

import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Map;

public interface AutomationProfile {
    void onStart(AutomationContext context);

    void onStop(AutomationContext context);

    void tick(AutomationContext context);

    /** Runs before Create moves the train, for controls that must precede mounted actors. */
    default void beforeTrainTick(AutomationContext context) {}

    default void onTrainArrived(AutomationContext context, String station) {}

    default void onInventoryChanged(AutomationContext context) {}

    CompoundTag serializeState();

    void deserializeState(CompoundTag state);

    String getStatus();

    List<ConfigurationField> getConfigurationSchema();

    default void validateConfiguration(Map<String, Object> config) {}
}
