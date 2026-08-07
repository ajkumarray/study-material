package dev.ajay.tracker.domain;

/** Spending buckets. Stored by {@code name()}, never ordinal (Phase 2.6). */
public enum Category {
    FOOD, TRANSPORT, RENT, ENTERTAINMENT, UTILITIES, HEALTH, OTHER;

    /** Case-insensitive parse used by the console; OTHER on anything unknown. */
    public static Category from(String raw) {
        if (raw == null || raw.isBlank()) return OTHER;
        try {
            return Category.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
