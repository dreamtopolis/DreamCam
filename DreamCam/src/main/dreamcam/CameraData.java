package com.jape.dreamcam;

import org.bukkit.Location;

import java.util.Objects;

/** Immutable value object representing a single saved camera position. */
public final class CameraData {

    private final Location location; // includes yaw + pitch
    private final String   region;

    public CameraData(Location location, String region) {
        this.location = Objects.requireNonNull(location, "location");
        this.region   = Objects.requireNonNull(region,   "region");
    }

    /** Returns a defensive copy so callers can safely mutate (e.g. setDirection). */
    public Location getLocation() { return location.clone(); }
    public String   getRegion()   { return region; }
}