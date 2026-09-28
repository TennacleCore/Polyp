package io.github.term4.polyp.mechanics.mobs.path;

import io.github.term4.polyp.world.MechanicsWorld;
import net.kyori.adventure.key.Key;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.Shape;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.registry.RegistryTag;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** The block classes the walkers and the ray care about, read off the modern tags. */
public final class Blocks {

    private static final RegistryTag<Block> FENCES = tag("minecraft:fences");
    private static final RegistryTag<Block> FENCE_GATES = tag("minecraft:fence_gates");
    private static final RegistryTag<Block> WALLS = tag("minecraft:walls");
    private static final RegistryTag<Block> TRAPDOORS = tag("minecraft:trapdoors");
    private static final RegistryTag<Block> WOODEN_DOORS = tag("minecraft:wooden_doors");
    private static final RegistryTag<Block> DOORS = tag("minecraft:doors");
    private static final RegistryTag<Block> RAILS = tag("minecraft:rails");
    private static final RegistryTag<Block> CLIMBABLE = tag("minecraft:climbable");
    private static final RegistryTag<Block> LEAVES = tag("minecraft:leaves");
    private static final RegistryTag<Block> FIRE = tag("minecraft:fire");
    private static final RegistryTag<Block> CAMPFIRES = tag("minecraft:campfires");
    private static final RegistryTag<Block> SLABS = tag("minecraft:slabs");
    private static final RegistryTag<Block> STAIRS = tag("minecraft:stairs");
    private static final RegistryTag<Block> BEDS = tag("minecraft:beds");
    private static final RegistryTag<Block> CAULDRONS = tag("minecraft:cauldrons");
    private static final RegistryTag<Block> ANVIL = tag("minecraft:anvil");
    private static final RegistryTag<Block> FLOWER_POTS = tag("minecraft:flower_pots");
    private static final RegistryTag<Block> CANDLE_CAKES = tag("minecraft:candle_cakes");
    private static final RegistryTag<Block> WALL_HANGING_SIGNS = tag("minecraft:wall_hanging_signs");
    // 26.1 isPathfindable(LAND) overrides to false, the ones a tag does not name
    private static final Set<Block> NEVER_PATHFINDABLE = Set.of(Block.BAMBOO, Block.BELL, Block.BREWING_STAND, Block.CACTUS,
            Block.CAKE, Block.IRON_CHAIN, Block.CHEST, Block.TRAPPED_CHEST, Block.ENDER_CHEST, Block.CHORUS_PLANT, Block.COCOA,
            Block.COMPOSTER, Block.CONDUIT, Block.DECORATED_POT, Block.DIRT_PATH, Block.DRAGON_EGG, Block.ENCHANTING_TABLE,
            Block.END_PORTAL_FRAME, Block.FARMLAND, Block.GRINDSTONE, Block.HEAVY_CORE, Block.HOPPER, Block.LANTERN,
            Block.SOUL_LANTERN, Block.LECTERN, Block.MUD, Block.POINTED_DRIPSTONE, Block.POWDER_SNOW, Block.RESPAWN_ANCHOR,
            Block.END_ROD, Block.LIGHTNING_ROD, Block.SCULK_SENSOR, Block.CALIBRATED_SCULK_SENSOR, Block.SEA_PICKLE,
            Block.SNIFFER_EGG, Block.SOUL_SAND, Block.STONECUTTER, Block.AZALEA, Block.FLOWERING_AZALEA);

    private Blocks() {}

    private static @Nullable RegistryTag<Block> tag(String key) {
        return Block.staticRegistry().getTag(Key.key(key));
    }

    private static boolean in(@Nullable RegistryTag<Block> tag, Block block) {
        return tag != null && tag.contains(block);
    }

    /** Air where the chunk is missing, as 1.8's ChunkCache reads outside its window. */
    public static Block at(MechanicsWorld world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return Block.AIR;
        Block b = world.getBlock(x, y, z, Block.Getter.Condition.TYPE);
        return b != null ? b : Block.AIR;
    }

    public static Block at(MechanicsWorld world, Point p) {
        return at(world, p.blockX(), p.blockY(), p.blockZ());
    }

    public static boolean water(Block b) { return b.compare(Block.WATER); }

    /** Water, or a block holding some (26.1's fluid state). */
    public static boolean waterish(Block b) { return water(b) || "true".equals(b.getProperty("waterlogged")); }

    public static boolean lava(Block b) { return b.compare(Block.LAVA); }

    public static boolean fenceLike(Block b) { return in(FENCES, b) || in(FENCE_GATES, b) || in(WALLS, b); }

    public static boolean trapdoor(Block b) { return in(TRAPDOORS, b); }

    public static boolean woodenDoor(Block b) { return in(WOODEN_DOORS, b); }

    public static boolean door(Block b) { return in(DOORS, b); }

    public static boolean rail(Block b) { return in(RAILS, b); }

    public static boolean climbable(Block b) { return in(CLIMBABLE, b); }

    private static boolean open(Block b) { return "true".equals(b.getProperty("open")); }

    /** 1.8 Block.isPassable: nothing to walk into. */
    public static boolean passable(Block b) {
        if (b.air() || b.liquid()) return true;
        Shape s = b.collisionShape();
        Point a = s.relativeStart(), e = s.relativeEnd();
        return e.x() - a.x() <= 0 || e.y() - a.y() <= 0 || e.z() - a.z() <= 0;
    }

