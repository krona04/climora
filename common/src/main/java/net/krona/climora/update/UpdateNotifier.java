package net.krona.climora.update;

import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/**
 * Tells the player in chat, once per launch, that a newer release exists. Waits a few seconds after the
 * player enters a world so the message is not lost among the join messages. Client only.
 */
public final class UpdateNotifier {
    private static final int DELAY_TICKS = 100;

    private static int worldTicks;
    private static boolean shown;

    private UpdateNotifier() {
    }

    public static void init() {
        ClientTickEvent.CLIENT_POST.register(UpdateNotifier::tick);
    }

    private static void tick(Minecraft minecraft) {
        if (shown) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            worldTicks = 0;
            return;
        }
        UpdateChecker.Update update = UpdateChecker.result();
        if (update == null || ++worldTicks < DELAY_TICKS) {
            return;
        }
        shown = true;
        Style link = Style.EMPTY
                .withColor(update.type() == UpdateChecker.Type.RELEASE ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, update.url()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(update.url())));
        minecraft.gui.getChat().addMessage(Component.translatable("climora.update.available" + update.type().key(),
                UpdateChecker.MOD_NAME, update.latest(), update.current()).withStyle(link));
    }
}
