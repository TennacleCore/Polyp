package io.github.term4.polyp.mechanics.consumable;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.mechanics.hunger.HungerSystem;
import io.github.term4.polyp.platform.fixes.FixToggleConfig;
import io.github.term4.polyp.platform.fixes.FixesConfig;
import io.github.term4.polyp.presets.vanilla18.Consumables;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.EntityStatusPacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A 1.8 client adds the food itself on status 9 (ItemFood.onItemUseFinish), so 1.8's server sends it before feeding. */
class LegacyFoodOrderTest extends HeadlessServerTest {

    @BeforeAll
    static void install() {
        ConsumableSystem.install(polyp, Consumables.config());
        HungerSystem.install(polyp);
    }

    @Test
    void statusBeforeTheFood() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(60.5, 64, 60.5), "LegacyEater");
        polyp.clientInfo().setConnectionDetails(p.player, "{\"version\": 47}");
        polyp.profiles().setPlayer(p.player, MechanicsKeys.FIXES,
                FixesConfig.builder().legacyConsume(FixToggleConfig.on()).build());
        try {
            p.player.setGameMode(GameMode.SURVIVAL);
            p.player.setFood(10);
            p.player.getInventory().setItemStack(p.player.getHeldSlot(), ItemStack.of(Material.BREAD, 5));
            p.sent.clear();
            EventDispatcher.call(new PlayerFinishItemUseEvent(p.player, PlayerHand.MAIN, ItemStack.of(Material.BREAD, 5), 32));

            List<ServerPacket> order = p.sent.stream()
                    .map(s -> SendablePacket.extractServerPacket(ConnectionState.PLAY, s))
                    .filter(s -> s instanceof EntityStatusPacket || s instanceof UpdateHealthPacket).toList();
            int status = -1, fed = -1;
            for (int i = 0; i < order.size(); i++) {
                if (status < 0 && order.get(i) instanceof EntityStatusPacket e && e.status() == 9) status = i;
                if (fed < 0 && order.get(i) instanceof UpdateHealthPacket h && h.food() > 10) fed = i;
            }
            assertTrue(fed >= 0, "the bread fed");
            assertTrue(status >= 0 && status < fed, "status 9 first, or the client eats twice: " + order);
        } finally {
            polyp.profiles().setPlayer(p.player, null);
            p.player.remove();
        }
    }
}
