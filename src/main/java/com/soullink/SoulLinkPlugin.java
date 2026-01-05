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
        // Initialize game manager
        gameManager = new GameManager(this);
        
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
            gameManager.cleanup();
        }
        getLogger().info("SoulLink plugin has been disabled!");
    }
    
    public GameManager getGameManager() {
        return gameManager;
    }
}
