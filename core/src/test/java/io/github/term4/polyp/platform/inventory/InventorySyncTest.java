package io.github.term4.polyp.platform.inventory;

import io.github.term4.polyp.platform.player.CreativeInventory;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.platform.player.PlayerConfig;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.common.ClientPongPacket;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket.ClickType;
import net.minestom.server.network.packet.client.play.ClientCloseWindowPacket;
import net.minestom.server.network.packet.client.play.ClientCreativeInventoryActionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.common.PingPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.SetCursorItemPacket;
import net.minestom.server.network.packet.server.play.SetPlayerInventorySlotPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySyncTest extends HeadlessServerTest {

    private static final int MODERN = 774;
    private static final int LEGACY = 47;
    /** Hotbar slot 0 in window 0. */
    private static final short HOTBAR_0 = 36;

    @Test
    void serverChangeSentOnce() {
        FakePlayer p = join("SyncOnce", MODERN);
        try {
            p.player.getInventory().setItemStack(0, ItemStack.of(Material.STONE, 5));
            assertTrue(inventoryPackets(p).isEmpty(), "nothing before the broadcast");
            sync(p).broadcast();
            List<SetSlotPacket> slots = p.sent(SetSlotPacket.class);
            assertEquals(1, slots.size());
            assertEquals(HOTBAR_0, slots.getFirst().slot());
            assertEquals(5, slots.getFirst().itemStack().amount());
            p.sent.clear();
            sync(p).broadcast();
            assertTrue(inventoryPackets(p).isEmpty(), "a slot the client shows is not sent again");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void rightPredictionSendsNothing() {
        FakePlayer p = join("SyncRight", MODERN);
        try {
            ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
            give(p, 0, sword);
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.PICKUP, Map.of(HOTBAR_0, ItemStack.Hash.AIR), sword));
            assertEquals(sword, p.player.getInventory().getCursorItem(), "the click landed");
            assertTrue(inventoryPackets(p).isEmpty());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void refusedClickIsCorrected() {
        FakePlayer p = join("SyncRefuse", MODERN);
        EventListener<InventoryPreClickEvent> refuse = EventListener.of(InventoryPreClickEvent.class,
                e -> e.setCancelled(true));
        MinecraftServer.getGlobalEventHandler().addListener(refuse);
        try {
            ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
            give(p, 0, sword);
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.PICKUP, Map.of(HOTBAR_0, ItemStack.Hash.AIR), sword));
            assertTrue(p.sent(WindowItemsPacket.class).isEmpty(), "a correction, not a full resend");
            List<SetSlotPacket> slots = p.sent(SetSlotPacket.class);
            assertEquals(1, slots.size());
            assertEquals(HOTBAR_0, slots.getFirst().slot());
            assertEquals(Material.DIAMOND_SWORD, slots.getFirst().itemStack().material());
            List<SetCursorItemPacket> cursor = p.sent(SetCursorItemPacket.class);
            assertEquals(1, cursor.size());
            assertTrue(cursor.getFirst().itemStack().isAir());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(refuse);
            p.player.remove();
        }
    }

    @Test
    void staleStateResendsAll() {
        FakePlayer p = join("SyncStale", MODERN);
        try {
            ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
            give(p, 0, sword);
            feed(p, new ClientClickWindowPacket(0, sync(p).stateId(0) - 1, HOTBAR_0, (byte) 0, ClickType.PICKUP,
                    Map.of(HOTBAR_0, ItemStack.Hash.AIR), hash(p, sword)));
            assertEquals(1, p.sent(WindowItemsPacket.class).size());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void shiftClickStaysQuiet() {
        FakePlayer p = join("SyncShift", MODERN);
        try {
            ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
            give(p, 0, sword);
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.QUICK_MOVE,
                    Map.of(HOTBAR_0, ItemStack.Hash.AIR, (short) 9, hash(p, sword)), ItemStack.AIR));
            assertEquals(sword, p.player.getInventory().getItemStack(9), "hotbar to the first main slot");
            assertTrue(inventoryPackets(p).isEmpty(), "Minestom's resend after a shift click is not sent");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldPickupStaysQuiet() {
        FakePlayer p = join("SyncOld", LEGACY);
        try {
            ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
            give(p, 0, sword);
            // below 1.17 ViaBackwards carries a left pickup's clicked item where the cursor goes
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.PICKUP, Map.of(HOTBAR_0, ItemStack.Hash.AIR), sword));
            assertEquals(sword, p.player.getInventory().getCursorItem());
            assertTrue(inventoryPackets(p).isEmpty(), "the client ran the same click");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldShiftClickStaysQuiet() {
        FakePlayer p = join("SyncOldShift", LEGACY);
        try {
            give(p, 0, ItemStack.of(Material.DIAMOND_SWORD));
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.QUICK_MOVE, Map.of(), ItemStack.AIR));
            assertEquals(Material.DIAMOND_SWORD, p.player.getInventory().getItemStack(9).material());
            assertTrue(inventoryPackets(p).isEmpty());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldStaleViewResendsAll() {
        FakePlayer p = join("SyncOldStale", LEGACY);
        try {
            give(p, 0, ItemStack.of(Material.DIAMOND_SWORD));
            // the client clicked a slot it still saw empty: 1.8 rejects the click and resends the window
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.PICKUP, Map.of(), ItemStack.AIR));
            assertEquals(1, p.sent(WindowItemsPacket.class).size());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldRefusedClickIsCorrected() {
        FakePlayer p = join("SyncOldRefuse", LEGACY);
        EventListener<InventoryPreClickEvent> refuse = EventListener.of(InventoryPreClickEvent.class,
                e -> e.setCancelled(true));
        MinecraftServer.getGlobalEventHandler().addListener(refuse);
        try {
            ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
            give(p, 0, sword);
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.PICKUP, Map.of(), sword));
            // what a 1.8 server sends for a rejected click: the window, and a fence
            assertEquals(1, p.sent(WindowItemsPacket.class).size());
            assertTrue(p.sent(SetSlotPacket.class).isEmpty());
            assertEquals(1, p.sent(PingPacket.class).size());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(refuse);
            p.player.remove();
        }
    }

    @Test
    void oldDragStaysQuiet() {
        FakePlayer p = join("SyncDrag", LEGACY);
        try {
            p.player.getInventory().setCursorItem(ItemStack.of(Material.STONE, 4));
            sync(p).broadcast();
            p.sent.clear();
            feed(p, click(p, 0, (short) -999, 0, ClickType.QUICK_CRAFT, Map.of(), ItemStack.AIR));
            feed(p, click(p, 0, (short) 9, 1, ClickType.QUICK_CRAFT, Map.of(), ItemStack.AIR));
            feed(p, click(p, 0, (short) -999, 2, ClickType.QUICK_CRAFT, Map.of(), ItemStack.AIR));
            assertEquals(4, p.player.getInventory().getItemStack(9).amount());
            assertTrue(inventoryPackets(p).isEmpty());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldThrowResendsTheSlot() {
        FakePlayer p = join("SyncOldThrow", LEGACY);
        try {
            give(p, 0, ItemStack.of(Material.STONE, 5));
            // how ViaBackwards replays a 1.8 Q drop, which the client never predicted
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.THROW, Map.of(), ItemStack.AIR));
            sync(p).broadcast();
            List<SetSlotPacket> slots = p.sent(SetSlotPacket.class);
            assertEquals(1, slots.size());
            assertEquals(4, slots.getFirst().itemStack().amount());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void blockClickKeepsTheHand() {
        FakePlayer p = join("SyncEatBlock", LEGACY);
        try {
            give(p, 0, ItemStack.of(Material.BREAD, 5));
            // a 1.8 client eating at a block sends a placement too; a hand resend would end its eat
            feed(p, new ClientPlayerBlockPlacementPacket(PlayerHand.MAIN, new Vec(0, 63, 0), BlockFace.TOP,
                    0.5f, 1f, 0.5f, false, false, 0));
            sync(p).broadcast();
            assertTrue(inventoryPackets(p).isEmpty());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldDragSkipsExtraSlots() {
        FakePlayer p = join("SyncDragWide", LEGACY);
        try {
            p.player.getInventory().setCursorItem(ItemStack.of(Material.STONE, 4));
            sync(p).broadcast();
            p.sent.clear();
            drag(p, 0, 9, 10, 11, 12, 13, 14);
            for (int slot = 9; slot <= 12; slot++) assertEquals(1, p.player.getInventory().getItemStack(slot).amount());
            assertTrue(p.player.getInventory().getItemStack(13).isAir(), "1.8 stops adding slots at the cursor's count");
            assertTrue(p.player.getInventory().getCursorItem().isAir());
            assertTrue(inventoryPackets(p).isEmpty());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldDragSkipsOtherItems() {
        FakePlayer p = join("SyncDragMix", LEGACY);
        try {
            give(p, 10, ItemStack.of(Material.DIRT));
            p.player.getInventory().setCursorItem(ItemStack.of(Material.STONE, 9));
            sync(p).broadcast();
            p.sent.clear();
            drag(p, 0, 9, 10, 11);
            assertEquals(4, p.player.getInventory().getItemStack(9).amount(), "split two ways, not three");
            assertEquals(Material.DIRT, p.player.getInventory().getItemStack(10).material());
            assertEquals(1, p.player.getInventory().getCursorItem().amount());
            assertTrue(inventoryPackets(p).isEmpty());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldDoubleClickMatches() {
        FakePlayer p = join("SyncGather", LEGACY);
        try {
            give(p, 9, ItemStack.of(Material.STONE, 64));
            give(p, 10, ItemStack.of(Material.STONE, 30));
            p.player.getInventory().setCursorItem(ItemStack.of(Material.STONE, 1));
            sync(p).broadcast();
            p.sent.clear();
            feed(p, click(p, 0, (short) 20, 0, ClickType.PICKUP_ALL, Map.of(), ItemStack.AIR));
            assertEquals(64, p.player.getInventory().getCursorItem().amount());
            assertTrue(p.player.getInventory().getItemStack(10).isAir(), "the partial stack goes first");
            assertEquals(31, p.player.getInventory().getItemStack(9).amount());
            assertTrue(inventoryPackets(p).isEmpty());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldNumberKeyPushesAside() {
        FakePlayer p = join("SyncNumKey", LEGACY);
        CreativeInventory chest = new CreativeInventory(InventoryType.CHEST_3_ROW, Component.text("Chest"));
        chest.setItemStack(0, ItemStack.of(Material.DIAMOND_SWORD));
        try {
            give(p, 0, ItemStack.of(Material.STONE, 5));
            p.player.openInventory(chest);
            p.sent.clear();
            feed(p, click(p, chest.getWindowId(), (short) 0, 0, ClickType.SWAP, Map.of(), ItemStack.AIR));
            assertEquals(Material.DIAMOND_SWORD, p.player.getInventory().getItemStack(0).material());
            assertEquals(5, p.player.getInventory().getItemStack(1).amount(), "the hotbar item goes aside, not into the chest");
            assertTrue(chest.getItemStack(0).isAir());
            assertTrue(inventoryPackets(p).isEmpty());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldRecountEndsTheEat() {
        FakePlayer p = join("SyncCutOld", LEGACY);
        try {
            sync(p).countChangeEndsUse(true);
            eat(p);
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.THROW, Map.of(), ItemStack.AIR));
            List<SetSlotPacket> slots = p.sent(SetSlotPacket.class);
            assertEquals(1, slots.size());
            assertEquals(4, slots.getFirst().itemStack().amount());
            assertTrue(p.player.isUsingItem(), "the server's eat runs on");
            assertTrue(sync(p).useCut());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldRecountWaitsForTheEat() {
        FakePlayer p = join("SyncHoldOld", LEGACY);
        try {
            sync(p).countChangeEndsUse(false);
            eat(p);
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.THROW, Map.of(), ItemStack.AIR));
            sync(p).broadcast();
            assertTrue(inventoryPackets(p).isEmpty(), "held while the eat runs");
            p.player.clearItemUse();
            sync(p).broadcast();
            List<SetSlotPacket> slots = p.sent(SetSlotPacket.class);
            assertEquals(1, slots.size());
            assertEquals(4, slots.getFirst().itemStack().amount());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void modernRecountEndsTheEat() {
        FakePlayer p = join("SyncCutNew", MODERN);
        try {
            sync(p).countChangeEndsUse(true);
            eat(p);
            feed(p, drop());
            sync(p).broadcast();
            assertTrue(inventoryPackets(p).isEmpty(), "the drop was predicted");
            List<EntityMetaDataPacket> meta = ownMeta(p);
            assertEquals(1, meta.size());
            byte flags = (Byte) meta.getFirst().entries().get(MetadataDef.LivingEntity.LIVING_ENTITY_FLAGS.index()).value();
            assertEquals(0, flags & 1, "told its use ended");
            assertTrue(p.player.isUsingItem(), "the server's eat runs on");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void modernRecountKeepsTheEat() {
        FakePlayer p = join("SyncKeepNew", MODERN);
        try {
            sync(p).countChangeEndsUse(false);
            eat(p);
            feed(p, drop());
            sync(p).broadcast();
            assertTrue(ownMeta(p).isEmpty());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void heldClickCountsOnce() {
        FakePlayer p = join("SyncHeldClick", LEGACY);
        EventListener<PlayerPacketEvent> hold = holdOnce(ClientClickWindowPacket.class);
        MinecraftServer.getGlobalEventHandler().addListener(hold);
        try {
            ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
            give(p, 0, sword);
            ClientClickWindowPacket pickup = click(p, 0, HOTBAR_0, 0, ClickType.PICKUP,
                    Map.of(HOTBAR_0, ItemStack.Hash.AIR), sword);
            // held back and fed in again, as the lag simulator does
            feed(p, pickup);
            sync(p).broadcast();
            assertTrue(inventoryPackets(p).isEmpty(), "nothing is corrected before the click lands");
            feed(p, pickup);
            assertEquals(sword, p.player.getInventory().getCursorItem());
            sync(p).broadcast();
            assertTrue(inventoryPackets(p).isEmpty());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(hold);
            p.player.remove();
        }
    }

    @Test
    void heldDropCountsOnce() {
        FakePlayer p = join("SyncHeldDrop", MODERN);
        EventListener<PlayerPacketEvent> hold = holdOnce(ClientPlayerActionPacket.class);
        MinecraftServer.getGlobalEventHandler().addListener(hold);
        try {
            give(p, 0, ItemStack.of(Material.STONE, 5));
            ClientPlayerActionPacket drop = drop();
            feed(p, drop);
            sync(p).broadcast();
            feed(p, drop);
            assertEquals(4, p.player.getInventory().getItemStack(0).amount());
            sync(p).broadcast();
            assertTrue(inventoryPackets(p).isEmpty(), "the predicted drop is counted once");
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(hold);
            p.player.remove();
        }
    }

    @Test
    void oldScreenThrowStaysQuiet() {
        FakePlayer p = join("SyncScreenThrow", LEGACY);
        try {
            give(p, 0, ItemStack.of(Material.STONE, 5));
            // a click on an empty slot: the inventory screen is up
            feed(p, click(p, 0, (short) 20, 0, ClickType.PICKUP, Map.of(), ItemStack.AIR));
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.THROW, Map.of(), ItemStack.AIR));
            assertEquals(4, p.player.getInventory().getItemStack(0).amount());
            assertTrue(inventoryPackets(p).isEmpty(), "the client threw it itself");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void closedScreenThrowResends() {
        FakePlayer p = join("SyncClosedThrow", LEGACY);
        try {
            give(p, 0, ItemStack.of(Material.STONE, 5));
            feed(p, click(p, 0, (short) 20, 0, ClickType.PICKUP, Map.of(), ItemStack.AIR));
            feed(p, new ClientCloseWindowPacket((byte) 0));
            sync(p).broadcast();
            p.sent.clear();
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.THROW, Map.of(), ItemStack.AIR));
            List<SetSlotPacket> slots = p.sent(SetSlotPacket.class);
            assertEquals(1, slots.size(), "a Q drop replayed as a throw");
            assertEquals(4, slots.getFirst().itemStack().amount());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void fenceDropsLaterClicks() {
        FakePlayer p = join("SyncFence", LEGACY);
        boolean[] refuse = {true};
        EventListener<InventoryPreClickEvent> refuseFirst = EventListener.of(InventoryPreClickEvent.class, e -> {
            if (refuse[0]) e.setCancelled(true);
        });
        MinecraftServer.getGlobalEventHandler().addListener(refuseFirst);
        try {
            ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
            give(p, 0, sword);
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.PICKUP, Map.of(), sword));
            List<PingPacket> pings = p.sent(PingPacket.class);
            assertEquals(1, pings.size());
            refuse[0] = false;
            p.sent.clear();
            // sent before the client took the resend
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.PICKUP, Map.of(), sword));
            assertEquals(sword, p.player.getInventory().getItemStack(0), "ignored until the client confirms");
            assertTrue(inventoryPackets(p).isEmpty());
            feed(p, new ClientPongPacket(pings.getFirst().id()));
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.PICKUP, Map.of(), sword));
            assertEquals(sword, p.player.getInventory().getCursorItem());
            assertTrue(inventoryPackets(p).isEmpty());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(refuseFirst);
            p.player.remove();
        }
    }

    @Test
    void closedCursorDrops() {
        FakePlayer p = join("SyncCursorDrop", LEGACY);
        List<ItemStack> dropped = new ArrayList<>();
        EventListener<ItemDropEvent> drops = EventListener.of(ItemDropEvent.class, e -> {
            if (e.getPlayer() == p.player) dropped.add(e.getItemStack());
        });
        MinecraftServer.getGlobalEventHandler().addListener(drops);
        try {
            sync(p).cursorOnClose(PlayerConfig.CursorOnClose.DROP);
            give(p, 0, ItemStack.of(Material.STONE, 10));
            carry(p, ItemStack.of(Material.STONE, 3));
            feed(p, new ClientCloseWindowPacket((byte) 0));
            assertTrue(p.player.getInventory().getCursorItem().isAir());
            assertEquals(List.of(ItemStack.of(Material.STONE, 3)), dropped);
            assertEquals(10, p.player.getInventory().getItemStack(0).amount());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(drops);
            p.player.remove();
        }
    }

    @Test
    void closedCursorReturns() {
        FakePlayer p = join("SyncCursorBack", MODERN);
        try {
            sync(p).cursorOnClose(PlayerConfig.CursorOnClose.RETURN);
            give(p, 0, ItemStack.of(Material.DIAMOND_SWORD));
            give(p, 5, ItemStack.of(Material.STONE, 63));
            carry(p, ItemStack.of(Material.STONE, 3));
            feed(p, new ClientCloseWindowPacket((byte) 0));
            assertTrue(p.player.getInventory().getCursorItem().isAir());
            // a stack with room first, then the first empty slot
            assertEquals(64, p.player.getInventory().getItemStack(5).amount());
            assertEquals(ItemStack.of(Material.STONE, 2), p.player.getInventory().getItemStack(1));
        } finally {
            p.player.remove();
        }
    }

    @Test
    void closedCursorStays() {
        FakePlayer p = join("SyncCursorKeep", LEGACY);
        try {
            sync(p).cursorOnClose(null);
            carry(p, ItemStack.of(Material.STONE, 3));
            feed(p, new ClientCloseWindowPacket((byte) 0));
            assertEquals(ItemStack.of(Material.STONE, 3), p.player.getInventory().getCursorItem());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void middleClientClickResendsAll() {
        FakePlayer p = join("SyncNine", 107);
        try {
            ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
            give(p, 0, sword);
            feed(p, click(p, 0, HOTBAR_0, 0, ClickType.PICKUP, Map.of(HOTBAR_0, ItemStack.Hash.AIR), sword));
            assertEquals(1, p.sent(WindowItemsPacket.class).size(), "1.9 to 1.16 clicks are not followed yet");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void modernDropStaysQuiet() {
        FakePlayer p = join("SyncDrop", MODERN);
        try {
            give(p, 0, ItemStack.of(Material.STONE, 5));
            feed(p, drop());
            assertEquals(4, p.player.getInventory().getItemStack(0).amount());
            sync(p).broadcast();
            assertTrue(inventoryPackets(p).isEmpty(), "1.13.1 and up predict their own drop");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void oldDropResendsTheSlot() {
        FakePlayer p = join("SyncOldDrop", LEGACY);
        try {
            give(p, 0, ItemStack.of(Material.STONE, 5));
            feed(p, drop());
            sync(p).broadcast();
            List<SetSlotPacket> slots = p.sent(SetSlotPacket.class);
            assertEquals(1, slots.size());
            assertEquals(HOTBAR_0, slots.getFirst().slot());
            assertEquals(4, slots.getFirst().itemStack().amount());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void creativeSlotStaysQuiet() {
        FakePlayer p = join("SyncCreative", MODERN);
        try {
            p.player.setGameMode(GameMode.CREATIVE);
            sync(p).broadcast();
            p.sent.clear();
            feed(p, new ClientCreativeInventoryActionPacket(HOTBAR_0, ItemStack.of(Material.STONE, 64)));
            assertEquals(Material.STONE, p.player.getInventory().getItemStack(0).material());
            sync(p).broadcast();
            assertTrue(inventoryPackets(p).isEmpty(), "the client set it itself");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void forgettingResendsAll() {
        FakePlayer p = join("SyncForget", MODERN);
        try {
            sync(p).forget();
            sync(p).broadcast();
            assertEquals(1, p.sent(WindowItemsPacket.class).size());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void chestCarriesPlayerSlots() {
        FakePlayer p = join("SyncChest", MODERN);
        CreativeInventory chest = new CreativeInventory(InventoryType.CHEST_3_ROW, Component.text("Chest"));
        try {
            p.player.openInventory(chest);
            List<WindowItemsPacket> opened = p.sent(WindowItemsPacket.class);
            assertEquals(1, opened.size());
            assertEquals(chest.getWindowId(), opened.getFirst().windowId());
            assertEquals(27 + 36, opened.getFirst().items().size());
            p.sent.clear();

            p.player.getInventory().setItemStack(0, ItemStack.of(Material.STONE));
            sync(p).broadcast();
            List<SetSlotPacket> slots = p.sent(SetSlotPacket.class);
            assertEquals(1, slots.size());
            assertEquals(chest.getWindowId(), slots.getFirst().windowId(), "through the open window, not window 0");
            assertEquals(27 + 27, slots.getFirst().slot());
            p.sent.clear();

            p.player.closeInventory();
            assertTrue(p.sent(WindowItemsPacket.class).isEmpty(), "the close carries the view over");
            sync(p).broadcast();
            assertTrue(p.sent(SetSlotPacket.class).isEmpty(), "window 0 already shows the stone");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void sharedChestReachesOthers() {
        FakePlayer a = join("SyncA", MODERN);
        FakePlayer b = join("SyncB", MODERN);
        CreativeInventory chest = new CreativeInventory(InventoryType.CHEST_3_ROW, Component.text("Chest"));
        ItemStack gem = ItemStack.of(Material.EMERALD);
        chest.setItemStack(0, gem);
        try {
            a.player.openInventory(chest);
            b.player.openInventory(chest);
            a.sent.clear();
            b.sent.clear();
            feed(a, click(a, chest.getWindowId(), (short) 0, 0, ClickType.PICKUP, Map.of((short) 0, ItemStack.Hash.AIR), gem));
            assertTrue(inventoryPackets(a).isEmpty(), "the clicker predicted it");
            sync(b).broadcast();
            List<SetSlotPacket> seen = b.sent(SetSlotPacket.class);
            assertEquals(1, seen.size());
            assertEquals(0, seen.getFirst().slot());
            assertTrue(seen.getFirst().itemStack().isAir());
        } finally {
            a.player.remove();
            b.player.remove();
        }
    }

    @Test
    void rawPacketIsTheBaseline() {
        FakePlayer p = join("SyncRaw", MODERN);
        try {
            // openBook's fake offhand, then its restore
            p.player.sendPacket(new SetSlotPacket(0, 0, (short) 45, ItemStack.of(Material.WRITTEN_BOOK)));
            p.player.sendPacket(new SetSlotPacket(0, 0, (short) 45, ItemStack.AIR));
            assertEquals(2, p.sent(SetSlotPacket.class).size(), "raw packets still go out");
            p.sent.clear();
            sync(p).broadcast();
            assertTrue(inventoryPackets(p).isEmpty());
        } finally {
            p.player.remove();
        }
    }

    @Test
    void refreshGoesOutNow() {
        FakePlayer p = join("SyncRefresh", MODERN);
        try {
            give(p, 0, ItemStack.of(Material.STONE));
            p.player.getInventory().sendSlotRefresh(0, ItemStack.of(Material.STONE));
            assertEquals(1, p.sent(SetSlotPacket.class).size());
        } finally {
            p.player.remove();
        }
    }

    private static FakePlayer join(String name, int protocol) {
        FakePlayer p = FakePlayer.connect(instance, new Pos(0.5, 64, 0.5), name);
        polyp.clientInfo().setConnectionDetails(p.player, "{\"version\": " + protocol + "}");
        sync(p).broadcast();
        p.sent.clear();
        return p;
    }

    /** Five bread in hotbar slot 0 and the server mid-eat. */
    private static void eat(FakePlayer p) {
        give(p, 0, ItemStack.of(Material.BREAD, 5));
        p.player.refreshItemUse(PlayerHand.MAIN, 32);
        p.player.refreshActiveHand(true, false, false);
        sync(p).broadcast();
        p.sent.clear();
    }

    private static List<EntityMetaDataPacket> ownMeta(FakePlayer p) {
        return p.sent(EntityMetaDataPacket.class).stream().filter(m -> m.entityId() == p.player.getEntityId()).toList();
    }

    /** Cancels the first pass of each {@code type} packet, so a second feed of the same one goes through. */
    private static EventListener<PlayerPacketEvent> holdOnce(Class<? extends ClientPacket> type) {
        Set<ClientPacket> held = Collections.newSetFromMap(new IdentityHashMap<>());
        return EventListener.of(PlayerPacketEvent.class, e -> {
            if (type.isInstance(e.getPacket()) && held.add(e.getPacket())) e.setCancelled(true);
        });
    }

    private static void carry(FakePlayer p, ItemStack item) {
        p.player.getInventory().setCursorItem(item);
        sync(p).broadcast();
        p.sent.clear();
    }

    private static void give(FakePlayer p, int slot, ItemStack item) {
        p.player.getInventory().setItemStack(slot, item);
        sync(p).broadcast();
        p.sent.clear();
    }

    private static InventorySync sync(FakePlayer p) {
        return ((OptimizedPlayer) p.player).inventorySync();
    }

    private static void feed(FakePlayer p, ClientPacket packet) {
        MinecraftServer.getPacketListenerManager().processClientPacket(packet, p.player.getPlayerConnection());
    }

    private static ClientClickWindowPacket click(FakePlayer p, int windowId, short slot, int button, ClickType type,
                                                 Map<Short, ItemStack.Hash> changed, ItemStack carried) {
        return new ClientClickWindowPacket(windowId, sync(p).stateId(windowId), slot, (byte) button, type, changed,
                hash(p, carried));
    }

    /** A left drag over {@code slots}, the three kinds of packet a client sends for one. */
    private static void drag(FakePlayer p, int windowId, int... slots) {
        feed(p, click(p, windowId, (short) -999, 0, ClickType.QUICK_CRAFT, Map.of(), ItemStack.AIR));
        for (int slot : slots) feed(p, click(p, windowId, (short) slot, 1, ClickType.QUICK_CRAFT, Map.of(), ItemStack.AIR));
        feed(p, click(p, windowId, (short) -999, 2, ClickType.QUICK_CRAFT, Map.of(), ItemStack.AIR));
    }

    private static ClientPlayerActionPacket drop() {
        return new ClientPlayerActionPacket(ClientPlayerActionPacket.Status.DROP_ITEM, Vec.ZERO, BlockFace.BOTTOM, 0);
    }

    /** What a modern client reports: the hash of the item as it is shown. */
    private static ItemStack.Hash hash(FakePlayer p, ItemStack item) {
        return ItemStack.Hash.of(((OptimizedPlayer) p.player).compat().view(item), MinecraftServer.process());
    }

    private static List<ServerPacket> inventoryPackets(FakePlayer p) {
        return p.sent.stream()
                .map(sent -> SendablePacket.extractServerPacket(ConnectionState.PLAY, sent))
                .filter(s -> s instanceof SetSlotPacket || s instanceof WindowItemsPacket
                        || s instanceof SetCursorItemPacket || s instanceof SetPlayerInventorySlotPacket)
                .toList();
    }
}
