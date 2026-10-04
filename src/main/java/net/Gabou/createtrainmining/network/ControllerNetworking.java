package net.Gabou.createtrainmining.network;

import net.Gabou.createtrainmining.api.*;
import net.Gabou.createtrainmining.block.TrainAutomationControllerBlockEntity;
import net.Gabou.createtrainmining.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.UUID;

public final class ControllerNetworking {
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(
                ControllerCommandPayload.TYPE,
                ControllerCommandPayload.CODEC,
                (packet, context) ->
                        context.enqueueWork(
                                () -> {
                                    if (!(context.player() instanceof ServerPlayer player)
                                            || !(player.containerMenu
                                                    instanceof ControllerMenu menu)
                                            || menu.containerId != packet.menuId()
                                            || !menu.position().equals(packet.pos())
                                            || !menu.stillValid(player)
                                            || !player.mayBuild()
                                            || player.isSpectator()) return;
                                    if (!(player.serverLevel().getBlockEntity(packet.pos())
                                            instanceof TrainAutomationControllerBlockEntity entity))
                                        return;
                                    String error = "";
                                    try {
                                        var c = entity.controller();
                                        if (java.util.Set.of("drive", "stop_drive", "go_to_station")
                                                .contains(packet.action()))
                                            c.requireExternalControl();
                                        switch (packet.action()) {
                                            case "select_train" ->
                                                    c.selectTrain(UUID.fromString(packet.value()));
                                            case "set_profile" -> c.setProfile(packet.value());
                                            case "set_config" ->
                                                    c.setConfig(packet.key(), packet.value());
                                            case "set_backend" -> c.setBackend(packet.value());
                                            case "start" -> c.start();
                                            case "stop" -> c.stop();
                                            case "drive" ->
                                                    c.startDriving(
                                                            DriveDirection.valueOf(packet.key()),
                                                            Double.parseDouble(packet.value()));
                                            case "stop_drive" -> c.stopDriving();
                                            case "go_to_station" -> c.goToStation(packet.value());
                                            case "refresh" -> {}
                                            default ->
                                                    throw new IllegalArgumentException(
                                                            "Unknown controller command");
                                        }
                                    } catch (RuntimeException e) {
                                        error =
                                                e.getMessage() == null
                                                        ? "Controller command failed"
                                                        : e.getMessage();
                                    }
                                    sendStatus(player, menu, error);
                                }));
        registrar.playToClient(
                ControllerStatusPayload.TYPE,
                ControllerStatusPayload.CODEC,
                (packet, context) ->
                        context.enqueueWork(
                                () ->
                                        net.Gabou.createtrainmining.client.ControllerClient.receive(
                                                packet)));
    }

    public static void sendStatus(ServerPlayer player, ControllerMenu menu, String commandError) {
        if (!(player.serverLevel().getBlockEntity(menu.position())
                instanceof TrainAutomationControllerBlockEntity entity)) return;
        var c = entity.controller();
        var tag = new CompoundTag();
        tag.putString("Profile", c.getProfileId());
        tag.putString("Backend", c.getBackendId());
        tag.putString("State", c.getStatus());
        tag.putString("Mode", c.getControlMode().name());
        tag.putBoolean("Enabled", c.isEnabled());
        tag.putString("Error", commandError.isEmpty() ? c.getLastError() : commandError);
        var trains = new ListTag();
        for (var train : c.discoverTrains()) {
            var t = new CompoundTag();
            t.putString("Id", train.getId().toString());
            t.putString("Name", train.getName());
            trains.add(t);
        }
        tag.put("Trains", trains);
        tag.put("Profiles", strings(AutomationProfileRegistry.ids()));
        tag.put("Backends", strings(DriveBackendRegistry.ids()));
        var fields = new ListTag();
        for (var f : c.getConfigurationSchema()) {
            var t = new CompoundTag();
            t.putString("Key", f.key());
            t.putString("Label", f.label());
            t.putString("Type", f.type().name());
            t.putString("Editor", f.editor().name());
            t.putBoolean("Advanced", f.advanced());
            t.putDouble("Min", f.min());
            t.putDouble("Max", f.max());
            t.putString("Value", c.getConfig().get(f.key()).toString());
            t.put("Options", strings(f.options()));
            fields.add(t);
        }
        tag.put("Fields", fields);
        if (c.getSelectedTrainId() != null)
            tag.putString("TrainId", c.getSelectedTrainId().toString());
        try {
            var train = c.getSelectedTrain();
            tag.putString("Train", train.getName());
            tag.putDouble("Speed", train.getSpeed());
            // Presentation-only data from Create; no motion fields are written here.
            var createTrain = com.simibubi.create.Create.RAILWAYS.trains.get(train.getId());
            if (createTrain != null) {
                tag.putString("TrainIcon", createTrain.icon.getId().toString());
                tag.putIntArray("CarriageLengths", createTrain.carriages.stream().mapToInt(carriage -> carriage.bogeySpacing).toArray());
                tag.putBoolean("DoubleEnded", createTrain.doubleEnded);
                double manualMaximum = createTrain.maxSpeed()
                        * com.simibubi.create.infrastructure.config.AllConfigs.server().trains.manualTrainSpeedModifier.getF();
                tag.putDouble("SpeedRatio", manualMaximum <= 0 ? 0 : Math.abs(train.getSpeed()) / manualMaximum);
            }
            tag.putString("Direction", c.getDirection().name());
            tag.putDouble("Inventory", train.getInventory().getUsageRatio());
            tag.putString(
                    "Station",
                    train.getCurrentStation() == null ? "None" : train.getCurrentStation());
            tag.put("Stations", strings(c.getStations()));
        } catch (RuntimeException e) {
            tag.putString("Train", "Unavailable");
        }
        PacketDistributor.sendToPlayer(player, new ControllerStatusPayload(menu.containerId, tag));
    }

    private static ListTag strings(java.util.List<String> values) {
        var list = new ListTag();
        values.forEach(v -> list.add(StringTag.valueOf(v)));
        return list;
    }
}
