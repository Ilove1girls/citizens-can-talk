package me.sshcrack.mc_talking.tts;

import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages paths for TTS model files and native libraries.
 * Files are stored under {@code <game-dir>/mc_talking/}.
 */
public class TtsModelManager {
    private static final String BASE_DIR = "mc_talking";
    private static final String MODELS_DIR = "models";
    private static final String NATIVE_DIR = "native";
    private static final String ESPEAK_DIR = "espeak-ng-data";

    private static final String MODEL_NAME = "en_US-libritts-high.onnx";
    private static final String TOKENS_NAME = "tokens.txt";

    public static Path getBasePath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(BASE_DIR);
    }

    public static Path getModelPath() {
        return getBasePath().resolve(MODELS_DIR).resolve(MODEL_NAME);
    }

    public static Path getTokensPath() {
        return getBasePath().resolve(MODELS_DIR).resolve(TOKENS_NAME);
    }

    public static Path getEspeakDataPath() {
        return getBasePath().resolve(ESPEAK_DIR);
    }

    public static Path getNativeLibPath() {
        return getBasePath().resolve(NATIVE_DIR);
    }

    public static Path getNativeJarPath() {
        String platform = detectPlatform();
        return getNativeLibPath().resolve("sherpa-onnx-native-" + platform + ".jar");
    }

    public static boolean isModelReady() {
        return Files.exists(getModelPath()) && Files.exists(getTokensPath())
                && Files.exists(getEspeakDataPath());
    }

    public static boolean isNativeLibReady() {
        Path nativePath = getNativeLibPath();
        Path jniLib = nativePath.resolve(getJniLibName());
        Path ortLib = nativePath.resolve(getOrtLibName());
        return Files.exists(jniLib) && Files.exists(ortLib);
    }

    public static boolean hasGpuRuntime() {
        // GPU runtime is bundled in the same native JAR for now;
        // CUDA support would require a separate download.
        return false;
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String osName;
        if (os.contains("win")) {
            osName = "win";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osName = "osx";
        } else {
            osName = "linux";
        }

        String archName;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            archName = "aarch64";
        } else {
            archName = "x64";
        }

        return osName + "-" + archName;
    }

    private static String getJniLibName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "sherpa-onnx-jni.dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            return "libsherpa-onnx-jni.dylib";
        } else {
            return "libsherpa-onnx-jni.so";
        }
    }

    private static String getOrtLibName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "onnxruntime.dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            return "libonnxruntime.dylib";
        } else {
            return "libonnxruntime.so";
        }
    }
}
