package com.soullink;

import com.soullink.commands.SettingsCommand;
import com.soullink.game.GameManager;
import com.soullink.gui.SettingsGUI;
import com.soullink.listeners.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

public class SoulLinkPlugin extends JavaPlugin {

    private GameManager gameManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        gameManager = new GameManager(this);

        // Load all settings from config
        gameManager.setNaturalRegeneration(getConfig().getBoolean("natural-regeneration", true));
        gameManager.setLastChanceCount(getConfig().getInt("last-chance-count", 1));
        gameManager.setLocateBarEnabled(getConfig().getBoolean("locate-bar-enabled", true));
        gameManager.setShowHealthBars(getConfig().getBoolean("show-health-bars", true));
        gameManager.setLinkedPlayerGlow(getConfig().getBoolean("linked-player-glow", true));

        getCommand("settings").setExecutor(new SettingsCommand(gameManager));
        getServer().getPluginManager().registerEvents(new PlayerListener(gameManager), this);
        SettingsGUI.register(this, gameManager);

        getLogger().info("SoulLink plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            // Persist all settings
            getConfig().set("natural-regeneration", gameManager.isNaturalRegeneration());
            getConfig().set("last-chance-count", gameManager.getLastChanceCount());
            getConfig().set("locate-bar-enabled", gameManager.isLocateBarEnabled());
            getConfig().set("show-health-bars", gameManager.isShowHealthBars());
            getConfig().set("linked-player-glow", gameManager.isLinkedPlayerGlow());
            saveConfig();

            gameManager.cleanup();
        }
        getLogger().info("SoulLink plugin has been disabled!");
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}
