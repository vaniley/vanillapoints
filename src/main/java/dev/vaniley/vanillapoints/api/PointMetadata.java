package dev.vaniley.vanillapoints.api;

public record PointMetadata(String description, String icon, String category, boolean publicVisible, String createdBy, long createdAt) {
    public PointMetadata {
        description = normalize(description);
        icon = normalize(icon);
        category = normalize(category);
        createdBy = normalize(createdBy);
        createdAt = Math.max(0L, createdAt);
    }

    /**
     * Backwards-compatible constructor without category/visibility. Points default to public with no category.
     */
    public PointMetadata(String description, String icon, String createdBy, long createdAt) {
        this(description, icon, "", true, createdBy, createdAt);
    }

    public static PointMetadata empty() {
        return new PointMetadata("", "", "", true, "", 0L);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
