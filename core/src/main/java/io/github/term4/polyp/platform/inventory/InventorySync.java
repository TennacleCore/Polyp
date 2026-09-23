package io.github.term4.polyp.platform.inventory;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.platform.PacketShapes;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.platform.player.PlayerConfig;
import io.github.term4.polyp.platform.player.PlayListeners;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.listener.CreativeInventoryActionListener;
import net.minestom.server.listener.PlayerActionListener;
import net.minestom.server.listener.UseItemListener;
import net.minestom.server.listener.WindowListener;
import net.minestom.server.network.packet.client.common.ClientPongPacket;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.server.network.packet.client.play.ClientCreativeInventoryActionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientUseItemPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.common.PingPacket;
import net.minestom.server.network.packet.server.play.CloseWindowPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.JoinGamePacket;
import net.minestom.server.network.packet.server.play.OpenWindowPacket;
import net.minestom.server.network.packet.server.play.RespawnPacket;
import net.minestom.server.network.packet.server.play.SetCursorItemPacket;
import net.minestom.server.network.packet.server.play.SetPlayerInventorySlotPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.StartConfigurationPacket;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What one player's client shows of its inventory and open window, kept as vanilla's menus keep it: only a slot the
 * client does not already show is sent, after a click and once per tick, so a correctly predicted click gets nothing
 * back. Polyp's inventories hand their updates here instead of sending them; a raw packet from anywhere else still goes
 * out and becomes what the client shows.
 */
public final class InventorySync {

    /** 1.8: clicks are followed with its own click logic ({@link LegacyClicks}). */
    private static final int LEGACY_PROTOCOL = 47;
    /** 1.17: a click carries the client's own post-click slots and cursor. */
    private static final int REPORTS_CLICKS_PROTOCOL = 755;
    /** 1.13.1: the first client that predicts its own Q drop. */
    private static final int PREDICTS_DROPS_PROTOCOL = 401;
    /** 1.14.4: holds a use while the stack is the same item; 1.14 still wants the same object (1.14.1-1.14.3 unread). */
    private static final int COMPARES_USE_BY_ITEM_PROTOCOL = 498;
    /** A pong that never comes back (a proxy that drops the ack) must not freeze the inventory. */
    private static final long FENCE_TICKS = 100;

    private final OptimizedPlayer player;
    private final ClientWindow inventoryWindow = new ClientWindow(0, PlayerInventory.INVENTORY_SIZE, null);
    private @Nullable ClientWindow container;
    private final Remote cursor = new Remote();
    /** The client's copy of 1.8's click state, run on what it shows. */
    private final LegacyClicks legacyClicks = new LegacyClicks();
    private int legacyWindow = -1;
    /** A 1.8 client has its inventory screen up: opening it sends nothing, so a window-0 click says so. */
    private boolean screenOpen;
    /** The server's copy, run on its own items: the same logic, so both land on the same result. */
    private final LegacyClicks serverClicks = new LegacyClicks();
    private int serverWindow = -1;
    private boolean inClick;
    /** Minestom ends a close with a full resend of window 0; the close already carried the view over. */
    private boolean closing;
    private boolean sending;
    private @Nullable Boolean countChangeEndsUse;
    private @Nullable PlayerHand usedHand;
    private ItemStack usedStack = ItemStack.AIR;
    private boolean recounting;
    private boolean useCut;
    private @Nullable PlayerConfig.CursorOnClose cursorOnClose;
    /** The ping a 1.8 client has to answer before its clicks count again; 0 when none is out. */
    private int fence;
    private int lastFence;
    private long fencedAt;

    public InventorySync(@NotNull OptimizedPlayer player) {
        this.player = player;
        inventoryWindow.resyncAll = true;
    }

