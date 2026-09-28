package io.github.term4.polyp.mechanics.mobs.path;

import io.github.term4.polyp.world.MechanicsWorld;
import net.kyori.adventure.key.Key;
import net.minestom.server.collision.Shape;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.registry.RegistryTag;
import org.jetbrains.annotations.Nullable;

/** The 1.8 block classes the walker and the ray care about, read off the modern tags. */
public final class Blocks {

    private static final RegistryTag<Block> FENCES = tag("minecraft:fences");
    private static final RegistryTag<Block> FENCE_GATES = tag("minecraft:fence_gates");
    private static final RegistryTag<Block> WALLS = tag("minecraft:walls");
    private static final RegistryTag<Block> TRAPDOORS = tag("minecraft:trapdoors");
    private static final RegistryTag<Block> WOODEN_DOORS = tag("minecraft:wooden_doors");
    private static final RegistryTag<Block> DOORS = tag("minecraft:doors");
    private static final RegistryTag<Block> RAILS = tag("minecraft:rails");
    private static final RegistryTag<Block> CLIMBABLE = tag("minecraft:climbable");

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

    public static boolean lava(Block b) { return b.compare(Block.LAVA); }

    public static boolean fenceLike(Block b) { return in(FENCES, b) || in(FENCE_GATES, b) || in(WALLS, b); }

    public static boolean trapdoor(Block b) { return in(TRAPDOORS, b); }

    public static boolean woodenDoor(Block b) { return in(WOODEN_DOORS, b); }

    public static boolean door(Block b) { return in(DOORS, b); }

    public static boolean rail(Block b) { return in(RAILS, b); }

    public static boolean climbable(Block b) { return in(CLIMBABLE, b); }

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
}
