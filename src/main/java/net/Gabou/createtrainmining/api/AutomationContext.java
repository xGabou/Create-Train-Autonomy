package net.Gabou.createtrainmining.api;

import net.Gabou.createtrainmining.core.ManagedTrain;
import net.Gabou.createtrainmining.core.TrainController;
import net.Gabou.createtrainmining.core.TrainInventoryView;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AutomationContext {
    ManagedTrain train();

    TrainController controller();

    default TrainInventoryView inventory() {
        return train().getInventory();
    }

    List<String> stations();

    long currentTime();

    Map<String, Object> configuration();

    CompoundTag persistentData();

    int redstonePower();

    Optional<Object> integration(String id);

    void markDirty();
}