    public static void install(@NotNull Polyp polyp) {
        EventNode<@NotNull Event> node = EventNode.all("polyp:inventory-sync");
        node.addListener(PlayerTickEvent.class, e -> {
            if (e.getPlayer() instanceof OptimizedPlayer op) op.inventorySync().broadcast();
        });
        node.addListener(InventoryCloseEvent.class, e -> {
            if (e.getPlayer() instanceof OptimizedPlayer op) op.inventorySync().closing(e.getInventory());
        });
        // an instance hop: a 1.8 client wipes its inventory on respawn, and Via can synthesize one the server never sent
        node.addListener(PlayerSpawnEvent.class, e -> {
            if (!e.isFirstSpawn() && e.getPlayer() instanceof OptimizedPlayer op) op.inventorySync().forget();
        });
        polyp.install(node);

        // the client's side of a packet is read here, where it is handled, and not off PlayerPacketEvent: a packet
        // held back and fed in again (the lag simulator) fires that event twice
        PlayListeners.wrap(ClientClickWindowPacket.class, WindowListener::clickWindowListener, (packet, player, next) -> {
            if (!(player instanceof OptimizedPlayer op)) {
                next.accept(packet, player);
                return;
            }
            InventorySync sync = op.inventorySync();
            // sent before the client took the last correction, which undoes it there too
            if (sync.fenced()) return;
            sync.beforeClick(packet);
            // vanilla ignores a click on any window but the one the server has open; Minestom applies it anyway
            if (sync.stale(packet.windowId())) return;
            sync.clicking(true);
            try {
                if (!sync.applyLegacy(packet)) next.accept(packet, player);
            } finally {
                sync.clicking(false);
                sync.afterClick();
            }
        });
        // ViaBackwards answers a ping with the pre-1.17 client's own transaction ack when
        // handle-pings-as-inv-acknowledgements is on, else at once
        PlayListeners.wrap(ClientPongPacket.class, WindowListener::pong, (packet, player, next) -> {
            if (player instanceof OptimizedPlayer op) op.inventorySync().pong(packet.id());
            next.accept(packet, player);
        });
        PlayListeners.wrap(ClientCreativeInventoryActionPacket.class, CreativeInventoryActionListener::listener,
                (packet, player, next) -> {
                    if (player instanceof OptimizedPlayer op) op.inventorySync().creative(packet.slot(), packet.item());
                    next.accept(packet, player);
                    if (player instanceof OptimizedPlayer op) op.inventorySync().broadcast();
                });
        PlayListeners.wrap(ClientPlayerActionPacket.class, PlayerActionListener::playerActionListener,
                (packet, player, next) -> {
                    if (player instanceof OptimizedPlayer op) {
                        switch (packet.status()) {
                            case DROP_ITEM -> op.inventorySync().dropped(false);
                            case DROP_ITEM_STACK -> op.inventorySync().dropped(true);
                            default -> {}
                        }
                    }
                    next.accept(packet, player);
                });
        PlayListeners.wrap(ClientUseItemPacket.class, UseItemListener::useItemListener, (packet, player, next) -> {
            next.accept(packet, player);
            if (player instanceof OptimizedPlayer op) op.inventorySync().afterUse(packet.hand());
        });
    }

    /** Vanilla resyncs the hand after a use that did not start using; one that did must not see its hand rewritten. */
    public void afterUse(@NotNull PlayerHand hand) {
        synchronized (this) {
            useCut = false;
        }
        if (!player.isUsingItem()) handUsed(hand);
    }

    /** {@link PlayerConfig#countChangeEndsUse}, resolved for this player. */
    public synchronized void countChangeEndsUse(@Nullable Boolean ends) {
        this.countChangeEndsUse = ends;
    }

    /** {@link PlayerConfig#cursorOnClose}, resolved for this player. */
    public synchronized void cursorOnClose(@Nullable PlayerConfig.CursorOnClose rule) {
        this.cursorOnClose = rule;
    }

    /** The hand slot going out now only recounts the stack in use: the same stack to the server, so its use runs on. */
    public boolean recounting() {
        return recounting;
    }

    /** A recount ended the client's use while the server's runs on; cleared by the client's next use packet. */
    public synchronized boolean useCut() {
        return useCut;
    }

    // ------------------------------------------------------------------ what the client predicted

    void beforeClick(@NotNull ClientClickWindowPacket click) {
        synchronized (this) {
            ClientWindow window = click.windowId() == 0 ? inventoryWindow : openWindow(click.windowId());
            if (window == null) return;
            if (protocol() <= LEGACY_PROTOCOL) {
                if (!predictLegacy(window, click)) window.resyncAll = true;
                return;
            }
            if (protocol() < REPORTS_CLICKS_PROTOCOL) {
                // below 1.17 ViaBackwards invents the reported slots, so they say nothing about the client;
                // a drag changes nothing until its last packet
                if (!dragInProgress(click)) window.resyncAll = true;
                return;
            }
            if (click.stateId() != window.stateId()) window.resyncAll = true;
            for (var reported : click.changedSlots().entrySet()) {
                int slot = reported.getKey();
                if (window.has(slot)) window.slot(slot).reported(reported.getValue());
            }
            cursor.reported(click.clickedItem()); // named clickedItem on the wire: it is the predicted cursor
        }
    }

