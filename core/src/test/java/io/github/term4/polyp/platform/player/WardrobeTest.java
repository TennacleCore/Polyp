package io.github.term4.polyp.platform.player;

import io.github.term4.polyp.platform.player.Wardrobe;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A kept world hands a body back on return; an outfitted world dresses every arrival. */
class WardrobeTest extends HeadlessServerTest {

    @Test
    void keptReturnsOutfitDresses() {
        Wardrobe.install();
        InstanceContainer home = flatInstance(null);
        InstanceContainer lobby = flatInstance(null);
        Wardrobe.keep(home);
        Wardrobe.outfit(lobby, p -> p.getInventory().setItemStack(0, ItemStack.of(Material.DIRT)));
        Player p = FakePlayer.connect(home, new Pos(0.5, 65, 0.5), "Dressed").player;
        try {
            p.getInventory().setItemStack(0, ItemStack.of(Material.STONE));
            p.setLevel(3);
            p.setInstance(lobby, new Pos(0.5, 65, 0.5)).join();
            assertEquals(Material.DIRT, p.getInventory().getItemStack(0).material(), "the lobby dressed the arrival");
            p.setLevel(0);
            p.setInstance(home, new Pos(0.5, 65, 0.5)).join();
            assertEquals(Material.STONE, p.getInventory().getItemStack(0).material(), "home kept the body");
            assertEquals(3, p.getLevel());
        } finally {
            p.remove();
            Wardrobe.forget(home);
            Wardrobe.forget(lobby);
        }
    }
}
