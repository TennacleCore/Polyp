package io.github.term4.polyp.platform.player;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.platform.compatibility.Compat18;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** A 1.8 profile takes the modern attack cooldown off the player, and switching back hands their base value over. */
class PlayerConfigApplierTest extends HeadlessServerTest {

    private static void scope(FakePlayer p, MechanicsProfile profile) {
        Polyp.getInstance().profiles().setPlayer(p.player, profile);
        PlayerConfigApplier.apply(Polyp.getInstance(), p.player);
    }

    @Test
    void a18ProfileRemovesTheAttackCooldown() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(500.5, 65, 500.5), "CooldownGone");
        try {
            AttributeInstance speed = p.player.getAttribute(Attribute.ATTACK_SPEED);
            assertNotNull(speed);
            speed.setBaseValue(4.0); // the vanilla base an app would leave alone
            scope(p, MechanicsProfile.builder().set(MechanicsKeys.COMPAT, Compat18.config()).build());
            assertEquals(1024.0, speed.getBaseValue(), 1.0e-6,
                    "the cooldown is always full: no sweep, no crosshair indicator");

            scope(p, MechanicsProfile.builder().set(MechanicsKeys.COMPAT, Compat18.off()).build());
            assertEquals(4.0, speed.getBaseValue(), 1.0e-6, "and switching back restores the base the app had set");
        } finally {
            Polyp.getInstance().profiles().setPlayer(p.player, null);
            p.player.remove();
        }
    }
}