    boolean stale(int windowId) {
        AbstractInventory open = player.getOpenInventory();
        return windowId != (open == null ? 0 : open.getWindowId());
    }

    synchronized void clicking(boolean inClick) {
        this.inClick = inClick;
    }

    /** The state id a click on {@code windowId} echoes back. */
    synchronized int stateId(int windowId) {
        if (windowId == 0) return inventoryWindow.stateId();
        return container != null && container.windowId == windowId ? container.stateId() : 0;
    }

    /**
     * A 1.8 click applied as a 1.8 server applies it, with the client's own logic, so the client's prediction holds by
     * construction; false leaves it to Minestom (the crafting grid, a window the port does not model).
     */
    boolean applyLegacy(@NotNull ClientClickWindowPacket packet) {
        if (protocol() > LEGACY_PROTOCOL) return false;
        AbstractInventory inventory = packet.windowId() == 0 ? player.getInventory() : player.getOpenInventory();
        if (inventory == null || packet.slot() == -1) return false;
        ClientWindow window;
        LegacyClicks.Layout layout;
        synchronized (this) {
            window = packet.windowId() == 0 ? inventoryWindow : current();
            layout = window == null || window.windowId != packet.windowId() ? null : layout(window);
        }
        int mode = packet.clickType().ordinal();
        int slot = packet.slot();
        if (layout == null || layout.playerWindow() && slot >= 0 && slot < 5 && mode != 5) return false;
        if (packet.windowId() != serverWindow) {
            serverClicks.resetDrag();
            serverWindow = packet.windowId();
        }
        // Minestom's preprocessing still runs: it names the click for InventoryPreClickEvent, as WindowListener does
        Integer size = packet.windowId() == 0 ? null : inventory.getSize();
        Click click = player.getClickPreprocessor().processClick(packet, size);
        if (click != null) {
            player.UNSAFE_changeDidCloseInventory(false);
            Click.Window target = Click.toWindow(click, size);
            InventoryPreClickEvent event = new InventoryPreClickEvent(
                    target.inOpened() ? inventory : player.getInventory(), player, target.click());
            EventDispatcher.call(event);
            if (player.didCloseInventory() || event.isCancelled()) {
                player.UNSAFE_changeDidCloseInventory(false);
                serverClicks.resetDrag();
                return true;
            }
            if (!event.getClick().equals(target.click())) {
                inventory.handleClick(player, Click.fromWindow(new Click.Window(target.inOpened(), event.getClick()), size));
                return true;
            }
        }
        ItemStack[] items = new ItemStack[layout.size()];
        synchronized (this) {
            for (int i = 0; i < items.length; i++) items[i] = truth(window, i);
        }
        ItemStack[] before = items.clone();
        ItemStack cursor = serverClicks.click(layout, items, player.getInventory().getCursorItem(), slot,
                packet.button(), mode, player.getGameMode() == GameMode.CREATIVE);
        if (cursor == null) {
            // a drag that ended over the crafting grid
            if (click != null) inventory.handleClick(player, click);
            return true;
        }
        for (int i = 0; i < items.length; i++) {
            if (!items[i].equals(before[i])) setTruth(window, i, items[i]);
        }
        player.getInventory().setCursorItem(cursor);
        for (ItemStack out : serverClicks.dropped()) {
            if (!player.dropItem(out)) player.getInventory().addItemStack(out);
        }
        return true;
    }

    private void setTruth(ClientWindow window, int slot, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        if (window == inventoryWindow) {
            inventory.setItemStack(PlayerInventoryUtils.convertWindow0SlotToMinestomSlot(slot), item);
            return;
        }
        int size = window.containerSize();
        if (slot < size) window.inventory.setItemStack(slot, item);
        else inventory.setItemStack(PlayerInventoryUtils.convertWindowSlotToMinestomSlot(slot, size), item);
    }

