package com.soullink.game;

import com.soullink.SoulLinkPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class GameManager {
    
    private final SoulLinkPlugin plugin;
    private final Set<UUID> linkedPlayers;
    private final Set<UUID> spectators;
    private boolean gameActive;
    
    // Settings
    private boolean naturalRegeneration;
    private int lastChanceCount;
    private final Map<UUID, Integer> playerLastChances;
    private boolean locateBarEnabled;
    
    private static final int MAX_PLAYERS = 4;
    
    public GameManager(SoulLinkPlugin plugin) {
        this.plugin = plugin;
        this.linkedPlayers = new HashSet<>();
        this.spectators = new HashSet<>();
        this.playerLastChances = new HashMap<>();
        this.gameActive = false;
        
        // Default settings
        this.naturalRegeneration = true;
        this.lastChanceCount = 1;
        this.locateBarEnabled = true;
    }
    
    public void linkPlayer(Player player) {
        if (linkedPlayers.size() >= MAX_PLAYERS) {
            // Make them a spectator
            spectators.add(player.getUniqueId());
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(Component.text("The game is full. You are now a spectator.", NamedTextColor.YELLOW));
            return;
        }
        
        linkedPlayers.add(player.getUniqueId());
        playerLastChances.put(player.getUniqueId(), lastChanceCount);
        
        // Set hardcore mode
        player.setGameMode(GameMode.SURVIVAL);
        
        player.sendMessage(Component.text("Your soul has been linked! Damage and healing are shared.", NamedTextColor.GREEN));
        
        // Start the game if we have at least 1 player
        if (!gameActive) {
            startGame();
        }
        
        // Update locate bars for all players
        if (locateBarEnabled) {
            updateLocateBars();
        }
    }
    
    public void unlinkPlayer(Player player) {
        linkedPlayers.remove(player.getUniqueId());
        spectators.remove(player.getUniqueId());
        playerLastChances.remove(player.getUniqueId());
        
        if (locateBarEnabled) {
            updateLocateBars();
        }
    }
    
    private void startGame() {
        gameActive = true;
        
        // Set game rules
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.NATURAL_REGENERATION, naturalRegeneration);
            world.setDifficulty(Difficulty.HARD);
        }
        
        // Broadcast game start
        Bukkit.broadcast(Component.text("Soul Link game has started!", NamedTextColor.GOLD));
    }
    
    public void endGame() {
        gameActive = false;
        
        // Send death message to all players
        Bukkit.broadcast(Component.text("A linked player has died! Game Over!", NamedTextColor.RED));
        
        // Respawn all linked players as spectators
        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
        
        // Clear data
        linkedPlayers.clear();
        spectators.clear();
        playerLastChances.clear();
    }
    
    public void shareDamage(Player source, double damage) {
        if (!gameActive || !linkedPlayers.contains(source.getUniqueId())) {
            return;
        }
        
        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !player.equals(source)) {
                double currentHealth = player.getHealth();
                double newHealth = Math.max(0, currentHealth - damage);
                
                // Check if player would die
                if (newHealth <= 0) {
                    // Check for last chance
                    int chances = playerLastChances.getOrDefault(player.getUniqueId(), 0);
                    if (chances > 0) {
                        // Use last chance
                        playerLastChances.put(player.getUniqueId(), chances - 1);
                        player.setHealth(1.0);
                        player.sendActionBar(Component.text("Last Chance Used! Remaining: " + (chances - 1), NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        continue;
                    } else {
                        // Player dies
                        player.setHealth(0);
                        endGame();
                        return;
                    }
                }
                
                player.setHealth(newHealth);
                player.sendActionBar(Component.text(String.format("❤ Shared Damage: -%.1f", damage / 2), NamedTextColor.RED));
            }
        }
    }
    
    public void shareHealing(Player source, double healing) {
        if (!gameActive || !linkedPlayers.contains(source.getUniqueId())) {
            return;
        }
        
        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !player.equals(source)) {
                double currentHealth = player.getHealth();
                double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                double newHealth = Math.min(maxHealth, currentHealth + healing);
                
                player.setHealth(newHealth);
                player.sendActionBar(Component.text(String.format("❤ Shared Healing: +%.1f", healing / 2), NamedTextColor.GREEN));
            }
        }
    }
    
    public void handlePlayerDeath(Player player) {
        if (linkedPlayers.contains(player.getUniqueId())) {
            // Check for last chance
            int chances = playerLastChances.getOrDefault(player.getUniqueId(), 0);
            if (chances > 0) {
                // Use last chance
                playerLastChances.put(player.getUniqueId(), chances - 1);
                
                // Prevent death
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.spigot().respawn();
                        player.setGameMode(GameMode.SURVIVAL);
                        player.setHealth(1.0);
                        player.sendActionBar(Component.text("Last Chance Used! Remaining: " + (chances - 1), NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                }.runTaskLater(plugin, 1L);
                return;
            }
            
            endGame();
        }
    }
    
    private void updateLocateBars() {
        if (!locateBarEnabled) {
            return;
        }
        
        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // Create locate bars for all other linked players
                for (UUID otherUuid : linkedPlayers) {
                    if (!otherUuid.equals(uuid)) {
                        Player otherPlayer = Bukkit.getPlayer(otherUuid);
                        if (otherPlayer != null && otherPlayer.isOnline()) {
                            // Set locate bar (tracking) - this is a 1.21.6+ feature
                            try {
                                player.setCompassTarget(otherPlayer.getLocation());
                            } catch (Exception e) {
                                // Fallback if locate bar not available
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Getters and setters for settings
    public boolean isNaturalRegeneration() {
        return naturalRegeneration;
    }
    
    public void setNaturalRegeneration(boolean naturalRegeneration) {
        this.naturalRegeneration = naturalRegeneration;
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.NATURAL_REGENERATION, naturalRegeneration);
        }
    }
    
    public int getLastChanceCount() {
        return lastChanceCount;
    }
    
    public void setLastChanceCount(int count) {
        this.lastChanceCount = Math.max(0, Math.min(2, count));
        // Update existing players
        for (UUID uuid : linkedPlayers) {
            if (!playerLastChances.containsKey(uuid)) {
                playerLastChances.put(uuid, this.lastChanceCount);
            }
        }
    }
    
    public boolean isLocateBarEnabled() {
        return locateBarEnabled;
    }
    
    public void setLocateBarEnabled(boolean enabled) {
        this.locateBarEnabled = enabled;
        if (enabled) {
            updateLocateBars();
        }
    }
    
    public boolean isGameActive() {
        return gameActive;
    }
    
    public Set<UUID> getLinkedPlayers() {
        return Collections.unmodifiableSet(linkedPlayers);
    }
    
    public void cleanup() {
        linkedPlayers.clear();
        spectators.clear();
        playerLastChances.clear();
        gameActive = false;
    }
}
