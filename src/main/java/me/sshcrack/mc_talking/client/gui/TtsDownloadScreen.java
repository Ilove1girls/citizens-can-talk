package me.sshcrack.mc_talking.client.gui;

import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.tts.ClientTtsEngine;
import me.sshcrack.mc_talking.tts.ModelDownloader;
import me.sshcrack.mc_talking.tts.TtsModelManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Screen for downloading TTS model files and native libraries.
 */
public class TtsDownloadScreen extends Screen {
    private static final Component TITLE = Component.translatable("mc_talking.tts_download.title");
    private static final Component STATUS_READY = Component.translatable("mc_talking.tts_download.status_ready");
    private static final Component STATUS_DOWNLOADING = Component.translatable("mc_talking.tts_download.status_downloading");
    private static final Component BUTTON_DOWNLOAD = Component.translatable("mc_talking.tts_download.button_download");
    private static final Component BUTTON_PLAY = Component.translatable("mc_talking.tts_download.button_play");

    private Button downloadButton;
    private Button playButton;
    private String statusText = "";
    private float progress = 0f;
    private boolean isDownloading = false;

    public TtsDownloadScreen(Screen parent) {
        super(TITLE);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 40;

        this.downloadButton = Button.builder(BUTTON_DOWNLOAD, btn -> startDownload())
                .pos(centerX - 100, startY + 80)
                .size(200, 20)
                .build();

        this.playButton = Button.builder(BUTTON_PLAY, btn -> onClose())
                .pos(centerX - 100, startY + 110)
                .size(200, 20)
                .build();

        addRenderableWidget(this.downloadButton);
        addRenderableWidget(this.playButton);

        updateButtonStates();
    }

    private void startDownload() {
        if (isDownloading) return;
        isDownloading = true;
        updateButtonStates();

        CompletableFuture.runAsync(() -> {
            try {
                ModelDownloader downloader = new ModelDownloader();
                downloader.downloadAll(
                        p -> progress = p.floatValue(),
                        s -> statusText = s
                );
            } catch (Exception e) {
                McTalking.LOGGER.error("[TTS] Download failed", e);
                statusText = "Error: " + e.getMessage();
            } finally {
                isDownloading = false;
                this.minecraft.execute(() -> {
                    updateButtonStates();
                    ClientTtsEngine.getInstance().init();
                });
            }
        });
    }

    private void updateButtonStates() {
        if (downloadButton != null) {
            downloadButton.active = !isDownloading;
            downloadButton.setMessage(isDownloading ? STATUS_DOWNLOADING : BUTTON_DOWNLOAD);
        }
        if (playButton != null) {
            playButton.active = !isDownloading;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 40;

        graphics.drawCenteredString(this.font, this.title, centerX, startY - 20, 0xFFFFFF);

        // Status rows
        int rowY = startY;
        boolean modelReady = TtsModelManager.isModelReady();
        boolean nativeReady = TtsModelManager.isNativeLibReady();

        drawStatusRow(graphics, "Piper Model + Config", modelReady, rowY);
        rowY += 20;
        drawStatusRow(graphics, "Sherpa-ONNX Runtime", nativeReady, rowY);
        rowY += 20;

        if (!statusText.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(statusText), centerX, rowY, 0xAAAAAA);
            rowY += 20;
        }

        // Progress bar
        if (isDownloading && progress > 0f) {
            int barWidth = 200;
            int barHeight = 10;
            int barX = centerX - barWidth / 2;
            int barY = rowY + 5;
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
            graphics.fill(barX, barY, barX + (int)(barWidth * progress), barY + barHeight, 0xFF55AA55);
        }
    }

    private void drawStatusRow(GuiGraphics graphics, String label, boolean ready, int y) {
        int centerX = this.width / 2;
        String status = ready ? "✓ Ready" : "✗ Missing";
        int color = ready ? 0x55FF55 : 0xFF5555;
        graphics.drawCenteredString(this.font, Component.literal(label + ": " + status), centerX, y, color);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !isDownloading;
    }
}
