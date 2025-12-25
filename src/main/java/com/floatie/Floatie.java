package com.floatie;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class Floatie extends JavaPlugin implements CommandExecutor {

    private static Floatie instance;
    private BlockCleaner blockCleaner;
    private AxeThrowListener axeThrowListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        blockCleaner = new BlockCleaner(this);
        axeThrowListener = new AxeThrowListener(this, blockCleaner);

        getServer().getPluginManager().registerEvents(axeThrowListener, this);

        if (getCommand("floatie") != null) {
            getCommand("floatie").setExecutor(this);
        } else {
            getLogger().severe("Command 'floatie' not found in plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Floatie v2.0 enabled successfully!");
        getLogger().info("Features: TPS Check, Protection Hooks, Visual Axes");
    }

    @Override
    public void onDisable() {
        if (axeThrowListener != null) {
            axeThrowListener.cleanup();
        }

        getServer().getScheduler().cancelTasks(this);
        getLogger().info("Floatie plugin disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendMainMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "status":
                return handleStatus(sender);
            case "clearcd":
                return handleClearCooldowns(sender);
            case "help":
                return handleHelp(sender);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /floatie help");
                return true;
        }
    }

    private void sendMainMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Floatie Plugin v2.0 ===");
        sender.sendMessage(ChatColor.YELLOW + "Version: " + getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Use /floatie help for commands");

        if (sender.hasPermission("floatie.admin")) {
            double tps = blockCleaner.getCurrentTPS();
            String tpsColor = tps >= 18 ? ChatColor.GREEN.toString() : tps >= 15 ? ChatColor.YELLOW.toString() : ChatColor.RED.toString();
            sender.sendMessage(ChatColor.GRAY + "Current TPS: " + tpsColor + String.format("%.2f", tps));
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("floatie.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        reloadConfig();
        blockCleaner.loadSettings();
        axeThrowListener.clearAllCooldowns();

        sender.sendMessage(ChatColor.GREEN + "Floatie configuration reloaded!");
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission("floatie.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        double tps = blockCleaner.getCurrentTPS();
        boolean canUse = blockCleaner.canClean();
        double minTPS = getConfig().getDouble("performance.min-tps", 15.0);
        boolean tpsCheckEnabled = getConfig().getBoolean("performance.tps-check-enabled", true);

        sender.sendMessage(ChatColor.GOLD + "=== Floatie Status ===");
        sender.sendMessage(ChatColor.YELLOW + "Current TPS: " + ChatColor.WHITE + String.format("%.2f", tps));
        sender.sendMessage(ChatColor.YELLOW + "Min TPS Required: " + ChatColor.WHITE + minTPS);
        sender.sendMessage(ChatColor.YELLOW + "TPS Check: " + (tpsCheckEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        sender.sendMessage(ChatColor.YELLOW + "Can Use Plugin: " + (canUse ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Visual Axes: " + (getConfig().getBoolean("visual-axe", true) ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        sender.sendMessage(ChatColor.YELLOW + "Effects: " + (getConfig().getBoolean("effects.enabled", false) ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));

        return true;
    }

    private boolean handleClearCooldowns(CommandSender sender) {
        if (!sender.hasPermission("floatie.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        axeThrowListener.clearAllCooldowns();
        sender.sendMessage(ChatColor.GREEN + "All player cooldowns cleared!");
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Floatie Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/floatie" + ChatColor.WHITE + " - Show plugin info and status");
        sender.sendMessage(ChatColor.YELLOW + "/floatie help" + ChatColor.WHITE + " - Show this help menu");

        if (sender.hasPermission("floatie.admin")) {
            sender.sendMessage(ChatColor.GOLD + "Admin Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/floatie reload" + ChatColor.WHITE + " - Reload configuration");
            sender.sendMessage(ChatColor.YELLOW + "/floatie status" + ChatColor.WHITE + " - Check detailed plugin status");
            sender.sendMessage(ChatColor.YELLOW + "/floatie clearcd" + ChatColor.WHITE + " - Clear all player cooldowns");
        }

        sender.sendMessage(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage(ChatColor.GRAY + "How to use: Right-click with an axe to throw it!");
        sender.sendMessage(ChatColor.GRAY + "The plugin will automatically chop connected logs and leaves.");
        return true;
    }

    public static Floatie getInstance() {
        return instance;
    }
}