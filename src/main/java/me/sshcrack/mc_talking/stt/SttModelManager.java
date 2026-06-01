package me.sshcrack.mc_talking.stt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Server-side path manager for STT model files.
 * Does NOT use Minecraft client classes — safe for dedicated servers.
 * Uses the working directory (user.dir) as the game directory fallback.
 */
public class SttModelManager {
    private static final String BASE_DIR = "mc_talking";
    private static final String MODELS_DIR = "models";
    private static final String NATIVE_DIR = "native";
    private static final String WHISPER_DIR = "sherpa-onnx-whisper-base.en";

    private static final String ENCODER_NAME = "base.en-encoder.int8.onnx";
    private static final String DECODER_NAME = "base.en-decoder.int8.onnx";
    private static final String TOKENS_NAME = "base.en-tokens.txt";

    public static Path getBasePath() {
        return Paths.get(System.getProperty("user.dir")).resolve(BASE_DIR);
    }

    public static Path getModelsPath() {
        return getBasePath().resolve(MODELS_DIR);
    }

    public static Path getWhisperPath() {
        return getModelsPath().resolve(WHISPER_DIR);
    }

    public static Path getEncoderPath() {
        return getWhisperPath().resolve(ENCODER_NAME);
    }

    public static Path getDecoderPath() {
        return getWhisperPath().resolve(DECODER_NAME);
    }

    public static Path getTokensPath() {
        return getWhisperPath().resolve(TOKENS_NAME);
    }

    public static Path getNativeLibPath() {
        return getBasePath().resolve(NATIVE_DIR);
    }

    // Expected file sizes from the official k2-fsa release.
    // Used to detect corrupted/partial extractions.
    private static final long MIN_ENCODER_SIZE = 29_000_000L;   // base.en-encoder.int8.onnx  ~29 MB
    private static final long MIN_DECODER_SIZE = 130_000_000L;  // base.en-decoder.int8.onnx  ~130 MB
    private static final long MIN_TOKENS_SIZE = 800_000L;       // base.en-tokens.txt         ~835 KB

    public static boolean isModelReady() {
        return isValidFile(getEncoderPath(), MIN_ENCODER_SIZE)
                && isValidFile(getDecoderPath(), MIN_DECODER_SIZE)
                && isValidFile(getTokensPath(), MIN_TOKENS_SIZE);
    }

    private static boolean isValidFile(Path path, long minSize) {
        try {
            return Files.exists(path) && Files.size(path) >= minSize;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isNativeLibReady() {
        Path nativePath = getNativeLibPath();
        Path jniLib = nativePath.resolve(getJniLibName());
        Path ortLib = nativePath.resolve(getOrtLibName());
        return isValidNativeLib(jniLib) && isValidNativeLib(ortLib);
    }

    private static boolean isValidNativeLib(Path path) {
        try {
            return Files.exists(path) && Files.size(path) > 0;
        } catch (Exception e) {
            return false;
        }
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
