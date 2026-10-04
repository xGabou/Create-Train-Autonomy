package net.Gabou.createtrainmining.api;

import java.util.List;

public record ConfigurationField(
        String key,
        String label,
        Type type,
        Object defaultValue,
        double min,
        double max,
        List<String> options,
        Editor editor,
        boolean advanced) {
    /** Optional client presentation; parsing and persisted values are unchanged. */
    public enum Editor { DEFAULT, STATION }

    // Preserve the original constructor for existing profile consumers.
    public ConfigurationField(String key, String label, Type type, Object defaultValue,
            double min, double max, List<String> options) {
        this(key, label, type, defaultValue, min, max, options, Editor.DEFAULT, false);
    }
    public enum Type {
        STRING,
        NUMBER,
        BOOLEAN,
        CHOICE
    }

    public ConfigurationField {
        options = List.copyOf(options);
    }

    public static ConfigurationField text(String key, String label, String value) {
        return new ConfigurationField(key, label, Type.STRING, value, 0, 256, List.of());
    }

    public static ConfigurationField station(String key, String label, String value) {
        return new ConfigurationField(key, label, Type.STRING, value, 0, 256, List.of(), Editor.STATION, false);
    }

    public ConfigurationField asAdvanced() {
        return new ConfigurationField(key, label, type, defaultValue, min, max, options, editor, true);
    }

    public static ConfigurationField ratio(String key, String label, double value) {
        return new ConfigurationField(key, label, Type.NUMBER, value, 0, 1, List.of());
    }

    public static ConfigurationField toggle(String key, String label, boolean value) {
        return new ConfigurationField(key, label, Type.BOOLEAN, value, 0, 1, List.of());
    }

    public static ConfigurationField choice(
            String key, String label, String value, String... choices) {
        return new ConfigurationField(key, label, Type.CHOICE, value, 0, 0, List.of(choices));
    }

    public Object parse(String value) {
        return switch (type) {
            case STRING -> {
                if (value.length() > max)
                    throw new IllegalArgumentException(label + " is too long");
                yield value;
            }
            case CHOICE -> {
                if (!options.contains(value))
                    throw new IllegalArgumentException("Invalid " + label);
                yield value;
            }
            case BOOLEAN -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
                    throw new IllegalArgumentException(label + " must be true or false");
                yield Boolean.parseBoolean(value);
            }
            case NUMBER -> {
                double number = Double.parseDouble(value);
                if (!Double.isFinite(number) || number < min || number > max)
                    throw new IllegalArgumentException(
                            label + " must be between " + min + " and " + max);
                yield number;
            }
        };
    }
}
