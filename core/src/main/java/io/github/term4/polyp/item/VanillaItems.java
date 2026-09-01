package io.github.term4.polyp.item;

import net.minestom.server.item.Material;

/**
 * Vanilla item defs shared by the presets. Only the 1.8 weapon attack-damage table is stored: modern attack damage
 * derives from Minestom's {@code ATTACK_DAMAGE} attribute, armor from {@code ARMOR}.
 *
 * <p>These are 1.8 TOTALS: the player's 1.0 base {@code ATTACK_DAMAGE} plus the item's additive modifier
 * ({@code ItemSword} {@code 4+material}, axe {@code 3+}, pickaxe {@code 2+}, shovel {@code 1+}), which is what
 * {@code EntityHuman.attack} reads. So an iron sword is 7, not 6 - capture-confirmed on hypixel/scrims/minemen.
 * Netherite has no 1.8 material and just continues the pattern.
 */
// TODO: Add modern weapons / items
public final class VanillaItems {

    private VanillaItems() {}

    public static ItemDef[] weapons() {
        return new ItemDef[]{
                w(Material.WOODEN_SWORD, 5), w(Material.GOLDEN_SWORD, 5), w(Material.STONE_SWORD, 6),
                w(Material.IRON_SWORD, 7), w(Material.DIAMOND_SWORD, 8), w(Material.NETHERITE_SWORD, 9),

                w(Material.WOODEN_AXE, 4), w(Material.GOLDEN_AXE, 4), w(Material.STONE_AXE, 5),
                w(Material.IRON_AXE, 6), w(Material.DIAMOND_AXE, 7), w(Material.NETHERITE_AXE, 8),

                w(Material.WOODEN_PICKAXE, 3), w(Material.GOLDEN_PICKAXE, 3), w(Material.STONE_PICKAXE, 4),
                w(Material.IRON_PICKAXE, 5), w(Material.DIAMOND_PICKAXE, 6), w(Material.NETHERITE_PICKAXE, 7),

                w(Material.WOODEN_SHOVEL, 2), w(Material.GOLDEN_SHOVEL, 2), w(Material.STONE_SHOVEL, 3),
                w(Material.IRON_SHOVEL, 4), w(Material.DIAMOND_SHOVEL, 5), w(Material.NETHERITE_SHOVEL, 6),
        };
    }

    private static ItemDef w(Material material, double legacyDamage) {
        return ItemDef.of(material).legacy(ItemStat.ATTACK_DAMAGE, legacyDamage).build();
    }
}
