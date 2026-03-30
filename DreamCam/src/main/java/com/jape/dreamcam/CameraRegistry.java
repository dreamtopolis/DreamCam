package com.jape.dreamcam;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.logging.Logger;

/**
 * Owns all camera data: the main map, the region index, and config persistence.
 * No Bukkit events, no player state — purely a data store.
 */
public final class CameraRegistry {

    private final DreamCam plugin;
    private final Logger   log;

    // Camera name → data (insertion-ordered for predictable iteration)
    private final Map<String, CameraData> cameras = new LinkedHashMap<>();

    // Lower-case region name → sorted list of camera names (O(1) region lookup)
    private final Map<String, List<String>> regionIndex = new HashMap<>();

    public CameraRegistry(DreamCam plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public CameraData get(String name)              { return cameras.get(name); }
    public boolean    contains(String name)         { return cameras.containsKey(name); }
    public Set<String> cameraNames()               { return Collections.unmodifiableSet(cameras.keySet()); }

    /** Returns a sorted, unmodifiable view of camera names in the given region. */
    public List<String> getCamerasInRegion(String region) {
        return Collections.unmodifiableList(
                regionIndex.getOrDefault(region.toLowerCase(Locale.ROOT), Collections.emptyList()));
    }

    /** Returns all known region names, sorted case-insensitively. */
    public Set<String> getAllRegions() {
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        cameras.values().forEach(d -> result.add(d.getRegion()));
        return result;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public void put(String name, CameraData data) {
        CameraData old = cameras.put(name, data);
        if (old != null) deindex(name, old.getRegion());
        index(name, data.getRegion());
    }

    /** Removes by exact camera name. Returns the removed data, or null if not found. */
    public CameraData remove(String name) {
        CameraData removed = cameras.remove(name);
        if (removed != null) deindex(name, removed.getRegion());
        return removed;
    }

    /** Removes all cameras belonging to the given region. Returns how many were removed. */
    public int removeRegion(String region) {
        List<String> toRemove = new ArrayList<>(
                regionIndex.getOrDefault(region.toLowerCase(Locale.ROOT), Collections.emptyList()));
        toRemove.forEach(name -> cameras.remove(name));
        regionIndex.remove(region.toLowerCase(Locale.ROOT));
        return toRemove.size();
    }

    // ── Index helpers ─────────────────────────────────────────────────────────

    private void index(String cameraName, String region) {
        List<String> list = regionIndex.computeIfAbsent(
                region.toLowerCase(Locale.ROOT), k -> new ArrayList<>());
        if (!list.contains(cameraName)) {
            list.add(cameraName);
            list.sort(String.CASE_INSENSITIVE_ORDER);
        }
    }

    private void deindex(String cameraName, String region) {
        List<String> list = regionIndex.get(region.toLowerCase(Locale.ROOT));
        if (list == null) return;
        list.remove(cameraName);
        if (list.isEmpty()) regionIndex.remove(region.toLowerCase(Locale.ROOT));
    }

    // ── Config I/O ────────────────────────────────────────────────────────────

    public void load() {
        plugin.getConfigManager().reloadConfig();
        FileConfiguration config = plugin.getConfigManager().getConfig();

        cameras.clear();
        regionIndex.clear();

        ConfigurationSection sec = config.getConfigurationSection("cameras");
        if (sec == null) { log.info("No cameras found in config."); return; }

        for (String name : sec.getKeys(false)) {
            String path      = "cameras." + name;
            String worldName = config.getString(path + ".world");
            World  world     = worldName != null ? Bukkit.getWorld(worldName) : null;

            if (world == null) {
                log.warning("Skipping camera '" + name + "': world '" + worldName + "' not found.");
                continue;
            }

            double x     = config.getDouble(path + ".x");
            double y     = config.getDouble(path + ".y");
            double z     = config.getDouble(path + ".z");
            float  yaw   = (float) config.getDouble(path + ".yaw");
            float  pitch = (float) config.getDouble(path + ".pitch");
            String region = config.getString(path + ".region", "default");

            put(name, new CameraData(new Location(world, x, y, z, yaw, pitch), region));
        }

        log.info("Loaded " + cameras.size() + " camera(s).");
    }

    public void save() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        config.set("cameras", null); // wipe stale entries

        cameras.forEach((name, data) -> {
            Location loc = data.getLocation();
            if (loc.getWorld() == null) {
                log.warning("Skipping camera '" + name + "': world is null.");
                return;
            }
            String path = "cameras." + name;
            config.set(path + ".world",  loc.getWorld().getName());
            config.set(path + ".x",      loc.getX());
            config.set(path + ".y",      loc.getY());
            config.set(path + ".z",      loc.getZ());
            config.set(path + ".yaw",    loc.getYaw());
            config.set(path + ".pitch",  loc.getPitch());
            config.set(path + ".region", data.getRegion());
        });

        plugin.getConfigManager().saveConfig();
    }
}