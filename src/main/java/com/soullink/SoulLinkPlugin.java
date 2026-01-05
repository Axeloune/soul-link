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
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Initialize game manager
        gameManager = new GameManager(this);
        
        // Load settings from config
        gameManager.setNaturalRegeneration(getConfig().getBoolean("natural-regeneration", true));
        gameManager.setLastChanceCount(getConfig().getInt("last-chance-count", 1));
        gameManager.setLocateBarEnabled(getConfig().getBoolean("locate-bar-enabled", true));
        
        // Register commands
        getCommand("settings").setExecutor(new SettingsCommand(gameManager));
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(gameManager), this);
        SettingsGUI.register(this, gameManager);
        
        getLogger().info("SoulLink plugin has been enabled!");
    }
    
    @Override
    public void onDisable() {
        if (gameManager != null) {
            // Save settings to config
            getConfig().set("natural-regeneration", gameManager.isNaturalRegeneration());
            getConfig().set("last-chance-count", gameManager.getLastChanceCount());
            getConfig().set("locate-bar-enabled", gameManager.isLocateBarEnabled());
            saveConfig();
            
            gameManager.cleanup();
        }
        getLogger().info("SoulLink plugin has been disabled!");
    }
    
    public GameManager getGameManager() {
        return gameManager;
    }
}