    /** The liquid's surface height within its cell (1.8 getLiquidHeightPercent). */
    public static double liquidTop(Block b) {
        String level = b.getProperty("level");
        int meta = level != null ? Integer.parseInt(level) : 0;
        if (meta >= 8) meta = 0;
        return 1.0 - (meta + 1) / 9.0;
    }

    /** A collision shape filling the cell. */
    public static boolean fullCube(Block b) {
        Shape s = b.collisionShape();
        for (BlockFace face : BlockFace.values()) if (!s.isFaceFull(face)) return false;
        return true;
    }

    /** 26.1 isPathfindable(LAND): not a full cube, less the blocks that say no anyway. */
    public static boolean pathfindable(Block b) {
        if (water(b)) return true;
        if (lava(b) || in(SLABS, b) || in(STAIRS, b) || in(BEDS, b) || in(CAULDRONS, b) || in(ANVIL, b) || in(FLOWER_POTS, b)
                || in(CANDLE_CAKES, b) || in(WALL_HANGING_SIGNS, b) || in(CAMPFIRES, b) || in(WALLS, b) || in(FENCES, b)
                || NEVER_PATHFINDABLE.contains(b.defaultState())) return false;
        String name = b.key().value();
        if (name.endsWith("_pane") || name.equals("iron_bars") || name.endsWith("_head") || name.endsWith("_skull")) return false;
        if (b.compare(Block.SNOW)) return Integer.parseInt(b.getProperty("layers")) < 5;
        if (door(b) || trapdoor(b) || in(FENCE_GATES, b)) return open(b);
        return !fullCube(b);
    }

    private static boolean burning(Block b) {
        return in(FIRE, b) || lava(b) || b.compare(Block.MAGMA_BLOCK) || b.compare(Block.LAVA_CAULDRON)
                || in(CAMPFIRES, b) && "true".equals(b.getProperty("lit"));
    }

    /** 26.1 WalkNodeEvaluator.getPathTypeFromState: the cell on its own. */
    public static PathType pathType(Block b) {
        if (b.air()) return PathType.OPEN;
        if (trapdoor(b) || b.compare(Block.LILY_PAD) || b.compare(Block.BIG_DRIPLEAF)) return PathType.TRAPDOOR;
        if (b.compare(Block.POWDER_SNOW)) return PathType.POWDER_SNOW;
        if (b.compare(Block.CACTUS) || b.compare(Block.SWEET_BERRY_BUSH)) return PathType.DAMAGING;
        if (b.compare(Block.HONEY_BLOCK)) return PathType.STICKY_HONEY;
        if (b.compare(Block.COCOA)) return PathType.COCOA;
        if (b.compare(Block.WITHER_ROSE) || b.compare(Block.POINTED_DRIPSTONE)) return PathType.DAMAGE_CAUTIOUS;
        if (lava(b)) return PathType.LAVA;
        if (burning(b)) return PathType.FIRE;
        if (door(b)) {
            if (open(b)) return PathType.DOOR_OPEN;
            return b.compare(Block.IRON_DOOR) ? PathType.DOOR_IRON_CLOSED : PathType.DOOR_WOOD_CLOSED;
        }
        if (rail(b)) return PathType.RAIL;
        if (in(LEAVES, b)) return PathType.LEAVES;
        if (in(FENCES, b) || in(WALLS, b) || in(FENCE_GATES, b) && !open(b)) return PathType.FENCE;
        if (!pathfindable(b)) return PathType.BLOCKED;
        return waterish(b) ? PathType.WATER : PathType.OPEN;
    }

    /** 26.1 WalkNodeEvaluator.getFloorLevel: the top of what lies under the cell. */
    public static double floorLevel(MechanicsWorld world, int x, int y, int z) {
        Block below = at(world, x, y - 1, z);
        if (below.air() || below.liquid()) return y - 1;
        Shape s = below.collisionShape();
        Point a = s.relativeStart(), e = s.relativeEnd();
        boolean empty = e.x() - a.x() <= 0 || e.y() - a.y() <= 0 || e.z() - a.z() <= 0;
        return y - 1 + (empty ? 0.0 : e.y());
    }

    /** Whether any block's collision shape meets the box. */
    public static boolean collides(MechanicsWorld world, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        BoundingBox box = new BoundingBox(maxX - minX, maxY - minY, maxZ - minZ);
        double cx = (minX + maxX) / 2.0, cz = (minZ + maxZ) / 2.0;
        for (int x = (int) Math.floor(minX) - 1; x <= (int) Math.floor(maxX) + 1; x++) {
            for (int y = (int) Math.floor(minY) - 1; y <= (int) Math.floor(maxY) + 1; y++) {
                for (int z = (int) Math.floor(minZ) - 1; z <= (int) Math.floor(maxZ) + 1; z++) {
                    Block b = at(world, x, y, z);
                    if (b.air() || b.liquid()) continue;
                    if (b.collisionShape().intersectBox(new Vec(cx - x, minY - y, cz - z), box)) return true;
                }
            }
        }
        return false;
    }
}
