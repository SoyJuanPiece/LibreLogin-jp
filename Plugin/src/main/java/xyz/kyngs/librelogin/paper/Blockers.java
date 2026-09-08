/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;
import xyz.kyngs.librelogin.api.server.ServerHandler;
import xyz.kyngs.librelogin.common.config.HoconPluginConfiguration;

import java.util.Locale;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.ALLOWED_COMMANDS_WHILE_UNAUTHORIZED;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.BACKEND_BLINDNESS_EFFECT;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.BACKEND_LOCKDOWN_ENABLED;

public class Blockers implements Listener {

    private static final PotionEffect BLINDNESS = new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false);
    private final AuthorizationProvider<Player> authorizationProvider;
    private final HoconPluginConfiguration configuration;
    private final ServerHandler<Player, World> serverHandler;

    public Blockers(PaperLibreLogin plugin) {
        this.authorizationProvider = plugin.getAuthorizationProvider();
        this.configuration = plugin.getConfiguration();
        this.serverHandler = plugin.getServerHandler();
        plugin.getEventProvider().subscribe(plugin.getEventProvider().getTypes().authenticated, event -> {
            var player = event.getPlayer();
            if (player != null) clearLoginEffects(player);
        });
    }

    private <E extends PlayerEvent & Cancellable> void cancelIfNeeded(E event) {
        cancelIfNeeded(event.getPlayer(), event);
    }

    private void cancelIfNeeded(Player player, Cancellable cancellable) {
        if (inLimbo(player) && configuration.get(BACKEND_LOCKDOWN_ENABLED)) {
            cancellable.setCancelled(true);
        }
    }

    private void applyLoginEffects(Player player) {
        player.setInvisible(true);
        if (configuration.get(BACKEND_BLINDNESS_EFFECT)) {
            player.addPotionEffect(BLINDNESS, true);
        }
    }

    private void clearLoginEffects(Player player) {
        player.setInvisible(false);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    private boolean inLimbo(Player player) {
        return !authorizationProvider.isAuthorized(player) || authorizationProvider.isAwaiting2FA(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (inLimbo(event.getPlayer())) {
            event.setCancelled(true);
        } else {
            if (serverHandler.getLimboServers().contains(event.getTo().getWorld()) && !event.getPlayer().hasPermission("librelogin.limbo.access")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (authorizationProvider.isAuthorized(event.getPlayer()) && !authorizationProvider.isAwaiting2FA(event.getPlayer()))
            return;

        var command = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);

        for (String allowed : configuration.get(ALLOWED_COMMANDS_WHILE_UNAUTHORIZED)) {
            if (command.equals(allowed.toLowerCase(Locale.ROOT))) return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        if (inLimbo(event.getPlayer())) {
            applyLoginEffects(event.getPlayer());
        }
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        cancelIfNeeded(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelIfNeeded(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (inLimbo(event.getPlayer())) {
            applyLoginEffects(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemHeld(PlayerItemHeldEvent event) {
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        clearLoginEffects(event.getPlayer());
    }

}
