package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// item tags need the bound server
class LegacyZeroCountBridgeTest extends HeadlessServerTest {

    @Test
    void aZeroItemIsAStackOfOneCarryingTheMark() {
        ItemStack zero = LegacyZeroCountBridge.zero(ItemStack.of(Material.BREWING_STAND, 7));
        assertEquals(1, zero.amount(), "the modern wire cannot carry 0");
        assertTrue(LegacyZeroCountBridge.isZero(zero));
        assertFalse(LegacyZeroCountBridge.isZero(ItemStack.of(Material.BREWING_STAND)));
        assertFalse(LegacyZeroCountBridge.isZero(ItemStack.AIR));
    }

    @Test
    void theTransformedSlotLosesItsIdAndItsCount() {
        // 1.8 SET_SLOT 0x2F: window 5, slot 49, brewing stand (379) x1, damage 0, no tag
        byte[] transformed = {0x2F, 5, 0, 49, 0x01, 0x7B, 1, 0, 0, 0};
        byte[] body = LegacyZeroCountBridge.zeroed(transformed);
        assertArrayEquals(new byte[]{5, 0, 49, 0x01, 0x7B, 0, 0, 0, 0}, body);
    }

    @Test
    void anEmptySlotHasNoCountToPatch() {
        byte[] transformed = {0x2F, 5, 0, 49, (byte) 0xFF, (byte) 0xFF};
        assertNull(LegacyZeroCountBridge.zeroed(transformed));
    }
}
