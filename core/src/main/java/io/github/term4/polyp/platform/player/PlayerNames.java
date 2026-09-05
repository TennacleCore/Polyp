package io.github.term4.polyp.platform.player;

import io.github.term4.polyp.world.WorldPolicy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.suggestion.SuggestionCallback;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The player names a viewer may complete: those their tab lists. The app plugs its tab scope through
 * {@link #visible}; unset, whoever the viewer can see. {@link #suggest} is the one callback every player argument
 * attaches - a modern client then asks the server for it, and a 1.8 client reaches the same answer through its
 * own request - so both eras complete the same names from the same rule.
 */
public final class PlayerNames {

    private static volatile BiPredicate<Player, Player> visible = WorldPolicy::canSee;

    private PlayerNames() {}

    public static void visible(@NotNull BiPredicate<Player, Player> rule) {
        visible = rule;
    }

    /** Sorted, the viewer included - vanilla lists them too. */
    public static @NotNull List<String> of(@NotNull Player viewer) {
        List<String> names = new ArrayList<>();
        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (p == viewer || visible.test(viewer, p)) names.add(p.getUsername());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** The names matching the word under the cursor; a sender with no viewpoint completes everyone. */
    public static @NotNull SuggestionCallback suggest() {
        return (sender, context, suggestion) -> {
            // the word under the cursor: past the start (which counts the leading '/'), minus the placeholder the
            // stock listener appends when the word is still empty
            String partial = suggestion.getInput().substring(Math.max(0, suggestion.getStart() - 1)).replace("\0", "");
            Collection<String> names = sender instanceof Player viewer ? of(viewer)
                    : MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                            .map(Player::getUsername).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            for (String name : names) {
                if (name.regionMatches(true, 0, partial, 0, partial.length())) suggestion.addEntry(new SuggestionEntry(name));
            }
        };
    }
}
