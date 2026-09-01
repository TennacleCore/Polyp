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
import java.util.concurrent.ThreadLocalRandom;

/**
 * One thing eating something does to the eater. A consumable's payload is a LIST of these, so a scope changes
 * what an item dishes out without replacing the item: bridge's apple is the vanilla golden apple with
 * {@code heal(full)} + a conditional absorption instead of regeneration + absorption. Same item, same eat
 * time, same edibility gate - different effects.
 *
 * @see ConsumableBehavior#payload(ConsumeEffect...)
 */
@FunctionalInterface
public interface ConsumeEffect {

    void apply(@NotNull ConsumableContext ctx);

    /** Restores food and saturation ({@code effectFood}'s nutrition half). */
    static @NotNull ConsumeEffect food(int nutrition, float saturation) {
        return ctx -> {
            HungerSystem hunger = ctx.services() != null ? ctx.services().hunger() : null;
            if (hunger != null) hunger.restore(ctx.user(), nutrition, saturation);
        };
    }

    /** A potion effect, applied at {@code chance} (1 = always). Level is 1-based, duration in ticks. */
    static @NotNull ConsumeEffect effect(@NotNull PotionEffect id, int level, int ticks, float chance) {
        return ctx -> {
            if (chance < 1f && ThreadLocalRandom.current().nextFloat() >= chance) return;
            VanillaPotions.addEffect(ctx.user(), new Potion(id, (byte) (level - 1), ticks,
                    ctx.particles().potionFlags()));
        };
    }

    /** Heals {@code hearts} of health, or everything when {@code hearts} is not positive. */
    static @NotNull ConsumeEffect heal(double hearts) {
        return ctx -> {
            Player user = ctx.user();
            float max = (float) user.getAttributeValue(Attribute.MAX_HEALTH);
            user.setHealth(hearts > 0 ? Math.min(max, user.getHealth() + (float) hearts) : max);
        };
    }

    /** Runs {@code effect} only when the eater does NOT already have {@code id} - no refresh, no stacking. */
    static @NotNull ConsumeEffect ifAbsent(@NotNull PotionEffect id, @NotNull ConsumeEffect effect) {
        return ctx -> {
            boolean present = ctx.user().getActiveEffects().stream()
                    .anyMatch(active -> active.potion().effect().equals(id));
            if (!present) effect.apply(ctx);
        };
    }

    /** Every listed effect, in order - the shape a payload built from data comes back as. */
    static @NotNull ConsumeEffect all(@NotNull ConsumeEffect... effects) {
        List<ConsumeEffect> list = List.of(effects);
        return ctx -> { for (ConsumeEffect e : list) e.apply(ctx); };
    }

    static void registerFactories() {
        FieldFns.register(ConsumeEffect.class, "food(nutrition, saturation)", "restores food and saturation",
                args -> food(args.arity(2).integer(0), args.flt(1)));
        FieldFns.register(ConsumeEffect.class, "effect(id, level, ticks)", "a potion effect, always applied",
                args -> effect(potion(args.arity(3), 0), args.integer(1), args.integer(2), 1f));
        FieldFns.register(ConsumeEffect.class, "chance-effect(id, level, ticks, chance)", "a potion effect at a chance",
                args -> effect(potion(args.arity(4), 0), args.integer(1), args.integer(2), args.flt(3)));
        FieldFns.register(ConsumeEffect.class, "heal(hearts)", "heals that much, or fully when 0 or less",
                args -> heal(args.arity(1).dbl(0)));
        FieldFns.register(ConsumeEffect.class, "if-absent(id, effect)", "the effect, only when that potion is missing",
                args -> ifAbsent(potion(args.arity(2), 0), args.of(1, ConsumeEffect.class)));
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
