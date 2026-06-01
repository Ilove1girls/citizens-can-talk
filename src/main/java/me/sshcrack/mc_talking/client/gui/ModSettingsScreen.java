package me.sshcrack.mc_talking.client.gui;

import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.TtsEngineMode;
import me.sshcrack.mc_talking.tts.ClientTtsEngine;
import me.sshcrack.mc_talking.tts.EspeakNgNativeDownloader;
import me.sshcrack.mc_talking.tts.KokoroModelDownloader;
import me.sshcrack.mc_talking.tts.KokoroModelManager;
import me.sshcrack.mc_talking.tts.ModelDownloader;
import me.sshcrack.mc_talking.stt.SttModelManager;
import me.sshcrack.mc_talking.stt.WhisperModelDownloader;
import me.sshcrack.mc_talking.tts.TtsModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Tabbed settings screen for McTalking accessible from the title screen.
 * Contains a Settings tab (TTS thread count, etc.) and a Download tab.
 */
public class ModSettingsScreen extends Screen {
    private static final Component TITLE = Component.translatable("mc_talking.mod_settings.title");
    private static final Component TAB_SETTINGS = Component.translatable("mc_talking.mod_settings.tab_settings");
    private static final Component TAB_DOWNLOAD = Component.translatable("mc_talking.mod_settings.tab_download");
    private static final Component LABEL_THREADS = Component.translatable("mc_talking.mod_settings.threads");
    private static final Component LABEL_RESTART = Component.translatable("mc_talking.mod_settings.restart_required");
    private static final Component LABEL_ENGINE = Component.translatable("mc_talking.mod_settings.engine");

    private final Screen parent;
    private int activeTab = 0;

    // Settings tab widgets
    private AbstractSliderButton threadSlider;
    private Button engineButton;
    private Button saveButton;

    // Download tab widgets
    private Button downloadButton;
    private String statusText = "";
    private float progress = 0f;
    private boolean isDownloading = false;

    public ModSettingsScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int tabY = 40;
        int tabWidth = 80;
        int tabSpacing = 5;
        int totalTabWidth = tabWidth * 2 + tabSpacing;
        int startX = (this.width - totalTabWidth) / 2;

        // Tab buttons — always visible. Active tab is disabled to show it’s selected.
        Button settingsTab = Button.builder(TAB_SETTINGS, btn -> switchTab(0))
                .pos(startX, tabY)
                .size(tabWidth, 20)
                .build();
        Button downloadTab = Button.builder(TAB_DOWNLOAD, btn -> switchTab(1))
                .pos(startX + tabWidth + tabSpacing, tabY)
                .size(tabWidth, 20)
                .build();

        settingsTab.active = activeTab != 0;
        downloadTab.active = activeTab != 1;

        addRenderableWidget(settingsTab);
        addRenderableWidget(downloadTab);

