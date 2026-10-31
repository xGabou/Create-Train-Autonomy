package net.Gabou.createtrainmining.automation.mining;

import net.Gabou.createtrainmining.api.*;

import java.util.List;
import java.util.Map;

public record MiningConfiguration(
        String returnStation,
        DriveDirection outboundDirection,
        double speed,
        double returnThreshold,
        double unloadThreshold,
        boolean automaticResume,
        boolean trackDeployerControl,
        boolean pauseToolsOnSlopes,
        double slopeLookahead,
        boolean pauseToolsWhenNotMining,
        boolean pauseDeployersNearStations, double stationRadius, boolean clearLava) {
    public MiningConfiguration(String returnStation, DriveDirection outboundDirection, double speed,
            double returnThreshold, double unloadThreshold, boolean automaticResume,
            boolean trackDeployerControl, boolean pauseToolsOnSlopes, double slopeLookahead,
            boolean pauseToolsWhenNotMining) {
        this(returnStation, outboundDirection, speed, returnThreshold, unloadThreshold,
                automaticResume, trackDeployerControl, pauseToolsOnSlopes, slopeLookahead,
                pauseToolsWhenNotMining, false, 100, true);
    }
    public MiningConfiguration(String returnStation, DriveDirection outboundDirection, double speed,
            double returnThreshold, double unloadThreshold, boolean automaticResume,
            boolean trackDeployerControl) {
        this(returnStation, outboundDirection, speed, returnThreshold, unloadThreshold,
                automaticResume, trackDeployerControl, true, 8, true);
    }
    public MiningConfiguration(String returnStation, DriveDirection outboundDirection, double speed,
            double returnThreshold, double unloadThreshold, boolean automaticResume,
            boolean trackDeployerControl, boolean pauseToolsOnSlopes, double slopeLookahead) {
        this(returnStation, outboundDirection, speed, returnThreshold, unloadThreshold,
                automaticResume, trackDeployerControl, pauseToolsOnSlopes, slopeLookahead, true);
    }
    public static List<ConfigurationField> schema() {
        return List.of(
                ConfigurationField.station("return_station", "Return Station", ""),
                ConfigurationField.ratio("return_threshold", "Return Threshold", 0.85),
                ConfigurationField.ratio("unload_threshold", "Unload Threshold", 0.05),
                ConfigurationField.ratio("mining_speed", "Mining Speed", 0.2),
                ConfigurationField.choice(
                        "outbound_direction",
                        "Outbound Direction",
                        "FORWARD",
                        "FORWARD",
                        "BACKWARD"),
                ConfigurationField.toggle("pause_deployers_near_stations", "Pause Deployers Near Stations", false),
                new ConfigurationField("station_radius", "Station Radius (blocks)", ConfigurationField.Type.NUMBER,
                        100.0, 0, 1024, List.of()),
                ConfigurationField.toggle("clear_lava", "Clear Lava Around Drills", true),
                ConfigurationField.toggle("automatic_resume", "Automatic Resume", true),
                ConfigurationField.toggle("pause_tools_when_not_mining", "Pause Tools When Not Mining", true),
                ConfigurationField.toggle(
                                "track_deployer_control", "Control Track Deployers", false)
                        .asAdvanced(),
                ConfigurationField.toggle("pause_tools_on_slopes", "Pause Tools on Slopes", true)
                        .asAdvanced(),
                new ConfigurationField("slope_lookahead", "Slope Lookahead (blocks)", ConfigurationField.Type.NUMBER,
                        8.0, 2, 64, List.of()).asAdvanced());
    }

    public static MiningConfiguration from(Map<String, Object> values) {
        var result =
                new MiningConfiguration(
                        (String) values.get("return_station"),
                        DriveDirection.valueOf((String) values.get("outbound_direction")),
                        ((Number) values.get("mining_speed")).doubleValue(),
                        ((Number) values.get("return_threshold")).doubleValue(),
                        ((Number) values.get("unload_threshold")).doubleValue(),
                        (Boolean) values.get("automatic_resume"),
                        (Boolean) values.get("track_deployer_control"),
                        (Boolean) values.getOrDefault("pause_tools_on_slopes", true),
                        ((Number) values.getOrDefault("slope_lookahead", 8.0)).doubleValue(),
                        (Boolean) values.getOrDefault("pause_tools_when_not_mining", true),
                        (Boolean) values.getOrDefault("pause_deployers_near_stations", false),
                        ((Number) values.getOrDefault("station_radius", 100.0)).doubleValue(),
                        (Boolean) values.getOrDefault("clear_lava", true));
        if (result.returnThreshold <= 0 || result.unloadThreshold >= result.returnThreshold)
            throw new IllegalArgumentException(
                    "Unload threshold must be below a positive return threshold");
        if (!Double.isFinite(result.slopeLookahead) || result.slopeLookahead < 2
                || result.slopeLookahead > 64)
            throw new IllegalArgumentException("Slope lookahead must be between 2 and 64 blocks");
        if (!Double.isFinite(result.stationRadius) || result.stationRadius < 0 || result.stationRadius > 1024)
            throw new IllegalArgumentException("Station radius must be between 0 and 1024 blocks");
        return result;
    }
}