    /** The click as a 1.8 client ran it on what it shows; false when that cannot be followed. */
    private boolean predictLegacy(ClientWindow window, ClientClickWindowPacket click) {
        if (click.windowId() != legacyWindow) {
            legacyClicks.resetDrag();
            legacyWindow = click.windowId();
        }
        int mode = click.clickType().ordinal();
        int slot = click.slot();
        if (window == inventoryWindow) {
            // ViaBackwards replays a 1.8 Q drop with no screen open as a throw on the held slot, one the client
            // never predicted; any other window-0 click means the inventory screen is up
            if (mode == 4 && !screenOpen && slot == PlayerInventoryUtils.convertMinestomSlotToWindowSlot(player.getHeldSlot())) {
                if (window.has(slot)) window.slot(slot).forget();
                return true;
            }
            screenOpen = true;
        }
        LegacyClicks.Layout layout = layout(window);
        if (layout == null) return false;
        ItemStack[] shown = new ItemStack[layout.size()];
        for (int i = 0; i < shown.length; i++) {
            shown[i] = window.slot(i).sent();
            if (shown[i] == null) return false;
        }
        ItemStack carried = cursor.sent();
        if (carried == null) return false;
        // ViaBackwards passes a left pickup's clicked item as the cursor: 1.8's own check of what the client saw
        if (mode == 0 && click.button() == 0 && slot >= 0 && slot < shown.length
                && !click.clickedItem().equals(hashOfView(shown[slot]))) return false;
        ItemStack after = legacyClicks.click(layout, shown, carried, slot, click.button(), mode,
                player.getGameMode() == GameMode.CREATIVE);
        if (after == null) return false;
        for (int i = 0; i < shown.length; i++) window.slot(i).sent(shown[i]);
        cursor.sent(after);
        return true;
    }

    private static LegacyClicks.@Nullable Layout layout(ClientWindow window) {
        if (window.windowId == 0) return LegacyClicks.Layout.PLAYER;
        if (!(window.inventory instanceof Inventory inventory)) return null;
        InventoryType type = inventory.getInventoryType();
        boolean chest = type.name().startsWith("CHEST_") || type == InventoryType.SHULKER_BOX;
        return chest ? LegacyClicks.Layout.chest(window.containerSize()) : null;
    }

    private static boolean dragInProgress(ClientClickWindowPacket click) {
        if (click.clickType() != ClientClickWindowPacket.ClickType.QUICK_CRAFT) return false;
        int stage = click.button() & 3;
        return stage == 0 || stage == 1;
    }

    void creative(short windowSlot, @NotNull ItemStack shown) {
        if (windowSlot < 1 || windowSlot >= PlayerInventory.INVENTORY_SIZE) return; // 0 is the result, -1 a drop
        synchronized (this) {
            ItemStack.Hash hash = ItemStack.Hash.of(shown, MinecraftServer.process());
            inventoryWindow.slot(windowSlot).reported(hash);
            int containerSlot = containerSlot(PlayerInventoryUtils.convertWindow0SlotToMinestomSlot(windowSlot));
            if (containerSlot >= 0) container.slot(containerSlot).reported(hash);
        }
    }

    void handUsed(@NotNull PlayerHand hand) {
        synchronized (this) {
            int slot = hand == PlayerHand.OFF ? PlayerInventoryUtils.OFFHAND_SLOT : player.getHeldSlot();
            forgetPlayerSlot(slot);
        }
    }

    void dropped(boolean wholeStack) {
        synchronized (this) {
            int held = player.getHeldSlot();
            if (protocol() < PREDICTS_DROPS_PROTOCOL) {
                forgetPlayerSlot(held);
                return;
            }
            ItemStack stack = player.getInventory().getItemStack(held);
            ItemStack left = wholeStack || stack.amount() <= 1 ? ItemStack.AIR : stack.withAmount(stack.amount() - 1);
            inventoryWindow.slot(PlayerInventoryUtils.convertMinestomSlotToWindowSlot(held)).sent(left);
        }
    }

    void closing(@NotNull AbstractInventory inventory) {
        placeCursor();
        synchronized (this) {
            screenOpen = false;
            if (container != null && container.inventory == inventory) {
                transfer(container);
                container = null;
            }
            // 1.8 empties its cursor on close, a modern client keeps it on window 0 until told
            cursor.forget();
            closing = true;
        }
    }

    /** The next broadcast sends this player slot again, whatever the client is thought to show. */
    public void resend(int slot) {
        synchronized (this) {
            forgetPlayerSlot(slot);
        }
    }

