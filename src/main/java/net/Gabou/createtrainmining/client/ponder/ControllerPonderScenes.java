package net.Gabou.createtrainmining.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.Gabou.createtrainmining.block.ControllerLamp;
import net.Gabou.createtrainmining.block.TrainAutomationControllerBlock;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.ParrotElement;
import net.createmod.ponder.api.element.ParrotPose;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Ponder storyboard for the Train Automation Controller. Uses the schematic
 * assets/createtrainmining/ponder/train_automation_controller.nbt (14x5x12):
 * track along z=6, a Create station at (8,1,3), the controller at (5,1,3) facing south,
 * and a small drill locomotive spanning (5..10, 2..4, 5..7) with its bogey at (8,2,6).
 */
public final class ControllerPonderScenes {
    private ControllerPonderScenes() {}

    public static void automation(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("train_automation_controller", "Automating Trains with the Controller");
        scene.configureBasePlate(1, 0, 12);
        scene.scaleSceneView(.65f);
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();

        BlockPos controllerPos = util.grid().at(5, 1, 3);
        BlockPos stationPos = util.grid().at(8, 1, 3);
        BlockPos bogeyPos = util.grid().at(8, 2, 6);
        BlockPos controlsPos = util.grid().at(8, 3, 6);
        Selection controller = util.select().position(controllerPos);
        Selection station = util.select().position(stationPos);
        Selection drill = util.select().position(5, 2, 6);
        Selection train = util.select().fromTo(5, 2, 5, 10, 4, 7);
        Vec3 seat = util.vector().centerOf(7, 3, 6);
        Vec3 lamp = util.vector().blockSurface(controllerPos, Direction.SOUTH).add(0, .4, 0);

        for (int x = 13; x >= 0; x--) {
            scene.world().showSection(util.select().position(x, 1, 6), Direction.DOWN);
            scene.idle(1);
        }
        scene.world().showSection(station, Direction.DOWN);
        scene.idle(8);

        ElementLink<WorldSectionElement> trainElement =
                scene.world().showIndependentSection(train, Direction.DOWN);
        ElementLink<ParrotElement> birb =
                scene.special().createBirb(seat, ParrotPose.FacePointOfInterestPose::new);
        scene.world().animateTrainStation(stationPos, true);
        scene.idle(15);

        // 1. Introduce the block
        scene.world().showSection(controller, Direction.DOWN);
        scene.idle(15);
        scene.overlay()
                .showText(80)
                .pointAt(util.vector().topOf(controllerPos))
                .placeNearTarget()
                .attachKeyFrame()
                .text("The Train Automation Controller drives Create trains on its own, no player needed on board");
        scene.idle(90);

        // 2. Opening the panel
        scene.overlay()
                .showControls(util.vector().topOf(controllerPos), Pointing.DOWN, 50)
                .rightClick();
        scene.idle(10);
        scene.overlay()
                .showText(90)
                .pointAt(util.vector().topOf(controllerPos))
                .placeNearTarget()
                .attachKeyFrame()
                .text("Right-click it to open the control panel, then pick a train and an automation profile");
        scene.idle(100);

        // 3. Starting automation
        scene.overlay()
                .showLine(
                        PonderPalette.GREEN,
                        util.vector().topOf(controllerPos),
                        util.vector().centerOf(controlsPos),
                        40);
        scene.effects().indicateSuccess(controllerPos);
        setLamp(scene, controllerPos, ControllerLamp.RUNNING);
        scene.special().conductorBirb(birb, true);
        scene.special().movePointOfInterest(util.grid().at(-6, 3, 6));
        scene.idle(15);

        scene.overlay()
                .showText(70)
                .pointAt(lamp)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Once started, a green lamp shows the controller is driving the train");
        scene.idle(30);

        scene.world().animateTrainStation(stationPos, false);
        scene.world().setKineticSpeed(drill, 64);
        scene.world().moveSection(trainElement, util.vector().of(-9, 0, 0), 50);
        scene.world().animateBogey(bogeyPos, 9f, 50);
        scene.special().moveParrot(birb, util.vector().of(-9, 0, 0), 50);
        scene.idle(45);
        scene.world().hideIndependentSection(trainElement, Direction.WEST);
        scene.special().hideElement(birb, Direction.WEST);
        scene.idle(20);

        // 4. Profiles do the work
        scene.overlay()
                .showText(90)
                .pointAt(util.vector().topOf(1, 1, 6))
                .placeNearTarget()
                .attachKeyFrame()
                .text("The profile takes it from there: Mining drives out, digs, and heads home once the cargo fills up");
        scene.idle(100);

        trainElement = scene.world().showIndependentSection(train, Direction.EAST);
        scene.world().moveSection(trainElement, util.vector().of(-9, 0, 0), 0);
        birb = scene.special().createBirb(seat.add(-9, 0, 0), ParrotPose.FacePointOfInterestPose::new);
        scene.special().conductorBirb(birb, true);
        scene.special().movePointOfInterest(util.grid().at(18, 3, 6));
        scene.idle(5);
        scene.world().moveSection(trainElement, util.vector().of(9, 0, 0), 50);
        scene.world().animateBogey(bogeyPos, -9f, 50);
        scene.special().moveParrot(birb, util.vector().of(9, 0, 0), 50);
        scene.idle(50);

        // 5. Waiting
        scene.world().setKineticSpeed(drill, 0);
        scene.world().animateTrainStation(stationPos, true);
        setLamp(scene, controllerPos, ControllerLamp.WAITING);
        scene.idle(10);
        scene.overlay()
                .showText(90)
                .pointAt(lamp)
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame()
                .text("The lamp turns yellow while the train is stopped, like when it unloads at a station");
        scene.idle(100);

        // 6. Errors
        setLamp(scene, controllerPos, ControllerLamp.ERROR);
        scene.special().conductorBirb(birb, false);
        scene.idle(10);
        scene.overlay()
                .showText(90)
                .pointAt(lamp)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Red means something needs your attention. Hover the panel's status bar to read the full error");
        scene.idle(100);

        // 7. Stopping
        setLamp(scene, controllerPos, ControllerLamp.INACTIVE);
        scene.idle(10);
        scene.overlay()
                .showText(90)
                .pointAt(util.vector().topOf(controllerPos))
                .placeNearTarget()
                .attachKeyFrame()
                .text("Stop automation from the panel before driving manually or changing its settings");
        scene.idle(100);
        scene.markAsFinished();
    }

    private static void setLamp(CreateSceneBuilder scene, BlockPos pos, ControllerLamp lamp) {
        scene.world()
                .modifyBlock(
                        pos, s -> s.setValue(TrainAutomationControllerBlock.LAMP, lamp), false);
    }
}
