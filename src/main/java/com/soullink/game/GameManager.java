package com.soullink.game;

import com.soullink.SoulLinkPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.time.Duration;
import java.util.*;

public class GameManager {
    
    private final SoulLinkPlugin plugin;
    private final Set<UUID> linkedPlayers;
    private final Set<UUID> spectators;
    private boolean gameActive;
    private int locateBarTaskId;
    
    // Settings
    private boolean naturalRegeneration;
    private int lastChanceCount;
    private final Map<UUID, Integer> playerLastChances;
    private boolean locateBarEnabled;
    
    // Flag to prevent infinite recursion when sharing damage/healing
    private final Set<UUID> processingPlayers;

    // Flag to prevent double-triggering of game end / world reset
    private boolean gameEnding;

    // Current active world names (default to standard Bukkit world names)
    private String currentOverworldName;
    private String currentNetherName;
    private String currentEndName;

    private static final int MAX_PLAYERS = 4;
    
    public GameManager(SoulLinkPlugin plugin) {
        this.plugin = plugin;
        this.linkedPlayers = new HashSet<>();
        this.spectators = new HashSet<>();
        this.playerLastChances = new HashMap<>();
        this.processingPlayers = new HashSet<>();
        this.gameActive = false;
        this.gameEnding = false;
        this.locateBarTaskId = -1;

        // Resolve default world names from the server's loaded worlds
        List<World> worlds = Bukkit.getWorlds();
        this.currentOverworldName = worlds.stream()
                .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .map(World::getName)
                .findFirst().orElse("world");
        this.currentNetherName = worlds.stream()
                .filter(w -> w.getEnvironment() == World.Environment.NETHER)
                .map(World::getName)
                .findFirst().orElse("world_nether");
        this.currentEndName = worlds.stream()
                .filter(w -> w.getEnvironment() == World.Environment.THE_END)
                .map(World::getName)
                .findFirst().orElse("world_the_end");
        
        // Default settings
        this.naturalRegeneration = true;
        this.lastChanceCount = 1;
        this.locateBarEnabled = true;
        
        // Start locate bar update task
        startLocateBarTask();
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
    }
    
    public void unlinkPlayer(Player player) {
        linkedPlayers.remove(player.getUniqueId());
        spectators.remove(player.getUniqueId());
        playerLastChances.remove(player.getUniqueId());
    }
    
