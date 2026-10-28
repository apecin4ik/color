package ru.grief.chatcolor;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;

final class ColorMenu implements Listener {

    static final int PER_PAGE = 21;
    private static final int SIZE = 45;
    private static final int[] CONTENT = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private static final int[] STYLE_SLOTS = {11, 15, 29, 33};
    private static final int SLOT_RESET = 38, SLOT_PREVIEW = 40, SLOT_CLOSE = 42;

    enum Tab {
        COLORS(1, "chatcolor.color", Material.RED_DYE, "Цвета", "Обычные цвета чата"),
        GRADIENTS(3, "chatcolor.gradient", Material.FIREWORK_STAR, "Градиенты", "Плавные переходы цветов"),
        FONTS(5, "chatcolor.font", Material.WRITABLE_BOOK, "Шрифты", "Необычное начертание букв"),
        STYLES(7, "chatcolor.style", Material.FEATHER, "Стили", "Жирный, курсив и другое");

        final int slot;
        final String perm;
        final Material icon;
        final String title;
        final String desc;

        Tab(int slot, String perm, Material icon, String title, String desc) {
            this.slot = slot;
            this.perm = perm;
            this.icon = icon;
            this.title = title;
            this.desc = desc;
        }
    }

    static final class Holder implements InventoryHolder {
        Tab tab;
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ChatColorPlugin plugin;
    private final String title;

    ColorMenu(ChatColorPlugin plugin) {
        this.plugin = plugin;
        this.title = TextFx.gradient("Цвет чата", new int[]{0xB24592, 0xFF6FD8, 0x6C5CE7}, "§l");
    }

    // ------------------------------------------------------------ open / render

    void open(Player p) {
        Tab first = null;
        for (Tab t : Tab.values()) {
            if (p.hasPermission(t.perm)) {
                first = t;
                break;
            }
        }
        if (first == null) {
            p.sendMessage(plugin.msg("no-permission"));
            return;
        }
        Holder h = new Holder();
        h.tab = first;
        h.inv = Bukkit.createInventory(h, SIZE, title);
        render(p, h);
        p.openInventory(h.inv);
        sound(p, "block.ender_chest.open", 0.6f, 1.4f);
    }

    private boolean isContent(int slot) {
        for (int s : CONTENT) if (s == slot) return true;
        return false;
    }

    private void render(Player p, Holder h) {
        Inventory inv = h.inv;
        ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack gray = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, isContent(i) ? gray : black);

        inv.setItem(4, item(Material.NETHER_STAR, TextFx.gradient("Цвет чата", new int[]{0xB24592, 0xFF6FD8, 0x6C5CE7}, "§l"),
                "§7Выберите, как будут выглядеть", "§7ваши сообщения в чате."));

        for (Tab t : Tab.values()) inv.setItem(t.slot, tabItem(p, t, t == h.tab));

        PlayerStyle s = plugin.style(p.getUniqueId());
        switch (h.tab) {
            case COLORS -> renderColors(inv, p, s);
            case GRADIENTS -> renderGradients(inv, p, s);
            case FONTS -> renderFonts(inv, s);
            case STYLES -> renderStyles(inv, s);
        }

