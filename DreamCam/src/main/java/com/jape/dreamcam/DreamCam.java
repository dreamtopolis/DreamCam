package com.jape.dreamcam;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

public class DreamCam extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    /* =========================
       DATA CLASSES
       ========================= */

    public static final class CameraData {
        private final Location location;   // stored base location (never mutated)
        private final String region;
        private final Vector direction;

        public CameraData(Location location, String region, Vector direction) {
            this.location = location;
            this.region = region;
            this.direction = direction;
        }

        public Location getLocation() { return location; }
        public String getRegion() { return region; }
        public Vector getDirection() { return direction; }
    }

    /**
     * Stores region name for the camera menu (Java-8 compatible).
     */
    public static final class CameraMenuHolder implements InventoryHolder {
        private final String region;

        public CameraMenuHolder(String region) {
            this.region = region;
        }

        public String getRegion() {
            return region;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /* =========================
       FIELDS
       ========================= */

    private final Map<String, CameraData> cameras = new LinkedHashMap<>();

    private final Set<UUID> inCameraMode = new HashSet<>();
    private final Map<UUID, Location> previousLocations = new HashMap<>();
    private final Map<UUID, GameMode> previousGameModes = new HashMap<>();
    private final Map<UUID, List<String>> playerCameras = new HashMap<>();
    private final Map<UUID, Integer> playerCameraIndex = new HashMap<>();

    private static final String MENU_PREFIX = "Kameras in ";

    // messages.yml
    private FileConfiguration messages;

    /* =========================
       ENABLE / DISABLE
       ========================= */

    @Override
    public void onEnable() {
        PluginCommand cmd = getCommand("camera");
        if (cmd == null) {
            getLogger().severe("Command 'camera' fehlt in plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        cmd.setExecutor(this);
        cmd.setTabCompleter(this);

        Bukkit.getPluginManager().registerEvents(this, this);

        saveDefaultConfig();
        loadMessages();
        loadCamerasFromConfig();
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (inCameraMode.contains(p.getUniqueId())) {
                exitCameraMode(p);
            }
        }
        saveCamerasToConfig();
    }

    /* =========================
       COMMANDS
       ========================= */

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(msg("player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(msg("usage"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("create")) {
            if (!player.hasPermission("dreamcam.admin")) {
                player.sendMessage(msg("no-permission"));
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(msg("usage"));
                return true;
            }

            String cameraName = args[1];
            String regionName = args[2];

            Location base = player.getLocation().clone();
            Vector dir = base.getDirection();

            // store world+xyz only (no yaw/pitch mutation)
            Location stored = new Location(base.getWorld(), base.getX(), base.getY(), base.getZ());
            cameras.put(cameraName, new CameraData(stored, regionName, dir));

            Map<String, String> p = new HashMap<String, String>();
            p.put("camera", cameraName);
            p.put("region", regionName);
            player.sendMessage(msg("camera-created", p));
            return true;
        }

        if (sub.equals("delete")) {
            if (!player.hasPermission("dreamcam.admin")) {
                player.sendMessage(msg("no-permission"));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(msg("usage"));
                return true;
            }

            String name = args[1];

            // delete single camera
            if (cameras.containsKey(name)) {
                cameras.remove(name);
                player.sendMessage(msg("camera-deleted", singleton("camera", name)));
                return true;
            }

            // treat as region delete
            List<String> toRemove = new ArrayList<String>();
            for (Map.Entry<String, CameraData> e : cameras.entrySet()) {
                if (e.getValue().getRegion().equalsIgnoreCase(name)) {
                    toRemove.add(e.getKey());
                }
            }

            if (toRemove.isEmpty()) {
                player.sendMessage(msg("camera-not-found"));
            } else {
                for (String cam : toRemove) {
                    cameras.remove(cam);
                }
                player.sendMessage(msg("region-deleted", singleton("region", name)));
            }
            return true;
        }

        if (sub.equals("menu")) {
            if (args.length < 2) {
                player.sendMessage(msg("usage"));
                return true;
            }

            String regionName = args[1];
            List<String> camsInRegion = new ArrayList<String>();

            for (Map.Entry<String, CameraData> e : cameras.entrySet()) {
                if (e.getValue().getRegion().equalsIgnoreCase(regionName)) {
                    camsInRegion.add(e.getKey());
                }
            }

            if (camsInRegion.isEmpty()) {
                player.sendMessage(msg("region-empty"));
                return true;
            }

            openMenu(player, regionName, camsInRegion);
            return true;
        }

        if (sub.equals("reload")) {
            if (!player.hasPermission("dreamcam.admin")) {
                player.sendMessage(msg("no-permission"));
                return true;
            }

            reloadConfig();
            loadMessages();
            loadCamerasFromConfig();

            player.sendMessage(msg("cameras-reloaded"));
            return true;
        }

        if (sub.equals("save")) {
            if (!player.hasPermission("dreamcam.admin")) {
                player.sendMessage(msg("no-permission"));
                return true;
            }
            saveCamerasToConfig();
            player.sendMessage(msg("cameras-saved"));
            return true;
        }

        if (sub.equals("load")) {
            if (!player.hasPermission("dreamcam.admin")) {
                player.sendMessage(msg("no-permission"));
                return true;
            }
            loadCamerasFromConfig();
            player.sendMessage(msg("cameras-loaded"));
            return true;
        }

        player.sendMessage(msg("usage"));
        return true;
    }

    /* =========================
       MENU
       ========================= */

    private void openMenu(Player player, String region, List<String> cams) {
        int size = ((cams.size() - 1) / 9 + 1) * 9;

        // Optional: translate menu title too (example key "menu-title": "Kameras in %region%")
        String title = MENU_PREFIX + region;

        Inventory inv = Bukkit.createInventory(new CameraMenuHolder(region), size, title);

        for (String cam : cams) {
            ItemStack it = new ItemStack(Material.BLUE_CONCRETE);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + cam);
                it.setItemMeta(meta);
            }
            inv.addItem(it);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (!(holder instanceof CameraMenuHolder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != Material.BLUE_CONCRETE) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String cameraName = ChatColor.stripColor(meta.getDisplayName());
        CameraData data = cameras.get(cameraName);
        if (data == null) {
            player.sendMessage(msg("camera-missing-anymore"));
            return;
        }

        String region = ((CameraMenuHolder) holder).getRegion();

        // rebuild list for cycling
        List<String> list = new ArrayList<String>();
        for (Map.Entry<String, CameraData> e : cameras.entrySet()) {
            if (e.getValue().getRegion().equalsIgnoreCase(region)) {
                list.add(e.getKey());
            }
        }

        if (list.isEmpty()) {
            player.sendMessage(msg("region-missing-anymore"));
            player.closeInventory();
            return;
        }

        playerCameras.put(player.getUniqueId(), list);

        int idx = list.indexOf(cameraName);
        playerCameraIndex.put(player.getUniqueId(), idx < 0 ? 0 : idx);

        enterCameraMode(player, cameraName);
        player.closeInventory();
    }

    /* =========================
       CAMERA MODE
       ========================= */

    private void enterCameraMode(Player player, String cameraName) {
        UUID id = player.getUniqueId();

        if (!inCameraMode.contains(id)) {
            inCameraMode.add(id);
            previousLocations.put(id, player.getLocation().clone());
            previousGameModes.put(id, player.getGameMode());

            player.setGameMode(GameMode.SPECTATOR);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION,
                    20 * 60 * 60, // 1 hour, removed on exit
                    0,
                    true,
                    false
            ));
        }

        teleportToCamera(player, cameraName);
        sendActionBar(player, msg("camera-enter", singleton("camera", cameraName)));
    }

    private void teleportToCamera(Player player, String cameraName) {
        CameraData d = cameras.get(cameraName);
        if (d == null) return;

        Location tp = d.getLocation().clone();
        Vector dir = d.getDirection();
        if (dir != null) tp.setDirection(dir);

        player.teleport(tp);
    }

    private void exitCameraMode(Player player) {
        UUID id = player.getUniqueId();

        Location prev = previousLocations.get(id);
        GameMode prevGm = previousGameModes.get(id);

        if (prev != null) player.teleport(prev);
        if (prevGm != null) player.setGameMode(prevGm);

        player.removePotionEffect(PotionEffectType.NIGHT_VISION);

        inCameraMode.remove(id);
        previousLocations.remove(id);
        previousGameModes.remove(id);
        playerCameras.remove(id);
        playerCameraIndex.remove(id);
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && inCameraMode.contains(event.getPlayer().getUniqueId())) {
            exitCameraMode(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!inCameraMode.contains(player.getUniqueId())) return;

        Action a = event.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            switchCamera(player, true);
            event.setCancelled(true);
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            switchCamera(player, false);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (inCameraMode.contains(p.getUniqueId())) exitCameraMode(p);
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        Player p = event.getPlayer();
        if (inCameraMode.contains(p.getUniqueId())) exitCameraMode(p);
    }

    private void switchCamera(Player player, boolean next) {
        UUID id = player.getUniqueId();
        List<String> list = playerCameras.get(id);
        Integer idxObj = playerCameraIndex.get(id);

        if (list == null || list.isEmpty() || idxObj == null) return;

        int idx = idxObj.intValue();
        if (next) {
            idx = (idx + 1) % list.size();
        } else {
            idx = idx - 1;
            if (idx < 0) idx = list.size() - 1;
        }

        playerCameraIndex.put(id, idx);

        String cam = list.get(idx);
        teleportToCamera(player, cam);
        sendActionBar(player, msg("camera-switch", singleton("camera", cam)));
    }

    /* =========================
       TAB COMPLETE
       ========================= */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("camera")) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("create", "delete", "menu", "reload", "save", "load");
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String typed = args[1].toLowerCase(Locale.ROOT);

            List<String> out = new ArrayList<String>();

            if (sub.equals("menu")) {
                Set<String> regions = new HashSet<String>();
                for (CameraData d : cameras.values()) regions.add(d.getRegion());
                for (String r : regions) {
                    if (r.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(r);
                }
            } else if (sub.equals("delete")) {
                for (String cam : cameras.keySet()) {
                    if (cam.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(cam);
                }
                Set<String> regions = new HashSet<String>();
                for (CameraData d : cameras.values()) regions.add(d.getRegion());
                for (String r : regions) {
                    if (r.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(r);
                }
            }

            return out;
        }

        return Collections.emptyList();
    }

    /* =========================
       CONFIG (CAMERAS)
       ========================= */

    private void saveCamerasToConfig() {
        FileConfiguration config = getConfig();
        config.set("cameras", null);

        for (Map.Entry<String, CameraData> entry : cameras.entrySet()) {
            String name = entry.getKey();
            CameraData data = entry.getValue();

            Location loc = data.getLocation();
            if (loc.getWorld() == null) continue;

            String path = "cameras." + name;
            config.set(path + ".world", loc.getWorld().getName());
            config.set(path + ".x", loc.getX());
            config.set(path + ".y", loc.getY());
            config.set(path + ".z", loc.getZ());
            config.set(path + ".region", data.getRegion());
            config.set(path + ".direction", data.getDirection());
        }

        saveConfig();
    }

    private void loadCamerasFromConfig() {
        reloadConfig();
        cameras.clear();

        ConfigurationSection sec = getConfig().getConfigurationSection("cameras");
        if (sec == null) return;

        for (String cameraName : sec.getKeys(false)) {
            String path = "cameras." + cameraName;

            String worldName = getConfig().getString(path + ".world");
            World world = (worldName == null) ? null : Bukkit.getWorld(worldName);
            if (world == null) continue;

            double x = getConfig().getDouble(path + ".x");
            double y = getConfig().getDouble(path + ".y");
            double z = getConfig().getDouble(path + ".z");
            String region = getConfig().getString(path + ".region", "default");
            Vector direction = getConfig().getVector(path + ".direction");

            Location loc = new Location(world, x, y, z);
            cameras.put(cameraName, new CameraData(loc, region, direction));
        }
    }

    /* =========================
       MESSAGES.YML
       ========================= */

    private void loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) {
            // requires messages.yml inside src/main/resources
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    private String msg(String key) {
        String lang = getConfig().getString("language", "de");
        String path = "messages." + key + "." + lang;
        String raw = (messages == null) ? null : messages.getString(path);

        if (raw == null) raw = "&cMissing message: " + key;
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private String msg(String key, Map<String, String> placeholders) {
        String text = msg(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            text = text.replace("%" + e.getKey() + "%", e.getValue());
        }
        return text;
    }

    private Map<String, String> singleton(String k, String v) {
        Map<String, String> m = new HashMap<String, String>();
        m.put(k, v);
        return m;
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }
}
