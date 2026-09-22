package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.platform.player.PlayListeners;
import org.jetbrains.annotations.Nullable;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandSyntax;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentEnum;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.condition.CommandCondition;
import net.minestom.server.listener.TabCompleteListener;
import net.minestom.server.network.packet.client.play.ClientTabCompletePacket;
import net.minestom.server.network.packet.server.play.TabCompletePacket;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tab completion for legacy clients, at every depth. A 1.8 client asks the server to complete what a modern client
 * completes from its command tree: command names, subcommands, literals, restricted words and enum constants.
 * Minestom's {@code TabCompleteListener} answers only an argument's own {@code SuggestionCallback}, and a literal
 * has none, so a 1.8 client sees nothing past a command name unless an author set one by hand. This walks the
 * registered commands along the typed words and offers, at the cursor, exactly what {@code GraphConverter} sends a
 * modern client as literal nodes; anything it has no answer for goes to the stock listener, so an explicit
 * callback still wins. Replaces the {@link ClientTabCompletePacket} listener server-wide.
 *
 * <p>The approach - literal nodes suggesting themselves at every depth, as Brigadier's do - follows
 * KohanMathers/BrigadierStom, applied to Minestom's own graph rather than a second dispatcher. Upstream Minestom
 * PR #3252 carries the command-name half.
 */
public final class LegacyTabCompleteFix {

    private LegacyTabCompleteFix() {}

    private static volatile @Nullable Runnable handle;

    /** Removes this fix's link; whatever else holds the slot stays. */
    public static void uninstall() {
        Runnable installed = handle;
        handle = null;
        if (installed != null) installed.run();
    }

    public static void install() {
        if (handle != null) return;
        handle = PlayListeners.wrap(ClientTabCompletePacket.class, TabCompleteListener::listener, (packet, player, next) -> {
            final String text = packet.text();
            final String line = text.startsWith("/") ? text.substring(1) : text;
            final int lastSpace = line.lastIndexOf(' ');
            final String cursor = line.substring(lastSpace + 1);
            final List<String> typed = lastSpace < 0 ? List.of() : List.of(line.substring(0, lastSpace).split(" "));
            final List<TabCompletePacket.Match> matches = candidates(player, typed).stream()
                    .filter(name -> name.regionMatches(true, 0, cursor, 0, cursor.length()))
                    .map(name -> new TabCompletePacket.Match(name, null))
                    .toList();
            if (matches.isEmpty()) {
                next.accept(packet, player); // an argument's own callback, or nothing
                return;
            }
            // start counts the leading '/': the stock listener's lastSpace + 2
            player.sendPacket(new TabCompletePacket(packet.transactionId(), lastSpace + 2, cursor.length(), matches));
        });
    }

    /** What sits at the cursor after {@code typed}: command names at the root, else the literals the graph has there. */
    private static Set<String> candidates(CommandSender sender, List<String> typed) {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (typed.isEmpty()) {
            for (Command command : MinecraftServer.getCommandManager().getCommands()) {
                if (usable(command, sender)) out.addAll(Arrays.asList(command.getNames()));
            }
            return out;
        }
        Command command = MinecraftServer.getCommandManager().getCommand(typed.getFirst());
        if (command != null && usable(command, sender)) literalsAt(command, sender, typed.subList(1, typed.size()), out);
        return out;
    }

    private static boolean usable(Command command, CommandSender sender) {
        CommandCondition condition = command.getCondition();
        return condition == null || condition.canUse(sender, null);
    }

    /** The choices {@code typed} words into {@code command} arrive at, across its syntaxes and subcommands. */
    private static void literalsAt(Command command, CommandSender sender, List<String> typed, Set<String> out) {
        for (Command sub : command.getSubcommands()) {
            if (!usable(sub, sender)) continue;
            if (typed.isEmpty()) out.addAll(Arrays.asList(sub.getNames()));
            else if (Arrays.asList(sub.getNames()).contains(typed.getFirst())) literalsAt(sub, sender, typed.subList(1, typed.size()), out);
        }
        for (CommandSyntax syntax : command.getSyntaxes()) {
            Argument<?>[] args = syntax.getArguments();
            int i = 0;
            // one word per argument up to the cursor; a space-taking argument before it owns the rest of the line
            while (i < typed.size() && i < args.length && !args[i].allowSpace() && accepts(args[i], sender, typed.get(i))) i++;
            if (i == typed.size() && i < args.length) out.addAll(choices(args[i]));
        }
    }

    private static boolean accepts(Argument<?> argument, CommandSender sender, String word) {
        try {
            argument.parse(sender, word);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** What {@code GraphConverter} sends a modern client as literal children of this argument. */
    private static List<String> choices(Argument<?> argument) {
        if (argument instanceof ArgumentLiteral literal) return List.of(literal.getId());
        if (argument instanceof ArgumentEnum<?> enumeration) return enumeration.entries();
        if (argument instanceof ArgumentWord word && word.hasRestrictions()) return Arrays.asList(word.getRestrictions());
        return List.of();
    }
}
