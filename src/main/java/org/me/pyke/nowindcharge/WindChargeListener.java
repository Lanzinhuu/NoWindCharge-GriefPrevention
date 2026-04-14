package org.me.pyke.nowindcharge;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class WindChargeListener implements Listener {

    private final NoWindCharge plugin;

    public WindChargeListener(NoWindCharge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerUseWindCharge(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        // Check if neither hand has WIND_CHARGE OR the action is not a right-click
        if ((mainHandItem.getType() != Material.WIND_CHARGE && offHandItem.getType() != Material.WIND_CHARGE) ||
                (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        if (plugin.getConfig().getBoolean("disable-completely", false)) {
            event.setCancelled(true);
            String message = plugin.getConfig().getString("completely-disabled-message", "&cWIND_CHARGE usage is completely disabled!");
            player.sendMessage(plugin.formatMessage(message));
            return;
        }

        if (player.hasPermission("nowindcharge.bypass")) {
            return;
        }

        // --- Verificação WorldGuard ---
        if (isInExcludedRegion(player)) {
            event.setCancelled(true);
            String message = plugin.getConfig().getString("deny-message", "&cYou cannot use this item in region &e%region%&c!");
            player.sendMessage(plugin.formatMessage(message.replace("%region%", getRegionName(player))));
            return;
        }

        // --- Verificação GriefPrevention ---
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("GriefPrevention") != null) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), true, null);
            if (claim != null) {
                // Verifica se o jogador tem permissão de construção (ou uso de itens) no claim
                String noBuildReason = claim.allowBuild(player, Material.WIND_CHARGE);
                if (noBuildReason != null) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.formatMessage("&cVocê não tem permissão para usar Carga de Vento neste terreno protegido!"));
                }
            }
        }
    }

    @EventHandler
    public void onWindChargeExplosion(ExplosionPrimeEvent event) {
        if (plugin.getConfig().getBoolean("disable-completely", false)) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity().getType() == EntityType.WIND_CHARGE || event.getEntity().getType() == EntityType.BREEZE_WIND_CHARGE) {
            for (Player player : event.getEntity().getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(event.getEntity().getLocation()) < 16) {
                    
                    // Se estiver em região do WorldGuard
                    if (isInExcludedRegion(player)) {
                        event.setCancelled(true);
                        return;
                    }

                    // Se estiver em terreno do GriefPrevention
                    if (org.bukkit.Bukkit.getPluginManager().getPlugin("GriefPrevention") != null) {
                        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(event.getEntity().getLocation(), true, null);
                        if (claim != null && claim.allowBuild(player, Material.WIND_CHARGE) != null) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean isInExcludedRegion(Player player) {
        List<String> excludedRegions = plugin.getConfig().getStringList("excluded-regions");

        RegionManager regionManager = com.sk89q.worldguard.WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(player.getWorld()));

        if (regionManager == null) return false;

        ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(player.getLocation()));

        for (ProtectedRegion region : regions) {
            if (excludedRegions.contains(region.getId())) {
                return true;
            }
        }

        return false;
    }

    private String getRegionName(Player player) {
        RegionManager regionManager = com.sk89q.worldguard.WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(player.getWorld()));

        if (regionManager == null) return "unknown";

        ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(player.getLocation()));

        for (ProtectedRegion region : regions) {
            return region.getId();
        }

        return "unknown";
    }
}
