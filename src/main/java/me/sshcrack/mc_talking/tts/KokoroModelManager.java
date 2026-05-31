package me.sshcrack.mc_talking.tts;

import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * Manages paths for Kokoro TTS model files.
 */
public class KokoroModelManager {
    private static final String BASE_DIR = "mc_talking";
    private static final String KOKORO_DIR = "kokoro";

    public static Path getBasePath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(BASE_DIR).resolve(KOKORO_DIR);
    }

    public static Path getModelPath() {
        return getBasePath().resolve("kokoro-v1.0.onnx");
    }

    public static Path getEspeakNgPath() {
        String exe = System.getProperty("os.name").toLowerCase().contains("win")
                ? "espeak-ng.exe" : "espeak-ng";
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(BASE_DIR).resolve("native").resolve("espeak").resolve(exe);
    }

    public static boolean isModelReady() {
        return java.nio.file.Files.exists(getModelPath());
    }
}
