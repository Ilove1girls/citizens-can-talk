package me.sshcrack.mc_talking.client.gui;

import me.sshcrack.mc_talking.McTalking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Adds a "Voice Model" button to the Minecraft title screen.
 */
public class TitleScreenButtonHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen titleScreen)) {
            return;
        }

        // Small button in the bottom-left corner, above the accessibility button
        int x = 10;
        int y = titleScreen.height - 52;

        Button voiceButton = Button.builder(
                        Component.translatable("mc_talking.title.mod_settings_button"),
                        btn -> {
                            try {
                                Minecraft.getInstance().setScreen(new ModSettingsScreen(titleScreen));
                            } catch (Exception e) {
                                McTalking.LOGGER.error("[TitleScreen] Failed to open ModSettingsScreen", e);
                            }
                        }
                )
                .pos(x, y)
                .size(90, 20)
                .build();

        event.addListener(voiceButton);
    }
}
