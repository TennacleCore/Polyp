package io.github.term4.polyp.item;

import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponent;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Reads an item's {@code ENCHANTMENTS} component: a level by {@link Key}, or whether any carries an effect. */
public final class Enchants {

    private Enchants() {}

    /** Level of {@code key} on {@code stack}, or {@code 0} if absent / air / unenchanted. */
    public static int level(@Nullable ItemStack stack, Key key) {
        if (stack == null || stack.isAir()) return 0;
        EnchantmentList list = stack.get(DataComponents.ENCHANTMENTS);
        if (list == null) return 0;
        for (Map.Entry<RegistryKey<Enchantment>, Integer> e : list.enchantments().entrySet()) {
            if (e.getKey().key().equals(key)) return e.getValue();
        }
        return 0;
    }

    /** Whether an enchantment on {@code stack} carries {@code effect}, as {@code EnchantmentHelper.has} reads it. */
    public static boolean has(@Nullable ItemStack stack, DataComponent<?> effect) {
        if (stack == null || stack.isAir()) return false;
        EnchantmentList list = stack.get(DataComponents.ENCHANTMENTS);
        if (list == null) return false;
        for (RegistryKey<Enchantment> key : list.enchantments().keySet()) {
            Enchantment enchantment = MinecraftServer.getEnchantmentRegistry().get(key);
            if (enchantment != null && enchantment.effects().has(effect)) return true;
        }
        return false;
    }
}
