package net.krona.climora.update;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import net.krona.climora.config.ClimoraConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One chat line each time the player enters a world or a server. The first time in a world or on a server
 * it is a welcome with a hint where to start; after that, a short line about supporting the mod on Ko-fi.
 *
 * "First time" is remembered by this client in {@code config/climora-seen-worlds.txt}, so it works the same
 * in singleplayer, on servers with the mod and on servers without it. Shown a few seconds after joining so it
 * isn't lost among the join messages. Turn it off with {@code showSupportMessage} in the config. Client only.
 */
public final class SupportNotifier {
    public static final String URL = "https://ko-fi.com/krona04";
    private static final String MOD_NAME = "Climora";
    private static final int DELAY_TICKS = 60;

    private static int worldTicks;
    private static boolean shownInThisWorld;
    private static Set<String> seen;

    private SupportNotifier() {
    }

    public static void init() {
        ClientTickEvent.CLIENT_POST.register(SupportNotifier::tick);
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            // Left the world: show the line again on the next join
            worldTicks = 0;
            shownInThisWorld = false;
            return;
        }
        if (shownInThisWorld || minecraft.isPaused() || ++worldTicks < DELAY_TICKS) {
            return;
        }
        shownInThisWorld = true;
        if (!ClimoraConfig.client().showSupportMessage) {
            return;
        }
        Component link = Component.literal("Ko-fi").withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, URL))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("climora.support.hover"))));
        String key = firstVisit(minecraft) ? "climora.welcome" : "climora.support.message";
        minecraft.gui.getChat().addMessage(Component.translatable(key, MOD_NAME, link).withStyle(ChatFormatting.GOLD));
    }

    /** True the first time this client enters this world or this server with the mod; remembers it. */
    private static boolean firstVisit(Minecraft minecraft) {
        String id = worldId(minecraft);
        if (id == null) {
            return false;
        }
        Path file = Platform.getConfigFolder().resolve("climora-seen-worlds.txt");
        if (seen == null) {
            seen = new HashSet<>();
            try {
                if (Files.exists(file)) {
                    seen.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // An unreadable file only means the welcome may show once more
            }
        }
        if (!seen.add(id)) {
            return false;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, List.of(id), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Not remembered on disk: the welcome shows again next launch, nothing worse
        }
        return true;
    }

    /** The save folder in singleplayer, the address on a server; null when neither is known (Realms). */
    private static String worldId(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() != null) {
            Path root = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return root.getFileName() == null ? null : "world:" + root.getFileName();
        }
        ServerData server = minecraft.getCurrentServer();
        return server == null || server.ip == null ? null : "server:" + server.ip.toLowerCase(Locale.ROOT);
    }
}
