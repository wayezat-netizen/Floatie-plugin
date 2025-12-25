package com.floatie;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AxeThrowListener implements Listener {

    private static final String FLOATIE_AXE_KEY = "floatie_axe";
    private static final String AXE_TYPE_KEY = "axe_type";
    private static final String VISUAL_STAND_KEY = "visual_stand";
    private static final Set<Material> AXES = EnumSet.of(
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE,
            Material.NETHERITE_AXE
    );

    private final Floatie plugin;
    private final BlockCleaner blockCleaner;
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, ArmorStand> visualStands;

    public AxeThrowListener(Floatie plugin, BlockCleaner blockCleaner) {
        this.plugin = plugin;
        this.blockCleaner = blockCleaner;
        this.cooldowns = new ConcurrentHashMap<>();
        this.visualStands = new ConcurrentHashMap<>();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!isRightClick(event.getAction())) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("floatie.use")) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !AXES.contains(item.getType())) {
            return;
        }

        if (!blockCleaner.canClean()) {
            player.sendMessage(ChatColor.RED + "Server TPS too low! Axe throwing disabled.");
            return;
        }

        if (!checkCooldown(player)) {
            return;
        }

        event.setCancelled(true);
        throwAxe(player, item);
        updateCooldown(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball)) {
            return;
        }

        Snowball snowball = (Snowball) event.getEntity();

        if (!snowball.hasMetadata(FLOATIE_AXE_KEY)) {
            return;
        }

        if (snowball.hasMetadata(VISUAL_STAND_KEY)) {
            UUID standId = (UUID) snowball.getMetadata(VISUAL_STAND_KEY).get(0).value();
            ArmorStand stand = visualStands.remove(standId);
            if (stand != null && stand.isValid()) {
                stand.remove();
            }
        }

        if (event.getHitBlock() == null) {
            snowball.remove();
            return;
        }

        Player shooter = (Player) snowball.getShooter();
        blockCleaner.cleanBlocks(event.getHitBlock(), shooter);
        snowball.remove();
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean checkCooldown(Player player) {
        long cooldownMillis = plugin.getConfig().getLong("throw-cooldown", 1) * 1000;
        UUID playerId = player.getUniqueId();

        Long lastThrow = cooldowns.get(playerId);
        if (lastThrow == null) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - lastThrow;
        if (elapsed >= cooldownMillis) {
            return true;
        }

        long remainingSeconds = (cooldownMillis - elapsed) / 1000 + 1;
        player.sendMessage(ChatColor.RED + "Wait " + remainingSeconds + "s before throwing again!");
        return false;
    }

    private void updateCooldown(UUID playerId) {
        cooldowns.put(playerId, System.currentTimeMillis());
    }

    private void throwAxe(Player player, ItemStack axe) {
        double velocity = plugin.getConfig().getDouble("throw-velocity", 1.5);
        boolean showVisual = plugin.getConfig().getBoolean("visual-axe", true);

        Snowball projectile = player.launchProjectile(Snowball.class);
        projectile.setVelocity(player.getLocation().getDirection().multiply(velocity));
        projectile.setVisibleByDefault(false);
        projectile.setMetadata(FLOATIE_AXE_KEY, new FixedMetadataValue(plugin, true));
        projectile.setMetadata(AXE_TYPE_KEY, new FixedMetadataValue(plugin, axe.getType().name()));

        if (showVisual) {
            ArmorStand visual = createVisualAxe(projectile.getLocation(), axe.getType());
            UUID standId = UUID.randomUUID();
            visualStands.put(standId, visual);
            projectile.setMetadata(VISUAL_STAND_KEY, new FixedMetadataValue(plugin, standId));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!projectile.isValid() || projectile.isDead()) {
                        if (visual.isValid()) {
                            visual.remove();
                        }
                        visualStands.remove(standId);
                        cancel();
                        return;
                    }

                    Location loc = projectile.getLocation();
                    visual.teleport(loc);

                    Vector vel = projectile.getVelocity();
                    double pitch = Math.atan2(vel.getY(), Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ()));
                    visual.setHeadPose(new EulerAngle(pitch, 0, Math.PI / 4));
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        if (plugin.getConfig().getBoolean("consume-axe", true)) {
            consumeAxe(player, axe);
        }

        player.sendMessage(ChatColor.GREEN + "Axe thrown!");
    }

    private ArmorStand createVisualAxe(Location loc, Material axeType) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setCollidable(false);

        ItemStack axeItem = new ItemStack(axeType);
        stand.getEquipment().setHelmet(axeItem);
        stand.setHeadPose(new EulerAngle(0, 0, Math.PI / 4));

        return stand;
    }

    private void consumeAxe(Player player, ItemStack axe) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (axe.getAmount() > 1) {
            axe.setAmount(axe.getAmount() - 1);
        } else {
            player.getInventory().remove(axe);
        }
    }

    public void clearCooldown(UUID playerId) {
        cooldowns.remove(playerId);
    }

    public void clearAllCooldowns() {
        cooldowns.clear();
    }

    public void cleanup() {
        for (ArmorStand stand : visualStands.values()) {
            if (stand.isValid()) {
                stand.remove();
            }
        }
        visualStands.clear();
        cooldowns.clear();
    }
}