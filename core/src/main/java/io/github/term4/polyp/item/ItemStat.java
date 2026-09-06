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

    private static final Map<String, ItemStat> BY_ID = new java.util.concurrent.ConcurrentHashMap<>();

    /** A player's {@code ATTACK_DAMAGE} base in both eras ({@code EntityHuman.initAttributes}); what a fist lands. */
    public static final double PLAYER_BASE = 1.0;

    /**
     * Held-weapon attack damage: the holder's attribute base plus the item's own {@code ATTACK_DAMAGE} modifiers,
     * <em>excluding</em> the holder's potion-effect modifiers - the melee calculator folds Strength/Weakness once
     * through the attribute system, so including them here would double-count. A stored value is the item's
     * MODIFIER ({@code ItemSword}: 4 + material; {@code ItemTool}: 3, 2, 1 + material for axe, pickaxe, shovel), the
     * way {@code ItemSword.i()} registers it on the attribute; the base is added on read, so an iron sword's 6 lands 7.
     */
    public static final ItemStat ATTACK_DAMAGE = new ItemStat("attack_damage", ItemStat::weaponAttackDamage, ItemStat::baseAnd);

    /** The stat named {@code id} ({@code items/<material>/<id>} paths), or {@code null}. */
    public static @Nullable ItemStat byId(String id) { return BY_ID.get(id); }

    public static java.util.Set<String> ids() { return java.util.Set.copyOf(BY_ID.keySet()); }

    public static java.util.Collection<ItemStat> all() { return java.util.List.copyOf(BY_ID.values()); }

    /** The holder's attribute base ({@link #PLAYER_BASE} without one) plus a stored modifier. */
    private static double baseAnd(double modifier, @Nullable LivingEntity holder) {
        AttributeInstance inst = holder != null ? holder.getAttribute(Attribute.ATTACK_DAMAGE) : null;
        return (inst != null ? inst.getBaseValue() : PLAYER_BASE) + modifier;
    }

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
    private final BiFunction<Double, @Nullable LivingEntity, Double> compose;

    private ItemStat(String id, BiFunction<ItemStack, @Nullable LivingEntity, Double> minestomDefault,
                     BiFunction<Double, @Nullable LivingEntity, Double> compose) {
        this.id = id;
        this.minestomDefault = minestomDefault;
        this.compose = compose;
        BY_ID.put(id, this);
    }

    /** What a stored value reads as for {@code holder}: the modifier composed with their base, for a weapon stat. */
    double compose(double stored, @Nullable LivingEntity holder) {
        return compose.apply(stored, holder);
    }

    public String id() { return id; }

    /** {@code NaN} when Minestom can't supply it (the caller's fallback then wins). */
    double minestomDefault(ItemStack item, @Nullable LivingEntity holder) {
        return minestomDefault.apply(item, holder);
    }

    @Override public String toString() { return "ItemStat[" + id + "]"; }
}
