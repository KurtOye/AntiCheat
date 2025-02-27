package me.kurtoye.anticheat.handlers;

/**
 * Represents a single suspicion event.
 * Each event has a number of points and a timestamp.
 */
public class SuspiciousEvent {
    private final int points;
    private final long timestamp;

    public SuspiciousEvent(int points, long timestamp) {
        this.points = points;
        this.timestamp = timestamp;
    }

    public int getPoints() {
        return points;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
