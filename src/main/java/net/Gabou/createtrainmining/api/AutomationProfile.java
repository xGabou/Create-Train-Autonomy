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

    /** Optional operator request; profiles retain responsibility for their return workflow. */
    default void requestReturn(AutomationContext context) {
        throw new IllegalStateException("This automation profile does not support returning");
    }

    CompoundTag serializeState();

    void deserializeState(CompoundTag state);

    String getStatus();

    List<ConfigurationField> getConfigurationSchema();

    default void validateConfiguration(Map<String, Object> config) {}
}
