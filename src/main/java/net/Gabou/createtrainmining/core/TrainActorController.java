package net.Gabou.createtrainmining.core;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionDisableActorPacket;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Only loaded contraptions and actors whose behaviour explicitly supports disabling are exposed.
 */
public final class TrainActorController {
    private void visit(
            ManagedTrain train, BiConsumer<CarriageContraptionEntity, Contraption> consumer) {
        train.unwrap()
                .carriages
                .forEach(
                        c ->
                                c.forEachPresentEntity(
                                        e -> {
                                            if (e.getContraption() != null)
                                                consumer.accept(e, e.getContraption());
                                        }));
    }

    private Map<ResourceLocation, ItemStack> types(Contraption contraption) {
        var result = new LinkedHashMap<ResourceLocation, ItemStack>();
        for (var actor : contraption.getActors()) {
            var behavior = MovementBehaviour.REGISTRY.get(actor.getLeft().state());
            if (behavior == null) continue;
            var filter = behavior.canBeDisabledVia(actor.getRight());
            if (filter != null && !filter.isEmpty())
                result.put(BuiltInRegistries.ITEM.getKey(filter.getItem()), filter.copy());
        }
        return result;
    }

    public List<String> listActorTypes(ManagedTrain train) {
        var result = new java.util.TreeSet<String>();
        visit(train, (e, c) -> types(c).keySet().forEach(id -> result.add(id.toString())));
        return List.copyOf(result);
    }

    public boolean setActorTypeEnabled(
            ManagedTrain train, ResourceLocation itemType, boolean enabled) {
        boolean[] changed = {false};
        visit(
                train,
                (entity, contraption) -> {
                    var filter = types(contraption).get(itemType);
                    if (filter == null) return;
                    var disabled = contraption.getDisabledActors();
                    if (enabled && disabled.stream().anyMatch(ItemStack::isEmpty))
                        throw new IllegalStateException(
                                "Actors are disabled by an all-actors contraption control");
                    disabled.removeIf(i -> ContraptionControlsMovement.isSameFilter(i, filter));
                    if (!enabled) disabled.add(filter.copy());
                    contraption.setActorsActive(filter, enabled);
                    CatnipServices.NETWORK.sendToClientsTrackingEntity(
                            entity,
                            new ContraptionDisableActorPacket(entity.getId(), filter, enabled));
                    changed[0] = true;
                });
        return changed[0];
    }

    public boolean isActorTypeEnabled(ManagedTrain train, ResourceLocation type) {
        boolean[] found = {false}, enabled = {true};
        visit(
                train,
                (e, c) -> {
                    var filter = types(c).get(type);
                    if (filter != null) {
                        found[0] = true;
                        enabled[0] &=
                                !c.isActorTypeDisabled(filter)
                                        && !c.isActorTypeDisabled(ItemStack.EMPTY);
                    }
                });
        return found[0] && enabled[0];
    }
}