    /** Forgets everything; the next broadcast sends the whole inventory. */
    public void forget() {
        synchronized (this) {
            screenOpen = false;
            fence = 0;
            inventoryWindow.forgetAll();
            inventoryWindow.resyncAll = true;
            container = null;
            cursor.forget();
            closing = false;
        }
    }

    // ------------------------------------------------------------------ what the inventories ask for

    /**
     * A full resend of {@code inventory} for this player, vanilla's sendAllDataToRemote. Inside a click it is ignored:
     * Minestom resends after every shift, drag and double click, and the click's own broadcast decides instead.
     */
    public void resync(@NotNull AbstractInventory inventory) {
        synchronized (this) {
            if (inClick) return;
            ClientWindow window;
            if (inventory instanceof PlayerInventory) {
                if (closing) {
                    closing = false;
                    return;
                }
                inventoryWindow.resyncAll = true;
                // an open container shows the player's slots too
                window = container != null ? container : inventoryWindow;
            } else {
                if (container == null || container.inventory != inventory) container = ClientWindow.container(inventory);
                window = container;
            }
            sendAll(window);
        }
    }

    /** An explicit refresh of one slot: sent now and recorded as shown. */
    public void refresh(@NotNull AbstractInventory inventory, int slot, @NotNull ItemStack item) {
        synchronized (this) {
            ClientWindow window;
            int windowSlot;
            if (inventory instanceof PlayerInventory) {
                windowSlot = containerSlot(slot);
                if (windowSlot >= 0) {
                    window = container;
                } else if (container != null) {
                    // not in the open window: a 1.8 client drops a window-0 slot outside the hotbar, so it waits for the close
                    inventoryWindow.slot(PlayerInventoryUtils.convertMinestomSlotToWindowSlot(slot)).forget();
                    return;
                } else {
                    window = inventoryWindow;
                    windowSlot = PlayerInventoryUtils.convertMinestomSlotToWindowSlot(slot);
                }
            } else if (container != null && container.inventory == inventory) {
                window = container;
                windowSlot = slot;
            } else {
                return;
            }
            PlayerHand hand = player.getItemUseHand();
            if (inClick || windowSlot == handSlot(window, hand) && sameUse(hand, item) && holdsRecount()) {
                window.slot(windowSlot).forget();
                return;
            }
            window.slot(windowSlot).sent(item);
            send(new SetSlotPacket(window.windowId, window.nextStateId(), (short) windowSlot, item));
        }
    }

    // ------------------------------------------------------------------ 1.8's answer to a wrong prediction

    /** A 1.8 client that predicted wrong gets 1.8's rejection: the whole window, then none of its clicks until it has it. */
    void afterClick() {
        synchronized (this) {
            ClientWindow window = current();
            if (window != null && protocol() <= LEGACY_PROTOCOL && mispredicted(window)) {
                sendAll(window);
                lastFence = lastFence <= Short.MIN_VALUE + 1 ? -1 : lastFence - 1;
                fence = lastFence;
                fencedAt = player.getAliveTicks();
                send(new PingPacket(fence));
                return;
            }
        }
        broadcast();
    }

    /** A slot or the cursor the client was known to show, which the server now disagrees with. */
    private boolean mispredicted(ClientWindow window) {
        if (window.resyncAll) return true;
        for (int slot = 0; slot < window.size(); slot++) {
            Remote remote = window.slot(slot);
            if (remote.sent() != null && !remote.matches(truth(window, slot), this::hashOfView)) return true;
        }
        return cursor.sent() != null && !cursor.matches(player.getInventory().getCursorItem(), this::hashOfView);
    }

    synchronized boolean fenced() {
        if (fence != 0 && player.getAliveTicks() - fencedAt > FENCE_TICKS) fence = 0;
        return fence != 0;
    }

    synchronized void pong(int id) {
        if (id == fence) fence = 0;
    }

    // ------------------------------------------------------------------ the cursor at a close

