package com.soullink.gui;

import com.soullink.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class SettingsGUI implements Listener {

    private final GameManager gameManager;
    private static final String GUI_TITLE = "Soul Link Settings";

    // Slot layout (27-slot chest):
    //  Row 1:  decorative border
    //  Row 2:  9=healthBars  11=naturalRegen  13=lastChance  15=locateBar  17=playerGlow
    //  Row 3:  22=info
    private static final int SLOT_HEALTH_BARS   = 9;
    private static final int SLOT_NATURAL_REGEN = 11;
    private static final int SLOT_LAST_CHANCE   = 13;
    private static final int SLOT_LOCATE_BAR    = 15;
    private static final int SLOT_PLAYER_GLOW   = 17;
    private static final int SLOT_INFO          = 22;

    // RGBA colours matching GameManager palette
    private static final TextColor COLOR_TITLE  = TextColor.color(160,  80, 255);
    private static final TextColor COLOR_ON     = TextColor.color( 80, 230,  80);
    private static final TextColor COLOR_OFF    = TextColor.color(230,  80,  80);
    private static final TextColor COLOR_HINT   = TextColor.color(130, 130, 130);
    private static final TextColor COLOR_DESC   = TextColor.color( 70,  70,  70);

    public SettingsGUI(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(GUI_TITLE).color(COLOR_TITLE).decorate(TextDecoration.BOLD));

        // 1) Natural Regeneration
        inv.setItem(SLOT_NATURAL_REGEN, buildToggle(
                gameManager.isNaturalRegeneration() ? Material.GOLDEN_APPLE : Material.ROTTEN_FLESH,
                "Natural Regeneration",
                gameManager.isNaturalRegeneration(),
                "Enables natural health regeneration",
                null));

        // 2) Last Chance (Totem of Undying — increment / decrement)
        ItemStack lastChanceItem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta lcMeta = lastChanceItem.getItemMeta();
        lcMeta.displayName(Component.text("Last Chance")
                .color(TextColor.color(255, 210, 0)).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lcLore = new ArrayList<>();
        lcLore.add(Component.text("Current: " + gameManager.getLastChanceCount())
                .color(TextColor.color(200, 200, 200)).decoration(TextDecoration.ITALIC, false));
        lcLore.add(Component.text("Left Click: +1 (Max: 2)")
                .color(COLOR_HINT).decoration(TextDecoration.ITALIC, false));
        lcLore.add(Component.text("Right Click: -1 (Min: 0)")
                .color(COLOR_HINT).decoration(TextDecoration.ITALIC, false));
        lcLore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
        lcLore.add(Component.text("Saves players from death")
                .color(COLOR_DESC).decoration(TextDecoration.ITALIC, false));
        lcMeta.lore(lcLore);
        lastChanceItem.setItemMeta(lcMeta);
        inv.setItem(SLOT_LAST_CHANCE, lastChanceItem);

        // 3) Locate Bar
        inv.setItem(SLOT_LOCATE_BAR, buildToggle(
                gameManager.isLocateBarEnabled() ? Material.COMPASS : Material.RECOVERY_COMPASS,
                "Locate Bar",
                gameManager.isLocateBarEnabled(),
                "Shows compass direction to linked players",
                null));

        // 4) Show Health Bars (new)
        inv.setItem(SLOT_HEALTH_BARS, buildToggle(
                gameManager.isShowHealthBars() ? Material.HEART_OF_THE_SEA : Material.BARRIER,
                "Health Bars in TAB",
                gameManager.isShowHealthBars(),
                "Shows player health as hearts in the TAB list",
                null));

        // 5) Linked Player Glow (new)
        inv.setItem(SLOT_PLAYER_GLOW, buildToggle(
                gameManager.isLinkedPlayerGlow() ? Material.GLOWSTONE : Material.GRAY_STAINED_GLASS,
                "Linked Player Glow",
                gameManager.isLinkedPlayerGlow(),
                "Linked players always glow for each other",
                null));

        // 6) Info
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(Component.text("Soul Link Info")
                .color(TextColor.color(200, 160, 255)).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.text("Up to 4 players can link their souls")
                .color(TextColor.color(200, 200, 200)).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Damage and healing are shared")
                .color(TextColor.color(200, 200, 200)).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("If one dies, all lose!")
                .color(COLOR_OFF).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("5+ players become spectators")
                .color(COLOR_HINT).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("dev by ").color(COLOR_HINT)
                .append(Component.text("@Axeloune").color(TextColor.color(80, 200, 255)))
                .decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(SLOT_INFO, infoItem);

        player.openInventory(inv);
    }

    private ItemStack buildToggle(Material material, String name, boolean enabled,
                                  String description, String extraHint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name)
                .color(TextColor.color(200, 200, 200)).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Status: " + (enabled ? "Enabled" : "Disabled"))
                .color(enabled ? COLOR_ON : COLOR_OFF).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Click to toggle")
                .color(COLOR_HINT).decoration(TextDecoration.ITALIC, false));
        if (description != null && !description.isEmpty()) {
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(description).color(COLOR_DESC).decoration(TextDecoration.ITALIC, false));
        }
        if (extraHint != null && !extraHint.isEmpty()) {
            lore.add(Component.text(extraHint).color(COLOR_DESC).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!event.getView().title().equals(
                Component.text(GUI_TITLE).color(COLOR_TITLE).decorate(TextDecoration.BOLD))) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getSlot();

        if (slot == SLOT_NATURAL_REGEN) {
            gameManager.setNaturalRegeneration(!gameManager.isNaturalRegeneration());
            player.sendMessage(Component.text("Natural Regeneration: ")
                    .color(TextColor.color(200, 200, 200))
                    .append(Component.text(gameManager.isNaturalRegeneration() ? "Enabled" : "Disabled")
                            .color(gameManager.isNaturalRegeneration() ? COLOR_ON : COLOR_OFF)));
            player.closeInventory();
            open(player);

        } else if (slot == SLOT_LAST_CHANCE) {
            if (event.isLeftClick()) {
                int n = Math.min(2, gameManager.getLastChanceCount() + 1);
                gameManager.setLastChanceCount(n);
                player.sendMessage(Component.text("Last Chance set to: " + n)
                        .color(TextColor.color(255, 210, 0)));
            } else if (event.isRightClick()) {
                int n = Math.max(0, gameManager.getLastChanceCount() - 1);
                gameManager.setLastChanceCount(n);
                player.sendMessage(Component.text("Last Chance set to: " + n)
                        .color(TextColor.color(255, 210, 0)));
            }
            player.closeInventory();
            open(player);

        } else if (slot == SLOT_LOCATE_BAR) {
            gameManager.setLocateBarEnabled(!gameManager.isLocateBarEnabled());
            player.sendMessage(Component.text("Locate Bar: ")
                    .color(TextColor.color(200, 200, 200))
                    .append(Component.text(gameManager.isLocateBarEnabled() ? "Enabled" : "Disabled")
                            .color(gameManager.isLocateBarEnabled() ? COLOR_ON : COLOR_OFF)));
            player.closeInventory();
            open(player);

        } else if (slot == SLOT_HEALTH_BARS) {
            gameManager.setShowHealthBars(!gameManager.isShowHealthBars());
            player.sendMessage(Component.text("Health Bars in TAB: ")
                    .color(TextColor.color(200, 200, 200))
                    .append(Component.text(gameManager.isShowHealthBars() ? "Enabled" : "Disabled")
                            .color(gameManager.isShowHealthBars() ? COLOR_ON : COLOR_OFF)));
            player.closeInventory();
            open(player);

        } else if (slot == SLOT_PLAYER_GLOW) {
            gameManager.setLinkedPlayerGlow(!gameManager.isLinkedPlayerGlow());
            player.sendMessage(Component.text("Linked Player Glow: ")
                    .color(TextColor.color(200, 200, 200))
                    .append(Component.text(gameManager.isLinkedPlayerGlow() ? "Enabled" : "Disabled")
                            .color(gameManager.isLinkedPlayerGlow() ? COLOR_ON : COLOR_OFF)));
            player.closeInventory();
            open(player);
        }
    }

    public static void register(Plugin plugin, GameManager gameManager) {
        Bukkit.getPluginManager().registerEvents(new SettingsGUI(gameManager), plugin);
    }
}
