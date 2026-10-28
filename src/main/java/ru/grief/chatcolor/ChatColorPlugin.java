package ru.grief.chatcolor;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatColorPlugin extends JavaPlugin implements TabExecutor, Listener {

    record ColorEntry(String id, String name, int rgb) {}

    record GradEntry(String id, String name, int[] stops) {}

    private final Map<UUID, PlayerStyle> styles = new ConcurrentHashMap<>();
    private volatile List<ColorEntry> colors = List.of();
    private volatile List<GradEntry> gradients = List.of();
    private File dataFile;
    private ColorMenu menu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCatalog();
        dataFile = new File(getDataFolder(), "data.yml");
        loadData();

        menu = new ColorMenu(this);
        getServer().getPluginManager().registerEvents(menu, this);

        EventPriority prio = EventPriority.LOWEST;
        try {
            prio = EventPriority.valueOf(getConfig().getString("chat-priority", "LOWEST").toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }
        getServer().getPluginManager().registerEvent(AsyncPlayerChatEvent.class, this, prio,
                (listener, event) -> {
                    if (event instanceof AsyncPlayerChatEvent ev) onChat(ev);
                }, this, true);

        getCommand("chatcolor").setExecutor(this);
        getCommand("chatcolor").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // ------------------------------------------------------------ catalog

    List<ColorEntry> colors() {
        return colors;
    }

    List<GradEntry> gradients() {
        return gradients;
    }

    private void loadCatalog() {
        List<ColorEntry> cs = new ArrayList<>();
        for (Map<?, ?> m : getConfig().getMapList("colors")) {
            Integer rgb = parseHex(String.valueOf(m.get("color")));
            if (rgb == null || m.get("id") == null) {
                getLogger().warning("Неверный цвет в config.yml: " + m);
                continue;
            }
            cs.add(new ColorEntry(String.valueOf(m.get("id")), String.valueOf(m.get("name")), rgb));
            if (cs.size() >= ColorMenu.PER_PAGE) break;
        }
        List<GradEntry> gs = new ArrayList<>();
        for (Map<?, ?> m : getConfig().getMapList("gradients")) {
            Object raw = m.get("colors");
            List<Integer> stops = new ArrayList<>();
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    Integer rgb = parseHex(String.valueOf(o));
                    if (rgb != null) stops.add(rgb);
                }
            }
            if (stops.size() < 2 || m.get("id") == null) {
                getLogger().warning("Неверный градиент в config.yml: " + m);
                continue;
            }
            gs.add(new GradEntry(String.valueOf(m.get("id")), String.valueOf(m.get("name")),
                    stops.stream().mapToInt(Integer::intValue).toArray()));
            if (gs.size() >= ColorMenu.PER_PAGE) break;
        }
        colors = cs;
        gradients = gs;
    }

    static Integer parseHex(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (!s.matches("[0-9a-fA-F]{6}")) return null;
        return Integer.parseInt(s, 16);
    }

    // ------------------------------------------------------------ data

    PlayerStyle style(UUID id) {
        return styles.computeIfAbsent(id, k -> new PlayerStyle());
    }

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                PlayerStyle s = new PlayerStyle();
                s.paint = sec.getString(key + ".paint");
                s.font = TextFx.Font.byId(sec.getString(key + ".font", "normal"));
                s.bold = sec.getBoolean(key + ".bold");
                s.italic = sec.getBoolean(key + ".italic");
                s.underline = sec.getBoolean(key + ".underline");
                s.strike = sec.getBoolean(key + ".strike");
                styles.put(id, s);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    synchronized void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerStyle> e : styles.entrySet()) {
            PlayerStyle s = e.getValue();
            if (s.isDefault()) continue;
            String k = "players." + e.getKey();
            y.set(k + ".paint", s.paint);
            y.set(k + ".font", s.font.id);
            y.set(k + ".bold", s.bold);
            y.set(k + ".italic", s.italic);
            y.set(k + ".underline", s.underline);
            y.set(k + ".strike", s.strike);
        }
        try {
            y.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Не удалось сохранить data.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ rendering

    String msg(String key) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + key, key));
    }

    String styleCodes(Player p, PlayerStyle s) {
        if (!p.hasPermission("chatcolor.style")) return "";
        StringBuilder sb = new StringBuilder();
        if (s.bold) sb.append("§l");
        if (s.italic) sb.append("§o");
        if (s.underline) sb.append("§n");
        if (s.strike) sb.append("§m");
        return sb.toString();
    }

    /** Применяет выбранный стиль к тексту с учётом текущих прав игрока. */
    String apply(Player p, PlayerStyle s, String text) {
        String out = text;
        if (s.font != TextFx.Font.NORMAL && p.hasPermission("chatcolor.font")) {
            out = s.font.apply(out);
        }
        String st = styleCodes(p, s);
        String paint = s.paint;
        if (paint != null) {
            if (paint.startsWith("c:") && p.hasPermission("chatcolor.color")) {
                String id = paint.substring(2);
                for (ColorEntry c : colors) {
                    if (c.id().equals(id)) return TextFx.solid(out, c.rgb(), st);
                }
            } else if (paint.startsWith("g:") && p.hasPermission("chatcolor.gradient")) {
                String id = paint.substring(2);
                for (GradEntry g : gradients) {
                    if (g.id().equals(id)) return TextFx.gradient(out, g.stops(), st);
                }
            }
        }
        return st.isEmpty() ? out : st + out;
    }

    private void onChat(AsyncPlayerChatEvent e) {
        PlayerStyle s = styles.get(e.getPlayer().getUniqueId());
        if (s == null || s.isDefault()) return;
        String plain = ChatColor.stripColor(e.getMessage());
        if (plain == null || plain.isEmpty()) return;
        e.setMessage(apply(e.getPlayer(), s, plain));
    }

    // ------------------------------------------------------------ command

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("chatcolor.admin")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }
            reloadConfig();
            loadCatalog();
            sender.sendMessage(msg("reloaded"));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(msg("only-player"));
            return true;
        }
        if (!p.hasPermission("chatcolor.use")) {
            p.sendMessage(msg("no-permission"));
            return true;
        }
        menu.open(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("chatcolor.admin") && "reload".startsWith(args[0].toLowerCase())) {
            out.add("reload");
        }
        return out;
    }
}
