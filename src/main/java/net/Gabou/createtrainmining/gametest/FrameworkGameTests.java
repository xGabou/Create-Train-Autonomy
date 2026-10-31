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
    public static void namedControllerCommandsRetryCancelAndReturn(GameTestHelper helper)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var level = helper.getLevel();
        var server = level.getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack().withLevel(level).withPermission(2);
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        try (var f = new Fixture(level)) {
            var entity = placeController(level, pos);
            var c = entity.controller();
            f.station("Depot", 100);
            c.selectTrain(f.train.id);
            c.setProfile("mining");
            c.setConfig("return_station", "Depot");
            String command = "trainautomation station \"Remote Quarry\" ";
            dispatcher.execute("trainautomation name \"Remote Quarry\" at "
                    + pos.getX() + " " + pos.getY() + " " + pos.getZ(), source);
            var suggestions = dispatcher.getCompletionSuggestions(
                    dispatcher.parse("trainautomation station ", source)).join();
            helper.assertTrue(suggestions.getList().stream()
                            .anyMatch(suggestion -> suggestion.getText().equals("\"Remote Quarry\"")),
                    "Station suggestions must quote names containing spaces");
            // Subsequent commands originate in another dimension, far from the controller.
            source = source.withLevel(server.getLevel(Level.NETHER));
            helper.assertTrue(dispatcher.execute(command + "start", source) == 1 && c.isEnabled(),
                    "Named command must start the addressed block's configured profile");
            for (int i = 0; i < 101; i++) c.tick();
            helper.assertTrue(c.getStatus().equals("RECOVERING"), "Mining must enter recovery");
            dispatcher.execute(command + "retry", source);
            helper.assertTrue(c.getStatus().equals("MINING")
                            && c.save().getCompound("ProfileState").getInt("ConsecutiveJams") == 0,
                    "Retry must clear recovery and jam counters without changing configuration");
            dispatcher.execute(command + "return", source);
            helper.assertTrue(c.getStatus().equals("RETURNING") && c.isEnabled()
                            && c.getControlMode() == ControlMode.SCHEDULE_CONTROL
                            && c.save().getCompound("ProfileState").getBoolean("ReturnAfterStuck"),
                    "Force return must use profile navigation and prevent automatic departure after unloading");
            dispatcher.execute(command + "cancel", source);
            helper.assertTrue(!c.isEnabled() && Math.abs(f.train.speed) < 1e-6
                            && !c.isDirectDriving() && f.train.navigation.destination == null,
                    "Cancel must stop movement, navigation, and automation");
            helper.assertTrue(dispatcher.execute(command + "status", source) == 1,
                    "Status must be available for an inactive controller");
            helper.assertTrue(dispatcher.parse(command + "start", source.withPermission(0))
                            .getReader().canRead(), "Remote commands must require operator permission");
        } finally {
            level.removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void namedControllerAddressesPersistRenameAndRejectReplacement(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var otherPos = helper.absolutePos(new BlockPos(2, 2, 1));
        var names = NamedControllerSavedData.get(server);
        try {
            var entity = placeController(level, pos);
            var other = placeController(level, otherPos);
            names.name("Address test", level, entity);
            try {
                names.name("Address test", level, other);
                helper.fail("Duplicate names must be rejected");
            } catch (IllegalArgumentException expected) {}
            names.name("Renamed address", level, entity);
            helper.assertTrue(!names.stations().containsKey("Address test"), "Rename must discard the old alias");
            var restored = NamedControllerSavedData.load(names.save(new CompoundTag(), level.registryAccess()),
                    level.registryAccess());
            helper.assertTrue(restored.resolve(server, "Renamed address") == entity,
                    "Persisted address must resolve the same dimension, position and controller identity");
            level.removeBlock(pos, false);
            helper.assertTrue(!names.stations().containsKey("Renamed address"),
                    "Breaking a controller must unregister its name");
            placeController(level, pos);
            try {
                restored.resolve(server, "Renamed address");
                helper.fail("A stale name must never control a replacement block");
            } catch (IllegalStateException expected) {}
            helper.assertTrue(!restored.stations().containsKey("Renamed address"),
                    "Stale addresses must be pruned after detection");
        } finally {
            level.removeBlock(pos, false);
            level.removeBlock(otherPos, false);
        }
        helper.succeed();
    }

    private static long controllerTickets(ServerLevel level, BlockPos pos) {
        var data = level.getDataStorage().get(net.minecraft.world.level.ForcedChunksSavedData.factory(), "chunks");
        if (data == null) return 0;
        long chunk = new net.minecraft.world.level.ChunkPos(pos).toLong();
        return data.getBlockForcedChunks().getTickingChunks().values().stream()
                .filter(chunks -> chunks.contains(chunk)).count();
    }

    private static long trainTickets(ServerLevel level, net.minecraft.world.level.ChunkPos chunk) {
        var data = level.getDataStorage().get(net.minecraft.world.level.ForcedChunksSavedData.factory(), "chunks");
        if (data == null) return 0;
        return data.getEntityForcedChunks().getTickingChunks().values().stream()
                .filter(chunks -> chunks.contains(chunk.toLong())).count();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void trainFootprintMovesAndIndependentOwnersReleaseTheirTickets(GameTestHelper helper) {
        var level = helper.getLevel();
        var oldChunk = new net.minecraft.world.level.ChunkPos(4, 0);
        var newChunk = new net.minecraft.world.level.ChunkPos(20, 0);
        long baseline = trainTickets(level, oldChunk);
        long newBaseline = trainTickets(level, newChunk);
        try (var a = new Fixture(level); var b = new Fixture(level)) {
            a.controller.startDriving(DriveDirection.FORWARD, .2);
            b.controller.startDriving(DriveDirection.FORWARD, .2);
            helper.assertTrue(trainTickets(level, oldChunk) == baseline + 2,
                    "Each controller must own a separate train footprint, even when their chunks overlap");
            var farther = a.connect(a.last, 512, 256, 0);
            a.placeOn(farther, 200);
            a.train.carriages.getFirst().updateContraptionAnchors();
            a.controller.tickDrive();
            helper.assertTrue(trainTickets(level, oldChunk) == baseline + 1
                            && trainTickets(level, newChunk) == newBaseline + 1,
                    "Moving the train must load the new footprint and release the old footprint");
            b.controller.yieldToPlayer();
            helper.assertTrue(trainTickets(level, oldChunk) == baseline,
                    "Player takeover must release only that controller's train footprint");
            a.controller.unload();
            helper.assertTrue(trainTickets(level, newChunk) == newBaseline,
                    "Shutdown/unload must release transient train tickets, rebuilt only on resume");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void remoteDrillBreaksOreWithNoPlayersNearby(GameTestHelper helper) {
        var level = helper.getLevel();
        var f = new Fixture(level);
        var tools = f.attachTools();
        tools.getActors().removeIf(actor -> actor.getLeft().state().is(AllBlocks.DEPLOYER.get()));
        var entity = ((TestCarriage) f.train.carriages.getFirst()).testEntity;
        entity.setCarriage(f.train.carriages.getFirst());
        entity.setPos(64, 256, 0);
        var ore = new BlockPos(69, 257, 0);
        f.controller.startDriving(DriveDirection.FORWARD, .2);
        helper.runAfterDelay(5, () -> {
            level.setBlockAndUpdate(ore, net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState());
            var context = tools.getActors().getFirst().getRight();
            context.motion = context.relativeMotion = new Vec3(.1, 0, 0);
            context.position = Vec3.atCenterOf(ore);
            var drill = com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY
                    .get(tools.getActors().getFirst().getLeft().state());
            drill.startMoving(context);
            drill.visitNewPosition(context, ore);
            // Tick the real carriage/drill via the world's entity loop, without calling tickActors.
            level.addFreshEntity(entity);
        });
        helper.runAfterDelay(45, () -> {
            try {
                helper.assertTrue(level.players().isEmpty()
                                && com.simibubi.create.content.trains.entity.CarriageEntityHandler.isActiveChunk(level, ore),
                        "Remote train and drill chunks must be entity-ticking with no nearby players");
                helper.assertTrue(entity.tickCount > 0 && level.getBlockState(ore).isAir(),
                        "Normal world entity ticks must let a remote Create drill finish mining ore");
                helper.succeed();
            } finally {
                entity.discard();
                level.removeBlock(ore, false);
                f.close();
            }
        });
    }

    private static net.Gabou.createtrainmining.block.TrainAutomationControllerBlockEntity
            placeController(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos,
                net.Gabou.createtrainmining.block.ModBlocks.CONTROLLER.get().defaultBlockState());
        return (net.Gabou.createtrainmining.block.TrainAutomationControllerBlockEntity) level.getBlockEntity(pos);
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void controllerChunkTicketsReleaseAndRemainIndependent(GameTestHelper helper) {
        var level = helper.getLevel();
        var firstPos = helper.absolutePos(new BlockPos(2, 1, 2));
        // Random test origins can put the first controller at a chunk boundary.
        var secondPos = firstPos.offset((firstPos.getX() & 15) == 15 ? -1 : 1, 0, 0);
        var first = placeController(level, firstPos);
        var second = placeController(level, secondPos);
        long baseline = controllerTickets(level, firstPos);
        try (var a = new Fixture(level); var b = new Fixture(level)) {
            var ca = first.controller();
            var cb = second.controller();
            ca.selectTrain(a.train.id);
            cb.selectTrain(b.train.id);
            ca.startDriving(DriveDirection.FORWARD, .2);
            cb.startDriving(DriveDirection.FORWARD, .2);
            helper.assertTrue(controllerTickets(level, firstPos) == baseline + 2,
                    "Each active controller must own its own ticking ticket, even in the same chunk");
            ca.stopDriving();
            helper.assertTrue(controllerTickets(level, firstPos) == baseline + 1,
                    "Stopping one controller must retain the other's chunk ticket");
            cb.fail(new IllegalStateException("Test failure"));
            helper.assertTrue(controllerTickets(level, firstPos) == baseline,
                    "Errors must release chunk tickets immediately");
            ca.startDriving(DriveDirection.FORWARD, .2);
            ca.yieldToPlayer();
            helper.assertTrue(controllerTickets(level, firstPos) == baseline,
                    "Player takeover must release the controller's ticket");
            ca.setProfile("mining");
            boolean failedStart = false;
            try { ca.start(); } catch (IllegalArgumentException expected) { failedStart = true; }
            helper.assertTrue(failedStart && controllerTickets(level, firstPos) == baseline,
                    "A failed profile start must not leave a forced chunk behind");
            ca.startDriving(DriveDirection.FORWARD, .2);
            level.removeBlock(firstPos, false);
            helper.assertTrue(controllerTickets(level, firstPos) == baseline
                            && !ControllerChunkLoader.isValidOwner(level, firstPos),
                    "Removing the block must release its ticket and invalidate the saved owner");
        } finally {
            level.removeBlock(firstPos, false);
            level.removeBlock(secondPos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void enabledControllerRetainsTicketAndResumesAfterReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        var entity = placeController(level, pos);
        long baseline = controllerTickets(level, pos);
        try (var f = new Fixture(level)) {
            f.station("Depot", 100);
            var controller = entity.controller();
            controller.selectTrain(f.train.id);
            controller.setProfile("mining");
            controller.setConfig("return_station", "Depot");
            controller.start();
            var saved = entity.saveWithoutMetadata(level.registryAccess());
            entity.onChunkUnloaded();
            helper.assertTrue(controllerTickets(level, pos) == baseline + 1
                            && entity.shouldKeepLoaded() && ControllerChunkLoader.isValidOwner(level, pos),
                    "An enabled profile must retain its persistent ticket while shutting down/unloading");
            entity.loadWithComponents(saved, level.registryAccess());
            entity.controller().tick();
            helper.assertTrue(entity.controller().isEnabled()
                            && entity.controller().getStatus().equals("MINING")
                            && controllerTickets(level, pos) == baseline + 1,
                    "Reload must resume mining without duplicating the persistent ticket");
            entity.controller().stop();
            helper.assertTrue(controllerTickets(level, pos) == baseline
                            && !ControllerChunkLoader.isValidOwner(level, pos),
                    "Stopping resumed automation must release its ticket and invalidate its owner");
        } finally {
            level.removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void distantControllerTicksWithoutNearbyPlayers(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(512, 1, 512));
        var f = new Fixture(level);
        var entity = placeController(level, pos);
        try {
            f.station("Depot", 100);
            entity.controller().selectTrain(f.train.id);
            entity.controller().setProfile("mining");
            entity.controller().setConfig("return_station", "Depot");
            entity.controller().setConfig("return_threshold", "0.5");
            entity.controller().start();
            f.cargo.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
            f.cargo.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
            helper.runAfterDelay(40, () -> {
                try {
                    helper.assertTrue(level.players().isEmpty() && level.isPositionEntityTicking(pos),
                            "The remote controller's chunk must tick without any nearby players");
                    helper.assertTrue(entity.controller().getStatus().equals("RETURNING"),
                            "Real remote block ticks must advance the mining workflow for full cargo");
                    helper.succeed();
                } finally {
                    level.removeBlock(pos, false);
                    f.close();
                }
            });
        } catch (RuntimeException e) {
            level.removeBlock(pos, false);
            f.close();
            throw e;
        }
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void stuckMiningBacksUpTenBlocksAndRetries(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.station("Depot", 100);
            var tools = f.attachTools();
            f.controller.setActorTypeEnabled(ResourceLocation.parse("create:deployer"), false);
            f.controller.setProfile("mining");
            f.controller.setConfig("return_station", "Depot");
            f.controller.start();
            // Reproduce a train reporting motion while its graph position is frozen.
            f.train.speed = .2;
            f.train.speedBeforeStall = .2;
            f.train.carriages.getFirst().stalled = true;
            for (int i = 0; i < 100; i++) f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("MINING"),
                    "Brief drill stalls must not trigger recovery");
            f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("RECOVERING")
                            && f.controller.getDirection() == DriveDirection.BACKWARD
                            && !f.train.carriages.getFirst().stalled,
                    "Frozen position must reverse and clear the actor stall regardless of reported speed");
            helper.assertTrue(tools.getActors().stream().allMatch(a -> a.getRight().disabled),
                    "Tools must pause during the retreat");
            double stuckAt = f.position();
            for (int i = 0; i < 800 && f.controller.getStatus().equals("RECOVERING"); i++) f.ticks(1);
            helper.assertTrue(f.controller.getStatus().equals("MINING") && f.controller.isEnabled()
                            && f.controller.getDirection() == DriveDirection.FORWARD
                            && stuckAt - f.position() >= 10 && stuckAt - f.position() < 11,
                    "Actual Create movement must retreat about ten blocks and automatically retry mining");
            helper.assertTrue(f.controller.isActorTypeEnabled(ResourceLocation.parse("create:mechanical_drill"))
                            && !f.controller.isActorTypeEnabled(ResourceLocation.parse("create:deployer")),
                    "Retry must restore tool operation while preserving manually disabled actors");
            f.ticks(30);
            helper.assertTrue(f.position() > stuckAt - 10,
                    "Retry must move back toward the mining obstruction");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void backwardMiningRecoveryPersistsRemainingRetreat(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.station("Depot", 100);
            f.controller.setProfile("mining");
            f.controller.setConfig("return_station", "Depot");
            f.controller.setConfig("outbound_direction", "BACKWARD");
            f.controller.start();
            for (int i = 0; i < 101; i++) f.controller.tick();
            helper.assertTrue(f.controller.getDirection() == DriveDirection.FORWARD,
                    "Backward mining must retreat in the opposite direction");
            f.controller.tick();
            f.controller.tickDrive();
            f.setPosition(f.position() + 4);
            f.controller.tick();
            var saved = f.controller.save();
            f.controller.unload();
            var reloaded = new TrainController(f.level.getServer(), () -> {}, () -> 0);
            try {
                reloaded.load(saved);
                reloaded.tick();
                helper.assertTrue(reloaded.getStatus().equals("RECOVERING")
                                && reloaded.getDirection() == DriveDirection.FORWARD,
                        "Reload must resume recovery rather than abandoning the remaining retreat");
                reloaded.tickDrive();
                f.setPosition(f.position() + 5);
                reloaded.tick();
                helper.assertTrue(reloaded.getStatus().equals("RECOVERING"),
                        "Recovery must not finish before its saved remaining distance is covered");
                f.setPosition(f.position() + 1);
                reloaded.tick();
                helper.assertTrue(reloaded.getStatus().equals("MINING")
                                && reloaded.getDirection() == DriveDirection.BACKWARD,
                        "Reload must finish only the remaining six blocks and retry the configured direction");
            } finally {
                reloaded.stop();
                reloaded.unload();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void miningRecoveryIgnoresExpectedWaitsAndSlowProgress(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.station("Depot", 100);
            f.controller.setProfile("mining");
            f.controller.setConfig("return_station", "Depot");
            f.controller.start();
            f.train.carriageWaitingForChunks = 0;
            for (int i = 0; i < 200; i++) f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("MINING"),
                    "Unloaded chunks must not cause a retreat");
            f.train.carriageWaitingForChunks = -1;
            f.train.navigation.waitingForSignal = net.createmod.catnip.data.Pair.of(UUID.randomUUID(), true);
            for (int i = 0; i < 200; i++) f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("MINING"),
                    "Waiting at a signal must not cause a retreat");
            f.train.navigation.waitingForSignal = null;
            for (int i = 0; i < 400; i++) {
                f.setPosition(f.position() + .01);
                f.controller.tick();
            }
            helper.assertTrue(f.controller.getStatus().equals("MINING"),
                    "Slow but sustained actual movement must reset the stall timer");
            f.connect(f.last, 256, 240, 0);
            f.setPosition(122);
            f.controller.tickDrive();
            for (int i = 0; i < 200; i++) f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("SLOPE_PAUSED"),
                    "Paused mining tools on a slope must not count as actively stuck mining");
            f.controller.stop();
            f.setPosition(64);
            f.controller.setConfig("mining_speed", "0");
            f.controller.start();
            for (int i = 0; i < 200; i++) f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("MINING"),
                    "An intentional zero-speed request must not trigger recovery");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void miningRecoveryRetriesBlockedRetreatAndStillReturnsFullCargo(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.station("Depot", 100);
            f.controller.setProfile("mining");
            f.controller.setConfig("return_station", "Depot");
            f.controller.setConfig("return_threshold", "0.5");
            f.controller.start();
            for (int i = 0; i < 101; i++) f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("RECOVERING"), "Recovery must begin");
            for (int i = 0; i < 101; i++) f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("MINING") && f.controller.isEnabled(),
                    "If backing out is also blocked, retry mining instead of abandoning automation");
            for (int i = 0; i < 101; i++) f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("RECOVERING"),
                    "A repeated mining jam must be recoverable again");
            f.cargo.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
            f.cargo.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
            f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("RETURNING")
                            && f.controller.getControlMode() == ControlMode.SCHEDULE_CONTROL,
                    "Full cargo during recovery must still switch to the return schedule");
        }
        helper.succeed();
    }

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

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void miningUnloadInactivityRetainsRailsAndRespectsResumePolicy(GameTestHelper helper) {
        for (int policy = 0; policy < 3; policy++) {
            try (var f = new Fixture(helper.getLevel())) {
                f.station("Depot", 100);
                f.controller.setProfile("mining");
                f.controller.setConfig("return_station", "Depot");
                f.controller.setConfig("return_threshold", "0.5");
                f.controller.setConfig("automatic_resume", Boolean.toString(policy != 1));
                f.controller.start();
                f.cargo.setStackInSlot(0, new ItemStack(Items.RAIL, 64));
                f.cargo.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
                f.controller.tick();
                f.train.setCurrentStation(f.graph.getPoints(EdgePointType.STATION).iterator().next());
                f.controller.tick();
                f.cargo.setStackInSlot(1, ItemStack.EMPTY);
                f.controller.tick();
                var saved = f.controller.save();
                long now = f.level.getGameTime();
                helper.assertTrue(f.controller.getStatus().equals("UNLOADING")
                                && f.controller.getInventoryUsage() > .05
                                && saved.getCompound("ProfileState").getLong("UnloadIdleUntil") == now + 6000,
                        "Retained rails must allow a full five-minute inactivity wait above the threshold");

                // Exercise the deadline boundary without waiting 6,000 real server ticks.
                f.controller.unload();
                saved.getCompound("ProfileState").putLong("UnloadIdleUntil", now + 1);
                f.controller.load(saved);
                f.controller.tick();
                helper.assertTrue(f.controller.getStatus().equals("UNLOADING")
                                && f.controller.save().getCompound("ProfileState").getLong("UnloadIdleUntil") == now + 1,
                        "Reload must preserve the deadline and must not depart before it expires");

                saved = f.controller.save();
                f.controller.unload();
                saved.getCompound("ProfileState").putLong("UnloadIdleUntil", now);
                // A same-count replacement in an external handler is still cargo activity.
                f.cargo.setStackInSlot(0, new ItemStack(Items.POWERED_RAIL, 64));
                f.controller.load(saved);
                f.controller.tick();
                helper.assertTrue(f.controller.getStatus().equals("UNLOADING")
                                && f.controller.save().getCompound("ProfileState").getLong("UnloadIdleUntil") == now + 6000,
                        "Cargo activity at the deadline must reset the full wait, including equal-count swaps");

                saved = f.controller.save();
                f.controller.unload();
                // Saves from before inactivity tracking receive a fresh five-minute wait.
                saved.getCompound("ProfileState").remove("UnloadIdleUntil");
                saved.getCompound("ProfileState").remove("UnloadCargoSnapshot");
                f.controller.load(saved);
                f.controller.tick();
                helper.assertTrue(f.controller.getStatus().equals("UNLOADING")
                                && f.controller.save().getCompound("ProfileState").getLong("UnloadIdleUntil") == now + 6000,
                        "Older unloading saves must initialize the inactivity timer safely");

                saved = f.controller.save();
                f.controller.unload();
                saved.getCompound("ProfileState").putLong("UnloadIdleUntil", now);
                saved.getCompound("ProfileState").putBoolean("ReturnAfterStuck", policy == 2);
                f.controller.load(saved);
                f.controller.tick();
                f.controller.tick();
                helper.assertTrue(policy == 0
                                ? f.controller.getStatus().equals("MINING") && f.controller.isDirectDriving()
                                : !f.controller.isEnabled(),
                        "Idle completion must honor automatic resume and the repeated-jam stop policy");
                helper.assertTrue(f.cargo.getStackInSlot(0).getCount() == 64,
                        "Completing unloading must retain the supplies aboard");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void miningDockingPreservesPortableInterfaceAndOffloadsCargo(GameTestHelper helper) {
        var level = helper.getLevel();
        var basePos = helper.absolutePos(new BlockPos(2, 1, 2));
        try (var f = new Fixture(level)) {
            f.station("Depot", 100);
            var tools = f.attachTools();
            var entity = ((TestCarriage) f.train.carriages.getFirst()).testEntity;
            entity.setCarriage(f.train.carriages.getFirst());
            var psiState = AllBlocks.PORTABLE_STORAGE_INTERFACE.getDefaultState()
                    .setValue(com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlock.FACING,
                            Direction.EAST);
            var info = new StructureBlockInfo(BlockPos.ZERO, psiState, new CompoundTag());
            var psiContext = new MovementContext(level, info, tools);
            tools.getActors().add(MutablePair.of(info, psiContext));
            f.controller.setProfile("mining");
            f.controller.setConfig("return_station", "Depot");
            f.controller.setConfig("return_threshold", "0.5");
            f.controller.start();
            f.cargo.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
            f.cargo.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
            f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("RETURNING")
                            && tools.getActors().subList(0, 2).stream().allMatch(a -> a.getRight().disabled)
                            && !psiContext.disabled && tools.getDisabledActors().isEmpty(),
                    "Return must pause only drills/deployers without changing saved filters or disabling PSI");
            // Run real Create navigation up to arrival, before the profile's docking callback.
            for (int i = 0; i < 1500 && f.train.getCurrentStation() == null; i++) {
                f.train.earlyTick(level);
                f.train.tick(level);
            }
            helper.assertTrue(f.train.getCurrentStation() != null && Math.abs(f.position() - 100) < .5,
                    "The return schedule must reach the station completely");
            level.setBlockAndUpdate(basePos, psiState.setValue(
                    com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlock.FACING,
                    Direction.WEST));
            var base = (com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity)
                    level.getBlockEntity(basePos);
            psiContext.rotation = v -> v;
            psiContext.position = Vec3.atCenterOf(basePos.relative(Direction.WEST));
            psiContext.motion = Vec3.ZERO;
            var behavior = com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.get(psiState);
            behavior.visitNewPosition(psiContext, BlockPos.containing(psiContext.position));
            helper.assertTrue(psiContext.data.contains("WorkingPos"), "Aligned PSI must find its base-side interface");
            f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("UNLOADING")
                            && psiContext.data.contains("WorkingPos") && !psiContext.disabled,
                    "Holding at the station must preserve the PSI connection rather than cancel its stall");
            var saved = f.controller.save();
            f.controller.unload();
            helper.assertTrue(!psiContext.data.contains("WorkingPos"),
                    "Create's shutdown cleanup clears the transient interface connection");
            f.controller.load(saved);
            f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("UNLOADING")
                            && psiContext.data.contains("WorkingPos"),
                    "Resuming unloading must rediscover the stationary PSI even without train movement");
            for (int i = 0; i < 20; i++) {
                behavior.tick(psiContext);
                base.tick();
            }
            var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    basePos, Direction.DOWN);
            helper.assertTrue(base.canTransfer() && handler != null,
                    "Base-side interface must activate and expose the train cargo");
            int extracted = 0;
            for (int slot = 0; slot < handler.getSlots(); slot++) extracted += handler.extractItem(slot, 64, false).getCount();
            helper.assertTrue(extracted == 128 && f.controller.getInventoryUsage() == 0,
                    "Actual base-side capability extraction must offload the mounted train inventory");
            f.controller.tick();
            f.controller.tick();
            helper.assertTrue(f.controller.getStatus().equals("MINING")
                            && tools.getActors().subList(0, 2).stream().noneMatch(a -> a.getRight().disabled),
                    "Normal automatic resume must restore tools after unloading");
        } finally {
            level.removeBlock(basePos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void nonMiningToolPauseCanBeDisabledAndRestoresManualFilters(GameTestHelper helper) {
        for (boolean pause : List.of(true, false)) {
            try (var f = new Fixture(helper.getLevel())) {
                f.station("Depot", 100);
                var tools = f.attachTools();
                f.controller.setActorTypeEnabled(ResourceLocation.parse("create:deployer"), false);
                f.controller.setProfile("mining");
                f.controller.setConfig("return_station", "Depot");
                f.controller.setConfig("pause_tools_when_not_mining", Boolean.toString(pause));
                f.controller.setConfig("return_threshold", "0.5");
                f.controller.start();
                f.cargo.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
                f.cargo.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
                f.controller.tick();
                helper.assertTrue(tools.getActors().getFirst().getRight().disabled == pause
                                && tools.getActors().getLast().getRight().disabled,
                        "The idle pause toggle must control drills and respect manually disabled deployers");
                tools = f.attachTools();
                f.controller.tickActorControls(((TestCarriage) f.train.carriages.getFirst()).testEntity);
                helper.assertTrue(tools.getActors().stream().allMatch(a -> a.getRight().disabled == pause),
                        "Newly loaded carriages must receive the non-mining pause");
                f.controller.stop();
                helper.assertTrue(tools.getActors().stream().noneMatch(a -> a.getRight().disabled),
                        "Stopping automation must release its temporary actor overrides");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void repeatedJamsWaitTenMinutesPersistAndReturnWithoutRestarting(GameTestHelper helper) {
        for (DriveDirection direction : DriveDirection.values()) {
            try (var f = new Fixture(helper.getLevel())) {
                f.station("Depot", 100);
                var tools = f.attachTools();
                f.controller.setProfile("mining");
                f.controller.setConfig("return_station", "Depot");
                f.controller.setConfig("outbound_direction", direction.name());
                f.controller.start();
                for (int jam = 1; jam <= 4; jam++) {
                    for (int i = 0; i < 101; i++) f.controller.tick();
                    helper.assertTrue(f.controller.save().getCompound("ProfileState").getInt("ConsecutiveJams") == jam,
                            "Replaying the retreat must not clear the consecutive jam count");
                    if (jam == 4) break;
                    helper.assertTrue(f.controller.getStatus().equals("RECOVERING"),
                            "The first three jams must retain normal recovery");
                    f.controller.tickDrive();
                    f.controller.tick();
                    f.setPosition(f.position() - direction.sign() * 10);
                    f.controller.tick();
                    f.controller.tickDrive();
                    f.controller.tick();
                    f.setPosition(f.position() + direction.sign() * 10);
                    f.controller.tick();
                }
                var saved = f.controller.save();
                long deadline = saved.getCompound("ProfileState").getLong("StuckReturnAt");
                helper.assertTrue(f.controller.getStatus().equals("STUCK_WAITING")
                                && !f.controller.isDirectDriving() && f.train.speed == 0
                                && tools.getActors().stream().allMatch(a -> a.getRight().disabled)
                                && deadline == f.level.getGameTime() + 12000,
                        "Fourth consecutive jam must pause mining/tools for ten game minutes");
                f.controller.unload();
                var reloaded = new TrainController(f.level.getServer(), () -> {}, () -> 0);
                try {
                    reloaded.load(saved);
                    reloaded.tick();
                    helper.assertTrue(reloaded.getStatus().equals("STUCK_WAITING")
                                    && reloaded.save().getCompound("ProfileState").getLong("StuckReturnAt") == deadline,
                            "Reload must preserve the pending return rather than retry mining or restart the timer");
                    reloaded.unload();
                    // Advance the saved deadline rather than the world's clock or waiting 12,000 real ticks.
                    saved.getCompound("ProfileState").putLong("StuckReturnAt", f.level.getGameTime());
                    reloaded.load(saved);
                    reloaded.tick();
                    helper.assertTrue(reloaded.getStatus().equals("RETURNING")
                                    && reloaded.getControlMode() == ControlMode.SCHEDULE_CONTROL,
                            "Expired timeout must navigate home even with empty cargo");
                    for (int i = 0; i < 1500 && reloaded.isEnabled(); i++) {
                        f.train.earlyTick(f.level);
                        f.train.tick(f.level);
                        reloaded.tick();
                    }
                    helper.assertTrue(!reloaded.isEnabled() && reloaded.getCurrentStation().equals("Depot"),
                            "A train sent home after repeated jams must stay at base despite automatic resume");
                } finally {
                    reloaded.stop();
                    reloaded.unload();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 200)
    public static void miningBeyondJamClearsTheConsecutiveStreak(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.station("Depot", 100);
            f.controller.setProfile("mining");
            f.controller.setConfig("return_station", "Depot");
            f.controller.start();
            for (int i = 0; i < 101; i++) f.controller.tick();
            f.controller.tickDrive();
            f.controller.tick();
            f.setPosition(f.position() - 10);
            f.controller.tick();
            f.controller.tickDrive();
            f.controller.tick();
            f.setPosition(f.position() + 10.5);
            f.controller.tick();
            helper.assertTrue(f.controller.save().getCompound("ProfileState").getInt("ConsecutiveJams") == 0,
                    "A successful advance past the obstruction must reset consecutive jams");
            for (int i = 0; i < 101; i++) f.controller.tick();
            helper.assertTrue(f.controller.save().getCompound("ProfileState").getInt("ConsecutiveJams") == 1
                            && f.controller.getStatus().equals("RECOVERING"),
                    "A later unrelated jam must get a fresh recovery streak");
        }
        helper.succeed();
    }

    @GameTest(template = "framework_empty", timeoutTicks = 100)
    public static void stationRadiusPausesOnlyDeployersAndLavaClearingPreservesSolids(GameTestHelper helper) {
        try (var f = new Fixture(helper.getLevel())) {
            f.station("Depot", 70);
            Create.RAILWAYS.trackNetworks.put(f.graph.id, f.graph);
            var tools = f.attachTools();
            var entity = ((TestCarriage) f.train.carriages.getFirst()).testEntity;
            entity.setPos(10, 0, 0);
            try {
                f.controller.setProfile("mining");
                f.controller.setConfig("return_station", "Depot");
                f.controller.setConfig("pause_deployers_near_stations", "true");
                f.controller.start();
                helper.assertTrue(!f.controller.isActorTypeEnabled(ResourceLocation.parse("create:deployer"))
                                && f.controller.isActorTypeEnabled(ResourceLocation.parse("create:mechanical_drill")),
                        "Near stations only deployers must pause");
                entity.setPos(150, 0, 0);
                f.controller.tickDrive();
                helper.assertTrue(f.controller.isActorTypeEnabled(ResourceLocation.parse("create:deployer")),
                        "Deployers must resume outside station radius");
                var pos = helper.absolutePos(new BlockPos(2, 2, 2));
                tools.getActors().getFirst().getRight().position = Vec3.atCenterOf(pos);
                f.level.setBlock(pos, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(), 2);
                f.level.setBlock(pos.east(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                f.level.setBlock(pos.west(), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
                f.controller.tickDrive();
                helper.assertTrue(f.level.getBlockState(pos).isAir()
                                && f.level.getBlockState(pos.east()).is(net.minecraft.world.level.block.Blocks.STONE)
                                && f.level.getBlockState(pos.west()).is(net.minecraft.world.level.block.Blocks.WATER),
                        "Lava clearing must preserve adjacent water and stone");
            } finally {
                entity.discard();
                Create.RAILWAYS.trackNetworks.remove(f.graph.id);
            }
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
