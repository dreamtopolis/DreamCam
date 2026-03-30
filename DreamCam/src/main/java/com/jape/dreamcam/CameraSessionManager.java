package com.jape.dreamcam;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Manages per-player camera-mode state: enter, exit, switch, and position freeze.
 * Holds no Bukkit event listeners — those live in DreamCam and delegate here.
 */
public final class CameraSessionManager {

    private final DreamCam       plugin;
    private final CameraRegistry registry;

    // Per-player snapshots taken when entering camera mode
    private final Map<UUID, Location> previousLocations = new HashMap<>();
    private final Map<UUID, GameMode> previousGameModes = new HashMap<>();

    // Active camera-mode players
    private final Set<UUID> inCameraMode = new HashSet<>();

    // The ordered list of cameras the player is cycling through, and their current index
    private final Map<UUID, List<String>> playerCameras      = new HashMap<>();
    private final Map<UUID, Integer>      playerCameraIndex  = new HashMap<>();

    // Cached so we don't allocate a new PotionEffect on every enter/switch
    private PotionEffect cachedNightVision;

    public CameraSessionManager(DreamCam plugin, CameraRegistry registry) {
        this.plugin   = plugin;
        this.registry = registry;
        rebuildNightVisionCache();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isInCameraMode(UUID id) { return inCameraMode.contains(id); }

    public void enter(Player player, String cameraName, List<String> regionCameras) {
        UUID id = player.getUniqueId();

        if (!inCameraMode.contains(id)) {
            previousLocations.put(id, player.getLocation().clone());
            previousGameModes.put(id, player.getGameMode());
            inCameraMode.add(id);
            player.setGameMode(plugin.getConfigManager().getCameraGameMode());
        }

        int idx = regionCameras.indexOf(cameraName);
        playerCameras.put(id, regionCameras);
        playerCameraIndex.put(id, Math.max(idx, 0));

        applyNightVision(player);
        teleport(player, cameraName);
        actionBar(player, plugin.getMessageManager().getCameraModeEnterActionBar(cameraName));
    }

    public void exit(Player player) {
        UUID id = player.getUniqueId();

        player.teleport(previousLocations.getOrDefault(id, player.getLocation()));
        player.setGameMode(previousGameModes.getOrDefault(id, GameMode.SURVIVAL));
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);

        inCameraMode.remove(id);
        previousLocations.remove(id);
        previousGameModes.remove(id);
        playerCameras.remove(id);
        playerCameraIndex.remove(id);
    }

    /** delta = +1 for next, -1 for previous. */
    public void switchCamera(Player player, int delta) {
        UUID         id     = player.getUniqueId();
        List<String> list   = playerCameras.get(id);
        Integer      idxObj = playerCameraIndex.get(id);
        if (list == null || list.isEmpty() || idxObj == null) return;

        int idx = ((idxObj + delta) % list.size() + list.size()) % list.size();
        String cam = list.get(idx);

        teleport(player, cam);
        playerCameraIndex.put(id, idx);
        applyNightVision(player);
        actionBar(player, plugin.getMessageManager().getCameraModeSwitchActionBar(cam));
    }

    /**
     * Freezes the player's XYZ position while allowing free look.
     * Call this from PlayerMoveEvent when isInCameraMode() is true.
     */
    public void handleMove(org.bukkit.event.player.PlayerMoveEvent e) {
        if (!plugin.getConfigManager().isFreezePositionEnabled()) return;

        Location from = e.getFrom();
        Location to   = e.getTo();

        if (to.getX() != from.getX() || to.getY() != from.getY() || to.getZ() != from.getZ()) {
            Location corrected = from.clone();
            corrected.setYaw(to.getYaw());
            corrected.setPitch(to.getPitch());
            e.setTo(corrected);
        }
    }

    /** Call after a config reload so the cached PotionEffect stays in sync. */
    public void rebuildNightVisionCache() {
        ConfigManager cfg = plugin.getConfigManager();
        cachedNightVision = cfg.isNightVisionEnabled()
                ? new PotionEffect(PotionEffectType.NIGHT_VISION,
                cfg.getNightVisionDuration(),
                cfg.getNightVisionAmplifier(),
                cfg.isNightVisionAmbient(),
                cfg.hasNightVisionParticles())
                : null;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void teleport(Player player, String cameraName) {
        CameraData data = registry.get(cameraName);
        if (data != null) player.teleport(data.getLocation());
    }

    private void applyNightVision(Player player) {
        if (cachedNightVision != null) player.addPotionEffect(cachedNightVision, true);
    }

    private void actionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }
}