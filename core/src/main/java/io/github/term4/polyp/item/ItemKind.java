package io.github.term4.polyp.item;

import net.kyori.adventure.key.Key;
import net.minestom.server.item.ItemStack;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A stack's kind: the identity a use answers beyond its material, stamped as a key. A fire charge that throws as
 * Hypixel's fireball, a TNT that lights itself with MineMen's fuse; the config keyed by the kind carries the numbers.
 */
public final class ItemKind {

    public static final Tag<String> TAG = Tag.String("polyp:kind");

    private ItemKind() {}

    /** The stamped kind, or null for a plain stack. */
    public static @Nullable Key of(@Nullable ItemStack stack) {
        if (stack == null || stack.isAir()) return null;
        String kind = stack.getTag(TAG);
        return kind != null ? Key.key(kind) : null;
    }

    public static @NotNull ItemStack stamp(@NotNull ItemStack stack, @NotNull Key kind) {
        return stack.withTag(TAG, kind.asString());
    }

    public static boolean is(@Nullable ItemStack stack, @NotNull Key kind) {
        return kind.equals(of(stack));
    }

    /** The kind, else the material's own key. */
    public static @NotNull Key keyOf(@NotNull ItemStack stack) {
        Key kind = of(stack);
        return kind != null ? kind : stack.material().key();
    }
}
