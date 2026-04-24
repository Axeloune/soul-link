package com.soullink.game;

import com.soullink.SoulLinkPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class GameManager {

    // ── RGBA palette ────────────────────────────────────────────────────────
    private static final TextColor COLOR_GOLD        = TextColor.color(255, 190,   0);
    private static final TextColor COLOR_SOUL_PURPLE = TextColor.color(160,  80, 255);
    private static final TextColor COLOR_TIMER_GREEN = TextColor.color(120, 255, 120);
    private static final TextColor COLOR_PLAYER_RED  = TextColor.color(255, 110, 110);
    private static final TextColor COLOR_DIST_GRAY   = TextColor.color(190, 190, 190);
    private static final TextColor COLOR_SEP         = TextColor.color( 70,  70,  70);
    private static final TextColor COLOR_DEV_GRAY    = TextColor.color(130, 130, 130);
    private static final TextColor COLOR_DEV_BLUE    = TextColor.color( 80, 200, 255);
    private static final TextColor COLOR_HEAL        = TextColor.color( 80, 230,  80);
    private static final TextColor COLOR_DAMAGE      = TextColor.color(230,  80,  80);
    private static final TextColor COLOR_INFO        = TextColor.color(200, 200, 255);

    // ── Scoreboard sidebar constants ─────────────────────────────────────────
    // Fixed 9-line sidebar: 1 sep | 1 timer | 1 sep | 4 player slots | 1 sep | 1 dev credit
    private static final int SCOREBOARD_LINES = 9;
    // Fake entry names: §0 … §8 (colour-code chars render as empty text)
    private static final String[] FAKE_ENTRIES;
    static {
        FAKE_ENTRIES = new String[SCOREBOARD_LINES];
        for (int i = 0; i < SCOREBOARD_LINES; i++) {
            FAKE_ENTRIES[i] = "\u00a7" + Integer.toHexString(i);
        }
    }

    // ── World pre-generation radius (in chunks) ──────────────────────────────
    private static final int PREGEN_RADIUS = 5;  // 11×11 = 121 chunks

    private static final int MAX_PLAYERS = 4;

    // ── Core state ───────────────────────────────────────────────────────────
    private final SoulLinkPlugin plugin;
    private final Set<UUID> linkedPlayers;
    private final Set<UUID> spectators;
    private boolean gameActive;
    private boolean gameEnding;
    private long gameStartTime;

    // ── Settings ─────────────────────────────────────────────────────────────
    private boolean naturalRegeneration;
    private int lastChanceCount;
    private final Map<UUID, Integer> playerLastChances;
    private boolean locateBarEnabled;
    private boolean showHealthBars;
    private boolean linkedPlayerGlow;

    // ── Recursion guard ───────────────────────────────────────────────────────
    private final Set<UUID> processingPlayers;

    // ── World names ───────────────────────────────────────────────────────────
    private String currentOverworldName;
    private String currentNetherName;
    private String currentEndName;

    // ── Scoreboard & TAB ──────────────────────────────────────────────────────
    private final Map<UUID, Scoreboard> playerScoreboards;
    private int locateBarTaskId;
    private int scoreboardTaskId;

    // ─────────────────────────────────────────────────────────────────────────

    public GameManager(SoulLinkPlugin plugin) {
        this.plugin = plugin;
        this.linkedPlayers = new HashSet<>();
        this.spectators = new HashSet<>();
        this.playerLastChances = new HashMap<>();
        this.processingPlayers = new HashSet<>();
        this.playerScoreboards = new HashMap<>();
        this.gameActive = false;
        this.gameEnding = false;
        this.gameStartTime = 0;
        this.locateBarTaskId = -1;
        this.scoreboardTaskId = -1;

        // Resolve default world names
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
        this.showHealthBars = true;
        this.linkedPlayerGlow = true;

        startLocateBarTask();
        startScoreboardTask();
    }

    // ── Player linking ────────────────────────────────────────────────────────

    public void linkPlayer(Player player) {
        if (linkedPlayers.size() >= MAX_PLAYERS) {
            spectators.add(player.getUniqueId());
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(Component.text("The game is full. You are now a spectator.", COLOR_DEV_GRAY));
            updateTabListForPlayer(player);
            return;
        }

        linkedPlayers.add(player.getUniqueId());
        playerLastChances.put(player.getUniqueId(), lastChanceCount);
        player.setGameMode(GameMode.SURVIVAL);

        // Heal on first link
        healPlayer(player);

        // Glow
        if (linkedPlayerGlow) {
            player.setGlowing(true);
        }

        // Per-player scoreboard
        setupPlayerScoreboard(player);

        player.sendMessage(Component.text("Your soul has been linked! Damage and healing are shared.", COLOR_HEAL));

        if (!gameActive) {
            startGame();
        }

        updateTabListForPlayer(player);
    }

    public void unlinkPlayer(Player player) {
        linkedPlayers.remove(player.getUniqueId());
        spectators.remove(player.getUniqueId());
        playerLastChances.remove(player.getUniqueId());

        player.setGlowing(false);

        // Reset to main scoreboard
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        playerScoreboards.remove(player.getUniqueId());
    }

    private void healPlayer(Player player) {
        try {
            double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            player.setHealth(maxHealth);
        } catch (Exception e) {
            player.setHealth(20.0);
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
    }

    // ── Game lifecycle ────────────────────────────────────────────────────────

    private void startGame() {
        gameActive = true;
        gameStartTime = System.currentTimeMillis();

        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.NATURAL_REGENERATION, naturalRegeneration);
            world.setDifficulty(Difficulty.HARD);
        }

        // Heal all linked players at game start
        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                healPlayer(player);
                if (linkedPlayerGlow) {
                    player.setGlowing(true);
                }
            }
        }

        Title startTitle = Title.title(
                Component.text("SOUL LINK").color(COLOR_GOLD).decorate(TextDecoration.BOLD),
                Component.text("A new adventure begins!").color(COLOR_TIMER_GREEN),
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1)));

        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.showTitle(startTitle);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
            }
        }

        Bukkit.broadcast(Component.text("Soul Link game has started!", COLOR_GOLD));
    }

    public void endGame() {
        if (!gameActive || gameEnding) return;
        gameEnding = true;
        gameActive = false;

        Bukkit.broadcast(Component.text("Game Over! A linked player has fallen!", COLOR_DAMAGE));

        Title gameOverTitle = Title.title(
                Component.text("GAME OVER").color(COLOR_DAMAGE).decorate(TextDecoration.BOLD),
                Component.text("A linked player has fallen!").color(TextColor.color(180, 50, 50)),
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(4), Duration.ofSeconds(1)));

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(gameOverTitle);
            online.playSound(online.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        }

        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setGlowing(false);
                player.setGameMode(GameMode.SPECTATOR);
            }
        }

        linkedPlayers.clear();
        spectators.clear();
        playerLastChances.clear();

        new BukkitRunnable() {
            @Override
            public void run() {
                doWorldReset();
            }
        }.runTaskLater(plugin, 100L);
    }

    public void handleDragonDeath() {
        if (!gameActive || gameEnding) return;
        gameEnding = true;
        gameActive = false;

        Bukkit.broadcast(Component.text("The Ender Dragon has been defeated! You WIN!", COLOR_GOLD));

        List<Player> winners = new ArrayList<>();
        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                winners.add(player);
            }
        }

        Title victoryTitle = Title.title(
                Component.text("YOU WIN!").color(COLOR_GOLD).decorate(TextDecoration.BOLD),
                Component.text("The Ender Dragon has been slain!").color(TextColor.color(255, 230, 0)),
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(1)));

        for (Player player : winners) {
            player.showTitle(victoryTitle);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            spawnVictoryFireworks(player);
        }

        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setGlowing(false);
            }
        }

        linkedPlayers.clear();
        spectators.clear();
        playerLastChances.clear();

        new BukkitRunnable() {
            @Override
            public void run() {
                doWorldReset();
            }
        }.runTaskLater(plugin, 200L);
    }

    // ── World reset with spawn pre-generation ─────────────────────────────────

    private void doWorldReset() {
        Bukkit.broadcast(Component.text("Generating new worlds, please wait...", COLOR_INFO));

        String suffix = String.valueOf(System.currentTimeMillis());
        String newOverworldName = "soul_world_" + suffix;
        String newNetherName   = "soul_nether_" + suffix;
        String newEndName      = "soul_end_"     + suffix;

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
            Bukkit.broadcast(Component.text("World generation failed! Please contact an admin.", COLOR_DAMAGE));
            gameEnding = false;
            return;
        }

        for (World world : List.of(newOverworld, newNether, newEnd)) {
            world.setGameRule(GameRule.NATURAL_REGENERATION, naturalRegeneration);
            world.setDifficulty(Difficulty.HARD);
        }

        // Pre-generate spawn-area chunks so players don't see "Loading terrain"
        Bukkit.broadcast(Component.text(
                "Pre-generating spawn area (" + ((PREGEN_RADIUS * 2 + 1) * (PREGEN_RADIUS * 2 + 1))
                        + " chunks), please wait...", COLOR_INFO));

        Location spawnLocation = newOverworld.getSpawnLocation();
        int spawnChunkX = spawnLocation.getBlockX() >> 4;
        int spawnChunkZ = spawnLocation.getBlockZ() >> 4;

        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int cx = spawnChunkX - PREGEN_RADIUS; cx <= spawnChunkX + PREGEN_RADIUS; cx++) {
            for (int cz = spawnChunkZ - PREGEN_RADIUS; cz <= spawnChunkZ + PREGEN_RADIUS; cz++) {
                futures.add(newOverworld.getChunkAtAsync(cx, cz));
            }
        }

        // Capture variables for the lambda
        final List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        final String oldOverworldName = currentOverworldName;
        final String oldNetherName   = currentNetherName;
        final String oldEndName      = currentEndName;
        final World  finalNewOverworld = newOverworld;
        final String finalNewOverworldName = newOverworldName;
        final String finalNewNetherName   = newNetherName;
        final String finalNewEndName      = newEndName;

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () ->
                        finishWorldReset(
                                finalNewOverworld, finalNewOverworldName,
                                finalNewNetherName, finalNewEndName,
                                spawnLocation, allPlayers,
                                oldOverworldName, oldNetherName, oldEndName)));
    }

    private void finishWorldReset(
            World newOverworld, String newOverworldName,
            String newNetherName, String newEndName,
            Location spawnLocation, List<Player> allPlayers,
            String oldOverworldName, String oldNetherName, String oldEndName) {

        Bukkit.broadcast(Component.text("Spawn area ready! Teleporting all players...", COLOR_HEAL));

        for (Player player : allPlayers) {
            if (player.isOnline()) {
                player.setGameMode(GameMode.SURVIVAL);
                player.teleport(spawnLocation);
            }
        }

        currentOverworldName = newOverworldName;
        currentNetherName   = newNetherName;
        currentEndName      = newEndName;

        unloadAndDeleteWorld(oldOverworldName);
        unloadAndDeleteWorld(oldNetherName);
        unloadAndDeleteWorld(oldEndName);

        // Reset flags and re-link all players for a fresh game
        gameEnding = false;
        for (Player player : allPlayers) {
            if (player.isOnline()) {
                linkPlayer(player);
            }
        }
    }

    // ── Damage / healing sharing ───────────────────────────────────────────────

    public void shareDamage(Player source, double damage, String causeName) {
        if (!gameActive || !linkedPlayers.contains(source.getUniqueId())) return;
        if (processingPlayers.contains(source.getUniqueId())) return;

        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || player.equals(source)) continue;

            processingPlayers.add(player.getUniqueId());
            try {
                double newHealth = player.getHealth() - damage;
                if (newHealth <= 0) {
                    int chances = playerLastChances.getOrDefault(player.getUniqueId(), 0);
                    if (chances > 0) {
                        playerLastChances.put(player.getUniqueId(), chances - 1);
                        player.setHealth(1.0);
                        player.sendActionBar(Component.text(
                                "Last Chance Used! Remaining: " + (chances - 1), COLOR_GOLD));
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    } else {
                        endGame();
                        return;
                    }
                } else {
                    player.setHealth(newHealth);
                    player.sendActionBar(Component.text(
                            String.format("❤ %s: -%.1f (from %s)", source.getName(), damage, causeName),
                            COLOR_DAMAGE));
                }
            } finally {
                processingPlayers.remove(player.getUniqueId());
            }
        }
    }

    public void shareHealing(Player source, double healing, String causeName) {
        if (!gameActive || !linkedPlayers.contains(source.getUniqueId())) return;
        if (processingPlayers.contains(source.getUniqueId())) return;

        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || player.equals(source)) continue;

            processingPlayers.add(player.getUniqueId());
            try {
                double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                double newHealth = Math.min(maxHealth, player.getHealth() + healing);
                player.setHealth(newHealth);
                player.sendActionBar(Component.text(
                        String.format("❤ %s: +%.1f (from %s)", source.getName(), healing, causeName),
                        COLOR_HEAL));
            } finally {
                processingPlayers.remove(player.getUniqueId());
            }
        }
    }

    public void handlePlayerDeath(Player player) {
        if (!linkedPlayers.contains(player.getUniqueId())) return;
        int chances = playerLastChances.getOrDefault(player.getUniqueId(), 0);
        if (chances > 0) {
            playerLastChances.put(player.getUniqueId(), chances - 1);
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.spigot().respawn();
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setHealth(1.0);
                    player.sendActionBar(Component.text(
                            "Last Chance Used! Remaining: " + (chances - 1), COLOR_GOLD));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            }.runTaskLater(plugin, 1L);
            return;
        }
        endGame();
    }

    public boolean isLinkedPlayer(Player player) {
        return linkedPlayers.contains(player.getUniqueId());
    }

    public void handleLethalDamage(Player player) {
        if (!gameActive || gameEnding) return;
        int chances = playerLastChances.getOrDefault(player.getUniqueId(), 0);
        if (chances > 0) {
            playerLastChances.put(player.getUniqueId(), chances - 1);
            player.setHealth(1.0);
            player.sendActionBar(Component.text(
                    "Last Chance Used! Remaining: " + (chances - 1), COLOR_GOLD));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            endGame();
        }
    }

    // ── Locate bar (compass) ─────────────────────────────────────────────────

    private void updateLocateBars() {
        if (!locateBarEnabled) return;

        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;

            Player nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (UUID otherUuid : linkedPlayers) {
                if (otherUuid.equals(uuid)) continue;
                Player other = Bukkit.getPlayer(otherUuid);
                if (other == null || !other.isOnline()) continue;
                if (!other.getWorld().equals(player.getWorld())) continue;
                double d = player.getLocation().distance(other.getLocation());
                if (d < nearestDist) {
                    nearestDist = d;
                    nearest = other;
                }
            }
            if (nearest != null) {
                try {
                    player.setCompassTarget(nearest.getLocation());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to update compass for " + player.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    // ── Scoreboard ────────────────────────────────────────────────────────────

    private void setupPlayerScoreboard(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();

        // Sidebar objective (dummy criteria)
        Objective sidebarObj = sb.registerNewObjective(
                "soullink", "dummy",
                Component.text("■ SOUL LINK ■").color(COLOR_GOLD).decorate(TextDecoration.BOLD));
        sidebarObj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Health bars in TAB (optional)
        if (showHealthBars) {
            Objective healthObj = sb.registerNewObjective(
                    "sl_health", "health",
                    Component.text("HP").color(COLOR_DAMAGE), RenderType.HEARTS);
            healthObj.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        }

        // Create fixed-position entries using colour-code fake names + teams
        for (int i = 0; i < SCOREBOARD_LINES; i++) {
            String entry = FAKE_ENTRIES[i];
            Team team = sb.registerNewTeam("sl_line_" + i);
            team.addEntry(entry);
            team.prefix(Component.empty());
            team.suffix(Component.empty());
            // Higher score → higher position in sidebar
            sidebarObj.getScore(entry).setScore(SCOREBOARD_LINES - 1 - i);
        }

        playerScoreboards.put(player.getUniqueId(), sb);
        player.setScoreboard(sb);
        updateScoreboardForPlayer(player);
    }

    private void updateScoreboardForPlayer(Player viewer) {
        Scoreboard sb = playerScoreboards.get(viewer.getUniqueId());
        if (sb == null) return;

        Component[] lines = buildScoreboardLines(viewer);
        for (int i = 0; i < SCOREBOARD_LINES; i++) {
            Team team = sb.getTeam("sl_line_" + i);
            if (team != null) {
                team.prefix(lines[i]);
                team.suffix(Component.empty());
            }
        }
    }

    /** Builds exactly {@value #SCOREBOARD_LINES} components (top → bottom). */
    private Component[] buildScoreboardLines(Player viewer) {
        Component[] lines = new Component[SCOREBOARD_LINES];

        // Line 0: separator
        lines[0] = Component.text("─────────────").color(COLOR_SEP);

        // Line 1: timer
        String timerStr = gameActive ? getTimerString() : "00:00:00";
        lines[1] = Component.text("⏱ ").color(COLOR_DIST_GRAY)
                .append(Component.text(timerStr).color(COLOR_TIMER_GREEN));

        // Line 2: separator
        lines[2] = Component.text("─────────────").color(COLOR_SEP);

        // Lines 3-6: up to 4 player slots
        List<UUID> players = new ArrayList<>(linkedPlayers);
        for (int i = 0; i < 4; i++) {
            if (i < players.size()) {
                Player other = Bukkit.getPlayer(players.get(i));
                lines[3 + i] = (other != null && other.isOnline())
                        ? buildPlayerLine(viewer, other)
                        : Component.empty();
            } else {
                lines[3 + i] = Component.empty();
            }
        }

        // Line 7: separator
        lines[7] = Component.text("─────────────").color(COLOR_SEP);

        // Line 8: dev credit
        lines[8] = Component.text("dev by ").color(COLOR_DEV_GRAY)
                .append(Component.text("@Axeloune").color(COLOR_DEV_BLUE));

        return lines;
    }

    private Component buildPlayerLine(Player viewer, Player other) {
        if (other.equals(viewer)) {
            return Component.text("★ ").color(COLOR_GOLD)
                    .append(Component.text(other.getName()).color(COLOR_PLAYER_RED))
                    .append(Component.text(" (you)").color(COLOR_SEP));
        }

        String arrow;
        String distStr;
        if (viewer.getWorld().equals(other.getWorld())) {
            arrow = getDirectionArrow(viewer, other);
            distStr = formatDistance(viewer.getLocation().distance(other.getLocation()));
        } else {
            arrow = "? ";
            distStr = "other world";
        }

        return Component.text("• ").color(COLOR_SOUL_PURPLE)
                .append(Component.text(other.getName()).color(COLOR_PLAYER_RED))
                .append(Component.text(" " + arrow + distStr).color(COLOR_DIST_GRAY));
    }

    private String getTimerString() {
        long elapsed = (System.currentTimeMillis() - gameStartTime) / 1000;
        long h = elapsed / 3600;
        long m = (elapsed % 3600) / 60;
        long s = elapsed % 60;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    /**
     * Returns a directional arrow (↑ ↗ → ↘ ↓ ↙ ← ↖) indicating the direction
     * the viewer needs to look to face the target, based on the viewer's current yaw.
     */
    private String getDirectionArrow(Player viewer, Player target) {
        double dx = target.getLocation().getX() - viewer.getLocation().getX();
        double dz = target.getLocation().getZ() - viewer.getLocation().getZ();

        // Angle of target from north (north = −Z, clockwise positive)
        double targetAngle = Math.toDegrees(Math.atan2(dx, -dz));
        if (targetAngle < 0) targetAngle += 360;

        // Bukkit yaw: 0 = south, 90 = west, −90/270 = east, 180/−180 = north
        double compassBearing = (((double) viewer.getLocation().getYaw()) + 180 + 360) % 360;

        // Relative angle: 0 = ahead, 90 = right, 180 = behind, 270 = left
        double rel = (targetAngle - compassBearing + 360) % 360;

        if (rel < 22.5  || rel >= 337.5) return "↑ ";
        if (rel < 67.5)                  return "↗ ";
        if (rel < 112.5)                 return "→ ";
        if (rel < 157.5)                 return "↘ ";
        if (rel < 202.5)                 return "↓ ";
        if (rel < 247.5)                 return "↙ ";
        if (rel < 292.5)                 return "← ";
        return "↖ ";
    }

    private String formatDistance(double dist) {
        if (dist >= 1000) return String.format("%.1fkm", dist / 1000.0);
        return String.format("%.0fm", dist);
    }

    // ── TAB header / footer ───────────────────────────────────────────────────

    private void updateTabListForPlayer(Player player) {
        player.sendPlayerListHeaderAndFooter(buildTabHeader(), buildTabFooter());
    }

    private Component buildTabHeader() {
        return Component.text("━━━ ").color(COLOR_SEP)
                .append(Component.text("SOUL LINK").color(COLOR_GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text(" ━━━").color(COLOR_SEP))
                .append(Component.newline())
                .append(Component.text("Soul-linked adventure · dev by ").color(COLOR_DEV_GRAY))
                .append(Component.text("@Axeloune").color(COLOR_DEV_BLUE));
    }

    private Component buildTabFooter() {
        String timerStr = gameActive ? getTimerString() : "--:--";
        return Component.text("Players: ").color(COLOR_DIST_GRAY)
                .append(Component.text(linkedPlayers.size() + "/" + MAX_PLAYERS).color(COLOR_TIMER_GREEN))
                .append(Component.text(" │ ").color(COLOR_SEP))
                .append(Component.text("Timer: ").color(COLOR_DIST_GRAY))
                .append(Component.text(timerStr).color(COLOR_TIMER_GREEN))
                .append(Component.newline())
                .append(Component.text("Damage & healing are shared between linked players").color(COLOR_SEP));
    }

    // ── Periodic update task ──────────────────────────────────────────────────

    private void updateAll() {
        // Update scoreboards + TAB for every linked player
        for (UUID uuid : new ArrayList<>(linkedPlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                updateScoreboardForPlayer(player);
                updateTabListForPlayer(player);
            }
        }
        // TAB header/footer for spectators too
        for (UUID uuid : new ArrayList<>(spectators)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                updateTabListForPlayer(player);
            }
        }
    }

    private void startScoreboardTask() {
        scoreboardTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,
                this::updateAll, 20L, 20L);
    }

    private void stopScoreboardTask() {
        if (scoreboardTaskId != -1) {
            Bukkit.getScheduler().cancelTask(scoreboardTaskId);
            scoreboardTaskId = -1;
        }
    }

    // ── Helper: fireworks ─────────────────────────────────────────────────────

    private void spawnVictoryFireworks(Player player) {
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 10 || !player.isOnline()) { cancel(); return; }
                double ox = (Math.random() - 0.5) * 4;
                double oz = (Math.random() - 0.5) * 4;
                Location loc = player.getLocation().add(ox, 1, oz);
                Firework fw = (Firework) player.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
                FireworkMeta meta = fw.getFireworkMeta();
                FireworkEffect effect = FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.NAVY, Color.RED, Color.WHITE)
                        .withFade(Color.YELLOW)
                        .trail(true).flicker(true).build();
                meta.addEffect(effect);
                meta.setPower(1);
                fw.setFireworkMeta(meta);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // ── World helpers ─────────────────────────────────────────────────────────

    private void unloadAndDeleteWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
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
                if (file.isDirectory()) deleteFolder(file);
                else if (!file.delete()) {
                    plugin.getLogger().warning("Could not delete file: " + file.getAbsolutePath());
                }
            }
        }
        if (!folder.delete()) {
            plugin.getLogger().warning("Could not delete folder: " + folder.getAbsolutePath());
        }
    }

    // ── Locate bar task ───────────────────────────────────────────────────────

    private void startLocateBarTask() {
        locateBarTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,
                this::updateLocateBars, 20L, 20L);
    }

    private void stopLocateBarTask() {
        if (locateBarTaskId != -1) {
            Bukkit.getScheduler().cancelTask(locateBarTaskId);
            locateBarTaskId = -1;
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void cleanup() {
        stopLocateBarTask();
        stopScoreboardTask();

        // Remove glow and reset scoreboards
        for (UUID uuid : new ArrayList<>(linkedPlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setGlowing(false);
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }

        playerScoreboards.clear();
        linkedPlayers.clear();
        spectators.clear();
        playerLastChances.clear();
        processingPlayers.clear();
        gameActive = false;
        gameEnding = false;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public boolean isNaturalRegeneration() { return naturalRegeneration; }

    public void setNaturalRegeneration(boolean naturalRegeneration) {
        this.naturalRegeneration = naturalRegeneration;
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.NATURAL_REGENERATION, naturalRegeneration);
        }
    }

    public int getLastChanceCount() { return lastChanceCount; }

    public void setLastChanceCount(int count) {
        this.lastChanceCount = Math.max(0, Math.min(2, count));
        for (UUID uuid : linkedPlayers) {
            playerLastChances.putIfAbsent(uuid, this.lastChanceCount);
        }
    }

    public boolean isLocateBarEnabled() { return locateBarEnabled; }

    public void setLocateBarEnabled(boolean enabled) { this.locateBarEnabled = enabled; }

    public boolean isShowHealthBars() { return showHealthBars; }

    public void setShowHealthBars(boolean showHealthBars) {
        this.showHealthBars = showHealthBars;
        // Rebuild scoreboards to add/remove the health objective
        for (UUID uuid : new ArrayList<>(linkedPlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                setupPlayerScoreboard(player);
            }
        }
    }

    public boolean isLinkedPlayerGlow() { return linkedPlayerGlow; }

    public void setLinkedPlayerGlow(boolean linkedPlayerGlow) {
        this.linkedPlayerGlow = linkedPlayerGlow;
        for (UUID uuid : linkedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setGlowing(linkedPlayerGlow);
            }
        }
    }

    public boolean isGameActive() { return gameActive; }

    public Set<UUID> getLinkedPlayers() { return Collections.unmodifiableSet(linkedPlayers); }

    /** Expose RGBA colors for use in other classes (e.g. GUI). */
    public static TextColor colorGold()      { return COLOR_GOLD; }
    public static TextColor colorHeal()      { return COLOR_HEAL; }
    public static TextColor colorDamage()    { return COLOR_DAMAGE; }
    public static TextColor colorInfo()      { return COLOR_INFO; }
    public static TextColor colorDevGray()   { return COLOR_DEV_GRAY; }
    public static TextColor colorDevBlue()   { return COLOR_DEV_BLUE; }
    public static TextColor colorSoulPurple(){ return COLOR_SOUL_PURPLE; }
    public static TextColor colorTimerGreen(){ return COLOR_TIMER_GREEN; }
    public static TextColor colorSep()       { return COLOR_SEP; }
}
