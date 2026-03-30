package com.jape.dreamcam;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all /camera subcommands and tab completion.
 * Also owns the GUI menu-open logic since it's purely command-triggered.
 */
public final class CameraCommand implements CommandExecutor, TabCompleter {

    private final DreamCam           plugin;
    private final CameraRegistry     registry;
    private final CameraSessionManager sessions;
    private final ConfigManager      config;
    private final MessageManager     messages;
    private final NamespacedKey      cameraNameKey;

    public CameraCommand(DreamCam plugin, NamespacedKey cameraNameKey) {
        this.plugin        = plugin;
        this.registry      = plugin.getRegistry();
        this.sessions      = plugin.getSessions();
        this.config        = plugin.getConfigManager();
        this.messages      = plugin.getMessageManager();
        this.cameraNameKey = cameraNameKey;
    }

    // ── Command dispatch ──────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.getPlayerOnly());
            return true;
        }
        Player p = (Player) sender;
        if (args.length == 0) { p.sendMessage(messages.getUsageMain()); return true; }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create": return cmdCreate(p, args);
            case "delete": return cmdDelete(p, args);
            case "menu":   return cmdMenu(p, args);
            case "reload": return cmdReload(p);
            case "save":   return cmdSave(p);
            case "load":   return cmdLoad(p);
            default:       p.sendMessage(messages.getUnknownCommand()); return true;
        }
    }

    // ── Subcommands ───────────────────────────────────────────────────────────

    private boolean cmdCreate(Player p, String[] args) {
        if (!p.hasPermission("dreamcam.admin.create")) { p.sendMessage(messages.getNoPermission()); return true; }
        if (args.length < 3)                           { p.sendMessage(messages.getUsageCreate());  return true; }

        String name = args[1], region = args[2];
        registry.put(name, new CameraData(p.getLocation().clone(), region));
        p.sendMessage(messages.getCameraCreated(name, region));
        return true;
    }

    private boolean cmdDelete(Player p, String[] args) {
        if (!p.hasPermission("dreamcam.admin.delete")) { p.sendMessage(messages.getNoPermission()); return true; }
        if (args.length < 2)                           { p.sendMessage(messages.getUsageDelete());  return true; }

        String target = args[1];
        if (registry.remove(target) != null) {
            p.sendMessage(messages.getCameraDeleted(target));
        } else if (registry.removeRegion(target) > 0) {
            p.sendMessage(messages.getRegionDeleted(target));
        } else {
            p.sendMessage(messages.getCameraNotFound(target));
        }
        return true;
    }

    private boolean cmdMenu(Player p, String[] args) {
        if (!p.hasPermission("dreamcam.use")) { p.sendMessage(messages.getNoPermission()); return true; }
        if (args.length < 2)                  { p.sendMessage(messages.getUsageMenu());    return true; }

        String region = args[1];
        List<String> cams = registry.getCamerasInRegion(region);
        if (cams.isEmpty()) { p.sendMessage(messages.getNoCamerasInRegion(region)); return true; }

        openMenu(p, region, cams);
        return true;
    }

    private boolean cmdReload(Player p) {
        if (!p.hasPermission("dreamcam.admin.reload")) { p.sendMessage(messages.getNoPermission()); return true; }
        config.reloadConfig();
        messages.reloadMessages();
        registry.load();
        sessions.rebuildNightVisionCache();
        p.sendMessage(messages.getConfigReloaded());
        return true;
    }

    private boolean cmdSave(Player p) {
        if (!p.hasPermission("dreamcam.admin.save")) { p.sendMessage(messages.getNoPermission()); return true; }
        registry.save();
        p.sendMessage(messages.getConfigSaved());
        return true;
    }

    private boolean cmdLoad(Player p) {
        if (!p.hasPermission("dreamcam.admin.reload")) { p.sendMessage(messages.getNoPermission()); return true; }
        registry.load();
        p.sendMessage(messages.getConfigLoaded());
        return true;
    }

    // ── GUI ───────────────────────────────────────────────────────────────────

    public void openMenu(Player player, String region, List<String> cams) {
        int size = Math.min(54, ((cams.size() - 1) / 9 + 1) * 9);
        DreamCam.CameraMenuHolder holder = new DreamCam.CameraMenuHolder(region);
        Inventory inv = Bukkit.createInventory(holder, size, messages.getMenuTitle(region));
        holder.setInventory(inv);

        Material mat = config.getMenuMaterial();
        for (String cam : cams) {
            ItemStack item = new ItemStack(mat);
            ItemMeta  meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(messages.getMenuCameraItemName(cam));
                meta.getPersistentDataContainer().set(cameraNameKey, PersistentDataType.STRING, cam);
                item.setItemMeta(meta);
            }
            inv.addItem(item);
        }
        player.openInventory(inv);
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("camera")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("dreamcam.use"))          subs.add("menu");
            if (sender.hasPermission("dreamcam.admin.create")) subs.add("create");
            if (sender.hasPermission("dreamcam.admin.delete")) subs.add("delete");
            if (sender.hasPermission("dreamcam.admin.reload")) { subs.add("reload"); subs.add("load"); }
            if (sender.hasPermission("dreamcam.admin.save"))   subs.add("save");
            String t = args[0].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(t)).sorted().collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT), t = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("menu")) {
                return registry.getAllRegions().stream()
                        .filter(r -> r.toLowerCase(Locale.ROOT).startsWith(t))
                        .collect(Collectors.toList());
            }
            if (sub.equals("delete")) {
                Set<String> opts = new TreeSet<>(registry.cameraNames());
                opts.addAll(registry.getAllRegions());
                return opts.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(t)).collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            String t = args[2].toLowerCase(Locale.ROOT);
            return registry.getAllRegions().stream()
                    .filter(r -> r.toLowerCase(Locale.ROOT).startsWith(t))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}