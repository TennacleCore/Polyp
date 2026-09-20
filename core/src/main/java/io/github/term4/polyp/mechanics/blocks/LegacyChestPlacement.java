package io.github.term4.polyp.mechanics.blocks;

import io.github.term4.polyp.util.BlockContact;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 1.8's {@code BlockChest}, the placement half. {@link #canPlace} is {@code canPlaceBlockAt}: beside two chests,
 * or beside one already paired, nothing lands - the 1.8 client pairs by adjacency and would draw a half where no
 * block is. {@link #place} is the sequence after it: the block lands carrying the LOOK, {@code onPlace} runs
 * {@code checkForSurroundingChests} on it and then on its chest neighbour, and {@code postPlace} lands last with the
 * look's opposite - on both halves when the join crosses it, on neither otherwise. The modern {@code type} is set
 * alongside, so a modern viewer draws the same pair.
 */
public final class LegacyChestPlacement {

    private LegacyChestPlacement() {}

    public static boolean isChest(Block block) {
        return block.compare(Block.CHEST) || block.compare(Block.TRAPPED_CHEST);
    }

    /** Trapped and plain never count for each other. */
    static boolean canPlace(Block.Getter world, Point at, Block placing) {
        int beside = 0;
        for (Direction side : Direction.HORIZONTAL) {
            Point next = at.add(side.normalX(), 0, side.normalZ());
            if (!world.getBlock(next).compare(placing)) continue;
            beside++;
            for (Direction far : Direction.HORIZONTAL) {
                Point partner = next.add(far.normalX(), 0, far.normalZ());
                if (!partner.sameBlock(at) && world.getBlock(partner).compare(placing)) return false;
            }
        }
        return beside <= 1;
    }

    /** The state {@code placing} lands in at {@code at} for a placer looking along {@code yaw}; a chest beside it is
     *  rewritten through {@code setter} to the other half. */
    static Block place(Block.Getter world, Block.Setter setter, Point at, Block placing, float yaw) {
        Direction placed = horizontal(yaw).opposite();
        Block mine = half(placing, placed, "single");
        Direction join = null;
        for (Direction side : Direction.HORIZONTAL) {
            if (world.getBlock(at.add(side.normalX(), 0, side.normalZ())).compare(placing)) join = side;
        }
        if (join == null) return mine;
        Point other = at.add(join.normalX(), 0, join.normalZ());
        Block theirs = world.getBlock(other);
        if (!"single".equals(theirs.getProperty("type"))) return mine;

        Direction facing = surrounding(world, at, join, facingOf(theirs));
        // postPlace has no branch for a join along the placer's facing: then e()'s answer stands, for both halves
        if (crosses(placed, join)) facing = placed;

        boolean otherOnMyLeft = join == clockwise(facing);
        setter.setBlock(other, half(theirs, facing, otherOnMyLeft ? "right" : "left"));
        return half(mine, facing, otherOnMyLeft ? "left" : "right");
    }

    /** {@code EnumFacing.getHorizontal(floor(yaw * 4 / 360 + 0.5) & 3)}. */
    static Direction horizontal(float yaw) {
        return switch (((int) Math.floor(yaw * 4.0f / 360.0f + 0.5) & 3)) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    private static Block half(Block chest, Direction facing, String type) {
        return chest.withProperty("facing", facing.name().toLowerCase(Locale.ROOT)).withProperty("type", type);
    }

    /**
     * {@code checkForSurroundingChests}: the join's axis picks the default - SOUTH for a pair joined along x, EAST
     * along z - the partner's facing can pull it to the other side, and a full block against one side with open air
     * against the other overrides both. Never reads the chest's own facing.
     */
    private static Direction surrounding(Block.Getter world, Point at, Direction toPartner, @Nullable Direction partnerFacing) {
        boolean alongZ = toPartner.normalZ() != 0;
        Direction positive = alongZ ? Direction.EAST : Direction.SOUTH;
        Direction negative = alongZ ? Direction.WEST : Direction.NORTH;
        Direction facing = partnerFacing == negative ? negative : positive;
        Point partner = at.add(toPartner.normalX(), 0, toPartner.normalZ());
        boolean negativeBlocked = fullBlock(world, at, negative) || fullBlock(world, partner, negative);
        boolean positiveBlocked = fullBlock(world, at, positive) || fullBlock(world, partner, positive);
        if (negativeBlocked && !positiveBlocked) facing = positive;
        if (positiveBlocked && !negativeBlocked) facing = negative;
        return facing;
    }

    private static boolean crosses(Direction facing, Direction join) {
        return facing.normalX() * join.normalX() + facing.normalZ() * join.normalZ() == 0;
    }

    // Block.isFullBlock: isOpaqueCube read once in the constructor - opaque AND a whole cube, so a slab, stair or
    // pane turns nothing
    private static boolean fullBlock(Block.Getter world, Point from, Direction side) {
        Block block = world.getBlock(from.add(side.normalX(), 0, side.normalZ()));
        return block.occludes() && BlockContact.isFullCube(block);
    }

    private static Direction clockwise(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    private static @Nullable Direction facingOf(Block chest) {
        String facing = chest.getProperty("facing");
        return facing == null ? null : Direction.valueOf(facing.toUpperCase(Locale.ROOT));
    }
}
