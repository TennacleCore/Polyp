package io.github.term4.polyp.entity;

import io.github.term4.polyp.testsupport.HeadlessServerTest;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolypEntityTest extends HeadlessServerTest {

    /** 64 under the floor an item is gone whatever the item damage rules say, as both eras have it. */
    @Test
    void theVoidTakesAnItem() {
        instance.loadChunk(0, 0).join();
        DroppedItemEntity item = DroppedItemEntity.spawn(MechanicsWorld.of(instance),
                new Pos(8.5, instance.getCachedDimensionType().minY() - 70, 8.5), Vec.ZERO, ItemStack.of(Material.STONE), null, 10);
        assertNotNull(item);
        long deadline = System.currentTimeMillis() + 2000;
        while (item.getInstance() == null && System.currentTimeMillis() < deadline) Thread.onSpinWait();
        item.tick(0);
        assertTrue(item.isRemoved());
    }
}
