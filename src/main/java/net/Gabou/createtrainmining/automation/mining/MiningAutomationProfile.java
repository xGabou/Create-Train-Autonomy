package net.Gabou.createtrainmining.automation.mining;

import net.Gabou.createtrainmining.api.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** First consumer of the generic facade. No direct Create object access. */
public final class MiningAutomationProfile implements AutomationProfile {
    private MiningState state = MiningState.STOPPED;
    private Boolean previousDeployerEnabled;
    private boolean slopePaused;
    private static final ResourceLocation DEPLOYER = ResourceLocation.parse("create:deployer");
    private static final Set<ResourceLocation> SLOPE_TOOLS = Set.of(
            DEPLOYER, ResourceLocation.parse("create:mechanical_drill"));

    public void onStart(AutomationContext context) {
        slopePaused = false;
        var cfg = MiningConfiguration.from(context.configuration());
        if (cfg.returnStation().isBlank() || !context.stations().contains(cfg.returnStation()))
            throw new IllegalArgumentException(
                    "Choose a return station on the selected train's graph");
        if (context.inventory().getSlots() == 0)
            throw new IllegalStateException("Mining profile requires a cargo inventory");
        if (!context.train().hasForwardConductor() && !context.train().hasBackwardConductor())
            throw new IllegalStateException("Mining return navigation requires a conductor");
        if (cfg.trackDeployerControl()
                && previousDeployerEnabled == null
                && context.controller().listActorTypes().contains(DEPLOYER.toString()))
            previousDeployerEnabled = context.controller().isActorTypeEnabled(DEPLOYER);
        switch (state) {
            case RETURNING -> {
                if (cfg.returnStation().equals(context.train().getCurrentStation()))
                    state = MiningState.UNLOADING;
                else context.controller().goToStation(cfg.returnStation());
            }
            case UNLOADING -> {
                if (!cfg.returnStation().equals(context.train().getCurrentStation())) {
                    context.controller().goToStation(cfg.returnStation());
                    state = MiningState.RETURNING;
                }
            }
            default -> beginMining(context, cfg);
        }
        context.markDirty();
    }

    private void beginMining(AutomationContext context, MiningConfiguration cfg) {
        context.controller().clearSchedule();
        setDeployers(context, cfg, true);
        // Construction can extend the track. At its current end, wait and retry instead of ending
        // the request.
        context.controller()
                .startDriving(
                        new DriveRequest(
                                cfg.outboundDirection(),
                                cfg.speed(),
                                DriveRequest.SwitchStrategy.STRAIGHT,
                                DriveRequest.SignalBehavior.OBEY,
                                false,
                                true));
        state = MiningState.MINING;
        beforeTrainTick(context);
    }

    public void onStop(AutomationContext context) {
        clearSlopePause(context);
        context.controller().stopDriving();
        if (previousDeployerEnabled != null) {
            context.controller().setActorTypeEnabled(DEPLOYER, previousDeployerEnabled);
            previousDeployerEnabled = null;
        }
        state = MiningState.STOPPED;
        context.markDirty();
    }

    private void setDeployers(AutomationContext context, MiningConfiguration cfg, boolean enabled) {
        if (cfg.trackDeployerControl()) context.controller().setActorTypeEnabled(DEPLOYER, enabled);
    }

    public void beforeTrainTick(AutomationContext context) {
        var cfg = MiningConfiguration.from(context.configuration());
        boolean paused = state == MiningState.MINING && cfg.pauseToolsOnSlopes()
                && context.controller().hasSlopeNearTrain(cfg.outboundDirection(), cfg.slopeLookahead());
        if (paused != slopePaused) {
            context.controller().setPausedActorTypes(paused ? SLOPE_TOOLS : Set.of());
            slopePaused = paused;
            context.markDirty();
        }
    }

    private void clearSlopePause(AutomationContext context) {
        if (!slopePaused) return;
        context.controller().setPausedActorTypes(Set.of());
        slopePaused = false;
    }

    public void tick(AutomationContext context) {
        var cfg = MiningConfiguration.from(context.configuration());
        try {
            switch (state) {
                case MINING -> {
                    if (context.inventory().getUsageRatio() >= cfg.returnThreshold()) {
                        context.controller().stopDriving();
                        clearSlopePause(context);
                        setDeployers(context, cfg, false);
                        context.controller().goToStation(cfg.returnStation());
                        state = MiningState.RETURNING;
                    }
                }
                case RETURNING -> {
                    if (cfg.returnStation().equals(context.train().getCurrentStation())) {
                        context.controller().pauseSchedule();
                        state = MiningState.UNLOADING;
                    }
                }
                case UNLOADING -> {
                    if (context.inventory().getUsageRatio() <= cfg.unloadThreshold()) {
                        if (cfg.automaticResume()) state = MiningState.RESUMING;
                        else context.controller().stop();
                    }
                }
                case RESUMING -> beginMining(context, cfg);
                default -> {}
            }
        } catch (RuntimeException e) {
            state = MiningState.ERROR;
            context.markDirty();
            throw e;
        }
    }

    public CompoundTag serializeState() {
        var tag = new CompoundTag();
        tag.putString("State", state.name());
        if (previousDeployerEnabled != null)
            tag.putBoolean("PreviousDeployerEnabled", previousDeployerEnabled);
        return tag;
    }

    public void deserializeState(CompoundTag tag) {
        try {
            state = MiningState.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            state = MiningState.STOPPED;
        }
        previousDeployerEnabled =
                tag.contains("PreviousDeployerEnabled")
                        ? tag.getBoolean("PreviousDeployerEnabled")
                        : null;
    }

    public String getStatus() {
        return state == MiningState.MINING && slopePaused ? "SLOPE_PAUSED" : state.name();
    }

    public List<ConfigurationField> getConfigurationSchema() {
        return MiningConfiguration.schema();
    }

    public void validateConfiguration(Map<String, Object> values) {
        MiningConfiguration.from(values);
    }
}
