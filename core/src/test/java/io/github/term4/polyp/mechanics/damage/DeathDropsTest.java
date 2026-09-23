package io.github.term4.polyp.mechanics.damage;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.api.event.item.ItemSpawnEvent;
import io.github.term4.polyp.mechanics.containers.Spill;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.EventListener;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathDropsTest extends HeadlessServerTest {

    private static final ItemStack SWORD = ItemStack.of(Material.DIAMOND_SWORD);
    private static final ItemStack STONE = ItemStack.of(Material.STONE, 5);
    private static final ItemStack HELMET = ItemStack.of(Material.IRON_HELMET);
    private static final ItemStack SHIELD = ItemStack.of(Material.SHIELD);

    @Test
    void deathThrowsTheInventory() {
        List<ItemSpawnEvent> drops = die("DropAll", null, p -> {});
        assertEquals(List.of(SWORD, STONE, SHIELD, HELMET), drops.stream().map(e -> e.item().getItemStack()).toList(),
                "the inventory, the offhand, then the armor");
        for (ItemSpawnEvent drop : drops) {
            assertEquals(ItemSpawnEvent.Cause.DEATH_DROP, drop.cause());
            assertEquals(64 + 1.62 - 0.30000001192092896, drop.position().y(), 1e-6, "from the hand, eye less 0.3");
        }
    }

    @Test
    void keepLeavesTheInventory() {
        FakePlayer[] body = new FakePlayer[1];
        List<ItemSpawnEvent> drops = die("DropKeep", DeathConfig.builder().spill(Spill.KEEP).build(), p -> body[0] = p);
        assertTrue(drops.isEmpty());
        assertEquals(SWORD, body[0].player.getInventory().getItemStack(0));
        assertEquals(HELMET, body[0].player.getInventory().getItemStack(PlayerInventoryUtils.HELMET_SLOT));
    }

    @Test
    void vanishLeavesNothing() {
        FakePlayer[] body = new FakePlayer[1];
        List<ItemSpawnEvent> drops = die("DropVanish", DeathConfig.builder().spill(Spill.VANISH).build(), p -> body[0] = p);
        assertTrue(drops.isEmpty());
        assertTrue(empty(body[0].player.getInventory()));
    }

    @Test
    void cursedItemsVanish() {
        ItemStack cursed = enchant(ItemStack.of(Material.GOLDEN_SWORD), Key.key("minecraft:vanishing_curse"), 1);
        List<ItemSpawnEvent> modern = die("DropCurse", null, p -> p.player.getInventory().setItemStack(1, cursed));
        assertTrue(modern.stream().noneMatch(e -> e.item().getItemStack().equals(cursed)), "from 1.11 the curse destroys it");
        List<ItemSpawnEvent> legacy = die("DropCurse18", DeathConfig.builder().vanishingCurse(false).build(),
                p -> p.player.getInventory().setItemStack(1, cursed));
        assertTrue(legacy.stream().anyMatch(e -> e.item().getItemStack().equals(cursed)), "1.8 has no such curse");
    }

    @Test
    void naturalStaysInTheBlock() {
        List<ItemSpawnEvent> drops = die("DropNatural", DeathConfig.builder().dropThrow(Spill.Throw.NATURAL).build(), p -> {});
        assertEquals(4, drops.size());
        for (ItemSpawnEvent drop : drops) {
            assertEquals(64, drop.position().y(), 1e-9, "feet on a block top stay on it");
            assertTrue(drop.position().x() >= 20 && drop.position().x() < 21);
            assertTrue(drop.position().z() >= 20 && drop.position().z() < 21);
        }
    }

    @Test
    void cursorFollowsTheInventory() {
        ItemStack carried = ItemStack.of(Material.ARROW, 7);
        List<ItemSpawnEvent> dropped = die("DropCursor", null, p -> p.player.getInventory().setCursorItem(carried));
        assertTrue(dropped.stream().anyMatch(e -> e.item().getItemStack().equals(carried)));
        FakePlayer[] body = new FakePlayer[1];
        die("KeepCursor", DeathConfig.builder().spill(Spill.KEEP).build(), p -> {
            body[0] = p;
            p.player.getInventory().setCursorItem(carried);
        });
        assertTrue(body[0].player.getInventory().getCursorItem().isAir());
        assertEquals(carried, body[0].player.getInventory().getItemStack(1), "into the first free slot, as 26.1 places it back");
    }

    @Test
    void spectatorsDropNothing() {
        assertTrue(die("DropGhost", null, p -> p.player.setGameMode(GameMode.SPECTATOR)).isEmpty());
    }

    /** Kills a stocked player under {@code death} and returns what spawned; the drops are cancelled, never spawned. */
    private static List<ItemSpawnEvent> die(String name, @Nullable DeathConfig death, Consumer<FakePlayer> setup) {
        FakePlayer p = FakePlayer.connect(instance, new Pos(20.5, 64, 20.5), name);
        List<ItemSpawnEvent> spawned = new ArrayList<>();
        EventListener<ItemSpawnEvent> hold = EventListener.of(ItemSpawnEvent.class, e -> {
            if (e.player() != p.player) return;
            spawned.add(e);
            e.setCancelled(true);
        });
        MinecraftServer.getGlobalEventHandler().addListener(hold);
        try {
            if (death != null) {
                polyp.profiles().setPlayer(p.player, MechanicsProfile.builder().set(MechanicsKeys.DEATH, death).build());
            }
            PlayerInventory inventory = p.player.getInventory();
            inventory.setItemStack(0, SWORD);
            inventory.setItemStack(9, STONE);
            inventory.setItemStack(PlayerInventoryUtils.HELMET_SLOT, HELMET);
            inventory.setItemStack(PlayerInventoryUtils.OFFHAND_SLOT, SHIELD);
            setup.accept(p);
            p.player.kill();
            return spawned;
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(hold);
            polyp.profiles().setPlayer(p.player, null);
            p.player.remove();
        }
    }

    private static boolean empty(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!inventory.getItemStack(slot).isAir()) return false;
        }
        return inventory.getCursorItem().isAir();
    }
}