        // Only init widgets for the active tab to avoid overlap/confusion
        if (activeTab == 0) {
            initSettingsTab();
        } else {
            initDownloadTab();
        }
    }

    private void switchTab(int tab) {
        this.activeTab = tab;
        this.clearWidgets();
        this.init();
    }

    private void initSettingsTab() {
        int centerX = this.width / 2;
        int startY = 80;

        var config = McTalkingConfig.INSTANCE.instance();

        // TTS Engine selector
        engineButton = Button.builder(getEngineLabel(config.ttsEngine), btn -> cycleEngine())
                .pos(centerX - 100, startY)
                .size(200, 20)
                .build();

        // Thread count slider
        int currentThreads = Math.max(1, Math.min(16, config.ttsNumThreads));
        threadSlider = new AbstractSliderButton(centerX - 100, startY + 30, 200, 20,
                LABEL_THREADS.copy().append(": " + currentThreads), (currentThreads - 1) / 15.0) {
            @Override
            protected void updateMessage() {
                int val = (int) (this.value * 15) + 1;
                setMessage(LABEL_THREADS.copy().append(": " + val));
            }

            @Override
            protected void applyValue() {
                int val = (int) (this.value * 15) + 1;
                config.ttsNumThreads = val;
                McTalkingConfig.INSTANCE.save();
            }
        };

        // Save / close button
        saveButton = Button.builder(Component.translatable("gui.done"), btn -> onClose())
                .pos(centerX - 100, startY + 90)
                .size(200, 20)
                .build();

        addRenderableWidget(engineButton);
        addRenderableWidget(threadSlider);
        addRenderableWidget(saveButton);
    }

    private void initDownloadTab() {
        int centerX = this.width / 2;
        int startY = 80;

        this.downloadButton = Button.builder(
                        Component.translatable("mc_talking.tts_download.button_download"),
                        btn -> startDownload())
                .pos(centerX - 100, startY + 100)
                .size(200, 20)
                .build();

        Button backButton = Button.builder(Component.translatable("gui.done"), btn -> onClose())
                .pos(centerX - 100, startY + 130)
                .size(200, 20)
                .build();

        addRenderableWidget(this.downloadButton);
        addRenderableWidget(backButton);

        updateDownloadButton();
    }

    private void startDownload() {
        if (isDownloading) return;
        isDownloading = true;
        updateDownloadButton();

        CompletableFuture.runAsync(() -> {
            try {
                // Phase 1: Piper + native libs (0% → 40%)
                ModelDownloader piperDownloader = new ModelDownloader();
                piperDownloader.downloadAll(
                        p -> progress = p.floatValue() * 0.4f,
                        s -> statusText = "[Piper] " + s
                );

                // Phase 2: Kokoro model (40% → 70%)
                if (!KokoroModelManager.isModelReady()) {
                    KokoroModelDownloader kokoroDownloader = new KokoroModelDownloader();
                    kokoroDownloader.download(
                            p -> progress = 0.4f + p.floatValue() * 0.3f,
                            s -> statusText = "[Kokoro] " + s
                    );
                } else {
                    progress = 0.7f;
                }

                // Phase 3: Whisper model (70% → 100%)
                if (!SttModelManager.isModelReady()) {
                    WhisperModelDownloader whisperDownloader = new WhisperModelDownloader();
                    whisperDownloader.downloadIfMissing(
                            p -> progress = 0.7f + p * 0.3f,
                            s -> statusText = "[Whisper] " + s
                    );
                }
                progress = 1.0f;
                statusText = "[Whisper] Ready";
            } catch (Exception e) {
                McTalking.LOGGER.error("[TTS/STT] Download failed", e);
                statusText = "Error: " + e.getMessage();
            } finally {
                isDownloading = false;
                this.minecraft.execute(() -> {
                    updateDownloadButton();
                    ClientTtsEngine.getInstance().init();
                });
            }
        });
    }

    private void updateDownloadButton() {
        if (downloadButton != null) {
            downloadButton.active = !isDownloading;
            downloadButton.setMessage(isDownloading
                    ? Component.translatable("mc_talking.tts_download.status_downloading")
                    : Component.translatable("mc_talking.tts_download.button_download"));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;

        // Title
        graphics.drawCenteredString(this.font, this.title, centerX, 15, 0xFFFFFF);

        if (activeTab == 0) {
            renderSettingsTab(graphics, centerX);
        } else {
            renderDownloadTab(graphics, centerX);
        }
    }

    private void cycleEngine() {
        var config = McTalkingConfig.INSTANCE.instance();
        TtsEngineMode[] modes = TtsEngineMode.values();
        int next = (config.ttsEngine.ordinal() + 1) % modes.length;
        config.ttsEngine = modes[next];
        McTalkingConfig.INSTANCE.save();
        if (engineButton != null) {
            engineButton.setMessage(getEngineLabel(config.ttsEngine));
        }
        ClientTtsEngine.getInstance().init();
    }

    private Component getEngineLabel(TtsEngineMode mode) {
        return LABEL_ENGINE.copy().append(": ").append(Component.translatable("mc_talking.engine." + mode.name().toLowerCase()));
    }

    private void renderSettingsTab(GuiGraphics graphics, int centerX) {
        // Restart warning below the slider
        int warnY = 80 + 60;
        graphics.drawCenteredString(this.font, LABEL_RESTART, centerX, warnY, 0xFFAA55);
    }

    private void renderDownloadTab(GuiGraphics graphics, int centerX) {
        int startY = 80;
        int rowY = startY;

        boolean modelReady = TtsModelManager.isModelReady();
        boolean nativeReady = TtsModelManager.isNativeLibReady();
        boolean kokoroReady = KokoroModelManager.isModelReady();
        boolean espeakNativeReady = EspeakNgNativeDownloader.isNativeLibPresent();

        drawStatusRow(graphics, "Piper Model + Config", modelReady, rowY);
        rowY += 20;
        drawStatusRow(graphics, "Sherpa-ONNX Runtime", nativeReady, rowY);
        rowY += 20;
        drawStatusRow(graphics, "Espeak-NG Native", espeakNativeReady, rowY);
        rowY += 20;
        drawStatusRow(graphics, "Kokoro Model", kokoroReady, rowY);
        rowY += 20;

        if (!statusText.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(statusText), centerX, rowY, 0xAAAAAA);
            rowY += 20;
        }

        if (isDownloading && progress > 0f) {
            int barWidth = 200;
            int barHeight = 10;
            int barX = centerX - barWidth / 2;
            int barY = rowY + 5;
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
            graphics.fill(barX, barY, barX + (int) (barWidth * progress), barY + barHeight, 0xFF55AA55);
        }
    }

    private void drawStatusRow(GuiGraphics graphics, String label, boolean ready, int y) {
        int centerX = this.width / 2;
        String status = ready ? "\u2713 Ready" : "\u2717 Missing";
        int color = ready ? 0x55FF55 : 0xFF5555;
        graphics.drawCenteredString(this.font, Component.literal(label + ": " + status), centerX, y, color);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !isDownloading;
    }
}
