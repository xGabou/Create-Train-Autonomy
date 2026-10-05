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
        double slopeLookahead) {
    public MiningConfiguration(String returnStation, DriveDirection outboundDirection, double speed,
            double returnThreshold, double unloadThreshold, boolean automaticResume,
            boolean trackDeployerControl) {
        this(returnStation, outboundDirection, speed, returnThreshold, unloadThreshold,
                automaticResume, trackDeployerControl, true, 8);
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
                ConfigurationField.toggle("automatic_resume", "Automatic Resume", true),
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
                        ((Number) values.getOrDefault("slope_lookahead", 8.0)).doubleValue());
        if (result.returnThreshold <= 0 || result.unloadThreshold >= result.returnThreshold)
            throw new IllegalArgumentException(
                    "Unload threshold must be below a positive return threshold");
        if (!Double.isFinite(result.slopeLookahead) || result.slopeLookahead < 2
                || result.slopeLookahead > 64)
            throw new IllegalArgumentException("Slope lookahead must be between 2 and 64 blocks");
        return result;
    }
}
