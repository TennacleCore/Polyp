package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.packet.client.play.ClientTabCompletePacket;
import net.minestom.server.network.packet.server.play.TabCompletePacket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A 1.8 client completes, server-side, what a modern one completes from its tree: every literal, at every depth. */
class LegacyTabCompleteTest extends HeadlessServerTest {

    private static final Command PROBE = new Command("tabprobe");
    private static final Command HIDDEN = new Command("tabhidden");

    @BeforeAll
    static void install() {
        LegacyTabCompleteFix.install();
        PROBE.addSyntax((s, c) -> {}, ArgumentType.Literal("sub"), ArgumentType.Word("w").from("alpha", "beta"));
        PROBE.addSyntax((s, c) -> {}, ArgumentType.Literal("other"), ArgumentType.Word("free"));
        PROBE.addSyntax((s, c) -> {}, ArgumentType.Word("free"), ArgumentType.StringArray("tail"));
        var custom = ArgumentType.Word("custom");
        custom.setSuggestionCallback((sender, ctx, suggestion) -> suggestion.addEntry(new SuggestionEntry("handmade")));
        PROBE.addSyntax((s, c) -> {}, ArgumentType.Literal("cb"), custom);
        Command child = new Command("child", "kid");
        child.addSyntax((s, c) -> {}, ArgumentType.Literal("deep"));
        PROBE.addSubcommand(child);
        HIDDEN.setCondition((sender, commandString) -> false);
        MinecraftServer.getCommandManager().register(PROBE);
        MinecraftServer.getCommandManager().register(HIDDEN);
    }

    @AfterAll
    static void uninstall() {
        MinecraftServer.getCommandManager().unregister(PROBE);
        MinecraftServer.getCommandManager().unregister(HIDDEN);
    }

    private static List<TabCompletePacket> ask(FakePlayer p, String text) {
        p.sent.clear();
        MinecraftServer.getPacketListenerManager().processClientPacket(new ClientTabCompletePacket(7, text), p.player.getPlayerConnection());
        return p.sent(TabCompletePacket.class);
    }

    private static List<String> names(List<TabCompletePacket> packets) {
        return packets.stream().flatMap(t -> t.matches().stream()).map(m -> m.match()).toList();
    }

    @Test
    void everyDepthCompletes() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(0.5, 65, 0.5), "TabDepth");
        try {
            List<TabCompletePacket> root = ask(p, "/tabpr");
            assertEquals(List.of("tabprobe"), names(root), "the command name; the hidden one is not offered");
            assertEquals(1, root.getFirst().start());
            assertEquals(5, root.getFirst().length());

            assertEquals(List.of("other", "sub"), names(ask(p, "/tabprobe ")).stream().filter(n -> !n.equals("cb") && !n.equals("child") && !n.equals("kid")).toList(),
                    "the literals a syntax starts with");
            assertTrue(names(ask(p, "/tabprobe ")).containsAll(List.of("cb", "child", "kid")), "and the subcommands, by every name");

            List<TabCompletePacket> sub = ask(p, "/tabprobe su");
            assertEquals(List.of("sub"), names(sub));
            assertEquals(10, sub.getFirst().start(), "start counts the leading slash");
            assertEquals(2, sub.getFirst().length());

            assertEquals(List.of("alpha", "beta"), names(ask(p, "/tabprobe sub ")), "a restricted word's choices");
            assertEquals(List.of("beta"), names(ask(p, "/tabprobe sub b")));
            assertEquals(List.of("deep"), names(ask(p, "/tabprobe kid ")), "a subcommand by its alias, then its literal");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void freeWordsDefer() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(2.5, 65, 2.5), "TabFree");
        try {
            assertTrue(ask(p, "/tabprobe other x").isEmpty(), "a free word has no literal choices and no callback: nothing is sent");
            assertTrue(ask(p, "/tabprobe x y z").isEmpty(), "past a space-taking argument the line is its own");
            assertEquals(List.of("handmade"), names(ask(p, "/tabprobe cb h")), "an author's own callback still answers");
            assertTrue(ask(p, "/tabhid").isEmpty(), "a condition the sender fails hides the name");
        } finally {
            p.player.remove();
        }
    }
}
