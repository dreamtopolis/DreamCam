package com.jape.dreamcam;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public void reloadConfig() {
        loadConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    // Camera settings
    public GameMode getCameraGameMode() {
        String mode = config.getString("camera.gamemode", "SPECTATOR");
        try {
            return GameMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid gamemode in config: " + mode + ", using SPECTATOR");
            return GameMode.SPECTATOR;
        }
    }

    public boolean isNightVisionEnabled() {
        return config.getBoolean("camera.night-vision.enabled", true);
    }

    public int getNightVisionDuration() {
        return config.getInt("camera.night-vision.duration", 72000);
    }

    public int getNightVisionAmplifier() {
        return config.getInt("camera.night-vision.amplifier", 0);
    }

    public boolean isNightVisionAmbient() {
        return config.getBoolean("camera.night-vision.ambient", true);
    }

    public boolean hasNightVisionParticles() {
        return config.getBoolean("camera.night-vision.particles", false);
    }

    public boolean isFreezePositionEnabled() {
        return config.getBoolean("camera.freeze-position", true);
    }

    // Menu settings
    public Material getMenuMaterial() {
        String materialName = config.getString("camera.menu.material", "BLUE_CONCRETE");
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material in config: " + materialName + ", using BLUE_CONCRETE");
            return Material.BLUE_CONCRETE;
        }
    }

    public int getMenuRowsPerPage() {
        return config.getInt("camera.menu.rows-per-page", 0);
    }
}