        inv.setItem(SLOT_RESET, item(Material.REDSTONE_BLOCK, "§c§lСбросить всё",
                "§7Вернуть обычный вид сообщений."));
        inv.setItem(SLOT_PREVIEW, preview(p, s));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, "§c§lЗакрыть"));
    }

    private ItemStack tabItem(Player p, Tab t, boolean selected) {
        if (!p.hasPermission(t.perm)) {
            return item(t.icon, "§c" + t.title, "§7" + t.desc, "", "§cНет доступа");
        }
        ItemStack it = item(t.icon, (selected ? "§a§l▶ " : "§f§l") + t.title,
                "§7" + t.desc, "", selected ? "§aСейчас открыто" : "§eНажмите, чтобы открыть");
        return selected ? glow(it) : it;
    }

    private void renderColors(Inventory inv, Player p, PlayerStyle s) {
        List<ChatColorPlugin.ColorEntry> list = plugin.colors();
        for (int i = 0; i < Math.min(list.size(), CONTENT.length); i++) {
            ChatColorPlugin.ColorEntry c = list.get(i);
            boolean sel = ("c:" + c.id()).equals(s.paint);
            ItemStack it = dyed(c.rgb(),
                    TextFx.solid(c.name(), c.rgb(), "§l"),
                    "§7Пример:",
                    TextFx.solid("Привет, это мой чат!", c.rgb(), ""),
                    "",
                    sel ? "§a✔ Выбрано §8(нажмите, чтобы снять)" : "§eНажмите, чтобы выбрать");
            inv.setItem(CONTENT[i], sel ? glow(it) : it);
        }
    }

    private void renderGradients(Inventory inv, Player p, PlayerStyle s) {
        List<ChatColorPlugin.GradEntry> list = plugin.gradients();
        for (int i = 0; i < Math.min(list.size(), CONTENT.length); i++) {
            ChatColorPlugin.GradEntry g = list.get(i);
            boolean sel = ("g:" + g.id()).equals(s.paint);
            ItemStack it = dyed(TextFx.lerp(g.stops(), 0.5),
                    TextFx.gradient(g.name(), g.stops(), "§l"),
                    "§7Пример:",
                    TextFx.gradient("Привет, это мой чат!", g.stops(), ""),
                    "",
                    sel ? "§a✔ Выбрано §8(нажмите, чтобы снять)" : "§eНажмите, чтобы выбрать");
            inv.setItem(CONTENT[i], sel ? glow(it) : it);
        }
    }

    private void renderFonts(Inventory inv, PlayerStyle s) {
        TextFx.Font[] fonts = TextFx.Font.values();
        for (int i = 0; i < fonts.length && i < CONTENT.length; i++) {
            TextFx.Font f = fonts[i];
            boolean sel = s.font == f;
            ItemStack it = item(Material.PAPER, (sel ? "§a§l" : "§f§l") + f.title,
                    "§7Пример:",
                    "§b" + f.apply("Hello World 123"),
                    "§b" + f.apply("Привет, мир"),
                    "",
                    sel ? "§a✔ Выбрано" : "§eНажмите, чтобы выбрать");
            inv.setItem(CONTENT[i], sel ? glow(it) : it);
        }
        inv.setItem(CONTENT[CONTENT.length - 1], item(Material.OAK_SIGN, "§7Подсказка",
                "§8Латинские шрифты меняют только", "§8английские буквы. Для русского",
                "§8подходят: Разрядка, Волна, ЗАГЛАВНЫЕ."));
    }

    private static final Material[] STYLE_ICONS = {Material.ANVIL, Material.FEATHER, Material.LADDER, Material.SHEARS};
    private static final String[] STYLE_NAMES = {"Жирный", "Курсив", "Подчёркнутый", "Зачёркнутый"};
    private static final String[] STYLE_CODES = {"§l", "§o", "§n", "§m"};

    private void renderStyles(Inventory inv, PlayerStyle s) {
        for (int i = 0; i < STYLE_SLOTS.length; i++) {
            boolean on = s.get(i);
            ItemStack it = item(STYLE_ICONS[i], "§f" + STYLE_CODES[i] + STYLE_NAMES[i],
                    "§7Пример: §f" + STYLE_CODES[i] + "Привет, мир!",
                    "",
                    on ? "§a✔ Включено" : "§cВыключено",
                    "§eНажмите, чтобы переключить");
            inv.setItem(STYLE_SLOTS[i], on ? glow(it) : it);
        }
    }

    private ItemStack preview(Player p, PlayerStyle s) {
        String sample = plugin.getConfig().getString("preview-text", "Hello! Так выглядит ваш чат");
        return item(Material.NAME_TAG, "§f§lПредпросмотр",
                "§7Так будут выглядеть ваши сообщения:",
                "",
                plugin.apply(p, s, sample));
    }

    // ------------------------------------------------------------ events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != top) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        handle(p, h, e.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private int indexOf(int slot) {
        for (int i = 0; i < CONTENT.length; i++) if (CONTENT[i] == slot) return i;
        return -1;
    }

    private void handle(Player p, Holder h, int slot) {
        for (Tab t : Tab.values()) {
            if (t.slot != slot) continue;
            if (!p.hasPermission(t.perm)) {
                sound(p, "entity.villager.no", 0.8f, 1f);
                return;
            }
            h.tab = t;
            render(p, h);
            sound(p, "ui.button.click", 0.6f, 1.2f);
            return;
        }

        PlayerStyle s = plugin.style(p.getUniqueId());

        if (slot == SLOT_CLOSE) {
            p.closeInventory();
            return;
        }
        if (slot == SLOT_RESET) {
            s.reset();
            plugin.saveData();
            render(p, h);
            p.sendMessage(plugin.msg("reset"));
            sound(p, "entity.item.break", 0.6f, 1.2f);
            return;
        }

        if (!p.hasPermission(h.tab.perm)) {
            sound(p, "entity.villager.no", 0.8f, 1f);
            return;
        }

        boolean changed = false;
        switch (h.tab) {
            case COLORS -> {
                int i = indexOf(slot);
                List<ChatColorPlugin.ColorEntry> list = plugin.colors();
                if (i >= 0 && i < list.size()) {
                    String key = "c:" + list.get(i).id();
                    s.paint = key.equals(s.paint) ? null : key;
                    changed = true;
                }
            }
            case GRADIENTS -> {
                int i = indexOf(slot);
                List<ChatColorPlugin.GradEntry> list = plugin.gradients();
                if (i >= 0 && i < list.size()) {
                    String key = "g:" + list.get(i).id();
                    s.paint = key.equals(s.paint) ? null : key;
                    changed = true;
                }
            }
            case FONTS -> {
                int i = indexOf(slot);
                TextFx.Font[] fonts = TextFx.Font.values();
                if (i >= 0 && i < fonts.length) {
                    s.font = fonts[i];
                    changed = true;
                }
            }
            case STYLES -> {
                for (int i = 0; i < STYLE_SLOTS.length; i++) {
                    if (STYLE_SLOTS[i] == slot) {
                        s.toggle(i);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            plugin.saveData();
            render(p, h);
            sound(p, "entity.experience_orb.pickup", 0.5f, 1.6f);
        }
    }

    // ------------------------------------------------------------ item helpers

    private void sound(Player p, String key, float vol, float pitch) {
        p.playSound(p.getLocation(), key, vol, pitch);
    }

    private ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(" ");
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§r" + name);
        List<String> l = new ArrayList<>();
        for (String line : lore) l.add("§r" + line);
        meta.setLore(l);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack dyed(int rgb, String name, String... lore) {
        ItemStack it = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(Color.fromRGB(rgb));
        meta.setDisplayName("§r" + name);
        List<String> l = new ArrayList<>();
        for (String line : lore) l.add("§r" + line);
        meta.setLore(l);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack glow(ItemStack it) {
        Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (ench == null) return it;
        ItemMeta meta = it.getItemMeta();
        meta.addEnchant(ench, 1, true);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }
}
