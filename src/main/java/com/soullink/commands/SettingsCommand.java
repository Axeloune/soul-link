package com.soullink.commands;

import com.soullink.game.GameManager;
import com.soullink.gui.SettingsGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SettingsCommand implements CommandExecutor {
    
    private final GameManager gameManager;
    
    public SettingsCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Open the settings GUI
        SettingsGUI gui = new SettingsGUI(gameManager);
        gui.open(player);
        
        return true;
    }
}
