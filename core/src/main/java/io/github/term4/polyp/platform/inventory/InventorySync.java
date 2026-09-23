package io.github.term4.polyp.platform.inventory;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.platform.PacketShapes;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.platform.player.PlayListeners;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.entity.GameMode;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.listener.CreativeInventoryActionListener;
import net.minestom.server.listener.UseItemListener;
import net.minestom.server.listener.WindowListener;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.server.network.packet.client.play.ClientCreativeInventoryActionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import net.minestom.server.network.packet.client.play.ClientUseItemPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.CloseWindowPacket;
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

    private final OptimizedPlayer player;
    private final ClientWindow inventoryWindow = new ClientWindow(0, PlayerInventory.INVENTORY_SIZE, null);
    private @Nullable ClientWindow container;
    private final Remote cursor = new Remote();
    private final LegacyClicks legacyClicks = new LegacyClicks();
    private int legacyWindow = -1;
    private boolean inClick;
    /** Minestom ends a close with a full resend of window 0; the close already carried the view over. */
    private boolean closing;
    private boolean sending;

    public InventorySync(@NotNull OptimizedPlayer player) {
        this.player = player;
        inventoryWindow.resyncAll = true;
    }

    public static void install(@NotNull Polyp polyp) {
        EventNode<@NotNull Event> node = EventNode.all("polyp:inventory-sync");
        // even a packet another listener cancelled: the client already applied what it sent
        node.addListener(EventListener.builder(PlayerPacketEvent.class).ignoreCancelled(false).handler(e -> {
            if (!(e.getPlayer() instanceof OptimizedPlayer op)) return;
            InventorySync sync = op.inventorySync();
            switch (e.getPacket()) {
                case ClientClickWindowPacket p -> sync.beforeClick(p);
                case ClientCreativeInventoryActionPacket p -> sync.creative(p.slot(), p.item());
                case ClientPlayerBlockPlacementPacket p -> sync.handUsed(p.hand());
                case ClientPlayerActionPacket p when p.status() == ClientPlayerActionPacket.Status.DROP_ITEM ->
                        sync.dropped(false);
                case ClientPlayerActionPacket p when p.status() == ClientPlayerActionPacket.Status.DROP_ITEM_STACK ->
                        sync.dropped(true);
                default -> {}
            }
        }).build());
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

        PlayListeners.wrap(ClientClickWindowPacket.class, WindowListener::clickWindowListener, (packet, player, next) -> {
            if (!(player instanceof OptimizedPlayer op)) {
                next.accept(packet, player);
                return;
            }
            InventorySync sync = op.inventorySync();
            // vanilla ignores a click on any window but the one the server has open; Minestom applies it anyway
            if (sync.stale(packet.windowId())) return;
            sync.clicking(true);
            try {
                next.accept(packet, player);
            } finally {
                sync.clicking(false);
                sync.broadcast();
            }
        });
        PlayListeners.wrap(ClientCreativeInventoryActionPacket.class, CreativeInventoryActionListener::listener,
                (packet, player, next) -> {
                    next.accept(packet, player);
                    if (player instanceof OptimizedPlayer op) op.inventorySync().broadcast();
                });
        PlayListeners.wrap(ClientUseItemPacket.class, UseItemListener::useItemListener, (packet, player, next) -> {
            next.accept(packet, player);
            if (player instanceof OptimizedPlayer op) op.inventorySync().afterUse(packet.hand());
        });
    }

    /** Vanilla resyncs the hand after a use that did not start using; one that did must not see its hand rewritten. */
    public void afterUse(@NotNull PlayerHand hand) {
        if (!player.isUsingItem()) handUsed(hand);
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

    /** The click as a 1.8 client ran it on what it shows; false when that cannot be followed. */
    private boolean predictLegacy(ClientWindow window, ClientClickWindowPacket click) {
        if (click.windowId() != legacyWindow) {
            legacyClicks.resetDrag();
            legacyWindow = click.windowId();
        }
        int mode = click.clickType().ordinal();
        int slot = click.slot();
        if (window == inventoryWindow && mode == 4) {
            // ViaBackwards replays a 1.8 Q drop as a throw here, one the client never predicted
            if (window.has(slot)) window.slot(slot).forget();
            return true;
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
        synchronized (this) {
            if (container != null && container.inventory == inventory) {
                transfer(container);
                container = null;
            }
            // 1.8 empties its cursor on close, a modern client keeps it on window 0 until told
            cursor.forget();
            closing = true;
        }
    }

    /** Forgets everything; the next broadcast sends the whole inventory. */
    public void forget() {
        synchronized (this) {
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
            if (inClick) {
                window.slot(windowSlot).forget();
                return;
            }
            window.slot(windowSlot).sent(item);
            send(new SetSlotPacket(window.windowId, window.nextStateId(), (short) windowSlot, item));
        }
    }

    // ------------------------------------------------------------------ the broadcast

    /** Sends every slot of the open window the client does not already show, and the cursor. */
    public void broadcast() {
        synchronized (this) {
            closing = false;
            ClientWindow window = current();
            if (window == null) return;
            if (window.resyncAll) {
                sendAll(window);
                return;
            }
            for (int slot = 0; slot < window.size(); slot++) {
                ItemStack truth = truth(window, slot);
                Remote remote = window.slot(slot);
                if (remote.matches(truth, this::hashOfView)) continue;
                remote.sent(truth);
                send(new SetSlotPacket(window.windowId, window.nextStateId(), (short) slot, truth));
            }
            ItemStack carried = player.getInventory().getCursorItem();
            if (!cursor.matches(carried, this::hashOfView)) {
                cursor.sent(carried);
                send(new SetCursorItemPacket(carried));
            }
        }
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
                    // its contents follow; opening over another container fires no close
                    if (container != null) transfer(container);
                    container = null;
                    yield packet;
                }
                case CloseWindowPacket p -> {
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
