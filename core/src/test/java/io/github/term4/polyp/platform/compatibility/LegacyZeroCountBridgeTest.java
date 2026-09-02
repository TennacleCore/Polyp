package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.CustomData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// item tags need the bound server
class LegacyZeroCountBridgeTest extends HeadlessServerTest {

    @Test
    void aZeroItemIsAStackOfOneCarryingTheBridgeKey() {
        ItemStack zero = LegacyZeroCountBridge.zero(ItemStack.of(Material.BREWING_STAND, 7));
        assertEquals(1, zero.amount(), "the modern wire cannot carry 0");
        assertTrue(LegacyZeroCountBridge.isZero(zero));
        // what the proxy reads after Via turns custom data into the 1.8 item tag
        CustomData data = zero.get(DataComponents.CUSTOM_DATA);
        assertEquals((byte) 0, data.nbt().getByte(LegacyZeroCountBridge.TAG));
        assertFalse(LegacyZeroCountBridge.isZero(ItemStack.of(Material.BREWING_STAND)));
        assertFalse(LegacyZeroCountBridge.isZero(ItemStack.AIR));
    }
}
