package com.soullink.listeners;

import com.soullink.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerListener implements Listener {
    
    private final GameManager gameManager;
    
    public PlayerListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Link the player when they join
        gameManager.linkPlayer(player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Unlink the player when they quit
        gameManager.unlinkPlayer(player);
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        double damage = event.getFinalDamage();
        
        // Show damage on action bar
        if (damage > 0) {
            player.sendActionBar(Component.text(String.format("❤ Damage Taken: -%.1f", damage), NamedTextColor.RED));
        }
        
        // Share damage with linked players
        gameManager.shareDamage(player, damage);
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        double healing = event.getAmount();
        
        // Show healing on action bar
        if (healing > 0) {
            player.sendActionBar(Component.text(String.format("❤ Healing: +%.1f", healing), NamedTextColor.GREEN));
        }
        
        // Share healing with linked players
        gameManager.shareHealing(player, healing);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        // Handle player death (end game or use last chance)
        gameManager.handlePlayerDeath(player);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon) {
            gameManager.handleDragonDeath();
        }
    }
}
