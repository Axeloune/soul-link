package com.soullink.listeners;

import com.soullink.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Arrays;
import java.util.stream.Collectors;

public class PlayerListener implements Listener {

    // RGBA colours
    private static final TextColor COLOR_DAMAGE = TextColor.color(230,  80,  80);
    private static final TextColor COLOR_HEAL   = TextColor.color( 80, 230,  80);

    private final GameManager gameManager;

    public PlayerListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        gameManager.linkPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        gameManager.unlinkPlayer(event.getPlayer());
    }

    /**
     * Intercepts damage at the highest priority so we can cancel lethal hits for
     * linked players and prevent the actual death event from firing.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.isCancelled()) return;

        double damage = event.getFinalDamage();
        if (damage <= 0) return;

        String causeName = buildDamageCauseName(event);

        player.sendActionBar(Component.text(
                String.format("❤ %s: -%.1f (from %s)", player.getName(), damage, causeName),
                COLOR_DAMAGE));

        if (gameManager.isLinkedPlayer(player) && player.getHealth() - damage <= 0) {
            event.setCancelled(true);
            gameManager.handleLethalDamage(player);
            return;
        }

        gameManager.shareDamage(player, damage, causeName);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double healing = event.getAmount();
        if (healing <= 0) return;

        String causeName = buildHealCauseName(event, player);

        player.sendActionBar(Component.text(
                String.format("❤ %s: +%.1f (from %s)", player.getName(), healing, causeName),
                COLOR_HEAL));

        gameManager.shareHealing(player, healing, causeName);
    }

    /**
     * Safety net: if a linked player somehow reaches the death screen, trigger game-over.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        gameManager.handlePlayerDeath(event.getEntity());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon) {
            gameManager.handleDragonDeath();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildDamageCauseName(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Player p) return p.getName();
            if (damager instanceof Projectile proj) {
                ProjectileSource shooter = proj.getShooter();
                String shooterName = (shooter instanceof Player p) ? p.getName()
                        : (shooter instanceof LivingEntity le ? formatEntityName(le.getType()) : "Unknown");
                return shooterName + "'s " + formatEntityName(proj.getType());
            }
            return formatEntityName(damager.getType());
        }
        return formatDamageCause(event.getCause());
    }

    private String buildHealCauseName(EntityRegainHealthEvent event, Player player) {
        return switch (event.getRegainReason()) {
            case REGEN -> "Natural Regeneration";
            case SATIATED -> "Satiation";
            case EATING -> {
                String itemName = player.getInventory().getItemInMainHand().getType().name();
                yield formatEntityName(itemName);
            }
            case MAGIC -> "Potion";
            case MAGIC_REGEN -> "Regeneration Effect";
            case WITHER_SPAWN -> "Wither Spawn";
            case ENDER_CRYSTAL -> "End Crystal";
            case CUSTOM -> "Custom Effect";
            default -> capitalise(event.getRegainReason().name());
        };
    }

    private String formatEntityName(EntityType type) {
        return formatEntityName(type.name());
    }

    private String formatEntityName(String name) {
        return Arrays.stream(name.split("_"))
                .map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private String formatDamageCause(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case FALL -> "Fall Damage";
            case FIRE, FIRE_TICK -> "Fire";
            case LAVA -> "Lava";
            case DROWNING -> "Drowning";
            case SUFFOCATION -> "Suffocation";
            case STARVATION -> "Starvation";
            case POISON -> "Poison";
            case MAGIC -> "Magic";
            case WITHER -> "Wither";
            case VOID -> "Void";
            case LIGHTNING -> "Lightning";
            case FREEZE -> "Freeze";
            case CONTACT -> "Cactus / Berry Bush";
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "Explosion";
            case CRAMMING -> "Cramming";
            case DRAGON_BREATH -> "Dragon Breath";
            case FLY_INTO_WALL -> "Kinetic Energy";
            default -> capitalise(cause.name());
        };
    }

    private String capitalise(String name) {
        return Arrays.stream(name.split("_"))
                .map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
