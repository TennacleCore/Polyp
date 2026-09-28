package io.github.term4.polyp.mechanics.consumable;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.hunger.HungerSystem;
import io.github.term4.polyp.presets.vanilla18.Consumables;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityStatuses;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket;
import net.minestom.server.potion.PotionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A watcher's client predicts a drink's remainder on status 9; the hand it is sent after that is the one it keeps. */
class ItemFinishViewTest extends HeadlessServerTest {

    @BeforeAll
    static void setup() {
        if (polyp.module(HungerSystem.class) == null) HungerSystem.install(polyp);
        if (polyp.module(ConsumableSystem.class) == null) ConsumableSystem.install(polyp, Consumables.config());
    }

    @Test
    void theHandFollowsTheFinishStatus() {
        FakePlayer drinker = FakePlayer.connect(instance, new Pos(70.5, 64, 70.5), "DrinkView");
        FakePlayer watcher = FakePlayer.connect(instance, new Pos(72.5, 64, 70.5), "DrinkWatch");
        try {
            assertTrue(drinker.player.getViewers().contains(watcher.player), "in view");
            Polyp.getInstance().clientInfo().setProtocol(watcher.player, 47); // a legacy watcher: equipment goes per viewer, so the harness sees it
            ItemStack potion = ItemStack.of(Material.POTION).with(DataComponents.POTION_CONTENTS, new PotionContents(PotionType.SWIFTNESS));
            drinker.player.setItemInMainHand(potion);
            watcher.sent.clear();
            EventDispatcher.call(new PlayerFinishItemUseEvent(drinker.player, PlayerHand.MAIN, potion, 32L));
            List<ItemStack> before = hands(watcher, drinker.player.getEntityId());
            drinker.player.triggerStatus((byte) EntityStatuses.Player.MARK_ITEM_FINISHED); // what Player.tick does after the event
            List<ItemStack> after = hands(watcher, drinker.player.getEntityId());
            assertTrue(after.size() > before.size(), "the hand goes out again behind the finish status");
            assertEquals(drinker.player.getItemInMainHand(), after.getLast(), "the hand as the server holds it");
        } finally {
            drinker.player.remove();
            watcher.player.remove();
        }
    }

    private static List<ItemStack> hands(FakePlayer watcher, int entityId) {
        return watcher.sent(EntityEquipmentPacket.class).stream()
                .filter(eq -> eq.entityId() == entityId && eq.equipments().containsKey(EquipmentSlot.MAIN_HAND))
                .map(eq -> eq.equipments().get(EquipmentSlot.MAIN_HAND)).toList();
    }
}
