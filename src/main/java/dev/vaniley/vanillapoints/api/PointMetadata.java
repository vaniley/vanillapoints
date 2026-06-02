package dev.vaniley.vanillapoints.api;

public record PointMetadata(String description, String icon, String createdBy, long createdAt) {
    public PointMetadata {
        description = normalize(description);
        icon = normalize(icon);
        createdBy = normalize(createdBy);
        createdAt = Math.max(0L, createdAt);
    }

    public static PointMetadata empty() {
        return new PointMetadata("", "", "", 0L);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
