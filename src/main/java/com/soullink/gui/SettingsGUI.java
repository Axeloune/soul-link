package com.soullink.gui;

import com.soullink.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    
    public SettingsGUI(GameManager gameManager) {
        this.gameManager = gameManager;
    }
    
    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE, NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        
        // Natural Regeneration Toggle
        ItemStack regenItem = new ItemStack(gameManager.isNaturalRegeneration() ? Material.GOLDEN_APPLE : Material.ROTTEN_FLESH);
        ItemMeta regenMeta = regenItem.getItemMeta();
        regenMeta.displayName(Component.text("Natural Regeneration", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> regenLore = new ArrayList<>();
        regenLore.add(Component.text("Status: " + (gameManager.isNaturalRegeneration() ? "Enabled" : "Disabled"), 
            gameManager.isNaturalRegeneration() ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        regenLore.add(Component.text("Click to toggle", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        regenMeta.lore(regenLore);
        regenItem.setItemMeta(regenMeta);
        inventory.setItem(11, regenItem);
        
        // Last Chance Setting
        ItemStack lastChanceItem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta lastChanceMeta = lastChanceItem.getItemMeta();
        lastChanceMeta.displayName(Component.text("Last Chance", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lastChanceLore = new ArrayList<>();
        lastChanceLore.add(Component.text("Current: " + gameManager.getLastChanceCount(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        lastChanceLore.add(Component.text("Left Click: +1 (Max: 2)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lastChanceLore.add(Component.text("Right Click: -1 (Min: 0)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lastChanceLore.add(Component.text("", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        lastChanceLore.add(Component.text("Saves players from death", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lastChanceMeta.lore(lastChanceLore);
        lastChanceItem.setItemMeta(lastChanceMeta);
        inventory.setItem(13, lastChanceItem);
        
        // Locate Bar Toggle
        ItemStack locateBarItem = new ItemStack(gameManager.isLocateBarEnabled() ? Material.COMPASS : Material.RECOVERY_COMPASS);
        ItemMeta locateBarMeta = locateBarItem.getItemMeta();
        locateBarMeta.displayName(Component.text("Locate Bar", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> locateBarLore = new ArrayList<>();
        locateBarLore.add(Component.text("Status: " + (gameManager.isLocateBarEnabled() ? "Enabled" : "Disabled"), 
            gameManager.isLocateBarEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        locateBarLore.add(Component.text("Click to toggle", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        locateBarLore.add(Component.text("", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        locateBarLore.add(Component.text("Shows location of linked players", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        locateBarMeta.lore(locateBarLore);
        locateBarItem.setItemMeta(locateBarMeta);
        inventory.setItem(15, locateBarItem);
        
        // Info item
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(Component.text("Soul Link Info", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.text("Up to 4 players can link their souls", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Damage and healing are shared", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("If one dies, all lose!", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("5+ players become spectators", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inventory.setItem(22, infoItem);
        
        player.openInventory(inventory);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        if (!event.getView().title().equals(Component.text(GUI_TITLE, NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))) {
            return;
        }
        
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        int slot = event.getSlot();
        
        // Natural Regeneration Toggle
        if (slot == 11) {
            gameManager.setNaturalRegeneration(!gameManager.isNaturalRegeneration());
            player.sendMessage(Component.text("Natural Regeneration: " + (gameManager.isNaturalRegeneration() ? "Enabled" : "Disabled"), 
                gameManager.isNaturalRegeneration() ? NamedTextColor.GREEN : NamedTextColor.RED));
            player.closeInventory();
            // Reopen to refresh
            open(player);
        }
        // Last Chance Setting
        else if (slot == 13) {
            if (event.isLeftClick()) {
                int newCount = Math.min(2, gameManager.getLastChanceCount() + 1);
                gameManager.setLastChanceCount(newCount);
                player.sendMessage(Component.text("Last Chance set to: " + newCount, NamedTextColor.YELLOW));
            } else if (event.isRightClick()) {
                int newCount = Math.max(0, gameManager.getLastChanceCount() - 1);
                gameManager.setLastChanceCount(newCount);
                player.sendMessage(Component.text("Last Chance set to: " + newCount, NamedTextColor.YELLOW));
            }
            player.closeInventory();
            // Reopen to refresh
            open(player);
        }
        // Locate Bar Toggle
        else if (slot == 15) {
            gameManager.setLocateBarEnabled(!gameManager.isLocateBarEnabled());
            player.sendMessage(Component.text("Locate Bar: " + (gameManager.isLocateBarEnabled() ? "Enabled" : "Disabled"), 
                gameManager.isLocateBarEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED));
            player.closeInventory();
            // Reopen to refresh
            open(player);
        }
    }
    
    public static void register(Plugin plugin, GameManager gameManager) {
        Bukkit.getPluginManager().registerEvents(new SettingsGUI(gameManager), plugin);
    }
}
