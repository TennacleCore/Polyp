package test.presets;

import io.github.term4.polyp.api.event.damage.FatalDamageEvent;
import io.github.term4.polyp.mechanics.attribute.catalog.enchant.Sharpness;
import io.github.term4.polyp.mechanics.damage.DamageSystem;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.polyp.presets.Preset;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.EventNode;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MineMen's overdamage rule: inside the i-frame window a CRIT with the same item as the opening hit never replaces
 * it - the extra is the crit roll's. Anything else that exceeds the opening hit still lands, a stronger weapon or the
 * same material carrying more. A blocked replacement is no kill: the fatal seam never fires for it.
 */
class Mmc18OverdamageTest extends HeadlessServerTest {

    private static final AtomicInteger fatal = new AtomicInteger();
    // a global listener that cancels fatal hits outlives this class on the shared server: scope it to our victims
    private static final Tag<Boolean> MINE = Tag.Transient("test:mmc18-overdamage-victim");
    private static final EventNode<net.minestom.server.event.Event> node = EventNode.all("mmc18-overdamage");

    @BeforeAll
    static void countFatal() {
        node.addListener(FatalDamageEvent.class, e -> {
            if (!Boolean.TRUE.equals(e.target().getTag(MINE))) return;
            fatal.incrementAndGet();
            e.setCancelled(true);
        });
        MinecraftServer.getGlobalEventHandler().addChild(node);
    }

    @AfterAll
    static void dropListener() {
        MinecraftServer.getGlobalEventHandler().removeChild(node);
    }

    private static LivingEntity mob(Instance inst, double x) {
        LivingEntity mob = new LivingEntity(EntityType.ZOMBIE);
        mob.setInstance(inst, new Pos(x, 65, 100.5)).join();
        mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.0); // a player's base, so the sword numbers are a player's
        mob.setHealth(20f);
        mob.setTag(MINE, true);
        return mob;
    }

    /** Opens a window with a plain wooden sword, drops the victim to 0.1 hp, then lands {@code second} inside it: the outcome and whether it was fatal. */
    private static Object[] openThen(ItemStack second, boolean critical) {
        Instance inst = flatInstance(Preset.MMC18.profile());
        LivingEntity attacker = mob(inst, 100.5);
        LivingEntity victim = mob(inst, 102.5);
        try {
            fatal.set(0);
            DamageSystem damage = services.damage();
            DamageSystem.DamageOutcome first = damage.apply(MeleeDamage.INSTANCE.snapshot(attacker, victim, false, ItemStack.of(Material.WOODEN_SWORD), services));
            assertEquals(DamageSystem.DamageOutcome.FRESH_DAMAGE, first, "the wooden sword opens the window");
            victim.setHealth(0.1f); // the window's highwater and opening item stay; any landed delta is now lethal
            DamageSystem.DamageOutcome outcome = damage.apply(MeleeDamage.INSTANCE.snapshot(attacker, victim, critical, second, services));
            return new Object[]{outcome, fatal.get(), victim.getHealth()};
        } finally {
            attacker.remove();
            victim.remove();
        }
    }

    @Test
    void sameSwordCritIsNoKill() {
        Object[] r = openThen(ItemStack.of(Material.WOODEN_SWORD), true);
        assertEquals(DamageSystem.DamageOutcome.BLOCKED, r[0], "the crit's 6 over the opening 4 is the roll's, not the weapon's");
        assertEquals(0, r[1], "no fatal seam for a replacement that never lands");
        assertEquals(0.1f, (Float) r[2], 1e-4);
    }

    @Test
    void strongerSwordStillReplaces() {
        Object[] r = openThen(ItemStack.of(Material.STONE_SWORD), false);
        assertEquals(DamageSystem.DamageOutcome.OVERDAMAGE, r[0], "stone's 5 over wood's 4 is the weapon's");
        assertEquals(1, r[1], "and it kills");
    }

    @Test
    void sameMaterialWithMoreStillReplaces() {
        Object[] r = openThen(enchanted(Material.WOODEN_SWORD, Sharpness.KEY, 5), false);
        assertEquals(DamageSystem.DamageOutcome.OVERDAMAGE, r[0], "the extra is Sharpness, not a crit");
        assertEquals(1, r[1]);
    }
}
