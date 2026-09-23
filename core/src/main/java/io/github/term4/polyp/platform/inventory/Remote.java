package io.github.term4.polyp.platform.inventory;

import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/** What a client shows in one slot: the server item last sent, the hash the client reported, or nothing known. */
final class Remote {

    private @Nullable ItemStack sent;
    private ItemStack.@Nullable Hash reported;

    void sent(@NotNull ItemStack item) {
        sent = item;
        reported = null;
    }

    void reported(@NotNull ItemStack.Hash hash) {
        sent = null;
        reported = hash;
    }

    void forget() {
        sent = null;
        reported = null;
    }

    void copyFrom(@NotNull Remote other) {
        sent = other.sent;
        reported = other.reported;
    }

    /** The server item the client shows, when known exactly. */
    @Nullable ItemStack sent() {
        return sent;
    }

    /** A report is of the client's view of an item; a match is kept as the item, as vanilla's RemoteSlot does. */
    boolean matches(@NotNull ItemStack truth, @NotNull Function<ItemStack, ItemStack.Hash> hashOfView) {
        if (sent != null) return sent == truth || sent.equals(truth);
        if (reported == null || !reported.equals(hashOfView.apply(truth))) return false;
        sent(truth);
        return true;
    }
}
