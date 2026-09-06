package io.github.term4.polyp.item;

import net.minestom.server.item.Material;

/**
 * Vanilla item defs shared by the presets. Only the 1.8 weapon attack-damage table is stored: modern attack damage
 * derives from Minestom's {@code ATTACK_DAMAGE} attribute, armor from {@code ARMOR}.
 *
 * <p>The 1.8 values are the item's ATTACK_DAMAGE MODIFIER, as {@code ItemSword} ({@code 4 + material}) and
 * {@code ItemTool} ({@code 3, 2, 1 + material} for axe, pickaxe, shovel) register it; material damage is wood and
 * gold 0, stone 1, iron 2, diamond 3. {@code EntityHuman.attack} reads the attribute, the player's 1.0 base plus
 * that modifier, so an iron sword's 6 lands 7 - capture-confirmed on hypixel, scrims and minemen. Netherite has no
 * 1.8 material and continues the pattern.
 */
// TODO: Add modern weapons / items
public final class VanillaItems {

    private VanillaItems() {}

    public static ItemDef[] weapons() {
        return new ItemDef[]{
                w(Material.WOODEN_SWORD, 4), w(Material.GOLDEN_SWORD, 4), w(Material.STONE_SWORD, 5),
                w(Material.IRON_SWORD, 6), w(Material.DIAMOND_SWORD, 7), w(Material.NETHERITE_SWORD, 8),

                w(Material.WOODEN_AXE, 3), w(Material.GOLDEN_AXE, 3), w(Material.STONE_AXE, 4),
                w(Material.IRON_AXE, 5), w(Material.DIAMOND_AXE, 6), w(Material.NETHERITE_AXE, 7),

                w(Material.WOODEN_PICKAXE, 2), w(Material.GOLDEN_PICKAXE, 2), w(Material.STONE_PICKAXE, 3),
                w(Material.IRON_PICKAXE, 4), w(Material.DIAMOND_PICKAXE, 5), w(Material.NETHERITE_PICKAXE, 6),

                w(Material.WOODEN_SHOVEL, 1), w(Material.GOLDEN_SHOVEL, 1), w(Material.STONE_SHOVEL, 2),
                w(Material.IRON_SHOVEL, 3), w(Material.DIAMOND_SHOVEL, 4), w(Material.NETHERITE_SHOVEL, 5),
        };
    }

    private static ItemDef w(Material material, double legacyModifier) {
        return ItemDef.of(material).legacy(ItemStat.ATTACK_DAMAGE, legacyModifier).build();
    }
}
