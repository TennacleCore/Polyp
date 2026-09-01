package io.github.term4.polyp.mechanics.consumable;

import io.github.term4.polyp.config.FieldFns;
import io.github.term4.polyp.mechanics.attribute.catalog.VanillaPotions;
import io.github.term4.polyp.mechanics.consumable.ConsumableConfigResolver.ConsumableContext;
import io.github.term4.polyp.mechanics.hunger.HungerSystem;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <em>What</em> eating something does to the eater. A consumable's payload is a list of these, so a scope
 * changes what an item dishes out without replacing the item: bridge's apple is the vanilla golden apple
 * with a heal and a conditional absorption instead of regeneration + absorption. Same item, same eat time,
 * same edibility gate - different effects.
 *
 * <p>Whether an effect applies is {@link ConsumeCondition}'s axis, joined by {@link #when}: there is no
 * "chance" effect or "if absent" effect, because a probability and a missing potion are conditions that
 * work with every effect there is.
 *
 * @see ConsumableBehavior#payload(ConsumeEffect...)
 */
@FunctionalInterface
public interface ConsumeEffect {

    void apply(@NotNull ConsumableContext ctx);

    /** Restores food and saturation. */
    static @NotNull ConsumeEffect food(int nutrition, float saturation) {
        return ctx -> {
            HungerSystem hunger = ctx.services() != null ? ctx.services().hunger() : null;
            if (hunger != null) hunger.restore(ctx.user(), nutrition, saturation);
        };
    }

    /** A potion effect; level is 1-based, duration in ticks. */
    static @NotNull ConsumeEffect effect(@NotNull PotionEffect id, int level, int ticks) {
        return ctx -> VanillaPotions.addEffect(ctx.user(),
                new Potion(id, (byte) (level - 1), ticks, ctx.particles().potionFlags()));
    }

    /** Heals {@code hearts} of health, clamped to max - so any amount at or above max is a full heal. */
    static @NotNull ConsumeEffect heal(double hearts) {
        return ctx -> {
            Player user = ctx.user();
            float max = (float) user.getAttributeValue(Attribute.MAX_HEALTH);
            user.setHealth(Math.min(max, user.getHealth() + (float) hearts));
        };
    }

    /** {@code effect}, but only when {@code condition} holds. */
    static @NotNull ConsumeEffect when(@NotNull ConsumeCondition condition, @NotNull ConsumeEffect effect) {
        return ctx -> { if (condition.test(ctx)) effect.apply(ctx); };
    }

    /** Every listed effect, in order. */
    static @NotNull ConsumeEffect all(@NotNull ConsumeEffect... effects) {
        List<ConsumeEffect> list = List.of(effects);
        return ctx -> { for (ConsumeEffect e : list) e.apply(ctx); };
    }

    static void registerFactories() {
        ConsumeCondition.registerFactories();
        FieldFns.register(ConsumeEffect.class, "food(nutrition, saturation)", "restores food and saturation",
                args -> food(args.arity(2).integer(0), args.flt(1)));
        FieldFns.register(ConsumeEffect.class, "effect(id, level, ticks)", "applies a potion effect",
                args -> effect(potion(args.arity(3), 0), args.integer(1), args.integer(2)));
        FieldFns.register(ConsumeEffect.class, "heal(hearts)", "heals that much, clamped to max health",
                args -> heal(args.arity(1).dbl(0)));
        FieldFns.register(ConsumeEffect.class, "when(condition, effect)", "the effect, only when the condition holds",
                args -> when(args.arity(2).of(0, ConsumeCondition.class), args.of(1, ConsumeEffect.class)));
        FieldFns.register(ConsumeEffect.class, "all(effect...)", "every listed effect, in order", args -> {
            ConsumeEffect[] out = new ConsumeEffect[args.size()];
            for (int i = 0; i < out.length; i++) out[i] = args.of(i, ConsumeEffect.class);
            return all(out);
        });
    }

    private static PotionEffect potion(FieldFns.Args args, int i) {
        Key key = args.key(i);
        PotionEffect effect = PotionEffect.fromKey(key);
        if (effect == null) throw new IllegalArgumentException("unknown potion effect '" + key.asString() + "'");
        return effect;
    }
}
