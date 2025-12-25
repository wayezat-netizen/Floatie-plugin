package com.floatie;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ProtectionManager {

    private final Floatie plugin;
    private boolean worldGuardEnabled;
    private boolean griefPreventionEnabled;
    private boolean townyEnabled;
    private boolean landsEnabled;

    public ProtectionManager(Floatie plugin) {
        this.plugin = plugin;
        detectPlugins();
    }

    private void detectPlugins() {
        worldGuardEnabled = isPluginEnabled("WorldGuard");
        griefPreventionEnabled = isPluginEnabled("GriefPrevention");
        townyEnabled = isPluginEnabled("Towny");
        landsEnabled = isPluginEnabled("Lands");

        if (worldGuardEnabled) {
            plugin.getLogger().info("✓ WorldGuard protection detected!");
        }
        if (griefPreventionEnabled) {
            plugin.getLogger().info("✓ GriefPrevention protection detected!");
        }
        if (townyEnabled) {
            plugin.getLogger().info("✓ Towny protection detected!");
        }
        if (landsEnabled) {
            plugin.getLogger().info("✓ Lands protection detected!");
        }

        if (!worldGuardEnabled && !griefPreventionEnabled && !townyEnabled && !landsEnabled) {
            plugin.getLogger().info("No protection plugins detected - running without protection checks");
        }
    }

    private boolean isPluginEnabled(String pluginName) {
        Plugin targetPlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return targetPlugin != null && targetPlugin.isEnabled();
    }

    public boolean canBreak(Player player, Location location) {
        if (player == null) {
            return false;
        }

        if (worldGuardEnabled && !checkWorldGuard(player, location)) {
            return false;
        }

        if (griefPreventionEnabled && !checkGriefPrevention(player, location)) {
            return false;
        }

        if (townyEnabled && !checkTowny(player, location)) {
            return false;
        }

        if (landsEnabled && !checkLands(player, location)) {
            return false;
        }

        return true;
    }

    private boolean checkWorldGuard(Player player, Location location) {
        try {
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");

            Object wgInstance = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = wgInstance.getClass().getMethod("getPlatform").invoke(wgInstance);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);

            Object adaptedLoc = adapterClass.getMethod("adapt", Location.class).invoke(null, location);
            Object wgPlugin = wgPluginClass.getMethod("inst").invoke(null);
            Object wrappedPlayer = wgPlugin.getClass().getMethod("wrapPlayer", Player.class).invoke(wgPlugin, player);
            Object blockBreakFlag = flagsClass.getField("BLOCK_BREAK").get(null);

            Boolean result = (Boolean) query.getClass()
                    .getMethod("testState",
                            Class.forName("com.sk89q.worldedit.util.Location"),
                            Class.forName("com.sk89q.worldguard.LocalPlayer"),
                            Class.forName("com.sk89q.worldguard.protection.flags.StateFlag"))
                    .invoke(query, adaptedLoc, wrappedPlayer, blockBreakFlag);

            return result != null ? result : true;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean checkGriefPrevention(Player player, Location location) {
        try {
            Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Object gpInstance = gpClass.getField("instance").get(null);
            Object dataStore = gpClass.getField("dataStore").get(gpInstance);

            Object claim = dataStore.getClass()
                    .getMethod("getClaimAt", Location.class, boolean.class,
                            Class.forName("me.ryanhamshire.GriefPrevention.Claim"))
                    .invoke(dataStore, location, false, null);

            if (claim != null) {
                String denial = (String) claim.getClass()
                        .getMethod("allowBreak", Player.class,
                                Class.forName("org.bukkit.Material"))
                        .invoke(claim, player, location.getBlock().getType());
                return denial == null;
            }
        } catch (Exception e) {
            return true;
        }
        return true;
    }

    private boolean checkTowny(Player player, Location location) {
        try {
            Class<?> townyAPIClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object townyAPI = townyAPIClass.getMethod("getInstance").invoke(null);

            Boolean isWilderness = (Boolean) townyAPIClass
                    .getMethod("isWilderness", Location.class)
                    .invoke(townyAPI, location);

            if (isWilderness != null && !isWilderness) {
                Object townBlock = townyAPIClass
                        .getMethod("getTownBlock", Location.class)
                        .invoke(townyAPI, location);

                if (townBlock != null) {
                    Class<?> actionTypeClass = Class.forName("com.palmergames.bukkit.towny.object.TownyPermission$ActionType");
                    Object destroyAction = actionTypeClass.getField("DESTROY").get(null);

                    Boolean allowed = (Boolean) townyAPIClass
                            .getMethod("isActionAllowedInLocation", Player.class, Location.class, actionTypeClass)
                            .invoke(townyAPI, player, location, destroyAction);

                    return allowed != null ? allowed : false;
                }
            }
        } catch (Exception e) {
            return true;
        }
        return true;
    }

    private boolean checkLands(Player player, Location location) {
        try {
            Class<?> landsIntegrationClass = Class.forName("me.angeschossen.lands.api.integration.LandsIntegration");
            Object landsAPI = landsIntegrationClass
                    .getMethod("of", Plugin.class)
                    .invoke(null, plugin);

            Object area = landsAPI.getClass()
                    .getMethod("getArea", Location.class)
                    .invoke(landsAPI, location);

            if (area != null) {
                Class<?> flagsClass = Class.forName("me.angeschossen.lands.api.flags.type.Flags");
                Object blockBreakFlag = flagsClass.getField("BLOCK_BREAK").get(null);

                Boolean hasFlag = (Boolean) area.getClass()
                        .getMethod("hasFlag", Player.class,
                                Class.forName("me.angeschossen.lands.api.flags.type.RoleFlag"),
                                boolean.class)
                        .invoke(area, player, blockBreakFlag, false);

                return hasFlag != null ? hasFlag : false;
            }
        } catch (Exception e) {
            return true;
        }
        return true;
    }
}