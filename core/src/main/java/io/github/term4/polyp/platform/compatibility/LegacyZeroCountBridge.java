package io.github.term4.polyp.platform.compatibility;

import net.minestom.server.item.ItemStack;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * A stack of zero on the 1.8 wire. The client draws a "0" over the icon; the modern wire cannot carry it (a count of
 * 0 is an empty slot), so an item marked {@link #zero} goes out as count 1 carrying {@value #TAG} = 0 in its custom
 * data, and the ViaBridge proxy plugin patches the 1.8 count inside ViaRewind's own translation. A modern client,
 * or a proxy without the plugin, keeps the lone icon.
 */
public final class LegacyZeroCountBridge {

    /** ViaBridge's item key: the count a legacy client should display. */
    public static final String TAG = "viabridge:legacy_count";
    private static final Tag<Byte> LEGACY_COUNT = Tag.Byte(TAG);

    private LegacyZeroCountBridge() {}

    /** {@code item} displayed as a stack of zero on 1.8 clients through the bridge; a lone icon elsewhere. */
    public static @NotNull ItemStack zero(@NotNull ItemStack item) {
        return item.withAmount(1).withTag(LEGACY_COUNT, (byte) 0);
    }

    public static boolean isZero(@NotNull ItemStack item) {
        if (item.isAir()) return false;
        Byte count = item.getTag(LEGACY_COUNT);
        return count != null && count == 0;
    }
}
