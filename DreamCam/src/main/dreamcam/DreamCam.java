package com.jape.dreamcam;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DreamCam extends JavaPlugin implements Listener {

    private ConfigManager        configManager;
    private MessageManager       messageManager;
    private CameraRegistry       registry;
    private CameraSessionManager sessions;
    private NamespacedKey        cameraNameKey;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        configManager  = new ConfigManager(this);
        messageManager = new MessageManager(this);
        registry       = new CameraRegistry(this);
        sessions       = new CameraSessionManager(this, registry);
        cameraNameKey  = new NamespacedKey(this, "camera_name");

        PluginCommand cmd = getCommand("camera");
        if (cmd == null) {
            getLogger().severe("Command 'camera' missing in plugin.yml – disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        CameraCommand cameraCommand = new CameraCommand(this, cameraNameKey);
        cmd.setExecutor(cameraCommand);
        cmd.setTabCompleter(cameraCommand);

        Bukkit.getPluginManager().registerEvents(this, this);
        registry.load();
        getLogger().info("DreamCam enabled.");
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> sessions.isInCameraMode(p.getUniqueId()))
                .forEach(sessions::exit);
        registry.save();
        getLogger().info("DreamCam disabled.");
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public ConfigManager        getConfigManager()  { return configManager; }
    public MessageManager       getMessageManager() { return messageManager; }
    public CameraRegistry       getRegistry()       { return registry; }
    public CameraSessionManager getSessions()       { return sessions; }

    // ── Event handlers ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof CameraMenuHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;

        Player    player = (Player) e.getWhoClicked();
        ItemStack item   = e.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String cam = meta.getPersistentDataContainer().get(cameraNameKey, PersistentDataType.STRING);
        if (cam == null || !registry.contains(cam)) {
            player.sendMessage(messageManager.getCameraDoesNotExist());
            return;
        }

        String       region = ((CameraMenuHolder) e.getInventory().getHolder()).getRegion();
        List<String> cams   = registry.getCamerasInRegion(region);
        if (cams.isEmpty()) { player.sendMessage(messageManager.getNoCamerasAnymore()); player.closeInventory(); return; }

        sessions.enter(player, cam, new ArrayList<>(cams));
        player.closeInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        if (sessions.isInCameraMode(e.getPlayer().getUniqueId())) sessions.handleMove(e);
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && sessions.isInCameraMode(e.getPlayer().getUniqueId())) sessions.exit(e.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!sessions.isInCameraMode(p.getUniqueId())) return;

        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            sessions.switchCamera(p, +1); e.setCancelled(true);
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            sessions.switchCamera(p, -1); e.setCancelled(true);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { if (sessions.isInCameraMode(e.getPlayer().getUniqueId())) sessions.exit(e.getPlayer()); }
    @EventHandler public void onKick(PlayerKickEvent e)  { if (sessions.isInCameraMode(e.getPlayer().getUniqueId())) sessions.exit(e.getPlayer()); }

    // ── Inner type ────────────────────────────────────────────────────────────

    public static final class CameraMenuHolder implements InventoryHolder {
        private final String region;
        private Inventory inventory;

        public CameraMenuHolder(String region) { this.region = region; }
        public String    getRegion()                     { return region; }
        public void      setInventory(Inventory inv)     { this.inventory = inv; }
        @Override public Inventory getInventory()        { return inventory; }
    }
}