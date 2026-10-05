package net.Gabou.createtrainmining.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.api.*;
import net.Gabou.createtrainmining.core.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.apache.commons.lang3.tuple.MutablePair;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.*;

/** Development-only integration tests, excluded from the distributed mod jar. */
@GameTestHolder(Createtrainmining.MODID)
@PrefixGameTestTemplate(false)
public final class FrameworkGameTests {
    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void threadedTrainsTicksOnceOnServerAndKeepsSlopeProtection(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("createthreadedtrains")) {
            helper.succeed();
            return;
        }
        try (var managed = new Fixture(helper.getLevel());
                var unmanaged = new Fixture(helper.getLevel())) {
            // Include these fixtures in the real global railway task, not just direct Train.tick.
            Create.RAILWAYS.addTrain(managed.train);
            Create.RAILWAYS.addTrain(unmanaged.train);
            managed.connect(managed.last, 256, 240, 0);
            managed.setPosition(122);
            var tools = managed.attachTools();
            managed.station("Depot", 70);
            managed.controller.setProfile("mining");
            managed.controller.setConfig("return_station", "Depot");
            managed.controller.start();
            double before = managed.position();
            var addon = Class.forName("de.mrjulsen.ctt.CreateThreadedTrains");
            var preTick = addon.getMethod("preTick", net.minecraft.server.MinecraftServer.class);
            var postTick = addon.getMethod("postTick", net.minecraft.server.MinecraftServer.class);
            for (int i = 1; i <= 8; i++) {
                preTick.invoke(null, helper.getLevel().getServer());
                helper.assertTrue(managed.trainTicks == i && unmanaged.trainTicks == i,
                        "The railway task must run immediately, exactly once, for all trains");
                postTick.invoke(null, helper.getLevel().getServer());
                helper.assertTrue(managed.trainTicks == i && unmanaged.trainTicks == i,
                        "Waiting for the completed future must not tick trains again");
                helper.assertTrue(!managed.tickedOffServerThread && !unmanaged.tickedOffServerThread,
                        "Both managed and unmanaged trains must tick on the server thread");
                helper.assertTrue(managed.controller.getStatus().equals("SLOPE_PAUSED")
                                && managed.controller.isDirectDriving()
                                && tools.getActors().stream().allMatch(a -> a.getRight().disabled),
                        "Real addon railway ticks must preserve mining and slope-tool protection");
            }
            helper.assertTrue(managed.position() > before,
                    "The compatibility fallback must preserve train movement");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not exercise Create Threaded Trains ticks", e);
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void slopesAreDetectedAheadInBothDirections(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.connect(f.last, 256, 240, 0);
            f.connect(f.first, -128, 240, 0);
            f.setPosition(110);
            helper.assertTrue(!f.controller.hasSlopeNearTrain(DriveDirection.FORWARD, 8),
                    "A distant slope must not pause tools on flat track");
            f.setPosition(122);
            helper.assertTrue(f.controller.hasSlopeNearTrain(DriveDirection.FORWARD, 8),
                    "Descending track must be detected before the front reaches it");
            helper.assertTrue(!f.controller.hasSlopeNearTrain(DriveDirection.BACKWARD, 8),
                    "Lookahead must follow the requested direction when stopped");
            f.setPosition(6);
            helper.assertTrue(f.controller.hasSlopeNearTrain(DriveDirection.BACKWARD, 8),
                    "Backward mining must scout the other end of the train");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void curvedSlopesWithLevelEndpointsAreDetected(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            var end = f.node(256, 256, 0);
            var curve = new BezierConnection(
                    Couple.create(new BlockPos(128, 256, 0), new BlockPos(256, 256, 0)),
                    Couple.create(new Vec3(128, 256, 0), new Vec3(256, 256, 0)),
                    Couple.create(new Vec3(1, .2, 0).normalize(), new Vec3(-1, .2, 0).normalize()),
                    Couple.create(new Vec3(0, 1, 0), new Vec3(0, 1, 0)),
                    true, false, TrackMaterial.ANDESITE);
            var edge = new TrackEdge(f.last, end, curve, TrackMaterial.ANDESITE);
            f.graph.putConnection(f.last, end, edge);
            f.graph.putConnection(end, f.last,
                    new TrackEdge(end, f.last, curve.secondary(), TrackMaterial.ANDESITE));
            f.placeOn(edge, 12);
            helper.assertTrue(f.controller.hasSlopeNearTrain(DriveDirection.FORWARD, 8),
                    "Sample curve elevation; equal endpoint heights do not imply level track");
            var levelCurve = new BezierConnection(curve.bePositions, curve.starts,
                    Couple.create(new Vec3(1, 0, 0), new Vec3(-1, 0, 0)), curve.normals,
                    true, false, TrackMaterial.ANDESITE);
            var flatEdge = new TrackEdge(f.last, end, levelCurve, TrackMaterial.ANDESITE);
            f.graph.putConnection(f.last, end, flatEdge);
            f.placeOn(flatEdge, 12);
            helper.assertTrue(!f.controller.hasSlopeNearTrain(DriveDirection.FORWARD, 8),
                    "Horizontal curves must not pause mining tools");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void miningToolsPauseUntilTheOverhangClearsAndRecoverOnUnload(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            var slope = f.connect(f.last, 256, 240, 0);
            var flat = f.connect(slope.node2, 384, 240, 0);
            var tools = f.attachTools();
            f.station("Depot", 100);
            f.controller.setProfile("mining");
            f.controller.setConfig("return_station", "Depot");
            f.setPosition(122);
            f.controller.start();
            helper.assertTrue(f.controller.getStatus().equals("SLOPE_PAUSED")
                            && tools.getActors().stream().allMatch(a -> a.getRight().disabled),
                    "Mining start must pause drill and deployer before movement");
            helper.assertTrue(tools.getDisabledActors().isEmpty() && f.controller.isDirectDriving(),
                    "Slope pause must retain movement without rewriting saved control settings");
            // Simulate a carriage entity being reconstructed after the train movement hook.
            tools = f.attachTools();
            var entity = ((TestCarriage) f.train.carriages.getFirst()).testEntity;
            entity.setCarriage(f.train.carriages.getFirst());
            entity.tickActors();
            helper.assertTrue(tools.getActors().stream().allMatch(a -> a.getRight().disabled),
                    "A newly loaded carriage must be paused by the actor hook before tools act");
            f.placeOn(flat, 10);
            f.controller.tickDrive();
            helper.assertTrue(f.controller.getStatus().equals("SLOPE_PAUSED"),
                    "The front reaching the bottom must not reactivate tools on the rear overhang");
            f.placeOn(flat, 40);
            f.controller.tickDrive();
            helper.assertTrue(f.controller.getStatus().equals("MINING")
                            && tools.getActors().stream().noneMatch(a -> a.getRight().disabled),
                    "Tools must resume only after the entire tool envelope clears the slope");
            f.placeOn(slope, 20);
            f.controller.tickDrive();
            var saved = f.controller.save();
            f.controller.unload();
            helper.assertTrue(tools.getActors().stream().noneMatch(a -> a.getRight().disabled),
                    "Unloading the controller must restore temporary tool pauses");
            var reloaded = new TrainController(f.level.getServer(), () -> {}, () -> 0);
            reloaded.load(saved);
            reloaded.tick();
            helper.assertTrue(reloaded.getStatus().equals("SLOPE_PAUSED"),
                    "Reloading mining on a slope must recompute protection before driving");
            reloaded.stop();
            reloaded.unload();
            helper.assertTrue(tools.getActors().stream().noneMatch(a -> a.getRight().disabled),
                    "Stopping mining must release temporary pauses");
            f.controller.stop();
            f.controller.setConfig("pause_tools_on_slopes", "false");
            f.controller.start();
            helper.assertTrue(f.controller.getStatus().equals("MINING")
                            && tools.getActors().stream().noneMatch(a -> a.getRight().disabled),
                    "Disabling slope protection must leave actor operation under normal controls");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void temporaryToolPausesRespectContraptionControls(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            var tools = f.attachTools();
            var deployer = ResourceLocation.parse("create:deployer");
            var drill = ResourceLocation.parse("create:mechanical_drill");
            f.controller.setActorTypeEnabled(deployer, false);
            var original = tools.getDisabledActors().stream().map(ItemStack::copy).toList();
            f.controller.setPausedActorTypes(Set.of(deployer, drill));
            helper.assertTrue(tools.getActors().stream().allMatch(a -> a.getRight().disabled),
                    "Temporary pauses must affect both tool types");
            f.controller.setPausedActorTypes(Set.of());
            helper.assertTrue(!f.controller.isActorTypeEnabled(deployer)
                            && f.controller.isActorTypeEnabled(drill)
                            && tools.getDisabledActors().size() == original.size(),
                    "A previously disabled tool must remain disabled when the pause ends");
            f.controller.setPausedActorTypes(Set.of(deployer, drill));
            tools.getDisabledActors().add(ItemStack.EMPTY);
            tools.setActorsActive(ItemStack.EMPTY, false);
            f.controller.setPausedActorTypes(Set.of());
            helper.assertTrue(tools.getActors().stream().allMatch(a -> a.getRight().disabled)
                            && tools.isActorTypeDisabled(ItemStack.EMPTY),
                    "All-actor Contraption Controls must remain authoritative");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void theRearCarriageKeepsSlopeProtectionActive(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            var slope = f.connect(f.last, 256, 240, 0);
            var flat = f.connect(slope.node2, 384, 240, 0);
            f.placeOn(flat, 40);
            double spacing = AllBlocks.SMALL_BOGEY.get().getWheelPointSpacing();
            var rear = new TestCarriage(new CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false,
                    new CompoundTag(),
                    new TravellingPoint(slope.node1, slope.node2, slope, 100, false),
                    new TravellingPoint(slope.node1, slope.node2, slope, 100 - spacing, false)));
            rear.storage.initialize();
            rear.setTrain(f.train);
            f.train.carriages = new ArrayList<>(f.train.carriages);
            f.train.carriages.add(rear);
            helper.assertTrue(f.controller.hasSlopeNearTrain(DriveDirection.FORWARD, 8),
                    "Tools must remain paused while a rear carriage still occupies the slope");
            for (var point : List.of(rear.getLeadingPoint(), rear.getTrailingPoint())) {
                point.node1 = flat.node1;
                point.node2 = flat.node2;
                point.edge = flat;
                point.position = 15 - (point == rear.getTrailingPoint() ? spacing : 0);
            }
            helper.assertTrue(!f.controller.hasSlopeNearTrain(DriveDirection.FORWARD, 8),
                    "Protection must clear once both carriages are entirely on level track");
        }
        helper.succeed();
    }

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
        CarriageContraptionEntity testEntity;
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

        @Override
        public void forEachPresentEntity(java.util.function.Consumer<CarriageContraptionEntity> callback) {
            if (testEntity != null) callback.accept(testEntity);
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
        int trainTicks;
        boolean tickedOffServerThread;

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
                            UUID.randomUUID(), null, graph, List.of(carriage), List.of(), true, 0) {
                        @Override
                        public void tick(Level tickLevel) {
                            trainTicks++;
                            tickedOffServerThread |= !level.getServer().isSameThread();
                            super.tick(tickLevel);
                        }
                    };
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

        TrackNode node(int x, int y, int z) {
            var location = new TrackNodeLocation(x, y, z).in(level);
            graph.loadNode(location, 10 + graph.getNodes().size(), new Vec3(0, 1, 0));
            return graph.locateNode(location);
        }

        TrackEdge connect(TrackNode from, int x, int y, int z) {
            var to = node(x, y, z);
            var connection = new TrackEdge(from, to, null, TrackMaterial.ANDESITE);
            graph.putConnection(from, to, connection);
            graph.putConnection(to, from, new TrackEdge(to, from, null, TrackMaterial.ANDESITE));
            return connection;
        }

        void placeOn(TrackEdge on, double at) {
            double spacing = AllBlocks.SMALL_BOGEY.get().getWheelPointSpacing();
            var carriage = train.carriages.getFirst();
            for (var point : List.of(carriage.getLeadingPoint(), carriage.getTrailingPoint())) {
                point.node1 = on.node1;
                point.node2 = on.node2;
                point.edge = on;
                point.position = at - (point == carriage.getTrailingPoint() ? spacing : 0);
            }
            train.speed = 0;
        }

        CarriageContraption attachTools() {
            var contraption = new CarriageContraption(Direction.EAST);
            contraption.bounds = new AABB(-24, 0, -1, 6, 3, 1);
            var drill = new StructureBlockInfo(new BlockPos(4, 1, 0),
                    AllBlocks.MECHANICAL_DRILL.getDefaultState(), new CompoundTag());
            var deployer = new StructureBlockInfo(new BlockPos(-20, 1, 0),
                    AllBlocks.DEPLOYER.getDefaultState(), new CompoundTag());
            for (var info : List.of(drill, deployer))
                contraption.getActors().add(MutablePair.of(info, new MovementContext(level, info, contraption)));
            ((TestCarriage) train.carriages.getFirst()).testEntity =
                    CarriageContraptionEntity.create(level, contraption);
            return contraption;
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
            Create.RAILWAYS.removeTrain(train.id);
        }
    }
}
