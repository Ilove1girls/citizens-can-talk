package me.sshcrack.mc_talking.client.gui;

import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.TtsEngineMode;
import me.sshcrack.mc_talking.tts.ClientTtsEngine;
import me.sshcrack.mc_talking.tts.KokoroModelDownloader;
import me.sshcrack.mc_talking.tts.KokoroModelManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Screen for downloading the Kokoro TTS ONNX model (~311MB).
 */
public class KokoroDownloadScreen extends Screen {
    private static final Component TITLE = Component.literal("Downloading Kokoro TTS Model");
    private static final Component STATUS_IDLE = Component.literal("Ready to download");
    private static final Component BUTTON_DOWNLOAD = Component.literal("Download Kokoro Model");
    private static final Component BUTTON_SKIP = Component.literal("Use Piper Instead");
    private static final Component BUTTON_BACK = Component.literal("Back");

    private final Screen parent;
    private Button downloadButton;
    private Button skipButton;
    private Button backButton;
    private String statusText = "";
    private float progress = 0f;
    private boolean isDownloading = false;

    public KokoroDownloadScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int startY = this.height / 2 - 50;

        this.downloadButton = Button.builder(BUTTON_DOWNLOAD, btn -> startDownload())
                .pos(centerX - 100, startY + 80)
                .size(200, 20)
                .build();

        this.skipButton = Button.builder(BUTTON_SKIP, btn -> skipToPiper())
                .pos(centerX - 100, startY + 105)
                .size(200, 20)
                .build();

        this.backButton = Button.builder(BUTTON_BACK, btn -> this.minecraft.setScreen(parent))
                .pos(centerX - 100, startY + 135)
                .size(200, 20)
                .build();

        addRenderableWidget(this.downloadButton);
        addRenderableWidget(this.skipButton);
        addRenderableWidget(this.backButton);

        updateButtonStates();
    }

    private void startDownload() {
        if (isDownloading) return;
        isDownloading = true;
        updateButtonStates();

        KokoroModelDownloader downloader = new KokoroModelDownloader();

        CompletableFuture.runAsync(() -> {
            try {
                downloader.download(
                        p -> progress = p,
                        s -> statusText = s
                );
                this.minecraft.execute(() -> {
                    ClientTtsEngine.getInstance().init();
                    statusText = "Kokoro ready!";
                });
            } catch (Exception e) {
                McTalking.LOGGER.error("[Kokoro] Download failed", e);
                statusText = "Error: " + e.getMessage();
            } finally {
                isDownloading = false;
                this.minecraft.execute(this::updateButtonStates);
            }
        });
    }

    private void skipToPiper() {
        McTalkingConfig.INSTANCE.instance().ttsEngine = TtsEngineMode.PIPER;
        McTalkingConfig.INSTANCE.save();
        ClientTtsEngine.getInstance().init();
        this.minecraft.setScreen(parent);
    }

    private void updateButtonStates() {
        if (downloadButton != null) {
            downloadButton.active = !isDownloading;
        }
        if (skipButton != null) {
            skipButton.active = !isDownloading;
        }
        if (backButton != null) {
            backButton.active = !isDownloading;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 50;

        graphics.drawCenteredString(this.font, this.title, centerX, startY - 20, 0xFFFFFF);

        // Description
        graphics.drawCenteredString(this.font,
                Component.literal("Kokoro provides higher-quality voices (~311MB download)"),
                centerX, startY, 0xAAAAAA);
        graphics.drawCenteredString(this.font,
                Component.literal("Piper will be used automatically if Kokoro is unavailable."),
                centerX, startY + 15, 0xAAAAAA);

        int rowY = startY + 45;

        // Model status
        boolean modelReady = KokoroModelManager.isModelReady();
        String status = modelReady ? "✓ Model ready" : "✗ Model not downloaded";
        int color = modelReady ? 0x55FF55 : 0xFF5555;
        graphics.drawCenteredString(this.font, Component.literal(status), centerX, rowY, color);
        rowY += 25;

        // Status text
        if (!statusText.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(statusText), centerX, rowY, 0xFFFFFF);
            rowY += 20;
        }

        // Progress bar
        if (isDownloading && progress > 0f) {
            int barWidth = 200;
            int barHeight = 10;
            int barX = centerX - barWidth / 2;
            int barY = rowY;
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
            graphics.fill(barX, barY, barX + (int)(barWidth * progress), barY + barHeight, 0xFF55AA55);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !isDownloading;
    }

    @Override
    public void onClose() {
        if (!isDownloading) {
            this.minecraft.setScreen(parent);
        }
    }
}
