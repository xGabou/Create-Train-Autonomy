package net.Gabou.createtrainmining.core;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionDisableActorPacket;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;

import net.Gabou.createtrainmining.network.ActorPausePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.Gabou.createtrainmining.network.ControllerNetworking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * Only loaded contraptions and actors whose behaviour explicitly supports disabling are exposed.
 */
public final class TrainActorController {
    private Set<ResourceLocation> pausedTypes = Set.of();
    private final Map<Contraption, Map<ResourceLocation, ItemStack>> appliedPauses =
            new WeakHashMap<>();

    public void setPausedTypes(ManagedTrain train, Set<ResourceLocation> types) {
        pausedTypes = Set.copyOf(types);
        tickPauses(train);
    }

    /** Transient overrides: Create's saved contraption-control filters remain authoritative. */
    public void tickPauses(ManagedTrain train) {
        if (pausedTypes.isEmpty() && appliedPauses.isEmpty()) return;
        visit(train, (entity, contraption) -> tickPauses(entity));
    }

    public void tickPauses(CarriageContraptionEntity entity) {
        var contraption = entity.getContraption();
        if (contraption == null || (pausedTypes.isEmpty() && !appliedPauses.containsKey(contraption)))
            return;
        var previous = appliedPauses.getOrDefault(contraption, Map.of());
        var available = types(contraption);
        var affected = new LinkedHashMap<>(previous);
        pausedTypes.forEach(type -> {
            var filter = available.get(type);
            if (filter != null) affected.put(type, filter);
        });
        var next = new LinkedHashMap<ResourceLocation, ItemStack>();
        affected.forEach((type, filter) -> {
            boolean paused = pausedTypes.contains(type);
            applyPause(contraption, filter, paused);
            if (paused) next.put(type, filter);
            if (paused != previous.containsKey(type)
                    || (paused && entity.level().getGameTime() % 20 == 0))
                ControllerNetworking.sendToPlayersTrackingEntity(
                        entity, new ActorPausePayload(entity.getId(), filter, paused));
        });
        if (next.isEmpty()) appliedPauses.remove(contraption);
        else appliedPauses.put(contraption, next);
    }

    public static void applyPause(Contraption contraption, ItemStack filter, boolean paused) {
        boolean enabled = !paused && !contraption.isActorTypeDisabled(filter)
                && !contraption.isActorTypeDisabled(ItemStack.EMPTY);
        boolean needsUpdate = contraption.getActors().stream().anyMatch(actor -> {
            var context = actor.getRight();
            if (context == null) return false;
            var behavior = MovementBehaviour.REGISTRY.get(actor.getLeft().state());
            if (behavior == null) return false;
            var actorFilter = behavior.canBeDisabledVia(context);
            return actorFilter != null
                    && ContraptionControlsMovement.isSameFilter(filter, actorFilter)
                    && context.disabled == enabled;
        });
        if (needsUpdate) contraption.setActorsActive(filter, enabled);
    }

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
                    com.simibubi.create.AllPackets.getChannel().send(
                            net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                            new ContraptionDisableActorPacket(entity.getId(), filter, enabled));
                    changed[0] = true;
                });
        return changed[0];
    }

    public boolean isActorTypeEnabled(ManagedTrain train, ResourceLocation type) {
        if (pausedTypes.contains(type)) return false;
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
