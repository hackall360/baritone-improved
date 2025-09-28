package baritone.api.pathing.seed;

import java.util.Locale;

public enum SeedOreMode {
    TERRAIN_ONLY,
    TERRAIN_PLUS_VEINS,
    TERRAIN_PLUS_EXACT_ORES;

    public static SeedOreMode fromString(String literal) {
        String normalized = literal.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TERRAIN_ONLY", "TERRAIN" -> TERRAIN_ONLY;
            case "VEINS", "TERRAIN_PLUS_VEINS", "TERRAIN_VEINS" -> TERRAIN_PLUS_VEINS;
            case "ORES", "TERRAIN_PLUS_EXACT_ORES", "EXACT" -> TERRAIN_PLUS_EXACT_ORES;
            default -> throw new IllegalArgumentException("Unknown seed ore mode: " + literal);
        };
    }
}
