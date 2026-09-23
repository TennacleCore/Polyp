package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.mechanics.blocking.catalog.VanillaBlocking;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.tracking.ClientVersion;
import net.minestom.server.coordinate.Pos;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.AttackRange;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** View rewrites are client-only; {@link CompatState#sanitizeInboundItem} keeps a creative echo from becoming server state. */
class CompatStateTest extends HeadlessServerTest {

    private static ItemStack stampedSword() {
        return ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.ATTACK_RANGE, new AttackRange(0f, 3f, 0f, 5f, 0.1f, 1f));
    }

    @Test
    void sanitizeStripsEchoedStampForStampedClient() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        assertTrue(s.stampsAttackRange());
        assertNull(s.sanitizeInboundItem(stampedSword()).get(DataComponents.ATTACK_RANGE),
                "a stamped client's echoed attack_range is stripped");
    }

    @Test
    void sanitizeLeavesItemsAloneWhenNotStamping() {
        CompatState s = new CompatState();
        assertFalse(s.stampsAttackRange());
        assertNotNull(s.sanitizeInboundItem(stampedSword()).get(DataComponents.ATTACK_RANGE),
                "a non-stamped client keeps a legit attack_range");
    }

    private static ItemStack slotItem(CompatState s, ItemStack item) {
        return ((SetSlotPacket) s.rewriteItems(new SetSlotPacket(0, 0, (short) 36, item))).itemStack();
    }

    private static ItemStack fancySnowball() {
        return enchant(ItemStack.of(Material.SNOWBALL, 16)
                .withCustomName(Component.text("Feather"))
                .withLore(Component.text("Kit projectile")), Key.key("minecraft:power"), 2);
    }

    @Test
    void reskinsThrowableToNonUsableBaseInClientView() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        assertTrue(s.suppressesThrowSwing());
        ItemStack shown = slotItem(s, ItemStack.of(Material.SNOWBALL, 16));
        assertEquals(Material.PAPER, shown.material(), "the client sees a non-usable base (no throw swing)");
        assertEquals("minecraft:snowball", shown.get(DataComponents.ITEM_MODEL), "but it still renders as a snowball");
        assertEquals(16, shown.amount(), "count preserved");
        assertEquals(ItemStack.of(Material.SNOWBALL).get(DataComponents.ITEM_NAME), shown.get(DataComponents.ITEM_NAME),
                "and reads as a snowball, not \"Paper\"");
        assertEquals(16, shown.get(DataComponents.MAX_STACK_SIZE), "and stacks like a snowball, not paper's 64");
    }

    @Test
    void reskinPreservesRealNameLoreEnchants() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        ItemStack shown = slotItem(s, fancySnowball());
        assertEquals(Material.PAPER, shown.material());
        assertEquals(Component.text("Feather"), shown.get(DataComponents.CUSTOM_NAME), "the item's real name is kept (not overwritten)");
        assertEquals(1, shown.get(DataComponents.LORE).size(), "lore is kept");
        assertNotNull(shown.get(DataComponents.ENCHANTMENTS), "enchantments are kept");
    }

    @Test
    void restoresEchoedReskinToTrueItem() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        ItemStack restored = s.sanitizeInboundItem(slotItem(s, ItemStack.of(Material.SNOWBALL, 16)));
        assertEquals(Material.SNOWBALL, restored.material(), "a creative-echoed reskin becomes the true snowball again");
        assertNull(restored.get(DataComponents.ITEM_MODEL), "the reskin marker is cleared (renders as a plain snowball)");
        assertEquals(ItemStack.of(Material.SNOWBALL).get(DataComponents.ITEM_NAME), restored.get(DataComponents.ITEM_NAME), "and reads as a snowball");
        assertEquals(16, restored.amount());
    }

    @Test
    void restoreKeepsRealComponents() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        ItemStack restored = s.sanitizeInboundItem(slotItem(s, fancySnowball()));
        assertEquals(Material.SNOWBALL, restored.material());
        assertEquals(Component.text("Feather"), restored.get(DataComponents.CUSTOM_NAME), "a real name survives the creative round-trip");
        assertNotNull(restored.get(DataComponents.ENCHANTMENTS), "so do enchantments");
        assertNull(restored.get(DataComponents.ITEM_MODEL));
    }

    @Test
    void leavesThrowablesUnchangedWhenNotSuppressing() {
        CompatState s = new CompatState();
        assertFalse(s.suppressesThrowSwing());
        assertEquals(Material.SNOWBALL, slotItem(s, ItemStack.of(Material.SNOWBALL)).material(),
                "a modern client without the fix keeps the real snowball (and its vanilla throw swing)");
    }

    /** An Animatium client takes the 1.8 set natively: compensations that would double or conflict are excluded;
     *  harmless strips stay on (belt against a spoofed handshake). */
    @Test
    void animatiumClientExclusionsFollowTheHarmLine() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        s.setAnimatiumClient(true);
        assertFalse(s.stampsAttackRange());
        assertFalse(s.suppressesThrowSwing());
        assertFalse(s.fistRayHits());
        assertEquals(Material.SNOWBALL, slotItem(s, ItemStack.of(Material.SNOWBALL)).material());
        // NOT excluded: Animatium only RESTYLES a block pose, so without the stamp there is nothing to restyle
        assertTrue(s.swordBlockingPose());
        assertNotNull(slotItem(s, ItemStack.of(Material.DIAMOND_SWORD)).get(DataComponents.BLOCKS_ATTACKS));
        // NOT excluded: use_cooldown isn't an Animatium feature, and the glider strip matches its native disable
        assertTrue(s.stripsUseCooldowns());
        assertNull(slotItem(s, ItemStack.of(Material.ENDER_PEARL)).get(DataComponents.USE_COOLDOWN));
        assertTrue(s.stripsGlider());
        assertNull(slotItem(s, ItemStack.of(Material.ELYTRA)).get(DataComponents.GLIDER));
    }

    @Test
    void gliderStrippedFromViewAndRestoredOnEcho() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        ItemStack shown = slotItem(s, ItemStack.of(Material.ELYTRA));
        assertNull(shown.get(DataComponents.GLIDER), "the view carries no glider");
        assertEquals(Material.ELYTRA, shown.material(), "still an elytra (worn/rendered normally)");
        ItemStack restored = s.sanitizeInboundItem(shown);
        assertNotNull(restored.get(DataComponents.GLIDER), "an echoed strip never becomes a truly glide-less server item");
    }

    @Test
    void useCooldownStrippedFromViewAndRestoredOnEcho() {
        assertNotNull(ItemStack.of(Material.ENDER_PEARL).get(DataComponents.USE_COOLDOWN),
                "precondition: the pinned Minestom pearl prototype carries use_cooldown");
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        ItemStack shown = slotItem(s, ItemStack.of(Material.ENDER_PEARL));
        assertNull(shown.get(DataComponents.USE_COOLDOWN), "no client-self-applied cooldown (1.8 pearls spam-throw)");
        assertNotNull(s.sanitizeInboundItem(shown).get(DataComponents.USE_COOLDOWN), "the echo restores the prototype cooldown");
    }

    /** Modern-only item, but the swing suppression is universal - so it is in the reskin set too. */
    @Test
    void windChargeIsReskinned() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        assertEquals(Material.PAPER, slotItem(s, ItemStack.of(Material.WIND_CHARGE)).material());
    }

    /** The applier re-sends the inventory whenever this key changes, so every view-rewrite knob must move it. */
    @Test
    void itemViewKeyTracksEveryViewRewrite() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        var full = s.itemViewKey();
        s.apply(Compat18.config().toBuilder().swordBlockingPose(false).build(), null);
        assertNotEquals(full, s.itemViewKey(), "same margin, different sword pose -> re-send");
        s.apply(Compat18.config().toBuilder().removeUseCooldowns(false).build(), null);
        assertNotEquals(full, s.itemViewKey(), "same margin, different cooldown strip -> re-send");
        s.apply(Compat18.config(), null);
        assertEquals(full, s.itemViewKey(), "identical policy -> no re-send");
        s.apply(null, null);
        assertNotEquals(full, s.itemViewKey(), "compat dropped -> re-send (views revert)");
    }

    @Test
    void swordBlockPoseStampedAndStrippedOnEcho() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        ItemStack shown = slotItem(s, ItemStack.of(Material.DIAMOND_SWORD));
        assertNotNull(shown.get(DataComponents.BLOCKS_ATTACKS), "the client sees a blockable sword");
        assertNull(slotItem(s, ItemStack.of(Material.STONE)).get(DataComponents.BLOCKS_ATTACKS), "only swords");
        assertNull(s.sanitizeInboundItem(shown).get(DataComponents.BLOCKS_ATTACKS), "the echo never becomes server state");
    }

    /**
     * A modern client renders the third-person block pose off the held item's {@code blocks_attacks}
     * ({@code Item.getUseAnimation} -> BLOCK), so an unstamped sword means you never see anyone else blocking.
     */
    @Test
    void anotherPlayersSwordIsStampedSoTheBlockPoseRenders() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);

        var equipment = new EntityEquipmentPacket(7, Map.of(
                EquipmentSlot.MAIN_HAND, ItemStack.of(Material.DIAMOND_SWORD),
                EquipmentSlot.OFF_HAND, ItemStack.of(Material.STONE)));
        var shown = (EntityEquipmentPacket) s.rewriteItems(equipment);

        assertEquals(7, shown.entityId());
        assertNotNull(shown.equipments().get(EquipmentSlot.MAIN_HAND).get(DataComponents.BLOCKS_ATTACKS),
                "a viewer must see the component or the block pose never renders");
        assertNull(shown.equipments().get(EquipmentSlot.OFF_HAND).get(DataComponents.BLOCKS_ATTACKS), "only swords");
    }

    /** An opted-out sword stays unstamped for viewers too - it would pose a block the server refuses. */
    @Test
    void anOptedOutSwordIsNotStampedForViewers() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);

        ItemStack optedOut = VanillaBlocking.nonBlocking(ItemStack.of(Material.DIAMOND_SWORD));
        var shown = (EntityEquipmentPacket) s.rewriteItems(new EntityEquipmentPacket(7, Map.of(EquipmentSlot.MAIN_HAND, optedOut)));
        assertNull(shown.equipments().get(EquipmentSlot.MAIN_HAND).get(DataComponents.BLOCKS_ATTACKS));
    }

    /** A legacy client blocks natively and the component is junk through Via - leave its equipment alone. */
    @Test
    void aLegacyViewersEquipmentIsUntouched() {
        CompatState s = new CompatState();
        s.apply(Compat18.config(), null);
        s.setLegacyClient(true);

        var equipment = new EntityEquipmentPacket(7, Map.of(EquipmentSlot.MAIN_HAND, ItemStack.of(Material.DIAMOND_SWORD)));
        assertSame(equipment, s.rewriteItems(equipment), "no stamp for a 1.8 viewer");
    }

    /**
     * From a 26.2 server through Via: a 1.9 client got the stamp, could not read it, and ViaBackwards backed the
     * component up into custom_data. That tag made the stack unmatchable, so pick block built a new one in the
     * next hotbar slot instead of switching to the stack already there.
     */
    @Test
    void theStampSkipsClientsThatCannotReadIt() {
        FakePlayer old = FakePlayer.connect(instance, new Pos(0.5, 65, 1200.5), "StampOld");
        FakePlayer now = FakePlayer.connect(instance, new Pos(2.5, 65, 1200.5), "StampNow");
        try {
            Polyp polyp = Polyp.getInstance();
            polyp.clientInfo().setProtocol(old.player, 773);  // 1.21.9, the last without attack_range
            polyp.clientInfo().setProtocol(now.player, ClientVersion.ATTACK_RANGE_PROTOCOL);

            CompatState before = new CompatState();
            before.apply(Compat18.config(), old.player);
            assertFalse(before.stampsAttackRange(), "773 cannot read attack_range");
            assertNull(slotItem(before, ItemStack.of(Material.DIAMOND_SWORD)).get(DataComponents.ATTACK_RANGE),
                    "so nothing rides the item down for Via to back up");

            CompatState after = new CompatState();
            after.apply(Compat18.config(), now.player);
            assertTrue(after.stampsAttackRange(), "774 reads it natively");
            assertNotNull(slotItem(after, ItemStack.of(Material.DIAMOND_SWORD)).get(DataComponents.ATTACK_RANGE));

            // the bare-fist fill does NOT follow the stamp: the client that loses it is the one that needs it
            assertTrue(before.fistRayHits(), "still filled without the stamp");
            assertTrue(after.fistRayHits());
        } finally {
            old.player.remove();
            now.player.remove();
        }
    }

    /**
     * Same shape as the attack_range floor: {@code blocks_attacks} is 1.21.5, so stamping it below that only gives
     * Via something to back up. Here the knob IS the stamp - nothing reads it server-side - so the floor is the
     * catalog range, and {@code scopedTo} does the rest.
     */
    @Test
    void theBlockPoseSkipsClientsThatCannotReadIt() {
        FakePlayer old = FakePlayer.connect(instance, new Pos(0.5, 65, 1240.5), "PoseOld");
        FakePlayer now = FakePlayer.connect(instance, new Pos(2.5, 65, 1240.5), "PoseNow");
        try {
            Polyp polyp = Polyp.getInstance();
            polyp.clientInfo().setProtocol(old.player, ClientVersion.BLOCKS_ATTACKS_PROTOCOL - 1); // 1.21.4
            polyp.clientInfo().setProtocol(now.player, ClientVersion.BLOCKS_ATTACKS_PROTOCOL);

            CompatState before = new CompatState();
            before.apply(Compat18.config(), old.player);
            assertFalse(before.swordBlockingPose(), "1.21.4 cannot read blocks_attacks");
            assertNull(slotItem(before, ItemStack.of(Material.DIAMOND_SWORD)).get(DataComponents.BLOCKS_ATTACKS));

            CompatState after = new CompatState();
            after.apply(Compat18.config(), now.player);
            assertTrue(after.swordBlockingPose(), "1.21.5 reads it natively");
            assertNotNull(slotItem(after, ItemStack.of(Material.DIAMOND_SWORD)).get(DataComponents.BLOCKS_ATTACKS));
        } finally {
            old.player.remove();
            now.player.remove();
        }
    }

    /**
     * The reskin is not a bare material swap: it rides {@code item_model} (1.21.2) to keep the original's look.
     * Below that the component is backed up by Via and the client simply renders paper, which is worse than the
     * swing the knob exists to hide. A strip is different - see the glider and cooldown knobs, which stay at
     * MODERN because removing a component is what SPARES an old client the backup.
     */
    @Test
    void theThrowableReskinSkipsClientsThatWouldSeePaper() {
        FakePlayer old = FakePlayer.connect(instance, new Pos(0.5, 65, 1280.5), "SkinOld");
        FakePlayer now = FakePlayer.connect(instance, new Pos(2.5, 65, 1280.5), "SkinNow");
        try {
            Polyp polyp = Polyp.getInstance();
            polyp.clientInfo().setProtocol(old.player, ClientVersion.ITEM_MODEL_PROTOCOL - 1); // 1.21.1
            polyp.clientInfo().setProtocol(now.player, ClientVersion.ITEM_MODEL_PROTOCOL);

            CompatState before = new CompatState();
            before.apply(Compat18.config(), old.player);
            assertFalse(before.suppressesThrowSwing(), "1.21.1 cannot read item_model");
            assertEquals(Material.ENDER_PEARL, slotItem(before, ItemStack.of(Material.ENDER_PEARL)).material(),
                    "so the pearl stays a pearl instead of becoming paper");

            CompatState after = new CompatState();
            after.apply(Compat18.config(), now.player);
            assertTrue(after.suppressesThrowSwing());
            assertEquals(Material.PAPER, slotItem(after, ItemStack.of(Material.ENDER_PEARL)).material(),
                    "1.21.2 takes the reskin and reads the model back off item_model");
        } finally {
            old.player.remove();
            now.player.remove();
        }
    }
}