    private void placeCursor() {
        PlayerConfig.CursorOnClose rule;
        synchronized (this) {
            rule = cursorOnClose;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack carried = inventory.getCursorItem();
        if (rule == null || carried.isAir()) return;
        inventory.setCursorItem(ItemStack.AIR);
        if (rule == PlayerConfig.CursorOnClose.RETURN) carried = placeBack(carried);
        if (!carried.isAir() && !player.dropItem(carried)) inventory.addItemStack(carried);
    }

    /** 26.1's placeItemBackInInventory; what does not fit comes back. */
    private ItemStack placeBack(ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        while (!item.isAir()) {
            int slot = roomFor(inventory, item);
            if (slot < 0) return item;
            ItemStack there = inventory.getItemStack(slot);
            int put = Math.min(item.amount(), item.maxStackSize() - (there.isAir() ? 0 : there.amount()));
            inventory.setItemStack(slot, there.isAir() ? item.withAmount(put) : there.withAmount(there.amount() + put));
            item = item.amount() == put ? ItemStack.AIR : item.withAmount(item.amount() - put);
        }
        return item;
    }

    /** The held slot, the offhand, any stack with room, then the first empty slot. */
    private int roomFor(PlayerInventory inventory, ItemStack item) {
        int held = player.getHeldSlot();
        if (hasRoom(inventory.getItemStack(held), item)) return held;
        if (hasRoom(inventory.getItemStack(PlayerInventoryUtils.OFFHAND_SLOT), item)) return PlayerInventoryUtils.OFFHAND_SLOT;
        for (int slot = 0; slot < PlayerInventory.INNER_INVENTORY_SIZE; slot++) {
            if (hasRoom(inventory.getItemStack(slot), item)) return slot;
        }
        for (int slot = 0; slot < PlayerInventory.INNER_INVENTORY_SIZE; slot++) {
            if (inventory.getItemStack(slot).isAir()) return slot;
        }
        return -1;
    }

    private static boolean hasRoom(ItemStack there, ItemStack item) {
        return !there.isAir() && there.isSimilar(item) && there.amount() < there.maxStackSize();
    }

    // ------------------------------------------------------------------ the broadcast

    /** Sends every slot of the open window the client does not already show, and the cursor. */
    public void broadcast() {
        synchronized (this) {
            closing = false;
            ClientWindow window = current();
            if (window == null) return;
            PlayerHand hand = player.getItemUseHand();
            ItemStack held = hand == null ? ItemStack.AIR : player.getItemInHand(hand);
            boolean sameUse = sameUse(hand, held);
            boolean recount = sameUse && held.amount() != usedStack.amount();
            usedHand = hand;
            usedStack = held;
            if (hand == null) useCut = false;
            if (window.resyncAll) {
                sendAll(window);
                return;
            }
            int handSlot = handSlot(window, hand);
            boolean byReference = protocol() < COMPARES_USE_BY_ITEM_PROTOCOL;
            boolean endsUse = recount && Boolean.TRUE.equals(countChangeEndsUse);
            // 1.13.1-1.14 predicted the drop and kept their use
            if (endsUse && byReference && handSlot >= 0) window.slot(handSlot).forget();
            for (int slot = 0; slot < window.size(); slot++) {
                if (slot == handSlot && sameUse && holdsRecount()) continue;
                ItemStack truth = truth(window, slot);
                Remote remote = window.slot(slot);
                if (remote.matches(truth, this::hashOfView)) continue;
                remote.sent(truth);
                SetSlotPacket packet = new SetSlotPacket(window.windowId, window.nextStateId(), (short) slot, truth);
                if (slot == handSlot && recount && byReference) sendRecount(packet);
                else send(packet);
            }
            ItemStack carried = player.getInventory().getCursorItem();
            if (!cursor.matches(carried, this::hashOfView)) {
                cursor.sent(carried);
                send(new SetCursorItemPacket(carried));
            }
            if (endsUse && !byReference) stopClientUse();
        }
    }

    private boolean sameUse(@Nullable PlayerHand hand, ItemStack held) {
        return hand != null && hand == usedHand && held.isSimilar(usedStack);
    }

    /** An old client ends its use at any rewrite of the stack in use, so a recount waits for the use to end. */
    private boolean holdsRecount() {
        return Boolean.FALSE.equals(countChangeEndsUse) && protocol() < COMPARES_USE_BY_ITEM_PROTOCOL;
    }

    /** The window slot showing the hand in use, or -1. */
    private int handSlot(ClientWindow window, @Nullable PlayerHand hand) {
        if (hand == null) return -1;
        int slot = hand == PlayerHand.OFF ? PlayerInventoryUtils.OFFHAND_SLOT : player.getHeldSlot();
        return window == inventoryWindow ? PlayerInventoryUtils.convertMinestomSlotToWindowSlot(slot) : containerSlot(slot);
    }

    private void sendRecount(SendablePacket packet) {
        useCut = true;
        recounting = true;
        try {
            send(packet);
        } finally {
            recounting = false;
        }
    }

    /** A 1.14.4+ client ends its own use when its entity's flags say so. */
    private void stopClientUse() {
        int index = MetadataDef.LivingEntity.LIVING_ENTITY_FLAGS.index();
        Metadata.Entry<?> flags = player.getMetadataPacket().entries().get(index);
        byte using = ((MetadataDef.Entry.BitMask) MetadataDef.LivingEntity.IS_HAND_ACTIVE).bitMask();
        byte value = flags != null && flags.value() instanceof Byte b ? b : 0;
        useCut = true;
        send(new EntityMetaDataPacket(player.getEntityId(), Map.of(index, Metadata.Byte((byte) (value & ~using)))));
    }

    private void sendAll(ClientWindow window) {
        List<ItemStack> items = new ArrayList<>(window.size());
        for (int slot = 0; slot < window.size(); slot++) {
            ItemStack truth = truth(window, slot);
            window.slot(slot).sent(truth);
            items.add(truth);
        }
        ItemStack carried = player.getInventory().getCursorItem();
        cursor.sent(carried);
        window.resyncAll = false;
        send(new WindowItemsPacket(window.windowId, window.nextStateId(), items, carried));
    }

    /** The window the client has in front of it; window 0 sits frozen underneath an open container, as in vanilla. */
    private @Nullable ClientWindow current() {
        AbstractInventory open = player.getOpenInventory();
        if (open == null) {
            if (container != null) {
                transfer(container);
                container = null;
            }
            return inventoryWindow;
        }
        if (container == null || container.windowId != open.getWindowId()
                || container.size() != open.getSize() + PlayerInventory.INNER_INVENTORY_SIZE) {
            // opened past the hooks: nothing about it is known
            container = ClientWindow.container(open);
            container.resyncAll = true;
        } else if (container.inventory == null) {
            container.inventory = open;
        }
        return container;
    }

    private @Nullable ClientWindow openWindow(int windowId) {
        ClientWindow window = current();
        return window != null && window != inventoryWindow && window.windowId == windowId ? window : null;
    }

    private ItemStack truth(ClientWindow window, int slot) {
        PlayerInventory inventory = player.getInventory();
        if (window == inventoryWindow) return inventory.getItemStack(PlayerInventoryUtils.convertWindow0SlotToMinestomSlot(slot));
        int size = window.containerSize();
        if (slot < size) return window.inventory.getItemStack(slot);
        return inventory.getItemStack(PlayerInventoryUtils.convertWindowSlotToMinestomSlot(slot, size));
    }

    /** Vanilla's transferState: what the container showed of the player's slots, window 0 now shows. */
    private void transfer(ClientWindow from) {
        int size = from.containerSize();
        for (int slot = size; slot < from.size(); slot++) {
            int minestom = PlayerInventoryUtils.convertWindowSlotToMinestomSlot(slot, size);
            inventoryWindow.slot(PlayerInventoryUtils.convertMinestomSlotToWindowSlot(minestom)).copyFrom(from.slot(slot));
        }
    }

    /** The open container's window slot for a player main or hotbar slot, or -1. */
    private int containerSlot(int minestomSlot) {
        if (container == null || minestomSlot < 0 || minestomSlot >= PlayerInventory.INNER_INVENTORY_SIZE) return -1;
        int size = container.containerSize();
        return minestomSlot < 9 ? size + 27 + minestomSlot : size + minestomSlot - 9;
    }

    private void forgetPlayerSlot(int minestomSlot) {
        inventoryWindow.slot(PlayerInventoryUtils.convertMinestomSlotToWindowSlot(minestomSlot)).forget();
        int containerSlot = containerSlot(minestomSlot);
        if (containerSlot >= 0) container.slot(containerSlot).forget();
    }

    // ------------------------------------------------------------------ packets that did not come from here

    /**
     * Called first on the player's send path. A raw inventory packet goes out and becomes what the client shows, under
     * this window's state id; during a click, Minestom's own cursor echo is dropped for the click's broadcast.
     */
    public @Nullable SendablePacket outgoing(@NotNull SendablePacket packet) {
        ServerPacket server = PacketShapes.unwrapStateless(packet);
        if (server == null) return packet;
        synchronized (this) {
            if (sending) return packet;
            return switch (server) {
                case SetSlotPacket p when p.windowId() == -1 -> cursorSent(p.itemStack(), packet);
                case SetSlotPacket p -> slotSent(p, packet);
                case SetCursorItemPacket p -> cursorSent(p.itemStack(), packet);
                case SetPlayerInventorySlotPacket p -> {
                    int minestom = PlayerInventoryUtils.convertPlayerInventorySlotToMinestomSlot(p.slot());
                    if (minestom >= 0) {
                        inventoryWindow.slot(PlayerInventoryUtils.convertMinestomSlotToWindowSlot(minestom)).sent(p.itemStack());
                        int containerSlot = containerSlot(minestom);
                        if (containerSlot >= 0) container.slot(containerSlot).sent(p.itemStack());
                    }
                    yield packet;
                }
                case WindowItemsPacket p -> windowSent(p);
                case OpenWindowPacket ignored -> {
                    screenOpen = false;
                    // its contents follow; opening over another container fires no close
                    if (container != null) transfer(container);
                    container = null;
                    yield packet;
                }
                case CloseWindowPacket p -> {
                    screenOpen = false;
                    if (container != null && container.windowId == p.windowId()) {
                        transfer(container);
                        container = null;
                    }
                    cursor.forget();
                    yield packet;
                }
                // the client rebuilds its inventory on these
                case RespawnPacket ignored -> forgotten(packet);
                case JoinGamePacket ignored -> forgotten(packet);
                case StartConfigurationPacket ignored -> forgotten(packet);
                default -> packet;
            };
        }
    }

    private SendablePacket forgotten(SendablePacket packet) {
        forget();
        return packet;
    }

    private @Nullable SendablePacket cursorSent(ItemStack item, SendablePacket packet) {
        if (inClick) return null;
        cursor.sent(item);
        return packet;
    }

    private SendablePacket slotSent(SetSlotPacket p, SendablePacket packet) {
        ClientWindow window = p.windowId() == 0 ? inventoryWindow
                : container != null && container.windowId == p.windowId() ? container : null;
        if (window == null || !window.has(p.slot())) return packet;
        window.slot(p.slot()).sent(p.itemStack());
        if (window == inventoryWindow) {
            int containerSlot = containerSlot(PlayerInventoryUtils.convertWindow0SlotToMinestomSlot(p.slot()));
            if (containerSlot >= 0) container.slot(containerSlot).sent(p.itemStack());
        }
        return new SetSlotPacket(p.windowId(), window.nextStateId(), p.slot(), p.itemStack());
    }

    private SendablePacket windowSent(WindowItemsPacket p) {
        List<ItemStack> items = p.items();
        ClientWindow window;
        if (p.windowId() == 0) {
            window = inventoryWindow;
        } else {
            window = container != null && container.windowId == p.windowId() ? container : null;
            if (window == null) {
                // Minestom's container update carries only the container's own slots: the player's follow
                window = new ClientWindow(p.windowId(), items.size() + PlayerInventory.INNER_INVENTORY_SIZE, null);
                container = window;
                for (int slot = items.size(); slot < window.size(); slot++) {
                    int minestom = PlayerInventoryUtils.convertWindowSlotToMinestomSlot(slot, items.size());
                    window.slot(slot).copyFrom(inventoryWindow.slot(PlayerInventoryUtils.convertMinestomSlotToWindowSlot(minestom)));
                }
            }
        }
        for (int slot = 0; slot < items.size() && window.has(slot); slot++) window.slot(slot).sent(items.get(slot));
        cursor.sent(p.carriedItem());
        window.resyncAll = false;
        return new WindowItemsPacket(p.windowId(), window.nextStateId(), items, p.carriedItem());
    }

    private void send(SendablePacket packet) {
        sending = true;
        try {
            player.sendPacket(packet);
        } finally {
            sending = false;
        }
    }

    private ItemStack.Hash hashOfView(ItemStack item) {
        return ItemStack.Hash.of(player.compat().view(item), MinecraftServer.process());
    }

    private int protocol() {
        return Polyp.getInstance().clientInfo().getProtocol(player);
    }
}
