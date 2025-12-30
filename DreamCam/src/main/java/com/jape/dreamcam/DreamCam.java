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

    // ===== Managers =====

    private MessageManager messageManager;
    private ConfigManager configManager;

    // ===== Data Model =====

    public static final class CameraData {
        private final Location location;
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
     * InventoryHolder to store the region name inside the menu.
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
        // Initialize managers
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);

        // Register command
        PluginCommand command = getCommand("camera");
        if (command == null) {
            getLogger().severe("Command 'camera' is missing in plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        command.setExecutor(this);
        command.setTabCompleter(this);

        // Register events
        Bukkit.getPluginManager().registerEvents(this, this);

        // Load cameras from config
        loadCamerasFromConfig();

        getLogger().info("DreamCam has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Exit camera mode for all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (inCameraMode.contains(p.getUniqueId())) {
                exitCameraMode(p);
            }
        }
        saveCamerasToConfig();
        getLogger().info("DreamCam has been disabled!");
    }

    // ===== Command handling =====

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.getPlayerOnly());
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(messageManager.getUsageMain());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("create")) {
            return handleCreateCommand(player, args);
        }

        if (sub.equals("delete")) {
            return handleDeleteCommand(player, args);
        }

        if (sub.equals("menu")) {
            return handleMenuCommand(player, args);
        }

        if (sub.equals("reload")) {
            return handleReloadCommand(player);
        }

        if (sub.equals("save")) {
            return handleSaveCommand(player);
        }

        if (sub.equals("load")) {
            return handleLoadCommand(player);
        }

        player.sendMessage(messageManager.getUnknownCommand());
        return true;
    }

    // ===== Command handlers =====

    private boolean handleCreateCommand(Player player, String[] args) {
        if (!player.hasPermission("dreamcam.admin.create")) {
            player.sendMessage(messageManager.getNoPermission());
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(messageManager.getUsageCreate());
            return true;
        }

        String cameraName = args[1];
        String regionName = args[2];

        Location baseLoc = player.getLocation().clone();
        Vector dir = baseLoc.getDirection();

        Location stored = new Location(baseLoc.getWorld(), baseLoc.getX(), baseLoc.getY(), baseLoc.getZ());
        cameras.put(cameraName, new CameraData(stored, regionName, dir));

        player.sendMessage(messageManager.getCameraCreated(cameraName, regionName));
        return true;
    }

    private boolean handleDeleteCommand(Player player, String[] args) {
        if (!player.hasPermission("dreamcam.admin.delete")) {
            player.sendMessage(messageManager.getNoPermission());
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(messageManager.getUsageDelete());
            return true;
        }

        String name = args[1];

        if (cameras.containsKey(name)) {
            cameras.remove(name);
            player.sendMessage(messageManager.getCameraDeleted(name));
            return true;
        }

        // Treat as region delete
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, CameraData> e : cameras.entrySet()) {
            if (e.getValue().getRegion().equalsIgnoreCase(name)) {
                toRemove.add(e.getKey());
            }
        }

        if (toRemove.isEmpty()) {
            player.sendMessage(messageManager.getCameraNotFound(name));
        } else {
            for (String cam : toRemove) {
                cameras.remove(cam);
            }
            player.sendMessage(messageManager.getRegionDeleted(name));
        }
        return true;
    }

    private boolean handleMenuCommand(Player player, String[] args) {
        if (!player.hasPermission("dreamcam.use")) {
            player.sendMessage(messageManager.getNoPermission());
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(messageManager.getUsageMenu());
            return true;
        }

        String regionName = args[1];

        List<String> camsInRegion = getCamerasInRegion(regionName);

        if (camsInRegion.isEmpty()) {
            player.sendMessage(messageManager.getNoCamerasInRegion(regionName));
            return true;
        }

        openCameraMenu(player, regionName, camsInRegion);
        return true;
    }

    private boolean handleReloadCommand(Player player) {
        if (!player.hasPermission("dreamcam.admin.reload")) {
            player.sendMessage(messageManager.getNoPermission());
            return true;
        }

        configManager.reloadConfig();
        messageManager.reloadMessages();
        loadCamerasFromConfig();

        player.sendMessage(messageManager.getConfigReloaded());
        return true;
    }

    private boolean handleSaveCommand(Player player) {
        if (!player.hasPermission("dreamcam.admin.save")) {
            player.sendMessage(messageManager.getNoPermission());
            return true;
        }

        saveCamerasToConfig();
        player.sendMessage(messageManager.getConfigSaved());
        return true;
    }

    private boolean handleLoadCommand(Player player) {
        if (!player.hasPermission("dreamcam.admin.reload")) {
            player.sendMessage(messageManager.getNoPermission());
            return true;
        }

        loadCamerasFromConfig();
        player.sendMessage(messageManager.getConfigLoaded());
        return true;
    }

    // ===== Helper methods =====

    private List<String> getCamerasInRegion(String regionName) {
        return cameras.entrySet().stream()
                .filter(e -> e.getValue().getRegion().equalsIgnoreCase(regionName))
                .map(Map.Entry::getKey)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private Set<String> getAllRegions() {
        Set<String> regions = new HashSet<>();
        for (CameraData data : cameras.values()) {
            regions.add(data.getRegion());
        }
        return regions;
    }

    // ===== GUI =====

    private void openCameraMenu(Player player, String regionName, List<String> camsInRegion) {
        int size = ((camsInRegion.size() - 1) / 9 + 1) * 9;
        String title = messageManager.getMenuTitle(regionName);
        Inventory inv = Bukkit.createInventory(new CameraMenuHolder(regionName), size, title);

        Material material = configManager.getMenuMaterial();

        for (String camName : camsInRegion) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(messageManager.getMenuCameraItemName(camName));
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
        if (item == null || item.getType() != configManager.getMenuMaterial()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String cameraName = ChatColor.stripColor(meta.getDisplayName());
        CameraData data = cameras.get(cameraName);
        if (data == null) {
            player.sendMessage(messageManager.getCameraDoesNotExist());
            return;
        }

        String region = ((CameraMenuHolder) holder).getRegion();
        List<String> camsInRegion = getCamerasInRegion(region);

        if (camsInRegion.isEmpty()) {
            player.sendMessage(messageManager.getNoCamerasAnymore());
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

            // Apply night vision if enabled
            if (configManager.isNightVisionEnabled()) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.NIGHT_VISION,
                        configManager.getNightVisionDuration(),
                        configManager.getNightVisionAmplifier(),
                        configManager.isNightVisionAmbient(),
                        configManager.hasNightVisionParticles()
                ));
            }

            player.setGameMode(configManager.getCameraGameMode());
        }

        teleportToCamera(player, cameraName);
        sendActionBar(player, messageManager.getCameraModeEnterActionBar(cameraName));
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

        // Freeze position
        if (configManager.isFreezePositionEnabled()) {
            if (e.getFrom().getX() != e.getTo().getX()
                    || e.getFrom().getY() != e.getTo().getY()
                    || e.getFrom().getZ() != e.getTo().getZ()) {
                e.setTo(e.getFrom());
            }
        }

        // Lock view direction (yaw and pitch)
        if (configManager.isLockViewDirectionEnabled()) {
            Location from = e.getFrom();
            Location to = e.getTo();
            if (to.getYaw() != from.getYaw() || to.getPitch() != from.getPitch()) {
                to.setYaw(from.getYaw());
                to.setPitch(from.getPitch());
                e.setTo(to);
            }
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

        sendActionBar(player, messageManager.getCameraModeSwitchActionBar(cam));
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

        sendActionBar(player, messageManager.getCameraModeSwitchActionBar(cam));
    }

    // ===== TabComplete =====

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("camera")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("create", "delete", "menu", "reload", "save", "load");
            String typed = args[0].toLowerCase(Locale.ROOT);
            for (String subcommand : subcommands) {
                if (subcommand.startsWith(typed)) {
                    completions.add(subcommand);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            String typed = args[1].toLowerCase(Locale.ROOT);

            if (subcommand.equals("delete")) {
                // Add camera names
                for (String cameraName : cameras.keySet()) {
                    if (cameraName.toLowerCase(Locale.ROOT).startsWith(typed)) {
                        completions.add(cameraName);
                    }
                }
                // Add region names
                for (String region : getAllRegions()) {
                    if (region.toLowerCase(Locale.ROOT).startsWith(typed)) {
                        completions.add(region);
                    }
                }
            } else if (subcommand.equals("menu")) {
                // Add region names
                for (String region : getAllRegions()) {
                    if (region.toLowerCase(Locale.ROOT).startsWith(typed)) {
                        completions.add(region);
                    }
                }
            }
        }

        return completions;
    }

    // ===== Config =====

    private void saveCamerasToConfig() {
        FileConfiguration config = configManager.getConfig();
        config.set("cameras", null);

        for (Map.Entry<String, CameraData> entry : cameras.entrySet()) {
            String name = entry.getKey();
            CameraData data = entry.getValue();

            Location loc = data.getLocation();
            if (loc.getWorld() == null) {
                getLogger().warning("Cannot save camera '" + name + "': world is null");
                continue;
            }

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
        configManager.reloadConfig();
        FileConfiguration config = configManager.getConfig();

        cameras.clear();

        ConfigurationSection sec = config.getConfigurationSection("cameras");
        if (sec == null) {
            getLogger().info("No cameras found in config");
            return;
        }

        for (String cameraName : sec.getKeys(false)) {
            String path = "cameras." + cameraName;

            String worldName = config.getString(path + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("World not found for camera '" + cameraName + "': " + worldName);
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

        getLogger().info("Loaded " + cameras.size() + " camera(s) from config");
    }

    // ===== Utils =====

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }
}
