package net.Gabou.createtrainmining.automation.mining;

import net.Gabou.createtrainmining.api.*;
import net.Gabou.createtrainmining.core.ManagedTrain.TrackPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** First consumer of the generic facade. No direct Create object access. */
public final class MiningAutomationProfile implements AutomationProfile {
    private MiningState state = MiningState.STOPPED;
    private Boolean previousDeployerEnabled;
    private boolean slopePaused;
    private boolean toolsPaused;
    private boolean stationPaused;
    private static final int STUCK_TICKS = 100;
    private static final double PROGRESS_DISTANCE = 0.25;
    private static final double RECOVERY_DISTANCE = 10;
    private TrackPosition progressPosition, lastPosition;
    private int stationaryTicks;
    private double recoveryRemaining;
    private static final int MAX_CONSECUTIVE_JAMS = 3;
    private static final long STUCK_RETURN_DELAY = 20L * 60 * 10;
    private int consecutiveJams;
    private double miningAdvanceSinceJam;
    private long stuckReturnAt;
    private boolean returnAfterStuck;
    private static final long UNLOAD_IDLE_TICKS = 20L * 60 * 5;
    private long unloadIdleUntil;
    private int unloadCargoFingerprint;
    private ListTag unloadCargoSnapshot = new ListTag();
    private static final ResourceLocation DEPLOYER = ResourceLocation.parse("create:deployer");
    private static final Set<ResourceLocation> MINING_TOOLS = Set.of(
            DEPLOYER, ResourceLocation.parse("create:mechanical_drill"));

