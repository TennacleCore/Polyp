package io.github.term4.polyp.entity;

import io.github.term4.polyp.testsupport.HeadlessServerTest;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** An item that ends up inside a block leaves through the nearest open face - downward like any other. */
class BuriedItemTest extends HeadlessServerTest {

    private static DroppedItemEntity itemAt(Pos at) {
        DroppedItemEntity item = new DroppedItemEntity(ItemStack.of(Material.STONE), DroppedItemEntity.Model.LEGACY);
        item.setInstance(instance, at).join();
        return item;
    }

    private static void tick(DroppedItemEntity item) {
        item.update(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    /** Walled in on every side but the floor: vanilla shoves it down, and the up default would pin it forever. */
    @Test
    void aBuriedItemLeavesThroughTheFloor() {
        MechanicsWorld.of(instance);
        int x = 200, y = 70, z = 200;
        instance.setBlock(x, y, z, Block.STONE);       // the cell it sits in
        instance.setBlock(x - 1, y, z, Block.STONE);
        instance.setBlock(x + 1, y, z, Block.STONE);
        instance.setBlock(x, y + 1, z, Block.STONE);
        instance.setBlock(x, y, z - 1, Block.STONE);
        instance.setBlock(x, y, z + 1, Block.STONE);
        instance.setBlock(x, y - 1, z, Block.AIR);     // the one way out

        DroppedItemEntity item = itemAt(new Pos(x + 0.5, y + 0.4, z + 0.5));
        try {
            tick(item);
            assertTrue(item.getVelocity().y() < 0, "shoved toward the open floor, not held against the ceiling");
        } finally {
            item.remove();
            for (int dy = -1; dy <= 1; dy++) instance.setBlock(x, y + dy, z, Block.AIR);
            instance.setBlock(x - 1, y, z, Block.AIR);
            instance.setBlock(x + 1, y, z, Block.AIR);
            instance.setBlock(x, y, z - 1, Block.AIR);
            instance.setBlock(x, y, z + 1, Block.AIR);
        }
    }

    /** A wall beside it and open air below: the floor is nearer than the side, so it falls rather than slides. */
    @Test
    void anOpenFloorBeatsAWall() {
        MechanicsWorld.of(instance);
        int x = 210, y = 70, z = 210;
        instance.setBlock(x, y, z, Block.STONE);
        instance.setBlock(x + 1, y, z, Block.STONE); // the wall it is pressed against

        DroppedItemEntity item = itemAt(new Pos(x + 0.5, y + 0.05, z + 0.5));
        try {
            tick(item);
            assertTrue(item.getVelocity().y() < 0, "the near floor wins: down, not sideways");
        } finally {
            item.remove();
            instance.setBlock(x, y, z, Block.AIR);
            instance.setBlock(x + 1, y, z, Block.AIR);
        }
    }
}