    private void startGame() {
        gameActive = true;
        
        // Set game rules
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.NATURAL_REGENERATION, naturalRegeneration);
            world.setDifficulty(Difficulty.HARD);
        }
        
        // Show start title and play sound to all linked players
        Title startTitle = Title.title(
                Component.text("SOUL LINK", NamedTextColor.GOLD),
                Component.text("A new adventure begins!", NamedTextColor.GREEN),
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1)));
        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.showTitle(startTitle);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
            }
        }
        
        // Broadcast game start
        Bukkit.broadcast(Component.text("Soul Link game has started!", NamedTextColor.GOLD));
    }
    
    public void endGame() {
        if (!gameActive || gameEnding) {
            return;
        }
        gameEnding = true;
        gameActive = false;

        // Single server-wide broadcast
        Bukkit.broadcast(Component.text("Game Over! A linked player has fallen!", NamedTextColor.RED));

        // Show Game Over title and play a dramatic sound to every online player
        Title gameOverTitle = Title.title(
                Component.text("GAME OVER", NamedTextColor.RED),
                Component.text("A linked player has fallen!", NamedTextColor.DARK_RED),
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(gameOverTitle);
            online.playSound(online.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        }

        // Make all linked players spectators
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
        
        // Schedule world reset after a short delay so players see the game-over message
        new BukkitRunnable() {
            @Override
            public void run() {
                doWorldReset();
            }
        }.runTaskLater(plugin, 100L); // 5 seconds
    }

    /**
     * Called when the Ender Dragon is killed. Shows a victory title and fireworks
     * to all surviving linked players, then triggers a world reset.
     */
    public void handleDragonDeath() {
        if (!gameActive || gameEnding) {
            return;
        }
        gameEnding = true;
        gameActive = false;

        Bukkit.broadcast(Component.text("The Ender Dragon has been defeated! You WIN!", NamedTextColor.GOLD));

        // Collect surviving linked players before clearing the set
        List<Player> winners = new ArrayList<>();
        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                winners.add(player);
            }
        }

        // Show victory title and fireworks to all survivors
        Title victoryTitle = Title.title(
                Component.text("YOU WIN!", NamedTextColor.GOLD),
                Component.text("The Ender Dragon has been slain!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(1)));
        for (Player player : winners) {
            player.showTitle(victoryTitle);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            spawnVictoryFireworks(player);
        }

        // Clear data
        linkedPlayers.clear();
        spectators.clear();
        playerLastChances.clear();

        // Schedule world reset after celebration (10 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                doWorldReset();
            }
        }.runTaskLater(plugin, 200L);
    }

    /**
     * Creates fresh overworld, nether and end worlds, teleports all online players
     * to the new overworld, deletes the old worlds, then restarts the game.
     */
    private void doWorldReset() {
        Bukkit.broadcast(Component.text("Generating new worlds, please wait...", NamedTextColor.AQUA));

        String suffix = String.valueOf(System.currentTimeMillis());
        String newOverworldName = "soul_world_" + suffix;
        String newNetherName   = "soul_nether_" + suffix;
        String newEndName      = "soul_end_" + suffix;

        World newOverworld = new WorldCreator(newOverworldName)
                .environment(World.Environment.NORMAL)
                .generateStructures(true)
                .createWorld();
        World newNether = new WorldCreator(newNetherName)
                .environment(World.Environment.NETHER)
                .generateStructures(true)
                .createWorld();
        World newEnd = new WorldCreator(newEndName)
                .environment(World.Environment.THE_END)
                .generateStructures(true)
                .createWorld();

        if (newOverworld == null || newNether == null || newEnd == null) {
            plugin.getLogger().severe("Failed to create one or more new worlds! Aborting reset.");
            Bukkit.broadcast(Component.text("World generation failed! Please contact an admin.", NamedTextColor.RED));
            gameEnding = false;
            return;
        }

        // Apply game rules to new worlds
        for (World world : List.of(newOverworld, newNether, newEnd)) {
            world.setGameRule(GameRule.NATURAL_REGENERATION, naturalRegeneration);
            world.setDifficulty(Difficulty.HARD);
        }

        Bukkit.broadcast(Component.text("New worlds generated! Teleporting all players...", NamedTextColor.GREEN));

        // Teleport all online players to the new overworld spawn
        Location spawnLocation = newOverworld.getSpawnLocation();
        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player player : allPlayers) {
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(spawnLocation);
        }

        // Unload and delete old worlds
        String oldOverworldName = currentOverworldName;
        String oldNetherName   = currentNetherName;
        String oldEndName      = currentEndName;

        currentOverworldName = newOverworldName;
        currentNetherName   = newNetherName;
        currentEndName      = newEndName;

        unloadAndDeleteWorld(oldOverworldName);
        unloadAndDeleteWorld(oldNetherName);
        unloadAndDeleteWorld(oldEndName);

        // Reset ending flag and re-link all players to start a new game
        gameEnding = false;
        for (Player player : allPlayers) {
            linkPlayer(player);
        }
    }

    private void unloadAndDeleteWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            // Do not save world data to disk — it will be deleted immediately after unloading
            Bukkit.unloadWorld(world, false);
        }
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            deleteFolder(worldFolder);
        }
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else if (!file.delete()) {
                    plugin.getLogger().warning("Could not delete file: " + file.getAbsolutePath());
                }
            }
        }
        if (!folder.delete()) {
            plugin.getLogger().warning("Could not delete folder: " + folder.getAbsolutePath());
        }
    }

    private void spawnVictoryFireworks(Player player) {
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 10 || !player.isOnline()) {
                    cancel();
                    return;
                }
                double offsetX = (Math.random() - 0.5) * 4;
                double offsetZ = (Math.random() - 0.5) * 4;
                Location loc = player.getLocation().add(offsetX, 1, offsetZ);

                Firework fw = (Firework) player.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
                FireworkMeta meta = fw.getFireworkMeta();
                FireworkEffect effect = FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.NAVY, Color.RED, Color.WHITE)
                        .withFade(Color.YELLOW)
                        .trail(true)
                        .flicker(true)
                        .build();
                meta.addEffect(effect);
                meta.setPower(1);
                fw.setFireworkMeta(meta);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }
    
    public void shareDamage(Player source, double damage, String causeName) {
        if (!gameActive || !linkedPlayers.contains(source.getUniqueId())) {
            return;
        }
        
        // Prevent infinite recursion
        if (processingPlayers.contains(source.getUniqueId())) {
            return;
        }
        
        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !player.equals(source)) {
                // Mark as processing to prevent recursion
                processingPlayers.add(player.getUniqueId());
                
                try {
                    double currentHealth = player.getHealth();
                    double newHealth = Math.max(0, currentHealth - damage);
                    
                    // Check if player would die
                    if (newHealth <= 0) {
                        // Check for last chance
                        int chances = playerLastChances.getOrDefault(player.getUniqueId(), 0);
                        if (chances > 0) {
                            playerLastChances.put(player.getUniqueId(), chances - 1);
                            player.setHealth(1.0);
                            player.sendActionBar(Component.text(
                                    "Last Chance Used! Remaining: " + (chances - 1), NamedTextColor.YELLOW));
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                            continue;
                        } else {
                            // End the game without actually killing the player
                            endGame();
                            return;
                        }
                    }
                    
                    player.setHealth(newHealth);
                    player.sendActionBar(Component.text(
                            String.format("❤ %s: -%.1f (from %s)", source.getName(), damage, causeName),
                            NamedTextColor.RED));
                } finally {
                    processingPlayers.remove(player.getUniqueId());
                }
            }
        }
    }
    
    public void shareHealing(Player source, double healing, String causeName) {
        if (!gameActive || !linkedPlayers.contains(source.getUniqueId())) {
            return;
        }
        
        // Prevent infinite recursion
        if (processingPlayers.contains(source.getUniqueId())) {
            return;
        }
        
        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !player.equals(source)) {
                // Mark as processing to prevent recursion
                processingPlayers.add(player.getUniqueId());
                
                try {
                    double currentHealth = player.getHealth();
                    double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                    double newHealth = Math.min(maxHealth, currentHealth + healing);
                    
                    player.setHealth(newHealth);
                    player.sendActionBar(Component.text(
                            String.format("❤ %s: +%.1f (from %s)", source.getName(), healing, causeName),
                            NamedTextColor.GREEN));
                } finally {
                    processingPlayers.remove(player.getUniqueId());
                }
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

    /**
     * Returns true if the given player is currently a linked game participant.
     */
    public boolean isLinkedPlayer(Player player) {
        return linkedPlayers.contains(player.getUniqueId());
    }

    /**
     * Called when a linked player receives damage that would kill them (health - damage ≤ 0).
     * The damage event has already been cancelled by the listener before this is invoked.
     * Uses a last chance if available, or triggers game over.
     */
    public void handleLethalDamage(Player player) {
        if (!gameActive || gameEnding) {
            return;
        }
        int chances = playerLastChances.getOrDefault(player.getUniqueId(), 0);
        if (chances > 0) {
            playerLastChances.put(player.getUniqueId(), chances - 1);
            player.setHealth(1.0);
            player.sendActionBar(Component.text(
                    "Last Chance Used! Remaining: " + (chances - 1), NamedTextColor.YELLOW));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
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
                // Set compass target to point to the nearest linked player
                Player nearestPlayer = null;
                double nearestDistance = Double.MAX_VALUE;
                
                for (UUID otherUuid : linkedPlayers) {
                    if (!otherUuid.equals(uuid)) {
                        Player otherPlayer = Bukkit.getPlayer(otherUuid);
                        if (otherPlayer != null && otherPlayer.isOnline()) {
                            double distance = player.getLocation().distance(otherPlayer.getLocation());
                            if (distance < nearestDistance) {
                                nearestDistance = distance;
                                nearestPlayer = otherPlayer;
                            }
                        }
                    }
                }
                
                if (nearestPlayer != null) {
                    try {
                        player.setCompassTarget(nearestPlayer.getLocation());
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to update compass target for player " + player.getName() + ": " + e.getMessage());
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
    }
    
    public boolean isGameActive() {
        return gameActive;
    }
    
    public Set<UUID> getLinkedPlayers() {
        return Collections.unmodifiableSet(linkedPlayers);
    }
    
    private void startLocateBarTask() {
        // Update locate bars every second (20 ticks)
        locateBarTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, 
            this::updateLocateBars, 20L, 20L);
    }
    
    private void stopLocateBarTask() {
        if (locateBarTaskId != -1) {
            Bukkit.getScheduler().cancelTask(locateBarTaskId);
            locateBarTaskId = -1;
        }
    }
    
    public void cleanup() {
        stopLocateBarTask();
        linkedPlayers.clear();
        spectators.clear();
        playerLastChances.clear();
        processingPlayers.clear();
        gameActive = false;
        gameEnding = false;
    }
}
