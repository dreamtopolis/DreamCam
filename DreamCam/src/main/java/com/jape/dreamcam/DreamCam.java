package com.jape.dreamcam;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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

import java.util.*;
import java.util.stream.Collectors;

public class DreamCam extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    // ===== Data Model =====

    public static final class CameraData {
        private final Location location;   // Stored base location (never mutated)
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
     * Java-8 compatible InventoryHolder to store the region name inside the menu.
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

    private static final String MENU_PREFIX = "Kameras in ";

    // All cameras by name
    private final Map<String, CameraData> cameras = new LinkedHashMap<>();

    // Player state
    private final Map<UUID, Location> previousLocations = new HashMap<>();
    private final Map<UUID, GameMode> previousGameModes = new HashMap<>();
    private final Set<UUID> inCameraMode = new HashSet<>();
    private final Map<UUID, List<String>> playerCameras = new HashMap<>();
    private final Map<UUID, Integer> playerCameraIndex = new HashMap<>();

    // ===== Plugin lifecycle =====

    @Override
    public void onEnable() {
        // IMPORTANT: make sure plugin.yml has command "camera"
        PluginCommand command = getCommand("camera");
        if (command == null) {
            getLogger().severe("Command 'camera' fehlt in plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        command.setExecutor(this);
        command.setTabCompleter(this);

        Bukkit.getPluginManager().registerEvents(this, this);

        saveDefaultConfig();
        loadCamerasFromConfig();
    }

    @Override
    public void onDisable() {
        // Cleanly exit camera mode for online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (inCameraMode.contains(p.getUniqueId())) {
                exitCameraMode(p);
            }
        }
        saveCamerasToConfig();
    }

    // ===== Command handling =====

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern verwendet werden.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("Verwendung: /camera create|delete|menu|reload|save|load (Kameraname) (Regionsname)");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("create")) {
            if (!player.hasPermission("dreamcam.admin")) {
                player.sendMessage(ChatColor.RED + "Keine Berechtigung.");
                return true;
            }

            if (args.length < 3) {
                player.sendMessage("Verwendung: /camera create <Kameraname> <Regionsname>");
                return true;
            }

            String cameraName = args[1];
            String regionName = args[2];

            Location baseLoc = player.getLocation().clone();
            Vector dir = baseLoc.getDirection();

            // store a fresh Location object (world + xyz only)
            Location stored = new Location(baseLoc.getWorld(), baseLoc.getX(), baseLoc.getY(), baseLoc.getZ());
            cameras.put(cameraName, new CameraData(stored, regionName, dir));

            player.sendMessage("Kamera '" + cameraName + "' in der Region '" + regionName + "' wurde erstellt.");
            return true;
        }

        if (sub.equals("delete")) {
            if (!player.hasPermission("dreamcam.admin")) {
                player.sendMessage(ChatColor.RED + "Keine Berechtigung.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("Verwendung: /camera delete <Kameraname|Regionsname>");
                return true;
            }

            String name = args[1];

            if (cameras.containsKey(name)) {
                cameras.remove(name);
                player.sendMessage("Kamera '" + name + "' wurde gelöscht.");
                return true;
            }

            // treat as region delete
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, CameraData> e : cameras.entrySet()) {
                if (e.getValue().getRegion().equalsIgnoreCase(name)) {
                    toRemove.add(e.getKey());
                }
            }

            if (toRemove.isEmpty()) {
                player.sendMessage("Es wurde keine Kamera oder Region mit dem Namen '" + name + "' gefunden.");
            } else {
                for (String cam : toRemove) cameras.remove(cam);
                player.sendMessage("Alle Kameras in der Region '" + name + "' wurden gelöscht.");
            }
            return true;
        }

        if (sub.equals("menu")) {
            if (args.length < 2) {
                player.sendMessage("Verwendung: /camera menu <Regionsname>");
                return true;
            }

            String regionName = args[1];

            List<String> camsInRegion = cameras.entrySet().stream()
                    .filter(e -> e.getValue().getRegion().equalsIgnoreCase(regionName))
                    .map(Map.Entry::getKey)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());

            if (camsInRegion.isEmpty()) {
                player.sendMessage("In der Region '" + regionName + "' gibt es keine Kameras.");
                return true;
            }

            openCameraMenu(player, regionName, camsInRegion);
            return true;
        }

        if (sub.equals("reload")) {
            if (!player.hasPermission("dreamcam.admin")) {
                player.sendMessage(ChatColor.RED + "Keine Berechtigung.");
                return true;
            }
            loadCamerasFromConfig();
            player.sendMessage("Kameras wurden neu geladen.");
            return true;
        }

        if (sub.equals("save")) {
            if (!player.hasPermission("dreamcam.admin")) {
                player.sendMessage(ChatColor.RED + "Keine Berechtigung.");
                return true;
            }
            saveCamerasToConfig();
            player.sendMessage("Kameras wurden gespeichert.");
            return true;
        }

        if (sub.equals("load")) {
            if (!player.hasPermission("dreamcam.admin")) {
                player.sendMessage(ChatColor.RED + "Keine Berechtigung.");
                return true;
            }
            loadCamerasFromConfig();
            player.sendMessage("Kameras wurden geladen.");
            return true;
        }

        player.sendMessage("Unbekannter Befehl. Verwendung: /camera create|delete|menu|reload|save|load ...");
        return true;
    }

    // ===== GUI =====

    private void openCameraMenu(Player player, String regionName, List<String> camsInRegion) {
        int size = ((camsInRegion.size() - 1) / 9 + 1) * 9;
        Inventory inv = Bukkit.createInventory(new CameraMenuHolder(regionName), size, MENU_PREFIX + regionName);

        for (String camName : camsInRegion) {
            ItemStack item = new ItemStack(Material.BLUE_CONCRETE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + camName);
                item.setItemMeta(meta);
            }
            inv.addItem(item);
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
            player.sendMessage(ChatColor.RED + "Diese Kamera existiert nicht mehr.");
            return;
        }

        String region = ((CameraMenuHolder) holder).getRegion();

        List<String> camsInRegion = cameras.entrySet().stream()
                .filter(e -> e.getValue().getRegion().equalsIgnoreCase(region))
                .map(Map.Entry::getKey)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        if (camsInRegion.isEmpty()) {
            player.sendMessage(ChatColor.RED + "In dieser Region gibt es keine Kameras mehr.");
            player.closeInventory();
            return;
        }

        playerCameras.put(player.getUniqueId(), camsInRegion);
        int idx = camsInRegion.indexOf(cameraName);
        playerCameraIndex.put(player.getUniqueId(), idx < 0 ? 0 : idx);

        enterCameraMode(player, cameraName);
        player.closeInventory();
    }

    // ===== Camera Mode =====

    private void enterCameraMode(Player player, String cameraName) {
        UUID id = player.getUniqueId();

        if (!inCameraMode.contains(id)) {
            previousLocations.put(id, player.getLocation().clone());
            previousGameModes.put(id, player.getGameMode());
            inCameraMode.add(id);

            // Night vision while in camera mode (1h, removed on exit)
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 60 * 60, 0, true, false));

            player.setGameMode(GameMode.SPECTATOR);
        }

        teleportToCamera(player, cameraName);
        sendActionBar(player,
                "Kamera " + ChatColor.AQUA + cameraName + ChatColor.WHITE +
                        " | Rechtsklick=Nächste, Linksklick=Vorherige, Shift=Zurück");
    }

    private void teleportToCamera(Player player, String cameraName) {
        CameraData data = cameras.get(cameraName);
        if (data == null) return;

        Location tp = data.getLocation().clone();
        Vector dir = data.getDirection();
        if (dir != null) tp.setDirection(dir);

        player.teleport(tp);
    }

    private void exitCameraMode(Player player) {
        UUID id = player.getUniqueId();

        Location prev = previousLocations.get(id);
        GameMode prevGm = previousGameModes.containsKey(id) ? previousGameModes.get(id) : GameMode.SURVIVAL;

        if (prev != null) player.teleport(prev);
        player.setGameMode(prevGm);

        player.removePotionEffect(PotionEffectType.NIGHT_VISION);

        inCameraMode.remove(id);
        previousLocations.remove(id);
        previousGameModes.remove(id);
        playerCameras.remove(id);
        playerCameraIndex.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!inCameraMode.contains(p.getUniqueId())) return;

        // Prevent position changes but allow looking around
        if (e.getFrom().getX() != e.getTo().getX()
                || e.getFrom().getY() != e.getTo().getY()
                || e.getFrom().getZ() != e.getTo().getZ()) {
            e.setTo(e.getFrom());
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (inCameraMode.contains(player.getUniqueId()) && event.isSneaking()) {
            exitCameraMode(player);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!inCameraMode.contains(player.getUniqueId())) return;

        Action a = event.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            switchToNextCamera(player);
            event.setCancelled(true);
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            switchToPreviousCamera(player);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (inCameraMode.contains(p.getUniqueId())) exitCameraMode(p);
    }

    @EventHandler
    public void onKick(PlayerKickEvent e) {
        Player p = e.getPlayer();
        if (inCameraMode.contains(p.getUniqueId())) exitCameraMode(p);
    }

    private void switchToNextCamera(Player player) {
        UUID id = player.getUniqueId();
        List<String> list = playerCameras.get(id);
        Integer idxObj = playerCameraIndex.get(id);

        if (list == null || list.isEmpty() || idxObj == null) return;

        int idx = (idxObj + 1) % list.size();
        String cam = list.get(idx);

        teleportToCamera(player, cam);
        playerCameraIndex.put(id, idx);

        sendActionBar(player, "Kamera: " + ChatColor.AQUA + cam + ChatColor.WHITE + " | Shift=Zurück");
    }

    private void switchToPreviousCamera(Player player) {
        UUID id = player.getUniqueId();
        List<String> list = playerCameras.get(id);
        Integer idxObj = playerCameraIndex.get(id);

        if (list == null || list.isEmpty() || idxObj == null) return;

        int idx = idxObj - 1;
        if (idx < 0) idx = list.size() - 1;

        String cam = list.get(idx);

        teleportToCamera(player, cam);
        playerCameraIndex.put(id, idx);

        sendActionBar(player, "Kamera: " + ChatColor.AQUA + cam + ChatColor.WHITE + " | Shift=Zurück");
    }

    // ===== TabComplete =====

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("camera")) return Collections.emptyList();

        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = Arrays.asList("create", "delete", "menu", "reload", "save", "load");
            String typed = args[0].toLowerCase(Locale.ROOT);
            for (String s : subs) {
                if (s.startsWith(typed)) out.add(s);
            }
            return out;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String typed = args[1].toLowerCase(Locale.ROOT);

            if (sub.equals("delete")) {
                for (String cam : cameras.keySet()) {
                    if (cam.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(cam);
                }
                Set<String> regions = new HashSet<String>();
                for (CameraData d : cameras.values()) regions.add(d.getRegion());
                for (String r : regions) {
                    if (r.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(r);
                }
            } else if (sub.equals("menu")) {
                Set<String> regions = new HashSet<String>();
                for (CameraData d : cameras.values()) regions.add(d.getRegion());
                for (String r : regions) {
                    if (r.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(r);
                }
            }
        }

        return out;
    }

    // ===== Config =====

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
        FileConfiguration config = getConfig();

        cameras.clear();

        ConfigurationSection sec = config.getConfigurationSection("cameras");
        if (sec == null) return;

        for (String cameraName : sec.getKeys(false)) {
            String path = "cameras." + cameraName;

            String worldName = config.getString(path + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("World nicht gefunden für Kamera '" + cameraName + "': " + worldName);
                continue;
            }

            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            String region = config.getString(path + ".region", "default");
            Vector direction = config.getVector(path + ".direction");

            Location loc = new Location(world, x, y, z);
            cameras.put(cameraName, new CameraData(loc, region, direction));
        }
    }

    // ===== Utils =====

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }
}
