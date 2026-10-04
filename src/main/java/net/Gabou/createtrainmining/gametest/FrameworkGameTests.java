package net.Gabou.createtrainmining.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.track.TrackMaterial;

import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.api.*;
import net.Gabou.createtrainmining.core.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.*;

/** Development-only integration tests, excluded from the distributed mod jar. */
@GameTestHolder(Createtrainmining.MODID)
@PrefixGameTestTemplate(false)
public final class FrameworkGameTests {
    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void stationlessForwardBackwardAndStop(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.controller.startDriving(DriveDirection.FORWARD, .2);
            double initial = f.position();
            f.ticks(40);
            helper.assertTrue(
                    f.position() > initial, "Forward drive must move without any station");
            helper.assertTrue(
                    f.train.navigation.destination == null,
                    "Stationless drive must not create a destination");
            helper.assertTrue(
                    !f.train.manualTick, "Train.tick must consume the backend's manualTick");
            f.controller.setDirection(DriveDirection.BACKWARD);
            f.ticks(150);
            helper.assertTrue(f.train.speed < 0, "Direction change must brake and reverse");
            f.controller.stopDriving();
            f.ticks(20);
            helper.assertTrue(
                    f.train.speed == 0 && f.train.targetSpeed == 0 && !f.train.manualTick,
                    "Stop must reset direct movement state");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void directToScheduleHandoff(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.station("Depot", 100);
            f.controller.startDriving(DriveDirection.FORWARD, .2);
            f.ticks(30);
            f.controller.goToStation("Depot");
            helper.assertTrue(
                    !f.controller.isDirectDriving() && !f.train.manualTick,
                    "Schedule handoff must disable direct control");
            helper.assertTrue(
                    !f.train.runtime.paused && f.train.runtime.getSchedule() != null,
                    "Create ScheduleRuntime must own the schedule");
            f.ticks(3);
            helper.assertTrue(
                    f.train.navigation.destination != null
                            && f.train.navigation.destination.name.equals("Depot"),
                    "Create must start normal destination navigation");
            f.controller.startDriving(DriveDirection.BACKWARD, .2);
            helper.assertTrue(
                    f.train.runtime.paused && f.train.navigation.destination == null,
                    "Re-entering direct control must suspend schedule navigation");
            f.controller.stopDriving();
            f.controller.resumeSchedule();
            f.ticks(3);
            helper.assertTrue(
                    f.train.navigation.destination != null,
                    "Suspended Create schedule must resume");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void trackEndBrakesAndStops(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.setPosition(124);
            f.controller.startDriving(DriveDirection.FORWARD, 1);
            f.ticks(1000);
            helper.assertTrue(
                    f.position() < 128 && !f.train.derailed,
                    "Track end must be approached without crossing or derailing");
            helper.assertTrue(
                    f.train.speed == 0 && !f.controller.isDirectDriving(),
                    "Default direct request must finish at a track end");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void occupiedSignalStopsAndThenResumes(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            var signal = f.signal(70, false);
            UUID group = signal.groups.getFirst();
            var occupied = new SignalEdgeGroup(group);
            Create.RAILWAYS.signalEdgeGroups.put(group, occupied);
            var blocker =
                    new Train(UUID.randomUUID(), null, f.graph, List.of(), List.of(), true, 0);
            occupied.trains.add(blocker);
            f.controller.startDriving(DriveDirection.FORWARD, .5);
            f.ticks(700);
            helper.assertTrue(
                    f.position() < 70 && Math.abs(f.train.speed) < 1e-5,
                    "Direct drive must stop before an occupied signal");
            occupied.trains.clear();
            occupied.reserved = null;
            f.ticks(150);
            helper.assertTrue(f.position() > 70, "Drive must resume when the signal clears");
            Create.RAILWAYS.signalEdgeGroups.remove(group);
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void inventoryUsesPerItemStackCapacity(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.cargo.setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE));
            f.cargo.setStackInSlot(1, new ItemStack(Items.ENDER_PEARL, 16));
            f.cargo.setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 64));
            var inventory = f.controller.getInventory();
            helper.assertTrue(
                    inventory.getSlots() == 4 && inventory.getUsedItemCount() == 81,
                    "Cargo must be read directly from Create storage");
            helper.assertTrue(
                    Math.abs(inventory.getUsageRatio() - .75) < 1e-6,
                    "Full non-stackable and 16-stack items each occupy one slot");
            var copy = inventory.getStack(2);
            copy.setCount(1);
            helper.assertTrue(
                    inventory.getStack(2).getCount() == 64,
                    "Inventory reads must not expose mutable cargo stacks");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void miningProfilePersistenceAndIndependentOwnership(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.station("Depot", 100);
            f.controller.setProfile("mining");
            f.controller.setConfig("return_station", "Depot");
            f.controller.setConfig("return_threshold", "0.5");
            f.controller.start();
            helper.assertTrue(
                    f.controller.getStatus().equals("MINING") && f.controller.isDirectDriving(),
                    "Mining profile must use generic direct drive");
            var other = new TrainController(helper.getLevel().getServer(), () -> {}, () -> 0);
            other.selectTrain(f.train.id);
            boolean rejected = false;
            try {
                other.startDriving(DriveDirection.FORWARD, .2);
            } catch (IllegalStateException e) {
                rejected = true;
            }
            helper.assertTrue(rejected, "Another controller must not acquire the same train");
            other.unload();
            f.cargo.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
            f.cargo.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
            f.controller.tick();
            helper.assertTrue(
                    f.controller.getStatus().equals("RETURNING")
                            && f.controller.getControlMode() == ControlMode.SCHEDULE_CONTROL,
                    "Full cargo must switch the profile to Create schedule control");
            var saved = f.controller.save();
            f.controller.unload();
            var reloaded = new TrainController(helper.getLevel().getServer(), () -> {}, () -> 0);
            reloaded.load(saved);
            reloaded.tick();
            helper.assertTrue(
                    reloaded.isEnabled() && reloaded.getStatus().equals("RETURNING"),
                    "Reload must preserve selected profile and return state");
            helper.assertTrue(
                    reloaded.getSelectedTrainId().equals(f.train.id),
                    "Selected train must persist by UUID");
            f.train.setCurrentStation(f.graph.getPoints(EdgePointType.STATION).iterator().next());
            reloaded.tick();
            helper.assertTrue(
                    reloaded.getStatus().equals("UNLOADING"),
                    "Return arrival must switch to unloading");
            f.cargo.setStackInSlot(0, ItemStack.EMPTY);
            f.cargo.setStackInSlot(1, ItemStack.EMPTY);
            reloaded.tick();
            reloaded.tick();
            helper.assertTrue(
                    reloaded.getStatus().equals("MINING") && reloaded.isDirectDriving(),
                    "Unloaded train must resume generic direct drive");
            reloaded.stop();
            reloaded.unload();
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void crossSignalProtectsTheWholeChain(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            var entry = f.signal(70, true);
            var exit = f.signal(95, false);
            var first = new SignalEdgeGroup(entry.groups.getFirst());
            var second = new SignalEdgeGroup(exit.groups.getFirst());
            Create.RAILWAYS.signalEdgeGroups.put(first.id, first);
            Create.RAILWAYS.signalEdgeGroups.put(second.id, second);
            var blocker =
                    new Train(UUID.randomUUID(), null, f.graph, List.of(), List.of(), true, 0);
            second.trains.add(blocker);
            f.controller.startDriving(DriveDirection.FORWARD, .5);
            f.ticks(600);
            helper.assertTrue(
                    f.position() < 70 && Math.abs(f.train.speed) < 1e-5,
                    "An occupied downstream group must block at the cross-signal entry");
            second.trains.clear();
            second.reserved = null;
            first.reserved = null;
            f.ticks(150);
            helper.assertTrue(
                    f.position() > 70, "A cleared cross-signal chain must release its entry");
            Create.RAILWAYS.signalEdgeGroups.remove(first.id);
            Create.RAILWAYS.signalEdgeGroups.remove(second.id);
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void manualSteeringSelectsJunctionBranches(GameTestHelper helper) {
        for (var strategy : DriveRequest.SwitchStrategy.values())
            try (var f = new Fixture(helper.getLevel())) {
                var leftLocation = new TrackNodeLocation(256, 256, -16).in(f.level);
                var rightLocation = new TrackNodeLocation(256, 256, 16).in(f.level);
                var straightLocation = new TrackNodeLocation(256, 256, 0).in(f.level);
                f.graph.loadNode(leftLocation, 3, new Vec3(0, 1, 0));
                f.graph.loadNode(rightLocation, 4, new Vec3(0, 1, 0));
                f.graph.loadNode(straightLocation, 5, new Vec3(0, 1, 0));
                for (var location : List.of(leftLocation, rightLocation, straightLocation)) {
                    var node = f.graph.locateNode(location);
                    f.graph.putConnection(
                            f.last,
                            node,
                            new TrackEdge(f.last, node, null, TrackMaterial.ANDESITE));
                    f.graph.putConnection(
                            node,
                            f.last,
                            new TrackEdge(node, f.last, null, TrackMaterial.ANDESITE));
                }
                f.setPosition(124);
                f.controller.startDriving(
                        new DriveRequest(
                                DriveDirection.FORWARD,
                                .5,
                                strategy,
                                DriveRequest.SignalBehavior.OBEY,
                                true,
                                true));
                f.ticks(150);
                var point = f.train.carriages.getFirst().getLeadingPoint();
                var expected =
                        switch (strategy) {
                            case LEFT -> leftLocation;
                            case RIGHT -> rightLocation;
                            case STRAIGHT -> straightLocation;
                        };
                helper.assertTrue(
                        point.node2.getLocation().equals(expected),
                        "Stationless steering must select the requested " + strategy + " branch");
            }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void optionalCruiseCannotDoubleTickOrFightSchedules(GameTestHelper helper) {
        if (!DriveBackendRegistry.ids().contains("railways_additions")) {
            helper.succeed();
            return;
        }
        try (var f = new Fixture(helper.getLevel())) {
            f.station("Depot", 100);
            f.controller.setBackend("railways_additions");
            f.controller.startDriving(DriveDirection.FORWARD, .2);
            f.ticks(30);
            double speed = f.train.speed;
            var api =
                    Class.forName(
                            "com.vodmordia.railwaysuntold_additions.contraption.CruiseControlManager");
            api.getMethod("tick", net.minecraft.server.MinecraftServer.class)
                    .invoke(null, f.level.getServer());
            helper.assertTrue(
                    f.train.speed == speed && !f.train.manualTick,
                    "Addon cruise must not accelerate or set manualTick again for a managed train");
            f.controller.goToStation("Depot");
            f.ticks(3);
            helper.assertTrue(
                    !(Boolean) api.getMethod("isEngaged", UUID.class).invoke(null, f.train.id),
                    "Schedule handoff must disengage addon cruise");
            helper.assertTrue(
                    f.train.navigation.destination != null,
                    "Addon backend must hand movement to Create schedules");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void reverseCancelsActorStallAndChunkWaitingIsExplicit(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.controller.startDriving(DriveDirection.FORWARD, .2);
            f.ticks(30);
            f.train.speedBeforeStall = f.train.speed;
            f.train.carriages.getFirst().stalled = true;
            f.train.speed = 0;
            f.ticks(3);
            helper.assertTrue(f.train.speed == 0, "An actor stall must suspend motion");
            f.controller.setDirection(DriveDirection.BACKWARD);
            f.ticks(30);
            helper.assertTrue(
                    !f.train.carriages.getFirst().stalled && f.train.speed < 0,
                    "Reverse controls must use Create's actor stall cancellation");
            f.train.carriageWaitingForChunks = 0;
            f.ticks(2);
            helper.assertTrue(
                    f.controller.isDirectDriving(), "Default direct requests may wait for chunks");
            f.controller.startDriving(
                    new DriveRequest(
                            DriveDirection.FORWARD,
                            .2,
                            DriveRequest.SwitchStrategy.STRAIGHT,
                            DriveRequest.SignalBehavior.OBEY,
                            true,
                            false));
            f.ticks(1);
            helper.assertTrue(
                    f.controller.getControlMode() == ControlMode.ERROR,
                    "Disallowed chunk waiting must report an error and release direct control");
            f.train.carriageWaitingForChunks = -1;
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void orphanedLeaseSuspendsMovementAndPlayerTakesOwnership(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.station("Depot", 100);
            new TrainScheduleController().goToStation(f.train, "Depot");
            f.train.speed = .2;
            UUID orphan = UUID.randomUUID();
            var manager = TrainAutomationManager.get(f.level.getServer());
            manager.ownership().claim(f.train.id, orphan);
            f.ticks(1);
            helper.assertTrue(
                    f.train.runtime.paused
                            && f.train.speed == 0
                            && f.train.navigation.destination == null,
                    "An owned train must remain stopped until its persisted controller loads");
            TrainAutomationManager.playerTakeover(f.train, f.level);
            helper.assertTrue(
                    manager.ownership().owner(f.train.id) == null,
                    "Player controls must clear an unloaded controller's movement lease");
            f.controller.startDriving(DriveDirection.FORWARD, .2);
            f.ticks(2);
            TrainAutomationManager.playerTakeover(f.train, f.level);
            helper.assertTrue(
                    f.controller.getControlMode() == ControlMode.MANUAL_PLAYER_CONTROL
                            && manager.ownership().owner(f.train.id) == null,
                    "Player takeover must stop automation and release its movement lease");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void controllerBlockPersistsSettingsAndOptionalPeripheral(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var level = helper.getLevel();
        level.setBlockAndUpdate(
                pos,
                net.Gabou.createtrainmining.block.ModBlocks.CONTROLLER.get().defaultBlockState());
        var entity =
                (net.Gabou.createtrainmining.block.TrainAutomationControllerBlockEntity)
                        level.getBlockEntity(pos);
        try (var f = new Fixture(level)) {
            var controller = entity.controller();
            controller.selectTrain(f.train.id);
            controller.setProfile("mining");
            controller.setConfig("return_threshold", "0.9");
            var saved = entity.saveWithoutMetadata(level.registryAccess());
            var reloaded =
                    new net.Gabou.createtrainmining.block.TrainAutomationControllerBlockEntity(
                            pos, entity.getBlockState());
            reloaded.setLevel(level);
            reloaded.loadWithComponents(saved, level.registryAccess());
            helper.assertTrue(
                    reloaded.controller().getSelectedTrainId().equals(f.train.id)
                            && reloaded.controller().getProfileId().equals("mining")
                            && reloaded.controller().getConfig().get("return_threshold").equals(.9),
                    "Block NBT must persist train UUID, profile and configuration");
            if (net.neoforged.fml.ModList.get().isLoaded("computercraft"))
                ComputerCraftGameTestSupport.verify(helper, level, pos, controller);
        } finally {
            level.setBlockAndUpdate(
                    pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    private static final class TestCarriage extends Carriage {
        TestCarriage(CarriageBogey bogey) {
            super(bogey, null, 0);
        }

        @Override
        public void manageEntities(Level level) {
            /* A real travelling bogey without spawning a test contraption entity. */
        }

        @Override
        public void updateConductors() {
            presentConductors = Couple.create(true, true);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final TrackGraph graph = new TrackGraph();
        final TrackNode first, last;
        final TrackEdge edge;
        final Train train;
        final TrainController controller;
        final ItemStackHandler cargo = new ItemStackHandler(4);

        Fixture(ServerLevel level) {
            this.level = level;
            var start = new TrackNodeLocation(0, 256, 0).in(level);
            var end = new TrackNodeLocation(128, 256, 0).in(level);
            graph.loadNode(start, 1, new Vec3(0, 1, 0));
            graph.loadNode(end, 2, new Vec3(0, 1, 0));
            first = graph.locateNode(start);
            last = graph.locateNode(end);
            edge = new TrackEdge(first, last, null, TrackMaterial.ANDESITE);
            graph.putConnection(first, last, edge);
            graph.putConnection(
                    last, first, new TrackEdge(last, first, null, TrackMaterial.ANDESITE));
            var type = AllBlocks.SMALL_BOGEY.get();
            double spacing = type.getWheelPointSpacing();
            var front = new TravellingPoint(first, last, edge, 64, false);
            var rear = new TravellingPoint(first, last, edge, 64 - spacing, false);
            var carriage =
                    new TestCarriage(
                            new CarriageBogey(type, false, new CompoundTag(), front, rear));
            carriage.storage.initialize();
            carriage.storage.attachExternal(cargo);
            carriage.updateConductors();
            train =
                    new Train(
                            UUID.randomUUID(), null, graph, List.of(carriage), List.of(), true, 0);
            train.name = Component.literal("Framework Test Train");
            Create.RAILWAYS.trains.put(train.id, train);
            controller = new TrainController(level.getServer(), () -> {}, () -> 0);
            controller.selectTrain(train.id);
        }

        double position() {
            return train.carriages.getFirst().getLeadingPoint().position;
        }

        void setPosition(double position) {
            double delta = position - position();
            train.carriages.getFirst().getLeadingPoint().position += delta;
            train.carriages.getFirst().getTrailingPoint().position += delta;
        }

        void ticks(int count) {
            for (int i = 0; i < count; i++) {
                for (var signal : graph.getPoints(EdgePointType.SIGNAL))
                    for (UUID id : signal.groups) {
                        var group = Create.RAILWAYS.signalEdgeGroups.get(id);
                        if (group != null) {
                            group.reserved = null;
                            group.trains.remove(train);
                        }
                    }
                train.earlyTick(level);
                train.tick(level);
                controller.tick();
            }
        }

        void station(String name, double at) {
            var station = new GlobalStation();
            station.setType(EdgePointType.STATION);
            station.setId(UUID.randomUUID());
            station.name = name;
            station.blockEntityPos = BlockPos.ZERO;
            station.blockEntityDimension = level.dimension();
            station.setLocation(Couple.create(first.getLocation(), last.getLocation()), at);
            graph.addPoint(EdgePointType.STATION, station);
        }

        SignalBoundary signal(double at, boolean cross) {
            var signal = new SignalBoundary();
            signal.setType(EdgePointType.SIGNAL);
            signal.setId(UUID.randomUUID());
            signal.setLocation(Couple.create(first.getLocation(), last.getLocation()), at);
            signal.groups = Couple.create(UUID.randomUUID(), UUID.randomUUID());
            if (cross) signal.types = Couple.create(() -> SignalBlock.SignalType.CROSS_SIGNAL);
            graph.addPoint(EdgePointType.SIGNAL, signal);
            return signal;
        }

        public void close() {
            controller.stop();
            controller.unload();
            Create.RAILWAYS.trains.remove(train.id);
        }
    }
}
