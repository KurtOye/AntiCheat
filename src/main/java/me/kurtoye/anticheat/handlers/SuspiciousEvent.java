package me.kurtoye.anticheat.handlers;

/**
 * Represents a single suspicion event instance.
 * Used for long-term behavior tracking in PlayerHistoryHandler.
 */
public class SuspiciousEvent {

    private final int points;
    private final long timestamp;

    public SuspiciousEvent(int points, long timestamp) {
        this.points = points;
        this.timestamp = timestamp;
    }

    /**
     * Returns the number of suspicion points earned in this event.
     */
    public int getPoints() {
        return points;
    }

    /**
     * Returns the timestamp when this event was recorded.
     */
    public long getTimestamp() {
        return timestamp;
    }
}
