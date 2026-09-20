package io.github.term4.polyp.platform.fixes;

import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.BlockFace;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * Reorders Minestom's block-update faces to vanilla's {@code NeighborUpdater.UPDATE_ORDER} (west, east, down,
 * up, north, south) in place - final pins the reference, not the elements.
 *
 * <p>OFF by default, and the reason is worth keeping: vanilla runs TWO passes, neighbour updates in that order
 * and SHAPE updates in {@code Block.UPDATE_SHAPE_ORDER} (west, east, north, south, down, up). Minestom has one
 * pass, whose array is the shape order and whose callback is {@code BlockPlacementRule.blockUpdate} - the shape
 * pass. So this makes support-dependent blocks resolve as vanilla's neighbour pass does, at the cost of the
 * shape pass no longer matching its own (Minestom#3356). {@link #restore} puts Minestom's order back.
 */
public final class BlockUpdateOrderFix {

    private static final BlockFace[] VANILLA_ORDER = {
            BlockFace.WEST, BlockFace.EAST, BlockFace.BOTTOM, BlockFace.TOP, BlockFace.NORTH, BlockFace.SOUTH
    };

    /** Minestom's own order: vanilla's {@code Block.UPDATE_SHAPE_ORDER}. */
    private static final BlockFace[] SHAPE_ORDER = {
            BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.BOTTOM, BlockFace.TOP
    };

    private BlockUpdateOrderFix() {}

    public static void install() {
        write(VANILLA_ORDER);
    }

    /** Minestom's shape order back in place - the static array outlives any one install. */
    public static void restore() {
        write(SHAPE_ORDER);
    }

    private static void write(BlockFace[] order) {
        try {
            Field field = InstanceContainer.class.getDeclaredField("BLOCK_UPDATE_FACES");
            field.setAccessible(true);
            BlockFace[] faces = (BlockFace[]) field.get(null);
            if (faces.length != order.length) throw new IllegalStateException("unexpected face count " + faces.length);
            System.arraycopy(order, 0, faces, 0, faces.length);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LoggerFactory.getLogger(BlockUpdateOrderFix.class)
                    .warn("block update order fix unavailable (upstream changed?): {}", e.toString());
        }
    }
}
