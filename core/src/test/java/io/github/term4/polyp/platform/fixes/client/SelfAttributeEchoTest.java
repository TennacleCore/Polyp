package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.network.packet.server.play.EntityAttributesPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sprint toggle's attribute echo is the client's own prediction; a modifier pushed under the same input is not. */
class SelfAttributeEchoTest extends HeadlessServerTest {

    private static final AttributeModifier SLOW = new AttributeModifier(Key.key("polyp", "test.slow"), -0.3,
            AttributeOperation.ADD_MULTIPLIED_TOTAL);

    @Test
    void aSprintEchoStaysHome() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(40.5, 64, 40.5), "EchoSprint");
        try {
            OptimizedPlayer op = (OptimizedPlayer) p.player;
            p.sent.clear();
            // a fresh player's speed instance is born in the toggle: its first packet carries the player default
            op.suppressSelf(() -> op.setSprinting(true));
            op.suppressSelf(() -> op.setSprinting(false));
            assertTrue(p.sent(EntityAttributesPacket.class).isEmpty(),
                    "the client applied its own sprint boost: " + p.sent(EntityAttributesPacket.class));
        } finally {
            p.player.remove();
        }
    }

    @Test
    void aPushedModifierReachesTheClient() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(40.5, 64, 40.5), "EchoSlow");
        try {
            OptimizedPlayer op = (OptimizedPlayer) p.player;
            op.suppressSelf(() -> op.setSprinting(true));
            p.sent.clear();
            op.suppressSelf(() -> op.getAttribute(Attribute.MOVEMENT_SPEED).addModifier(SLOW));
            List<EntityAttributesPacket> sent = p.sent(EntityAttributesPacket.class);
            assertEquals(1, sent.size(), "the slowdown is the server's to send");
            assertTrue(sent.getFirst().properties().getFirst().modifiers().contains(SLOW));
            p.sent.clear();
            op.suppressSelf(() -> op.setSprinting(false));
            assertTrue(p.sent(EntityAttributesPacket.class).isEmpty(), "the stop that follows is still an echo");
            op.suppressSelf(() -> op.getAttribute(Attribute.MOVEMENT_SPEED).removeModifier(SLOW));
            assertEquals(1, p.sent(EntityAttributesPacket.class).size(), "and so is the slowdown's end");
        } finally {
            p.player.remove();
        }
    }
}
