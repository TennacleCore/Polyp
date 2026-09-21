package io.github.term4.polyp.item;

import net.kyori.adventure.nbt.BinaryTag;
import net.minestom.server.MinecraftServer;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.item.ItemStack;
import net.minestom.server.registry.RegistryTranscoder;
import org.jetbrains.annotations.NotNull;

/**
 * Item NBT for the world-save descriptors. The bare {@link Transcoder#NBT} carries no registries, so any
 * registry-keyed component (an enchantment, a trim) fails to encode at all.
 */
public final class ItemNbt {

    private ItemNbt() {}

    private static Transcoder<BinaryTag> transcoder() {
        return new RegistryTranscoder<>(Transcoder.NBT, MinecraftServer.getRegistries());
    }

    public static @NotNull BinaryTag encode(@NotNull ItemStack item) {
        return ItemStack.CODEC.encode(transcoder(), item).orElseThrow();
    }

    public static @NotNull ItemStack decode(@NotNull BinaryTag tag) {
        return ItemStack.CODEC.decode(transcoder(), tag).orElseThrow();
    }
}
