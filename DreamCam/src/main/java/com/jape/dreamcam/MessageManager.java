package com.jape.dreamcam;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final JavaPlugin plugin;
    private File messagesFile;
    private FileConfiguration messagesConfig;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Load defaults from jar
        InputStream defConfigStream = plugin.getResource("messages.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            messagesConfig.setDefaults(defConfig);
        }
    }

    public void reloadMessages() {
        loadMessages();
    }

    public String getMessage(String path) {
        String message = messagesConfig.getString(path, "&cMessage not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getMessage(String path, Map<String, String> replacements) {
        String message = getMessage(path);

        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return message;
    }

    public String getMessageWithPrefix(String path) {
        return getMessage("prefix") + getMessage(path);
    }

    public String getMessageWithPrefix(String path, Map<String, String> replacements) {
        return getMessage("prefix") + getMessage(path, replacements);
    }

    // Convenience methods for common messages
    public String getNoPermission() {
        return getMessage("no-permission");
    }

    public String getPlayerOnly() {
        return getMessage("player-only");
    }

    public String getUnknownCommand() {
        return getMessage("unknown-command");
    }

    // Usage messages
    public String getUsageMain() {
        return getMessage("usage.main");
    }

    public String getUsageCreate() {
        return getMessage("usage.create");
    }

    public String getUsageDelete() {
        return getMessage("usage.delete");
    }

    public String getUsageMenu() {
        return getMessage("usage.menu");
    }

    // Camera messages
    public String getCameraCreated(String camera, String region) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("camera", camera);
        replacements.put("region", region);
        return getMessage("camera.created", replacements);
    }

    public String getCameraDeleted(String camera) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("camera", camera);
        return getMessage("camera.deleted", replacements);
    }

    public String getRegionDeleted(String region) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("region", region);
        return getMessage("camera.region-deleted", replacements);
    }

    public String getCameraNotFound(String name) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("name", name);
        return getMessage("camera.not-found", replacements);
    }

    public String getNoCamerasInRegion(String region) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("region", region);
        return getMessage("camera.no-cameras-in-region", replacements);
    }

    public String getCameraDoesNotExist() {
        return getMessage("camera.does-not-exist");
    }

    public String getNoCamerasAnymore() {
        return getMessage("camera.no-cameras-anymore");
    }

    // Config messages
    public String getConfigReloaded() {
        return getMessage("config.reloaded");
    }

    public String getConfigSaved() {
        return getMessage("config.saved");
    }

    public String getConfigLoaded() {
        return getMessage("config.loaded");
    }

    // Camera mode messages
    public String getCameraModeEnterActionBar(String camera) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("camera", camera);
        return getMessage("camera-mode.action-bar-enter", replacements);
    }

    public String getCameraModeSwitchActionBar(String camera) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("camera", camera);
        return getMessage("camera-mode.action-bar-switch", replacements);
    }

    public String getMenuTitle(String region) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("region", region);
        return ChatColor.translateAlternateColorCodes('&',
            messagesConfig.getString("camera-mode.menu-title", "Kameras in {region}")
                .replace("{region}", region));
    }

    public String getMenuCameraItemName(String camera) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("camera", camera);
        return getMessage("menu.camera-item-name", replacements);
    }
}
