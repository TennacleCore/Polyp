package io.github.term4.polyp.item;

import net.minestom.server.component.DataComponents;
import java.util.Map;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeInstance;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.AttributeList;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * A named, extensible item-stat key for {@link ItemRegistry} lookups. Each stat also knows how to derive its value from
 * Minestom ({@code minestomDefault}), so an unregistered item falls back to what Minestom computes rather than a
 * duplicated table.
 */
public final class ItemStat {

    /**
     * Held-weapon attack damage: attribute base + the item's own {@code ATTACK_DAMAGE} modifiers, <em>excluding</em> the
     * holder's potion-effect modifiers - the melee calculator folds Strength/Weakness once through the attribute system,
     * so including them here would double-count (unlike a fist, which uses the effect-free fallback).
     */
    private static final Map<String, ItemStat> BY_ID = new java.util.concurrent.ConcurrentHashMap<>();

    public static final ItemStat ATTACK_DAMAGE = new ItemStat("attack_damage", ItemStat::weaponAttackDamage);

    /** The stat named {@code id} ({@code items/<material>/<id>} paths), or {@code null}. */
    public static @Nullable ItemStat byId(String id) { return BY_ID.get(id); }

    public static java.util.Set<String> ids() { return java.util.Set.copyOf(BY_ID.keySet()); }

    public static java.util.Collection<ItemStat> all() { return java.util.List.copyOf(BY_ID.values()); }

    private static double weaponAttackDamage(ItemStack item, @Nullable LivingEntity holder) {
        if (holder == null) return Double.NaN;
        AttributeInstance inst = holder.getAttribute(Attribute.ATTACK_DAMAGE);
        if (inst == null) return Double.NaN;
        double base = inst.getBaseValue(), add = 0, multBase = 0, multTotal = 1;
        AttributeList list = item.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (list != null) for (AttributeList.Modifier m : list.modifiers()) {
            if (!m.attribute().equals(Attribute.ATTACK_DAMAGE)) continue;
            AttributeModifier mod = m.modifier();
            switch (mod.operation()) {
                case ADD_VALUE -> add += mod.amount();
                case ADD_MULTIPLIED_BASE -> multBase += mod.amount();
                case ADD_MULTIPLIED_TOTAL -> multTotal *= (1 + mod.amount());
            }
        }
        return (base + add) * (1 + multBase) * multTotal;
    }

    // TODO: ATTACK_SPEED, MINING_SPEED, ... as the attributes come online

    private final String id;
    private final BiFunction<ItemStack, @Nullable LivingEntity, Double> minestomDefault;

    private ItemStat(String id, BiFunction<ItemStack, @Nullable LivingEntity, Double> minestomDefault) {
        this.id = id;
        this.minestomDefault = minestomDefault;
        BY_ID.put(id, this);
    }

    public String id() { return id; }

    /** {@code NaN} when Minestom can't supply it (the caller's fallback then wins). */
    double minestomDefault(ItemStack item, @Nullable LivingEntity holder) {
        return minestomDefault.apply(item, holder);
    }

    @Override public String toString() { return "ItemStat[" + id + "]"; }
}