    public void onStart(AutomationContext context) {
        slopePaused = false;
        toolsPaused = false;
        resetProgress();
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
            case RECOVERING -> beginRecovery(context, cfg, recoveryRemaining);
            case STUCK_WAITING -> context.controller().stopDriving();
            case RETURNING -> {
                if (context.train().isDockedAt(cfg.returnStation()))
                    beginUnloading(context);
                else context.controller().goToStation(cfg.returnStation());
            }
            case UNLOADING -> {
                if (!context.train().isDockedAt(cfg.returnStation())) {
                    context.controller().goToStation(cfg.returnStation());
                    state = MiningState.RETURNING;
                } else beginUnloading(context);
            }
            default -> beginMining(context, cfg);
        }
        beforeTrainTick(context);
        context.markDirty();
    }

    private void beginMining(AutomationContext context, MiningConfiguration cfg) {
        unloadIdleUntil = 0;
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
        recoveryRemaining = 0;
        resetProgress();
        beforeTrainTick(context);
    }

    private DriveDirection recoveryDirection(MiningConfiguration cfg) {
        return cfg.outboundDirection() == DriveDirection.FORWARD
                ? DriveDirection.BACKWARD : DriveDirection.FORWARD;
    }

    private void beginRecovery(AutomationContext context, MiningConfiguration cfg, double remaining) {
        context.controller().stopDriving();
        recoveryRemaining = remaining;
        resetProgress();
        state = MiningState.RECOVERING;
        // Keep drills/deployers from digging or placing blocks while backing out of the jam.
        beforeTrainTick(context);
        context.controller().startDriving(new DriveRequest(recoveryDirection(cfg), cfg.speed(),
                DriveRequest.SwitchStrategy.STRAIGHT, DriveRequest.SignalBehavior.OBEY, false, true));
        context.markDirty();
    }

    private void resetProgress() {
        progressPosition = lastPosition = null;
        stationaryTicks = 0;
    }

    private void tickProgress(AutomationContext context, MiningConfiguration cfg) {
        var train = context.train();
        var position = train.getTrackPosition();
        if (!context.controller().isDirectDriving() || cfg.speed() <= 0
                || train.isWaitingForChunks() || train.isWaitingForSignal()
                || (state == MiningState.MINING && slopePaused) || position == null) {
            resetProgress();
            return;
        }
        if (lastPosition == null || !lastPosition.dimension().equals(position.dimension())) {
            progressPosition = lastPosition = position;
            stationaryTicks = 0;
            return;
        }
        if (state == MiningState.RECOVERING && train.getDirection() == recoveryDirection(cfg)) {
            double previousRemaining = recoveryRemaining;
            miningAdvanceSinceJam -= lastPosition.position().distanceTo(position.position());
            recoveryRemaining = Math.max(0, recoveryRemaining
                    - lastPosition.position().distanceTo(position.position()));
            if (recoveryRemaining != previousRemaining) context.markDirty();
            if (recoveryRemaining <= 1e-6) {
                context.controller().stopDriving();
                beginMining(context, cfg);
                context.markDirty();
                return;
            }
        } else if (state == MiningState.MINING && consecutiveJams > 0
                && train.getDirection() == cfg.outboundDirection()) {
            // Retreat/replaying the same ten blocks is not a successful mining run. Reset the
            // streak only after outbound movement has made up the retreat and advanced farther.
            miningAdvanceSinceJam += lastPosition.position().distanceTo(position.position());
            if (miningAdvanceSinceJam >= PROGRESS_DISTANCE) {
                consecutiveJams = 0;
                miningAdvanceSinceJam = 0;
            }
            context.markDirty();
        }
        lastPosition = position;
        if (progressPosition.position().distanceTo(position.position()) >= PROGRESS_DISTANCE) {
            progressPosition = position;
            stationaryTicks = 0;
        } else if (++stationaryTicks >= STUCK_TICKS) {
            if (state == MiningState.MINING) {
                consecutiveJams++;
                miningAdvanceSinceJam = 0;
                if (consecutiveJams > MAX_CONSECUTIVE_JAMS) {
                    context.controller().stopDriving();
                    state = MiningState.STUCK_WAITING;
                    recoveryRemaining = 0;
                    stuckReturnAt = context.currentTime() + STUCK_RETURN_DELAY;
                    returnAfterStuck = true;
                    resetProgress();
                    beforeTrainTick(context);
                    context.controller().broadcastMessage(Component.translatable(
                            "createtrainmining.chat.repeated_stuck", train.getName(),
                            consecutiveJams, cfg.returnStation()));
                    context.markDirty();
                } else beginRecovery(context, cfg, RECOVERY_DISTANCE);
            }
            else {
                // A short track or obstruction behind us may prevent the full retreat.
                context.controller().stopDriving();
                beginMining(context, cfg);
                context.markDirty();
            }
        }
    }

    public void onStop(AutomationContext context) {
        clearToolPause(context);
        context.controller().stopDriving();
        if (previousDeployerEnabled != null) {
            context.controller().setActorTypeEnabled(DEPLOYER, previousDeployerEnabled);
            previousDeployerEnabled = null;
        }
        state = MiningState.STOPPED;
        recoveryRemaining = 0;
        consecutiveJams = 0;
        miningAdvanceSinceJam = 0;
        stuckReturnAt = 0;
        returnAfterStuck = false;
        unloadIdleUntil = 0;
        resetProgress();
        context.markDirty();
    }

    private void setDeployers(AutomationContext context, MiningConfiguration cfg, boolean enabled) {
        if (cfg.trackDeployerControl()) context.controller().setActorTypeEnabled(DEPLOYER, enabled);
    }

    public void beforeTrainTick(AutomationContext context) {
        var cfg = MiningConfiguration.from(context.configuration());
        boolean onSlope = state == MiningState.MINING && cfg.pauseToolsOnSlopes()
                && context.controller().hasSlopeNearTrain(cfg.outboundDirection(), cfg.slopeLookahead());
        boolean paused = state == MiningState.RECOVERING || state == MiningState.STUCK_WAITING
                || (cfg.pauseToolsWhenNotMining() && state != MiningState.MINING) || onSlope;
        slopePaused = onSlope;
        if (state == MiningState.MINING && !paused && cfg.clearLava()) context.train().clearLavaAroundDrills();
        boolean nearStation = cfg.pauseDeployersNearStations() && context.train().isNearStation(cfg.stationRadius());
        if (paused != toolsPaused || nearStation != stationPaused) {
            context.controller().setPausedActorTypes(paused ? MINING_TOOLS : nearStation ? Set.of(DEPLOYER) : Set.of());
            toolsPaused = paused;
            stationPaused = nearStation;
            context.markDirty();
        }
    }

    private void clearToolPause(AutomationContext context) {
        if (!toolsPaused && !stationPaused) return;
        context.controller().setPausedActorTypes(Set.of());
        toolsPaused = false;
        slopePaused = false;
        stationPaused = false;
    }

    public void requestReturn(AutomationContext context) {
        // A requested trip completes at base, even when automatic resume is configured.
        returnAfterStuck = true;
        var cfg = MiningConfiguration.from(context.configuration());
        if (context.train().isDockedAt(cfg.returnStation())) beginUnloading(context);
        else beginReturn(context, cfg);
    }

    private void beginReturn(AutomationContext context, MiningConfiguration cfg) {
        context.controller().stopDriving();
        setDeployers(context, cfg, false);
        state = MiningState.RETURNING;
        unloadIdleUntil = 0;
        recoveryRemaining = 0;
        stuckReturnAt = 0;
        resetProgress();
        beforeTrainTick(context);
        context.controller().goToStation(cfg.returnStation());
        context.markDirty();
    }

    private void beginUnloading(AutomationContext context) {
        context.controller().pauseSchedule();
        state = MiningState.UNLOADING;
        // Preserve the inactivity deadline on reload; older saves start a fresh wait.
        if (unloadIdleUntil <= 0
                || !unloadCargoSnapshot.equals(context.inventory().snapshot(context.registries())))
            resetUnloadActivity(context);
        else unloadCargoFingerprint = context.inventory().fingerprint();
        beforeTrainTick(context);
        context.controller().refreshStorageInterfaces();
        context.markDirty();
    }

    private void resetUnloadActivity(AutomationContext context) {
        unloadCargoFingerprint = context.inventory().fingerprint();
        unloadCargoSnapshot = context.inventory().snapshot(context.registries());
        unloadIdleUntil = context.currentTime() + UNLOAD_IDLE_TICKS;
        context.markDirty();
    }

    public void tick(AutomationContext context) {
        var cfg = MiningConfiguration.from(context.configuration());
        try {
            switch (state) {
                case MINING, RECOVERING, STUCK_WAITING -> {
                    if (context.inventory().getUsageRatio() >= cfg.returnThreshold()) {
                        beginReturn(context, cfg);
                    } else if (state == MiningState.STUCK_WAITING) {
                        if (context.currentTime() >= stuckReturnAt) beginReturn(context, cfg);
                    } else tickProgress(context, cfg);
                }
                case RETURNING -> {
                    if (context.train().isDockedAt(cfg.returnStation())) beginUnloading(context);
                }
                case UNLOADING -> {
                    // Rails and other retained supplies may keep the cargo above the threshold.
                    // Check contents directly so external handlers and equal-count swaps count too.
                    if (context.inventory().fingerprint() != unloadCargoFingerprint)
                        resetUnloadActivity(context);
                    // Existing saves may have lost their PSI WorkingPos during the old docking
                    // cleanup; the stationary train will not cross another actor grid position.
                    if (context.currentTime() % 20 == 0) context.controller().refreshStorageInterfaces();
                    if (context.inventory().getUsageRatio() <= cfg.unloadThreshold()
                            || context.currentTime() >= unloadIdleUntil) {
                        if (cfg.automaticResume() && !returnAfterStuck) {
                            consecutiveJams = 0;
                            miningAdvanceSinceJam = 0;
                            state = MiningState.RESUMING;
                        }
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
        if (state == MiningState.RECOVERING) tag.putDouble("RecoveryRemaining", recoveryRemaining);
        tag.putInt("ConsecutiveJams", consecutiveJams);
        tag.putDouble("MiningAdvanceSinceJam", miningAdvanceSinceJam);
        tag.putLong("StuckReturnAt", stuckReturnAt);
        tag.putBoolean("ReturnAfterStuck", returnAfterStuck);
        if (state == MiningState.UNLOADING) {
            tag.putLong("UnloadIdleUntil", unloadIdleUntil);
            // Runtime item/component hashes are not stable between JVMs. Save actual contents.
            tag.put("UnloadCargoSnapshot", unloadCargoSnapshot.copy());
        }
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
        double remaining = tag.getDouble("RecoveryRemaining");
        recoveryRemaining = Double.isFinite(remaining) && remaining > 0
                ? Math.min(RECOVERY_DISTANCE, remaining) : RECOVERY_DISTANCE;
        resetProgress();
        consecutiveJams = Math.max(0, Math.min(MAX_CONSECUTIVE_JAMS + 1, tag.getInt("ConsecutiveJams")));
        double advance = tag.getDouble("MiningAdvanceSinceJam");
        miningAdvanceSinceJam = Double.isFinite(advance) ? Math.min(PROGRESS_DISTANCE, advance) : 0;
        stuckReturnAt = tag.getLong("StuckReturnAt");
        returnAfterStuck = tag.getBoolean("ReturnAfterStuck");
        unloadIdleUntil = tag.getLong("UnloadIdleUntil");
        unloadCargoSnapshot = tag.getList("UnloadCargoSnapshot", Tag.TAG_COMPOUND).copy();
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
