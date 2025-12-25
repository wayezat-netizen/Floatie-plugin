package com.floatie;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BlockCleaner {

    private static final Set<Material> LEAF_TYPES = EnumSet.of(
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
            Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES, Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES
    );

    private static final int[][] DIRECT_NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private final Floatie plugin;
    private final ProtectionManager protectionManager;
    private Set<Material> cleanableBlocks;
    private int maxBlocks;
    private boolean dropItems;
    private int blocksPerTick;
    private long removalDelayTicks;
    private boolean effectsEnabled;
    private Particle particleType;
    private boolean animatedRemoval;
    private int leafSearchRadius;
    private double minTPS;
    private boolean tpsCheckEnabled;
    private int maxChunkLoadRadius;

    public BlockCleaner(Floatie plugin) {
        this.plugin = plugin;
        this.protectionManager = new ProtectionManager(plugin);
        loadSettings();
    }

    public void loadSettings() {
        cleanableBlocks = loadCleanableBlocks();
        maxBlocks = plugin.getConfig().getInt("max-blocks", 500);
        dropItems = plugin.getConfig().getBoolean("drop-items", true);
        blocksPerTick = plugin.getConfig().getInt("performance.blocks-per-tick", 100);
        removalDelayTicks = Math.max(1L, plugin.getConfig().getInt("performance.removal-delay", 1));
        effectsEnabled = plugin.getConfig().getBoolean("effects.enabled", false);
        particleType = loadParticleType();
        animatedRemoval = plugin.getConfig().getBoolean("animated-removal", true);
        leafSearchRadius = plugin.getConfig().getInt("leaf-search-radius", 6);
        minTPS = plugin.getConfig().getDouble("performance.min-tps", 15.0);
        tpsCheckEnabled = plugin.getConfig().getBoolean("performance.tps-check-enabled", true);
        maxChunkLoadRadius = plugin.getConfig().getInt("performance.max-chunk-load-radius", 5);
    }

    private Set<Material> loadCleanableBlocks() {
        Set<Material> blocks = EnumSet.noneOf(Material.class);
        List<String> configBlocks = plugin.getConfig().getStringList("cleanable-blocks");

        for (String blockName : configBlocks) {
            try {
                blocks.add(Material.valueOf(blockName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid block type: " + blockName);
            }
        }

        return blocks.isEmpty() ? EnumSet.of(Material.OAK_LOG) : blocks;
    }

    private Particle loadParticleType() {
        try {
            String particleName = plugin.getConfig().getString("effects.particle-type", "CLOUD");
            return Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle type, using CLOUD");
            return Particle.CLOUD;
        }
    }

    public boolean canClean() {
        if (!tpsCheckEnabled) {
            return true;
        }
        return getCurrentTPS() >= minTPS;
    }

    public double getCurrentTPS() {
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Object tickTimes = server.getClass().getField("recentTps").get(server);
            return ((double[]) tickTimes)[0];
        } catch (Exception e) {
            return 20.0;
        }
    }

    public void cleanBlocks(Block hitBlock, Player player) {
        if (!cleanableBlocks.contains(hitBlock.getType())) {
            return;
        }

        if (!canClean()) {
            if (player != null) {
                player.sendMessage(ChatColor.RED + "Server TPS too low! Tree chopping disabled.");
            }
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!protectionManager.canBreak(player, hitBlock.getLocation())) {
                if (player != null) {
                    player.sendMessage(ChatColor.RED + "You cannot break blocks in this area!");
                }
                return;
            }

            List<BlockSnapshot> snapshots = new ArrayList<>();
            Set<Long> visited = new HashSet<>();

            List<Block> logs = findConnectedLogs(hitBlock, player, visited);
            if (logs.isEmpty()) {
                return;
            }

            for (Block log : logs) {
                if (snapshots.size() >= maxBlocks) break;
                snapshots.add(createSnapshot(log));
            }

            List<Block> leaves = findAttachedLeaves(logs, player, visited);
            for (Block leaf : leaves) {
                if (snapshots.size() >= maxBlocks) break;
                snapshots.add(createSnapshot(leaf));
            }

            if (animatedRemoval) {
                removeBlocksAnimated(snapshots, logs.get(0).getY());
            } else {
                removeBlocksImmediate(snapshots);
            }
        });
    }

    private List<Block> findConnectedLogs(Block startBlock, Player player, Set<Long> visited) {
        List<Block> connected = new ArrayList<>();
        Deque<Block> queue = new ArrayDeque<>();

        Material targetMaterial = startBlock.getType();
        queue.add(startBlock);
        visited.add(blockKey(startBlock));

        Location centerLoc = startBlock.getLocation();

        while (!queue.isEmpty() && connected.size() < maxBlocks) {
            Block current = queue.poll();

            if (centerLoc.distance(current.getLocation()) > maxChunkLoadRadius * 16) {
                continue;
            }

            if (!current.getChunk().isLoaded()) {
                continue;
            }

            if (!protectionManager.canBreak(player, current.getLocation())) {
                continue;
            }

            connected.add(current);

            for (int[] dir : DIRECT_NEIGHBORS) {
                Block neighbor = current.getRelative(dir[0], dir[1], dir[2]);

                if (neighbor.getType() != targetMaterial) {
                    continue;
                }

                long key = blockKey(neighbor);
                if (visited.add(key)) {
                    queue.add(neighbor);
                }
            }
        }

        return connected;
    }

    private List<Block> findAttachedLeaves(List<Block> logBlocks, Player player, Set<Long> visited) {
        if (logBlocks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Block> leaves = new ArrayList<>();
        Deque<Block> queue = new ArrayDeque<>();
        Location centerLoc = logBlocks.get(0).getLocation();

        for (Block log : logBlocks) {
            scanForLeaves(log, queue, visited, leafSearchRadius, centerLoc);
            if (leaves.size() >= maxBlocks) break;
        }

        while (!queue.isEmpty() && leaves.size() < maxBlocks) {
            Block current = queue.poll();

            if (!protectionManager.canBreak(player, current.getLocation())) {
                continue;
            }

            leaves.add(current);
            scanForLeaves(current, queue, visited, 1, centerLoc);
        }

        return leaves;
    }

    private void scanForLeaves(Block center, Deque<Block> queue, Set<Long> visited, int radius, Location origin) {
        for (int[] dir : DIRECT_NEIGHBORS) {
            for (int dist = 1; dist <= radius; dist++) {
                Block neighbor = center.getRelative(dir[0] * dist, dir[1] * dist, dir[2] * dist);

                if (origin.distance(neighbor.getLocation()) > maxChunkLoadRadius * 16) {
                    break;
                }

                if (!neighbor.getChunk().isLoaded()) {
                    break;
                }

                if (!LEAF_TYPES.contains(neighbor.getType())) {
                    break;
                }

                long key = blockKey(neighbor);
                if (visited.add(key)) {
                    queue.add(neighbor);
                }
            }
        }
    }

    private void removeBlocksAnimated(List<BlockSnapshot> snapshots, int lowestY) {
        snapshots.sort((s1, s2) -> {
            boolean s1IsLeaf = LEAF_TYPES.contains(s1.type);
            boolean s2IsLeaf = LEAF_TYPES.contains(s2.type);

            if (s1IsLeaf != s2IsLeaf) {
                return s1IsLeaf ? -1 : 1;
            }

            int yDiff1 = Math.abs(s1.y - lowestY);
            int yDiff2 = Math.abs(s2.y - lowestY);
            return Integer.compare(yDiff1, yDiff2);
        });

        new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                if (!canClean()) {
                    cancel();
                    return;
                }

                int toProcess = Math.min(blocksPerTick, snapshots.size() - index);

                if (toProcess <= 0) {
                    cancel();
                    return;
                }

                List<BlockSnapshot> batch = snapshots.subList(index, index + toProcess);
                index += toProcess;

                processBatch(batch);

                if (index >= snapshots.size()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, removalDelayTicks);
    }

    private void removeBlocksImmediate(List<BlockSnapshot> snapshots) {
        processBatch(snapshots);
    }

    private void processBatch(List<BlockSnapshot> batch) {
        for (BlockSnapshot snapshot : batch) {
            World world = Bukkit.getWorld(snapshot.worldName);
            if (world == null) {
                continue;
            }

            Block block = world.getBlockAt(snapshot.x, snapshot.y, snapshot.z);

            if (block.getType() != snapshot.type) {
                continue;
            }

            if (!cleanableBlocks.contains(block.getType()) && !LEAF_TYPES.contains(block.getType())) {
                continue;
            }

            removeBlock(block, snapshot.type);
        }
    }

    private void removeBlock(Block block, Material originalType) {
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);

        if (dropItems) {
            block.getDrops().forEach(drop -> block.getWorld().dropItemNaturally(loc, drop));
        }

        block.setType(Material.AIR, true);

        if (effectsEnabled) {
            spawnBreakEffect(loc, originalType);
            playBreakSound(loc);
        }
    }

    private void spawnBreakEffect(Location loc, Material blockType) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        world.spawnParticle(particleType, loc, 10, 0.25, 0.25, 0.25, 0.05);
    }

    private void playBreakSound(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        world.playSound(loc, Sound.BLOCK_WOOD_BREAK, 0.7f, 1.0f);
    }

    private BlockSnapshot createSnapshot(Block block) {
        return new BlockSnapshot(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                block.getType()
        );
    }

    private long blockKey(Block block) {
        return ((long) block.getX() & 0x7FFFFFF) |
                (((long) block.getY() & 0xFFF) << 27) |
                (((long) block.getZ() & 0x7FFFFFF) << 39);
    }

    private record BlockSnapshot(String worldName, int x, int y, int z, Material type) {}
}